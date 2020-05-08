(ns harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-lahetysvastaussanoma
  (:require [harja.tyokalut.json-validointi :as json]
            [cheshire.core :refer [decode]]
            [clojure.walk :as walk])
  (:use [slingshot.slingshot :only [throw+]]))

(def +paikkauksen-vienti-vastaus-skeema+ "json/yha/paikkausten-vienti-response.schema.json")

(defn lue-virheet [data]
  (walk/keywordize-keys (decode data)))

;; TODO: Varmista että ok-sanoma palautuu ilman sisältöä. Esim. [{}] täytyy käsitellä toisin kuin nyt on käsitelty. Oletus on että ok = []
(defn lue-sanoma
  "Tarkistaa onko YHA:n vastaussanoma paikkauskohteen vientiin validi. Jos sanoma on ok,
  tarkistetaan palauttiko YHA virheitä."
  [viesti]
  (when (json/validoi +paikkauksen-vienti-vastaus-skeema+ viesti)
    (throw (new RuntimeException "Paikkauskohteen viennin vastaussanoma ei ole json-skeeman mukainen.")))
  (let [virheet (lue-virheet viesti)]
    {:onnistunut (empty? virheet)
     :virheet virheet}))
