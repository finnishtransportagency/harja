(ns harja.tyokalut.versio
  "Työkaluja sovelluksen versiotietojen hallintaan"
  (:require [clojure.java.shell :as sh]
            [clojure.string :as string]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn sovelluksen-versio-sha []
  (str/trim (:out (sh/sh "git" "rev-parse" "HEAD"))))

(def palvelimen-versio (some->
                         (try
                           (slurp "harja_image_id.txt")
                           (catch Exception e
                             (log/error "harja_image_id.txt tiedoston lataamisessa tapahtui virhe:", (.getMessage e))
                             nil))
                         (string/trim-newline)))


