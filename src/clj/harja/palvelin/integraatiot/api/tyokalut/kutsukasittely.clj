(ns harja.palvelin.integraatiot.api.tyokalut.kutsukasittely
  "API:n kutsujen käsittely funktiot"

  (:require [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [harja.tyokalut.json_validointi :as json]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.palvelut.kayttajat :as q]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clojure.walk :as walk])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn tee-lokiviesti [suunta body viesti]
  {:suunta        suunta
   :sisaltotyyppi "application/json"
   :siirtotyyppi  "HTTP"
   :sisalto       body
   :otsikko       (str (walk/keywordize-keys (:headers viesti)))
   :parametrit    (str (:params viesti))})

(defn lokita-kutsu [integraatioloki resurssi request body]
  (log/debug "Vastaanotetiin kutsu resurssiin:" resurssi ".")
  (log/debug "Kutsu:" request)
  (log/debug "Parametrit: " (:params request))
  (log/debug "Headerit: " (:headers request))
  (log/debug "Sisältö:" body)

  (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "api" (name resurssi) nil (tee-lokiviesti "sisään" body request)))

(defn lokita-vastaus [integraatioloki resurssi response tapahtuma-id]
  (log/debug "Lähetetään vastaus resurssiin:" resurssi "kutsuun.")
  (log/debug "Vastaus:" response)
  (log/debug "Headerit: " (:headers response))
  (log/debug "Sisältö:" (:body response))

  (if (= 200 (:status response))
    (do
      (log/debug "Kutsu resurssiin:" resurssi "onnistui. Palautetaan vastaus:" response)
      (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki (tee-lokiviesti "ulos" (:body response) response) nil tapahtuma-id nil))
    (do
      (log/error "Kutsu resurssiin:" resurssi "epäonnistui. Palautetaan vastaus:" response)
      (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki (tee-lokiviesti "ulos" (:body response) response) nil tapahtuma-id nil))))

(defn tee-virhevastaus
  "Luo virhevastauksen annetulla statuksella ja asettaa vastauksen bodyksi JSON muodossa virheet."
  [status virheet]
  (let [body (cheshire/encode
               {:virheet
                (mapv (fn [virhe]
                        {:virhe
                         {:koodi  (:koodi virhe)
                          :viesti (:viesti virhe)}})
                      virheet)})]
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
   (if payload
     (let [json (cheshire/encode payload)]
       ;(json/validoi skeema json)
       {:status  status
        :headers {"Content-Type" "application/json"}
        :body    json})

     (if skeema
       (throw+ {:type    virheet/+sisainen-kasittelyvirhe+
                :virheet [{:koodi  virheet/+tyhja-vastaus+
                           :viesti "Tyhja vastaus vaikka skeema annettu"}]})
       {:status status}))))

(defn kasittele-invalidi-json [virheet]
  (log/warn virheet/+invalidi-json-koodi+ virheet)
  (tee-viallinen-kutsu-virhevastaus virheet))

(defn kasittele-viallinen-kutsu [virheet]
  (log/warn "Tehty kutsu on viallinen: " virheet)
  (tee-viallinen-kutsu-virhevastaus virheet))

(defn kasittele-sisainen-kasittelyvirhe [virheet]
  (log/warn "Tapahtui sisäinen käsittelyvirhe: " virheet)
  (tee-sisainen-kasittelyvirhevastaus virheet))

(defn lue-kutsu
  "Lukee kutsun bodyssä tulevan datan, mikäli kyseessä on POST-kutsu. Muille kutsuille palauttaa arvon nil.
  Validoi annetun kutsun JSON-datan ja mikäli data on validia, palauttaa datan Clojure dataksi muunnettuna.
  Jos annettu data ei ole validia, palautetaan nil."
  [skeema request body]
  (log/debug "Luetaan kutsua")
  (when (= :post (:request-method request))
    ;(json/validoi skeema body)
    (cheshire/decode body true)))

(defn hae-kayttaja [db kayttajanimi]
  (let [kayttaja (q/hae-kayttaja-kayttajanimella db kayttajanimi)]
    (if kayttaja
      kayttaja
      (do
        (log/error "Tuntematon käyttäjätunnus: " kayttajanimi)
        (throw+ {:type    virheet/+viallinen-kutsu+
                 :virheet [{:koodi  virheet/+tuntematon-kayttaja-koodi+
                            :viesti (str "Tuntematon käyttäjätunnus: " kayttajanimi)}]})))))

(defn kasittele-kutsu [db integraatioloki resurssi request kutsun-skeema vastauksen-skeema kasittele-kutsu-fn]
  "Käsittelee annetun kutsun ja palauttaa käsittelyn tuloksen mukaisen vastauksen. Vastaanotettu ja lähetetty data
  on JSON-formaatissa, joka muunnetaan Clojure dataksi ja toisin päin. Sekä sisääntuleva, että ulos tuleva data
  validoidaan käyttäen annettuja JSON-skeemoja.

  Käsittely voi palauttaa seuraavat HTTP-statukset: 200 = ok, 400 = kutsun data on viallista & 500 = sisäinen käsittelyvirhe."

  (let [body (if (:body request)
               (slurp (:body request))
               nil)
        tapahtuma-id (lokita-kutsu integraatioloki resurssi request body)]
    (let [vastaus (try+
                    (let
                      [parametrit (:params request)
                       kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))
                       kutsun-data (lue-kutsu kutsun-skeema request body)
                       vastauksen-data (kasittele-kutsu-fn parametrit kutsun-data kayttaja db)]
                      (tee-vastaus vastauksen-skeema vastauksen-data))
                    (catch [:type virheet/+invalidi-json+] {:keys [virheet]}
                      (kasittele-invalidi-json virheet))
                    (catch [:type virheet/+viallinen-kutsu+] {:keys [virheet]}
                      (kasittele-viallinen-kutsu virheet))
                    (catch [:type virheet/+sisainen-kasittelyvirhe+] {:keys [virheet]}
                      (kasittele-sisainen-kasittelyvirhe virheet))
                    (catch Exception e
                      (log/error "Tapahtui poikkeus: " e)
                      (log/error "NextException: " (.getNextException e))
                      (kasittele-sisainen-kasittelyvirhe
                        [{:koodi  virheet/+sisainen-kasittelyvirhe-koodi+
                          :viesti (.getMessage e)}])))]
      (lokita-vastaus integraatioloki resurssi vastaus tapahtuma-id)
      vastaus)))
