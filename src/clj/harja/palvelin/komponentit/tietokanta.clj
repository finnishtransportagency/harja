(ns harja.palvelin.komponentit.tietokanta
  (:require [com.stuartsierra.component :as component])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource DataSources)
           (java.util Properties)))

;; Tietokanta on pelkkä clojure.java.jdbc kirjaston mukainen db-spec, joka sisältää pelkään yhteyspoolin
(defrecord Tietokanta [datasource]
  component/Lifecycle
  (start [this]
    #_(println "Käytetään tietokantaa: " datasource)
    this)
  (stop [this]
    #_(println "Tietokanta suljetaan: " datasource)
    (DataSources/destroy  datasource)
    this))


(defn luo-tietokanta
  "Luodaan Harja järjestelmälle tietokantakomponentti käyttäen yhteyspoolia PostgreSQL tietokantaan."
  [palvelin portti tietokanta kayttaja salasana]
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
                  ;; ylimääräiset yhteydet suljetaan puolen tunnin inaktiivisuuden jälkeen
                  (.setMaxIdleTimeExcessConnections (* 30 60))
                  ;; yhteyden pisin inaktiivisuusaika 3 tuntia
                  (.setMaxIdleTime (* 3 60 60))) 
                ))

                
