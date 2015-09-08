(ns harja.tyokalut.json_validointi
  (:require [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [webjure.json-schema.validator :refer [validate]]
            [cheshire.core :as cheshire]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defmulti formatoi-virhe (fn [polku virhe] (:error virhe)))
(defmethod formatoi-virhe :wrong-type [polku virhe]
  (str polku ": Väärä tyyppi. Tyyppi: " (:expected virhe) ", arvo: " (pr-str (:data virhe))))
(defmethod formatoi-virhe :missing-property [polku _]
  (str polku ": Pakollinen arvo puuttuu"))
(defmethod formatoi-virhe :additional-properties [polku virhe]
  (str polku ": Ylimääräisiä kenttiä: " (str/join ", " (:property-names virhe))))
(defmethod formatoi-virhe :out-of-bounds [polku virhe]
  (str polku ": Ei-sallittu arvoalue. Minimi: " (:minimum ) ", maksimi: " (:maximum) ", arvo" (pr-str (:data virhe)) ))
(defmethod formatoi-virhe :properties [polku virhe]
  (str/join "\n"
            (for [[avain virhe] (seq (:properties virhe))]
              (formatoi-virhe (str polku "/" avain) virhe))))
(defmethod formatoi-virhe :array-items [polku virhe]
  (str/join "\n"
            (for [virhe (:items virhe)]
              (formatoi-virhe (str polku "[" (:position virhe) "]") virhe))))

(defmethod formatoi-virhe :default [polku virhe]
  (str polku ": " (pr-str virhe)))

(defn kasittele-validointivirheet
  [virheet]
  (log/error "JSON ei ole validia. Validointivirheet: " virheet)
  (throw+ {:type    virheet/+invalidi-json+
           :virheet [{:koodi  virheet/+invalidi-json-koodi+
                      :viesti (str "JSON ei ole validia: " (formatoi-virhe "" virheet))}]}))

(defn validoi
  "Validoi annetun JSON sisällön vasten annettua JSON-skeemaa. JSON-skeeman tulee olla tiedosto annettussa
  skeema-polussa. JSON on String, joka on sisältö. Jos annettu JSON ei ole validia, heitetään JSONException."
  [skeemaresurssin-polku json]

  (log/debug "Validoidaan JSON dataa käytäen skeemaa:" skeemaresurssin-polku)
  (let [virheet (validate
                  (cheshire/parse-string (slurp (io/resource skeemaresurssin-polku)))
                  (cheshire/parse-string json)
                  {:draft3-required true
                   :ref-resolver    (fn [uri]
                                      (log/debug "Ladataan linkattu schema: " uri)
                                      (let [resurssipolku (.substring uri (count "file:resources/"))]
                                        (log/debug "Resurssipolku: " resurssipolku)
                                        (let [ladattu (cheshire/parse-string (slurp (io/resource resurssipolku)))]
                                          (log/debug "Ladattiin: " ladattu)
                                          ladattu)))})]
    (if-not virheet
      (log/debug "JSON data on validia")
      (kasittele-validointivirheet virheet))))