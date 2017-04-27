(ns harja.kyselyt.specql-db
  (:require [clojure.future :refer :all]
            [clojure.string :refer [trim]]
            [clojure.java.io :as io]))

(defmacro define-db []
  (when (.canRead (io/file ".specql-db"))
    `(def ~'db {:connection-uri ~(trim (slurp ".specql-db"))})))
