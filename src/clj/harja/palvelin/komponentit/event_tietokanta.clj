(ns harja.palvelin.komponentit.event-tietokanta
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]))

(defrecord Tietokanta [db-spec]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this))

(defn luo-tietokanta [{:keys [palvelin portti tietokanta kayttaja salasana]}]
  (->Tietokanta {:dbtype "postgresql"
                 :classname "org.postgresql.Driver"
                 :dbname tietokanta
                 :host palvelin
                 :port portti
                 :user kayttaja
                 :password salasana}))
