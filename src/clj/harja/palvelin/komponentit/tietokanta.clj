(ns harja.palvelin.komponentit.tietokanta
  (:require [com.stuartsierra.component :as component])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource DataSources)))

;; Tietokanta on pelkkä clojure.java.jdbc kirjaston mukainen db-spec, joka sisältää pelkään yhteyspoolin
(defrecord Tietokanta [datasource]
  component/Lifecycle
  (start [this]
    (println "Käytetään tietokantaa: " datasource)
    this)
  (stop [this]
    (println "Tietokanta suljetaan: " datasource)
    (DataSources/destroy  datasource)
    this))


(defn luo-tietokanta
  "Luodaan Harja järjestelmälle tietokantakomponentti käyttäen yhteyspoolia PostgreSQL tietokantaan."
  [palvelin portti tietokanta kayttaja salasana]
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

                
