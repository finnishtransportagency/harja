(ns harja.palvelin.api.yleinen
  "Yleiset APIn apurit"
  (:require [cheshire.core :as cheshire]))

(defn virhe
  "JSON virhevastauksen apuri."
  [& koodit-ja-viestit]
  {:status 500
   :headers {"Content-Type" "application/json"}
   :body (cheshire/encode {:virheet
                           (for [[koodi viesti] (partition 2 koodit-ja-viestit)]
                             {:virhe
                              {:koodi koodi
                               :viesti viesti}})})})

(defn vastaus
  "JSON-vastauksen apuri, ottaa optionaalisen statuskoodin ja body payloadin.
Jos annetaan pelkkä payload, lähetetään statuksena 200 (OK). Payload on Clojure dataa, joka 
muunnetaan JSON-dataksi."
  ([payload] (vastaus 200 payload))
  ([status payload]
     {:status 200
      :headers {"Content-Type" "application/json"}
      :body (cheshire/encode payload)}))

