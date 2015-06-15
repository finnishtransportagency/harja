(ns harja.palvelin.api.yleinen
  "Yleiset APIn apurit"
  (:require [cheshire.core :as cheshire]
            [harja.tyokalut.json_validointi :as json]
            [taoensso.timbre :as log]))


(defn logita-kutsu [resurssi kutsu]
  (log/debug "Vastaanotetiin kutsu resurssiin:" resurssi)
  (log/debug "Kutsu" kutsu)
  (when (= :post (:request-method kutsu))
    (log/debug "POST-kutsun sisältö: " (slurp (:body kutsu)))))

(defn logita-vastaus [resurssi vastaus]
  (if (= 200 (:status vastaus))
    (log/debug "Kutsu resurssiin:" resurssi "onnistui. Palautetaan vastaus:" vastaus)
    (log/error "Kutsu resurssiin:" resurssi "epäonnistui. Palautetaan vastaus:" vastaus)))

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
  (let [json (slurp (:body request))
        json-validi? (json/validoi skeema json)]
    (if json-validi?
      (cheshire/decode json)
      nil)))

(defn monitoroi-kasittely [resurssi request kasittele-kutsu-fn]
  "Monitoroi yksittäisen kutsun ja vastauksen käsittelyn."
  (logita-kutsu resurssi request)
  (let [vastaus (kasittele-kutsu-fn)]
    (logita-vastaus resurssi vastaus)
    vastaus))