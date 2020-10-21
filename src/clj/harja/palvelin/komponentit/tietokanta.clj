(ns harja.palvelin.komponentit.tietokanta
  (:require [com.stuartsierra.component :as component]
            [jeesql.autoreload :as autoreload]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]

            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.kyselyt.status :as status-q]
            [harja.tyokalut.muunnos :as muunnos])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource DataSources)
           (java.util Properties)))

(s/def ::db-tapahtuma boolean?)

(defn- db-tunnistin->db-tila-tapahtuma [tunnistin]
  (keyword (str (name tunnistin) "-tila")))

(defn tarkkaile-kantaa [db
                        lopeta-tarkkailu-kanava
                        {:keys [paivitystiheys-ms kyselyn-timeout-ms]}
                        tapahtuma-julkaisija]
  (tapahtuma-apurit/tarkkaile lopeta-tarkkailu-kanava
                          paivitystiheys-ms
                          (fn []
                            (try (with-open [c (.getConnection (:datasource db))
                                             stmt (jdbc/prepare-statement c
                                                                          "SELECT 1;"
                                                                          {:timeout (muunnos/ms->s kyselyn-timeout-ms)
                                                                           :result-type :forward-only
                                                                           :concurrency :read-only})
                                             rs (.executeQuery stmt)]
                                   (let [kanta-ok? (if (.next rs)
                                                     (= 1 (.getObject rs 1))
                                                     false)]
                                     (tapahtuma-julkaisija kanta-ok?)))
                                 (catch Throwable t
                                   (log/error "Kannan tilan tarkastaminen epäonnistui: " (.getMessage t) "\nStackTrace: " (.printStackTrace t))
                                   (tapahtuma-julkaisija false))))))

(defn tarkkaile-replicaa [db-replica lopeta-tarkkailu-kanava
                          {:keys [paivitystiheys-ms replikoinnin-max-viive-ms]}
                          tapahtuma-julkaisija]
  (tapahtuma-apurit/tarkkaile lopeta-tarkkailu-kanava
                          paivitystiheys-ms
                          (fn []
                            (let [replikoinnin-viive (try (status-q/hae-replikoinnin-viive db-replica)
                                                          (catch Throwable t
                                                            (log/error "Replican tilan tarkastaminen epäonnistui: " (.getMessage t) "\nStackTrace: " (.printStackTrace t))
                                                            :virhe))
                                  replica-ok? (boolean (and (not= :virhe replikoinnin-viive)
                                                            (not (and replikoinnin-viive
                                                                      (> replikoinnin-viive replikoinnin-max-viive-ms)))))]
                              (tapahtuma-julkaisija replica-ok?)))))

(defn luo-db-tapahtumat [this db-nimi tarkkailun-timeout-arvot lopeta-tarkkailu-kanava]
  (let [tapahtuma (db-tunnistin->db-tila-tapahtuma db-nimi)
        tapahtuma-julkaisija (tapahtuma-apurit/tapahtuma-datan-spec (tapahtuma-apurit/tapahtuma-julkaisija tapahtuma)
                                                            ::db-tapahtuma)]
    (case db-nimi
      :db (tarkkaile-kantaa this lopeta-tarkkailu-kanava tarkkailun-timeout-arvot tapahtuma-julkaisija)
      :db-replica (tarkkaile-replicaa this lopeta-tarkkailu-kanava tarkkailun-timeout-arvot tapahtuma-julkaisija)
      nil)))

;; Tietokanta on pelkkä clojure.java.jdbc kirjaston mukainen db-spec, joka sisältää pelkään yhteyspoolin
(defrecord Tietokanta [datasource db-nimi tarkkailun-timeout-arvot kehitysmoodi]
  component/Lifecycle
  (start [this]
    (let [lopeta-tarkkailu-kanava (async/chan)]
      (when db-nimi
        (luo-db-tapahtumat this db-nimi tarkkailun-timeout-arvot lopeta-tarkkailu-kanava))
      (when kehitysmoodi
        (autoreload/start-autoreload))
      (assoc this ::lopeta-tarkkailu-kanava lopeta-tarkkailu-kanava)))
  (stop [this]
    (when db-nimi
      (tapahtuma-apurit/julkaise-tapahtuma (db-tunnistin->db-tila-tapahtuma db-nimi) :suljetaan)
      (async/>!! (:lopeta-tarkkailu-kanava this) true))
    (DataSources/destroy  datasource)
    (when kehitysmoodi
      (autoreload/stop-autoreload))
    this))


(defn luo-tietokanta
  "Luodaan Harja järjestelmälle tietokantakomponentti käyttäen yhteyspoolia PostgreSQL tietokantaan."
  ([asetukset]
   (luo-tietokanta asetukset false))
  ([{:keys [palvelin portti tietokanta kayttaja salasana yhteyspoolin-koko tarkkailun-timeout-arvot tarkkailun-nimi]} kehitysmoodi]
   ;; c3p0 voi käyttää loggaukseen esimerkiksi slf4j:sta, mutta fallbackina toimii aina stderr.
   ;; Tämä on ongelmallista, koska oletuksena c3p0 tuntuu loggaavan (lähes) kaiken, emmekä me halua
   ;; että stderriin tungetaan INFO-tason viestejä. Siksi tässä asetetaan ensiksi loggausmekanismi
   ;; fallbackiksi (eli System.error), ja loggaustaso SEVERE:ksi.
   ;; http://www.mchange.com/projects/c3p0/#configuring_logging
   (System/setProperties
    (doto (new Properties (System/getProperties))
      (.put "com.mchange.v2.log.MLog", "com.mchange.v2.log.FallbackMLog")
      (.put "com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL", "SEVERE")))
   (->Tietokanta (doto (ComboPooledDataSource.)
                   (.setDriverClass "org.postgresql.Driver")
                   (.setJdbcUrl (str "jdbc:postgresql://" palvelin ":" portti "/" tietokanta))
                   (.setUser kayttaja)
                   (.setPassword salasana)
                   (.setMaxPoolSize (or yhteyspoolin-koko 16))

                   ;; ylimääräiset yhteydet suljetaan puolen tunnin inaktiivisuuden jälkeen
                   (.setMaxIdleTimeExcessConnections (* 30 60))
                   ;; yhteyden pisin inaktiivisuusaika 3 tuntia
                   (.setMaxIdleTime (* 3 60 60))

                   ;; Testataan yhteyden status ennen connection poolista poimintaa,
                   ;; jotta selvitään tietokannan uudelleenkäynnistyksestä ilman poikkeuksia sovellukselle
                   (.setPreferredTestQuery "SELECT 1")
                   (.setTestConnectionOnCheckout true))
                 tarkkailun-nimi
                 tarkkailun-timeout-arvot
                 kehitysmoodi)))
