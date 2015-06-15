(ns harja.tyokalut.json_validointi
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [clojure.string :as str])
  (:import (java.io IOException)
           (com.github.fge.jsonschema.core.exceptions ProcessingException)
           (com.github.fge.jsonschema.main JsonSchemaFactory)
           (com.github.fge.jackson JsonLoader)
           (com.github.fge.jsonschema.core.report ProcessingReport)))

(defn kirjaa-validointivirheet
  [^ProcessingReport validointiraportti]
  (log/error "JSON ei ole validia. Validointivirheet: "  (str/join validointiraportti)))

(defn validoi
  "Validoi annetun JSON sisällön vasten annettua JSON-skeemaa. JSON-skeeman tulee olla tiedosto annettussa skeema-polussa. JSON on
  String, joka on sisältö."
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
             (do
               (log/debug "JSON data on validia")
               true)
             (do
               (kirjaa-validointivirheet validointiraportti)
               false))))

       (catch IOException e
         (do
           (println "Tiedostokäsittelyssä tapahtui poikkeus: " e)
           false))

       (catch ProcessingException e
         (do
           (println "JSON validoinnissa tapahtui poikkeus: " e)
           false))))