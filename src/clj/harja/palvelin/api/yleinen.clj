(ns harja.palvelin.api.yleinen
  "Yleiset APIn apurit"
  (:require [cheshire.core :as cheshire]
            [harja.tyokalut.json_validointi :as json]))

(defn virhe
  "JSON virhevastauksen apuri."
  [& koodit-ja-viestit]
  {:status  500
   :headers {"Content-Type" "application/json"}
   :body    (cheshire/encode {:virheet
                              (for [[koodi viesti] (partition 2 koodit-ja-viestit)]
                                {:virhe
                                 {:koodi  koodi
                                  :viesti viesti}})})})

(defn vastaus
  "JSON-vastauksen apuri, ottaa optionaalisen statuskoodin ja body payloadin.
  Jos annetaan pelkkä payload, lähetetään statuksena 200 (OK). Payload on Clojure dataa, joka
  muunnetaan JSON-dataksi. Jokainen payload täytyy validoida annetulla skeemalla. Jos payload ei ole validi,
  palautetaan status 500."
  ([skeema payload] (vastaus 200 skeema payload))
  ([status skeema payload]
   (let [json (cheshire/encode payload)
         json-validi? (json/validoi skeema json)]
     (if json-validi?
       {:status  status
        :headers {"Content-Type" "application/json"}
        :body    json}
       (virhe "Sisainen käsittelyvirhe")))))

(defn kutsu
  "JSON-kutsun apuri, joka ottaa vataan kutsun sekä skeeman. Validoi skeeman mukaan kutsun payloadin.
  Jos annettu data ei ole validia, palautetaan nil."
  [request skeema]
  (let [json (:body request)
        json-validi? (json/validoi skeema json)]
    (if json-validi?
      (cheshire/decode json)
      nil)))

