(ns harja.palvelin.tyokalut.json-tyyppikonversiot
  (:require [clojure.set :refer [intersection]]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json])
  (:import (clojure.lang IPersistentMap)))

(import  'org.postgresql.util.PGobject)

; Clojure-map -> JSON
(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value]
    (doto (PGobject.)
      (.setType "json")
      (.setValue (json/write-str value)))))

; JSON -> Clojure-map
(extend-protocol jdbc/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (json/read-str value :key-fn keyword)
        "jsonb" (json/read-str value :key-fn keyword)
        :else value))))