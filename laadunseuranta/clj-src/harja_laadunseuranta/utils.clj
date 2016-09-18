(ns harja-laadunseuranta.utils
  (:require [harja-laadunseuranta.config :as c]
            [ring.util.http-response :refer :all]
            [taoensso.timbre :as log]
            [clojure.string :as str]

            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.domain.oikeudet :as oikeudet]))


(defn polku [s]
  (str (:url-prefix @c/config) s))

(defn poikkeuskasittelija [^Exception e data req]
  (log/error e "Virhe " (.getMessage e))
  (when-let [next-ex (.getNextException e)]
    (log/error next-ex "-- Sisempi virhe " (.getMessage next-ex)))
  (internal-server-error {:error (.getMessage e)}))

(defn select-non-nil-keys [c keys]
  (into {} (filterv #(not (nil? (second %))) (into [] (select-keys c keys)))))
