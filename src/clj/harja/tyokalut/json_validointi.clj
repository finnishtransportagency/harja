(ns harja.tyokalut.json_validointi
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [clojure.string :as str])
  (:import
    (com.github.fge.jsonschema.main JsonSchemaFactory)
    (com.github.fge.jackson JsonLoader)
    (com.github.fge.jsonschema.core.report ProcessingReport)
    (com.google.gson JsonParseException)))

(defn kasittele-validointivirheet
  [^ProcessingReport validointiraportti]
  (let [validointi-virheet (str/join validointiraportti)]
    (log/error "JSON ei ole validia. Validointivirheet: " validointi-virheet)
    (throw (JsonParseException. validointi-virheet))))

(defn validoi
  "Validoi annetun JSON sisällön vasten annettua JSON-skeemaa. JSON-skeeman tulee olla tiedosto annettussa
  skeema-polussa. JSON on String, joka on sisältö. Jos annettu JSON ei ole validia, heitetään JSONException."
  [skeemaresurssin-polku json]

  (log/debug "Validoidaan JSON dataa käytäemn skeemaa:" skeemaresurssin-polku ". Data: " json)

  (try (->
         (let
           [skeema (JsonLoader/fromURL (io/resource skeemaresurssin-polku))
            validaattori-rakentaja (JsonSchemaFactory/byDefault)
            validaattori (.getJsonSchema validaattori-rakentaja skeema)
            json-data (JsonLoader/fromString json)
            validointiraportti (.validate validaattori json-data)]

           (if (.isSuccess validointiraportti)
             (log/debug "JSON data on validia")
             (kasittele-validointivirheet validointiraportti))))))