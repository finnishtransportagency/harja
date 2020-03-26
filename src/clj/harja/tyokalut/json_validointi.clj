(ns harja.tyokalut.json-validointi
  (:require [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [webjure.json-schema.validator :refer [validate]]
            [webjure.json-schema.validator.macro :refer [make-validator]]
            [cheshire.core :as cheshire]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defmulti formatoi-virhe (fn [polku virhe] (:error virhe)))
(defmethod formatoi-virhe :wrong-type [polku virhe]
  (str polku ": Väärä tyyppi. Tyyppi: " (:expected virhe) ", arvo: " (pr-str (:data virhe))))
(defmethod formatoi-virhe :missing-property [polku _]
  (str polku ": Pakollinen arvo puuttuu"))
(defmethod formatoi-virhe :additional-properties [polku virhe]
  (str polku ": Ylimääräisiä kenttiä: " (str/join ", " (:property-names virhe))))
(defmethod formatoi-virhe :out-of-bounds [polku virhe]
  (str polku ": Ei-sallittu arvoalue. Minimi: " (:minimum (or virhe "-")) ", maksimi: " (or (:maximum virhe) "-") ", annettu arvo: " (pr-str (:data virhe)) ))
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
  [skeema virheet]
  (log/warn (format "JSON ei ole validia (skeema: %s)." skeema))
  (throw+ {:type    virheet/+invalidi-json+
           :virheet [{:koodi  virheet/+invalidi-json-koodi+
                      :viesti (str "JSON ei ole validia: " (formatoi-virhe "" virheet))}]}))

(defn- lue-skeemaresurssi* [polku]
  (log/debug "Ladataan schema: " polku)
  (cheshire/parse-string (slurp (io/resource polku))))

(def lue-skeemaresurssi (memoize lue-skeemaresurssi*))

(defn validoi
  "Validoi annetun JSON sisällön vasten annettua JSON-skeemaa. JSON-skeeman tulee olla tiedosto annettussa
  skeema-polussa. JSON on String, joka on sisältö. Jos annettu JSON ei ole validia, heitetään JSONException."
  [skeemaresurssin-polku json]
  (log/debug "Tarkistetaan, että JSON on syntaksiltaan validi")
  (try+
    (cheshire/decode json true)
    (catch Exception e
      (kasittele-validointivirheet skeemaresurssin-polku (.getMessage e))))
  (log/debug "Validoidaan JSON dataa käytäen skeemaa:" skeemaresurssin-polku)
  (let [virheet (validate
                 (lue-skeemaresurssi skeemaresurssin-polku)
                 (cheshire/parse-string json)
                 {:draft3-required true
                  :lax-date-time-format? true
                  :ref-resolver #(lue-skeemaresurssi
                                  (.substring % (count "file:resources/")))})]
    (if-not virheet
      (log/debug "JSON data on validia")
      (kasittele-validointivirheet skeemaresurssin-polku virheet))))

(defn lue-skeematiedosto [polku]
  (cheshire/parse-string (slurp polku)))

(defn ref-resolver [polku]
  (lue-skeematiedosto
   (.substring polku (count "file:"))))

(defmacro tee-validaattori
  [skeemaresurssin-polku]
  (let [skeema (lue-skeematiedosto (str "resources/" (eval skeemaresurssin-polku)))]
    `(let [validator#
           (make-validator ~skeema {:draft3-required true
                                    :ref-resolver ~ref-resolver
                                    :lax-date-time-format? true})]
       (fn [json#]
         (try
           (let [virheet# (validator# (cheshire/parse-string json#))]
             (if-not virheet#
               (log/debug "JSON data on validia")
               (kasittele-validointivirheet ~skeemaresurssin-polku virheet#)))
           (catch Exception e#
             (kasittele-validointivirheet ~skeemaresurssin-polku (.getMessage e#))))))))
