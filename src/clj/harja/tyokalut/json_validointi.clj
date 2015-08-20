(ns harja.tyokalut.json_validointi
  (:require [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [webjure.json-schema.validator :refer [validate]]
            [cheshire.core :as cheshire])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-validointivirheet
  [virheet]
  (log/error "JSON ei ole validia. Validointivirheet: " virheet)
  (throw+ {:type virheet/+invalidi-json+
           :virheet [{:koodi  virheet/+invalidi-json-koodi+
                      :virheet virheet
                      :viesti "JSON ei ole validia"}]}))



(defn validoi
  "Validoi annetun JSON sisällön vasten annettua JSON-skeemaa. JSON-skeeman tulee olla tiedosto annettussa
  skeema-polussa. JSON on String, joka on sisältö. Jos annettu JSON ei ole validia, heitetään JSONException."
  [skeemaresurssin-polku json]

  (log/debug "Validoidaan JSON dataa käytäen skeemaa:" skeemaresurssin-polku ". Data: " json)

  (try (->
        (let [virheet (validate (cheshire/parse-string (slurp (io/resource skeemaresurssin-polku)))
                                (cheshire/parse-string json)
                                {:draft3-required true
                                 :ref-resolver (fn [uri]
                                                 (log/debug "Ladataan linkattu schema: " uri)
                                                 (let [resurssipolku (.substring uri (count "file:resources/"))]
                                                   (log/debug "Resurssipolku: " resurssipolku)
                                                   (let [ladattu (cheshire/parse-string (slurp (io/resource resurssipolku)))]
                                                     (log/debug "Ladattiin: " ladattu)
                                                     ladattu)))})]
          (if-not virheet
            (log/debug "JSON data on validia")
            (kasittele-validointivirheet virheet))))
       (catch Exception e
         (log/error e "JSON validoinnissa tapahtui poikkeus.")
         (throw+ {:type virheet/+invalidi-json+ :virheet
                        [{:koodi  virheet/+invalidi-json-koodi+
                          :viesti "JSON ei ole validia"}]}))))
