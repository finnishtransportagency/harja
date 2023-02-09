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
            [cheshire.core :as cheshire]))

(def +virhe-kaistojen-haussa+ ::digiroad-virhe-kaistojen-haussa)

(defn digiroad-otsikot
  "Muotoilee Digiroadin api-key otsikon"
  [api-key]
  (merge {"Content-Type" "application/json"}
    (when api-key {"x-api-key" api-key})))

(defprotocol DigiroadHaku
  (hae-kaistat [this tr-osoite ajorata]))

(defn kasittele-kaistat-vastaus [body headers]
  (println "### Kaistat vastaus: " body)
  ;; TODO: Virhetilanteet
  (cheshire/decode body true))

;; tien numero (road_number)
;; ajorata (track) (on tulossa muutos, että ajoratatieto ei olisi pakollinen.
;                 Tällöin rajapinnasta voitaisiin hakea kaistatietoa jo aiemmin, sisältäen mahdolliset ajoradat)
;; aosa (start_part)
;; aet (start_addrm)
;; losa (end_part)
;; let (end_addrm)

(comment
  (harja.palvelin.integraatiot.digiroad.digiroad-komponentti/hae-kaistat
    (:digiroad harja.palvelin.main/harja-jarjestelma)
    {:tie 4 :aosa 101 :aet 0 :losa 101 :let 100}
    1))

(defn hae-kaistat-digiroadista
  [integraatioloki db {:keys [url api-key] :as asetukset} tr-osoite ajorata]
  (let [url (str url "lanes/lanes_in_range")]
    (println "### Digiroad url: " url)
    (println "### tr-osoite: " tr-osoite)
    (println "### ajorata: " ajorata)

    ;; TODO: Virhevastauksien hallinta
    ;;       200 OK -> JSON-muotoinen järjestämätön puurakenteinen (tie, tieosa, kaistat) lista hakuparametrien
    ;;                 (tie, ajorata, tieosoiteväli) mukaisista kaistaosuuksista
    ;;       400 Bad Request -> Virheellinen parametri (ajokaista, tr-osoite) TAI haussa tehty liian monta
    ;;                          automaattista uudelleenyritystä: "Maximum retries reached. Unable to get object."
    ;;       500 Internal Server Error -> Digiroadin puolen sisäinen virhe
    ;;       AWS API GW:n mahdolliset vastaukset: https://docs.aws.amazon.com/apigateway/latest/api/API_GetGatewayResponses.html

    (try+
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "digiroad" "hae-kaistat"
        (fn [konteksti]
          (let [{:keys [tie aosa aet losa let]} tr-osoite
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
      (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
        (do
          (log/error "Kaistojen haku Digiroadista epäonnistui:" virheet)
          false)))))

(defrecord Digiroad [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  DigiroadHaku
  (hae-kaistat [this tr-osoite ajorata]
    (hae-kaistat-digiroadista (:integraatioloki this) (:db this) asetukset tr-osoite ajorata)))
