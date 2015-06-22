(ns harja.palvelin.api.tyokalut.kutsukasittely
  "API:n kutsujen käsittely funktiot"

  (:require [harja.tyokalut.json_validointi :as json]
            [harja.palvelin.api.tyokalut.virheet :as virheet]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [try+ throw+]]))


(defn logita-kutsu [resurssi request body]
  ;; fixme: lisää monitorointikutsu
  (log/debug "Vastaanotetiin kutsu resurssiin:" resurssi)
  (log/debug "Kutsu:" request)
  (log/debug "Sisältö:" body))

(defn logita-vastaus [resurssi response]
  ;; fixme: lisää monitorointikutsu
  (if (= 200 (:status response))
    (log/debug "Kutsu resurssiin:" resurssi "onnistui. Palautetaan vastaus:" response)
    (log/error "Kutsu resurssiin:" resurssi "epäonnistui. Palautetaan vastaus:" response)))

(defn tee-virhevastaus
  "Luo virhevastauksen annetulla statuksella ja asettaa vastauksen bodyksi JSON muodossa virheet."
  [status virheet]
  (log/debug "Virheet:" virheet)

  (let [body (cheshire/encode
               {:virheet
                (mapv (fn [virhe]
                        {:virhe
                         {:koodi  (:koodi virhe)
                          :viesti (:viesti virhe)}})
                      virheet)})]
    (log/debug "Body on:" body)
    {:status  status
     :headers {"Content-Type" "application/json"}
     :body    body}))

(defn tee-sisainen-kasittelyvirhevastaus
  [virheet]
  (tee-virhevastaus 500 virheet))

(defn tee-viallinen-kutsu-virhevastaus
  [virheet]
  (tee-virhevastaus 400 virheet))

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

(defn kasittele-invalidi-json [virheet]
  (log/warn virheet/+invalidi-json-koodi+ virheet)
  (tee-viallinen-kutsu-virhevastaus virheet))

(defn kasittele-viallinen-kutsu [virheet]
  (log/warn "Tehty kutsu on viallinen: " virheet)
  (tee-viallinen-kutsu-virhevastaus virheet))

(defn kasittele-sisainen-kasittelyvirhe [virheet]
  (log/warn "Tapahtui sisäinen käsittelyvirhe: " virheet)
  (tee-viallinen-kutsu-virhevastaus virheet))

(defn lue-kutsu
  "Lukee kutsun bodyssä tulevan datan, mikäli kyseessä on POST-kutsu. Muille kutsuille palauttaa arvon nil.
  Validoi annetun kutsun JSON-datan ja mikäli data on validia, palauttaa datan Clojure dataksi muunnettuna.
  Jos annettu data ei ole validia, palautetaan nil."
  [skeema request body]
  (log/debug "Luetaan kutsua")
  (when (= :post (:request-method request))
    (json/validoi skeema body)
    (cheshire/decode body true)))

(defn kasittele-kutsu [resurssi request kutsun-skeema vastauksen-skeema kasittele-kutsu-fn]
  "Käsittelee annetun kutsun ja palauttaa käsittelyn tuloksen mukaisen vastauksen. Vastaanotettu ja lähetetty data
  on JSON-formaatissa, joka muunnetaan Clojure dataksi ja toisin päin. Sekä sisääntuleva, että ulos tuleva data
  validoidaan käyttäen annettuja JSON-skeemoja.

  Käsittely voi palauttaa seuraavat HTTP-statukset: 200 = ok, 400 = kutsun data on viallista & 500 = sisäinen käsittelyvirhe."

  (let [body (if (:body request)
               (slurp (:body request))
               nil)]
    (logita-kutsu resurssi request body)
    (let [vastaus (try+
                    (let
                      [parametrit (:params request)
                       kutsun-data (lue-kutsu kutsun-skeema request body)
                       vastauksen-data (kasittele-kutsu-fn parametrit kutsun-data)]
                      (tee-vastaus vastauksen-skeema vastauksen-data))
                    (catch [:type virheet/+invalidi-json+] {:keys [virheet]}
                      (kasittele-invalidi-json virheet))
                    (catch [:type virheet/+viallinen-kutsu+] {:keys [virheet]}
                      (kasittele-viallinen-kutsu virheet))
                    (catch [:type virheet/+sisainen-kasittelyvirhe+] {:keys [virheet]}
                      (kasittele-sisainen-kasittelyvirhe virheet))
                    (catch Exception e
                      (kasittele-sisainen-kasittelyvirhe [{:koodi  virheet/+sisainen-kasittelyvirhe-koodi+
                                                           :viesti (.getMessage e)}])))]
      (logita-vastaus resurssi vastaus)
      vastaus)))