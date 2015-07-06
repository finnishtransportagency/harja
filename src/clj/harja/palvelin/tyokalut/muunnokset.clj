(ns harja.palvelin.tyokalut.muunnokset
  "Apufunktioita kannasta tulevan datan muuntamiseen"

  (:require
    [chime :refer [chime-at]]
    [clj-time.periodic :refer [periodic-seq]]
    [clj-time.coerce :as coerce]
    [clj-time.format :as format]
    [cheshire.core :as cheshire]))

(defn jsonb->clojuremap [json avain]
  (-> json
      (assoc avain
             (some-> json
                     avain
                     .getValue
                     (cheshire/decode true)))))

(defn parsi-json-pvm [json avainpolku]
  (-> json
      (assoc-in avainpolku
                (when-let [dt (some-> json (get-in avainpolku))]
                  (coerce/to-date (format/parse (format/formatters :date-time) dt))))))

(defn string->avain [data avainpolku]
  (-> data
      (assoc-in avainpolku (keyword (get-in data avainpolku)))))