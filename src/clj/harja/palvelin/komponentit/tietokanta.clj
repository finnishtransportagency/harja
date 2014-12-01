(ns harja.palvelin.komponentit.tietokanta
  (:require [com.stuartsierra.component :as component])
  (:import com.mchange.v2.c3p0.ComboPooledDataSource))

;; Tietokanta on pelkkä clojure.java.jdbc kirjaston mukainen db-spec, joka sisältää pelkään yhteyspoolin
(defrecord Tietokanta [datasource]
  component/Lifecycle
  (start [this]
    (println "Käytetään tietokantaa: " datasource)
    this)
  (stop [this]
    (println "Tietokanta suljetaan: " datasource)
    this))


(defn luo-tietokanta [palvelin portti kayttaja salasana]
  "Luodaan Harja järjestelmälle tietokantakomponentti käyttäen yhteyspoolia PostgreSQL tietokantaan."
  (->Tietokanta (doto (ComboPooledDataSource.)
                  (.setDriverClass "org.postgresql.Driver")
                  (.setJdbcUrl (str "jdbc:postgres://" palvelin ":" portti))
                  (.setUser kayttaja)
                  (.setPassword salasana)
                  ;; ylimääräiset yhteydet suljetaan puolen tunnin inaktiivisuuden jälkeen
                  (.setMaxIdleTimeExcessConnections (* 30 60))
                  ;; yhteyden pisin inaktiivisuusaika 3 tuntia
                  (.setMaxIdleTime (* 3 60 60))) 
                ))

                
