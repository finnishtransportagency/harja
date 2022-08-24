(ns tarkkailija.palvelin.komponentit.event-tietokanta
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]))

(defrecord Tietokanta [db-spec]
  component/Lifecycle
  (start [this]
    (let [timeout-s 10]
      (jdbc/with-db-connection [db (:db-spec this)]
                               (with-open [c (jdbc/get-connection db)
                                           stmt (jdbc/prepare-statement c
                                                                        "SELECT 1;"
                                                                        {:timeout timeout-s
                                                                         :result-type :forward-only
                                                                         :concurrency :read-only})
                                           rs (.executeQuery stmt)]
                                 (let [kanta-ok? (if (.next rs)
                                                   (= 1 (.getObject rs 1))
                                                   false)]
                                   (when-not kanta-ok?
                                     (log/error (str "Ei saatu event-kantaan yhteyttä " timeout-s " sekunnin kuluessa"))))))
      this))
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
