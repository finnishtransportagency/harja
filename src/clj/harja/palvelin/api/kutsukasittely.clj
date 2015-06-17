(ns harja.palvelin.api.kutsukasittely
  "API:n kutsujen käsittely funktiot"

  (:require [cheshire.core :as cheshire]
            [harja.tyokalut.json_validointi :as json]
            [taoensso.timbre :as log])
  (:import (javax.ws.rs BadRequestException)
           (com.google.gson JsonParseException)))


(defn logita-kutsu [resurssi request body]
  (log/debug "Vastaanotetiin kutsu resurssiin:" resurssi)
  (log/debug "Kutsu:" request)
  (log/debug "Sisältö:" body))

(defn logita-vastaus [resurssi response]
  (if (= 200 (:status response))
    (log/debug "Kutsu resurssiin:" resurssi "onnistui. Palautetaan vastaus:" response)
    (log/error "Kutsu resurssiin:" resurssi "epäonnistui. Palautetaan vastaus:" response)))

(defn tee-virhevastaus
  "Luo virhevastauksen annetulla statuksella ja asettaa vastauksen bodyksi JSON muodossa virheet."
  [status koodit-ja-viestit]
  (log/debug "koodit ja viestit:" koodit-ja-viestit)
  {:status  status
   :headers {"Content-Type" "application/json"}
   :body    (cheshire/encode {:virheet
                              (for [[koodi viesti] (partition 2 koodit-ja-viestit)]
                                {:virhe
                                 {:koodi  koodi
                                  :viesti viesti}})})})

(defn tee-sisainen-kasittelyvirhevastaus
  "Luo sisäisen käsittelyvirhevastauksen (Status 500) ja asettaa vastauksen bodyksi JSON muodossa virheet."
  [& koodit-ja-viestit]
  (tee-virhevastaus 500 koodit-ja-viestit))

(defn tee-viallinen-kutsu-virhevastaus
  "Luo viallinen kutsu virhevastauksen (Status 400) ja asettaa vastauksen bodyksi JSON muodossa virheet."
  [& koodit-ja-viestit]
  (tee-virhevastaus 400 koodit-ja-viestit))

(defn tee-vastaus
  "Luo JSON-vastauksen joko annetulla statuksella tai oletuksena statuksella 200 (ok). Payload on Clojure dataa, joka
  muunnetaan JSON-dataksi. Jokainen payload validoidaan annetulla skeemalla. Jos payload ei ole validi,
  palautetaan status 500 (sisäinen käsittelyvirhe)."
  ([skeema payload] (tee-vastaus 200 skeema payload))
  ([status skeema payload]
   (let [json (cheshire/encode payload)]
     (json/validoi skeema json)
     {:status  status
      :headers {"Content-Type" "application/json"}
      :body    json}
     )))

(defn lue-kutsu
  "Lukee kutsun bodyssä tulevan datan, mikäli kyseessä on POST-kutsu. Muille kutsuille palauttaa arvon nil.
  Validoi annetun kutsun JSON-datan ja mikäli data on validia, palauttaa datan Clojure dataksi muunnettuna.
  Jos annettu data ei ole validia, palautetaan nil."
  [skeema request body]

  (when (= :post (:request-method request))
    (let [json body
          json-validi? (json/validoi skeema json)]
      (if json-validi?
        (cheshire/decode json)
        nil))))


(defn kasittele-kutsu [resurssi request kutsun-skeema vastauksen-skeema kasittele-kutsu-fn]
  "Käsittelee annetun kutsun ja palauttaa käsittelyn tuloksen mukaisen vastauksen. Vastaanotettu ja lähetetty data
  on JSON-formaatissa, joka muunnetaan Clojure dataksi ja toisin päin. Sekä sisääntuleva, että ulos tuleva data
  validoidaan käyttäen annettuja JSON-skeemoja.

  Mikäli vastaanotettu data on viallista tai käsittelyssä tapahtuu virhe, oletetaan käsittelyfunktion heittävän, joko
  BadRequestException => HTTP status 400 tai InternalServerErrorException => HTTP status 500

  Käsittely voi palauttaa seuraavat HTTP-statukset: 200 = ok, 400 =
  kutsun data on viallista & 500 = sisäinen käsittelyvirhe."


  (let [body (if (:body request)
               (slurp (:body request))
               nil)]
    (logita-kutsu resurssi request body)
    (let [vastaus (try
                    (let
                      [parametrit (:params request)
                       kutsun-data (lue-kutsu kutsun-skeema request body)
                       vastauksen-data (kasittele-kutsu-fn parametrit kutsun-data)]
                      (tee-vastaus vastauksen-skeema vastauksen-data))
                    (catch JsonParseException e
                      (tee-viallinen-kutsu-virhevastaus "Viallinen kutsu. Vastaanotettu JSON ei ole validi. " (.getMessage e)))
                    (catch BadRequestException e
                      (tee-viallinen-kutsu-virhevastaus "Viallinen kutsu" (.getMessage e)))
                    (catch Exception e
                      (tee-sisainen-kasittelyvirhevastaus "Sisäinen käsittelyvirhe" (.getMessage e))))]
      (logita-vastaus resurssi vastaus)
      vastaus)))