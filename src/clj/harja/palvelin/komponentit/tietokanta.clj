(ns harja.palvelin.komponentit.tietokanta
  (:require [com.stuartsierra.component :as component]
            [jeesql.autoreload :as autoreload])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource DataSources)
           (java.util Properties)))

;; Tietokanta on pelkkä clojure.java.jdbc kirjaston mukainen db-spec, joka sisältää pelkään yhteyspoolin
(defrecord Tietokanta [datasource kehitysmoodi]
  component/Lifecycle
  (start [this]
    (when kehitysmoodi
      (autoreload/start-autoreload))
    this)
  (stop [this]
    (DataSources/destroy  datasource)
    (when kehitysmoodi
      (autoreload/stop-autoreload))
    this))


(defn luo-tietokanta
  "Luodaan Harja järjestelmälle tietokantakomponentti käyttäen yhteyspoolia PostgreSQL tietokantaan."
  ([asetukset]
   (luo-tietokanta asetukset false))
  ([{:keys [palvelin portti tietokanta kayttaja salasana yhteyspoolin-koko]} kehitysmoodi]
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
                 kehitysmoodi)))
