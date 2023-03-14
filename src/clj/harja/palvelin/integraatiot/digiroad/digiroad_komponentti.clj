(ns harja.palvelin.integraatiot.digiroad.digiroad-komponentti
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as clj-str]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.palvelut.yllapitokohteet.maaramuutokset :as maaramuutokset]
            [slingshot.slingshot :refer [throw+ try+]]
            [cheshire.core :as cheshire]
            [clojure.string :as str]))

(def +virhe-kaistojen-haussa+ ::digiroad-virhe-kaistojen-haussa)

(defn digiroad-otsikot
  "Muotoilee Digiroadin api-key otsikon"
  [api-key]
  (merge {"Content-Type" "application/json"}
    (when api-key {"x-api-key" api-key})))

(defprotocol DigiroadHaku
  (hae-kaistat [this tr-osoite ajorata]))


(defn kasittele-hae-kaistat-virheet [virheet]
  (let [viestit-str (str/join ";" (map :viesti virheet))
        virhe-sisaltaa-tekstin? (fn [teksti]
                                  (str/includes? viestit-str teksti))]

    (cond
      ;; Niputetaan kaikki kaistoihin liittyvät HTTP 400 alla tulevat virheet
      ;;       400 Bad Request -> Virheellinen parametri (ajorata, tr-osoite) TAI olematon ajorata TAI olematon tieosoite
      ;;                           TAI haussa tehty liian monta automaattista uudelleenyritystä.
      ;; TODO: Harjan http integraatiopiste palauttaa kaikki virheet ja koodit tekstinä. Yleisesti voisi parantaa
      ;;      integraatiopisteiden virheidenhallintaa, jotta olisi mahdollista napata myös koodit erikseen ulkopuolelta.
      ;;      Ratkaisematta on vielä tilanne, että mitä tehdään kun haun maksimiyritysten määrä on ylittynyt.
      ;;       -> Yrittäisikö Harjan Digiroad integraatio hakua itsenäisesti uudelleen vai pitääkö käyttäjän muokata
      ;;          lomakkeella jotakin riviä, jotta kaistojen hakua yritetään uudelleen?
      (virhe-sisaltaa-tekstin? "palautti statuskoodin: 400")
      {:koodi +virhe-kaistojen-haussa+
       :onnistunut? false
       :virheet virheet}

      ;; Tuntematon virhe. Esimerkiksi ongelma Digiroadin puolella.
      :else
      {:koodi virheet/+ulkoinen-kasittelyvirhe-koodi+
       :onnistunut? false
       :virheet virheet})))

(defn kasittele-kaistat-vastaus [body headers]
  (cheshire/decode body true))

(comment
  (harja.palvelin.integraatiot.digiroad.digiroad-komponentti/hae-kaistat
    (:digiroad-integraatio harja.palvelin.main/harja-jarjestelma)
    {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
    1))

(defn hae-kaistat-digiroadista
  [integraatioloki db {:keys [url api-key] :as asetukset} tr-osoite ajorata]
  (let [url (str url "lanes/lanes_in_range")]
    ;; Digiroad vastaukset
    ;;       200 OK -> JSON-muotoinen järjestämätön puurakenteinen (tie, tieosa, kaistat) lista hakuparametrien
    ;;                 (tie, ajorata, tieosoiteväli) mukaisista kaistaosuuksista
    ;;       400 Bad Request -> Virheellinen parametri (ajorata, tr-osoite) TAI haussa tehty liian monta
    ;;                          automaattista uudelleenyritystä: "Maximum retries reached. Unable to get object."
    ;;       500 Internal Server Error -> Digiroadin puolen sisäinen virhe
    ;;       AWS API GW:n mahdolliset vastaukset: https://docs.aws.amazon.com/apigateway/latest/api/API_GetGatewayResponses.html

    (try+
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "digiroad" "hae-kaistat"
        (fn [konteksti]
          (let [{:keys [tie aosa aet losa let]} tr-osoite
                ;; tien numero (road_number)
                ;; ajorata (track) (on tulossa muutos, että ajoratatieto ei olisi pakollinen.
                ;                 Tällöin rajapinnasta voitaisiin hakea kaistatietoa jo aiemmin, sisältäen mahdolliset ajoradat)
                ;; aosa (start_part)
                ;; aet (start_addrm)
                ;; losa (end_part)
                ;; let (end_addrm)
                parametrit {:road_number tie
                            :start_part aosa
                            :start_addrm aet
                            :end_part losa
                            :end_addrm let
                            :track ajorata}
                http-asetukset {:metodi :GET
                                :url url
                                :parametrit parametrit
                                :otsikot (digiroad-otsikot api-key)}
                {body :body headers :headers}
                (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
            (kasittele-kaistat-vastaus body headers))))
      (catch  [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
        (kasittele-hae-kaistat-virheet virheet)))))

(defrecord Digiroad [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  DigiroadHaku
  (hae-kaistat [this tr-osoite ajorata]
    (hae-kaistat-digiroadista (:integraatioloki this) (:db this) asetukset tr-osoite ajorata)))
