(ns harja.kyselyt.specql-db
  (:require [clojure.future :refer :all]
            [clojure.string :refer [trim]]
            [clojure.java.io :as io]))

(defonce db (when (.canRead (io/file ".specql-db"))
          {:connection-uri (trim (slurp ".specql-db"))}))