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

(defn- kantayhteys-ok? [db kyselyn-timeout-ms]
  (try (with-open [c (.getConnection (:datasource db))
                   stmt (jdbc/prepare-statement c
                                                "SELECT 1;"
                                                {:timeout (muunnos/ms->s kyselyn-timeout-ms)
                                                 :result-type :forward-only
                                                 :concurrency :read-only})
                   rs (.executeQuery stmt)]
         (if (.next rs)
           (= 1 (.getObject rs 1))
           false))
       (catch Throwable t
         (log/error "Kannan tilan tarkastaminen epäonnistui: " (.getMessage t) "\nStackTrace: " (.printStackTrace t))
         false)))

(defn- replica-ok? [db-replica replikoinnin-max-viive-ms]
  (let [replikoinnin-viive (try (status-q/hae-replikoinnin-viive db-replica)
                                (catch Throwable t
                                  (log/error "Replican tilan tarkastaminen epäonnistui: " (.getMessage t) "\nStackTrace: " (.printStackTrace t))
                                  :virhe))]
    (boolean (and (not= :virhe replikoinnin-viive)
                  (not (and replikoinnin-viive
                            (> replikoinnin-viive replikoinnin-max-viive-ms)))))))

(defn tarkkaile-kantaa [db
                        lopeta-tarkkailu-kanava
                        paivitystiheys-ms
                        tapahtuma-julkaisija]
  (tapahtuma-apurit/tarkkaile lopeta-tarkkailu-kanava
                              paivitystiheys-ms
                              (fn []
                                (let [yhteys-ok? (case (:tarkkailun-nimi db)
                                                   :db (kantayhteys-ok? db (get-in db [:tarkkailun-timeout-arvot :kyselyn-timeout-ms]))
                                                   :db-replica (replica-ok? db (get-in db [:tarkkailun-timeout-arvot :replikoinnin-max-viive-ms]))
                                                   nil)]
                                  (tapahtuma-julkaisija yhteys-ok?)))))

(defn luo-db-tapahtumat [this db-nimi tarkkailun-timeout-arvot lopeta-tarkkailu-kanava]
  (let [tapahtuma (db-tunnistin->db-tila-tapahtuma db-nimi)
        tapahtuma-julkaisija (tapahtuma-apurit/tapahtuma-datan-spec (tapahtuma-apurit/tapahtuma-julkaisija tapahtuma)
                                                            ::db-tapahtuma)]
    (case db-nimi
      :db (tarkkaile-kantaa this lopeta-tarkkailu-kanava (get tarkkailun-timeout-arvot :paivitystiheys-ms) tapahtuma-julkaisija)
      :db-replica (tarkkaile-kantaa this lopeta-tarkkailu-kanava (get tarkkailun-timeout-arvot :paivitystiheys-ms) tapahtuma-julkaisija)
      nil)))

;; Tietokanta on pelkkä clojure.java.jdbc kirjaston mukainen db-spec, joka sisältää pelkään yhteyspoolin
(defrecord Tietokanta [datasource kehitysmoodi tarkkailun-timeout-arvot tarkkailun-nimi]
  component/Lifecycle
  (start [this]
    (let [lopeta-tarkkailu-kanava (async/chan)
          this (assoc this ::lopeta-tarkkailu-kanava lopeta-tarkkailu-kanava)]
      (when tarkkailun-nimi
        (luo-db-tapahtumat this tarkkailun-nimi tarkkailun-timeout-arvot lopeta-tarkkailu-kanava))
      (when kehitysmoodi
        (autoreload/start-autoreload))
      this))
  (stop [this]
    (when (get-in this [:asetukset :tarkkailun-nimi])
      (tapahtuma-apurit/julkaise-tapahtuma (db-tunnistin->db-tila-tapahtuma (get-in this [:asetukset :tarkkailun-nimi])) :suljetaan)
      (async/>!! (::lopeta-tarkkailu-kanava this) true)
      (async/close! (::lopeta-tarkkailu-kanava this)))
    (DataSources/destroy datasource)
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
                 kehitysmoodi
                 tarkkailun-timeout-arvot
                 tarkkailun-nimi)))
