(ns harja.palvelin.integraatiot.api.ilmoitukset
  "Ilmoitusten haku ja ilmoitustoimenpiteiden kirjaus"
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [with-channel on-close send!]]
            [compojure.core :refer [PUT GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu lokita-kutsu lokita-vastaus tee-vastaus aja-virhekasittelyn-kanssa hae-kayttaja]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot :as notifikaatiot]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [harja.kyselyt.ilmoitukset :as ilmoitukset]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.api.sanomat.ilmoitus-sanomat :as sanomat]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik])
  (:use [slingshot.slingshot :only [throw+]]))

(defn hae-ilmoituksen-id [db ilmoitusid]
  (if-let [id (:id (first (ilmoitukset/hae-id-ilmoitus-idlla db ilmoitusid)))]
    id
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi  :tuntematon-ilmoitus
       :viesti (format "Ilmoitus id:llä %s. ei löydy ilmoitusta." ilmoitusid)})))

(defn tee-onnistunut-ilmoitustoimenpidevastaus []
  (let [vastauksen-data {:ilmoitukset "Ilmoitustoimenpide kirjattu onnistuneesti"}]
    vastauksen-data))

(defn kirjaa-ilmoitustoimenpide [db tloik parametrit data]
  (let [ilmoitusid (Integer/parseInt (:id parametrit))
        id (hae-ilmoituksen-id db ilmoitusid)
        ilmoitustoimenpide (:ilmoitustoimenpide data)
        ilmoittaja (:ilmoittaja ilmoitustoimenpide)
        kasittelija (:kasittelija ilmoitustoimenpide)
        _ (log/debug (format "Kirjataan toimenpide ilmoitukselle, jonka id on: %s ja ilmoitusid on: %s" id ilmoitusid))
        ilmoitustoimenpide-id
        (:id (ilmoitukset/luo-ilmoitustoimenpide<!
               db
               id
               ilmoitusid
               (pvm-string->java-sql-date (:aika ilmoitustoimenpide))
               (:vapaateksti ilmoitustoimenpide)
               (:tyyppi ilmoitustoimenpide)
               (get-in ilmoittaja [:henkilo :etunimi])
               (get-in ilmoittaja [:henkilo :sukunimi])
               (get-in ilmoittaja [:henkilo :matkapuhelin])
               (get-in ilmoittaja [:henkilo :tyopuhelin])
               (get-in ilmoittaja [:henkilo :sahkoposti])
               (get-in ilmoittaja [:organisaatio :nimi])
               (get-in ilmoittaja [:organisaatio :ytunnus])
               (get-in kasittelija [:henkilo :etunimi])
               (get-in kasittelija [:henkilo :sukunimi])
               (get-in kasittelija [:henkilo :matkapuhelin])
               (get-in kasittelija [:henkilo :tyopuhelin])
               (get-in kasittelija [:henkilo :sahkoposti])
               (get-in kasittelija [:organisaatio :nimi])
               (get-in kasittelija [:organisaatio :ytunnus])))]
    (tloik/laheta-ilmoitustoimenpide tloik ilmoitustoimenpide-id)
    (tee-onnistunut-ilmoitustoimenpidevastaus)))

(defn ilmoituslahettaja [integraatioloki tapahtumat kanava tapahtuma-id sulje-lahetyksen-jalkeen?]
  (fn [ilmoitukset]
    (send! kanava
           (aja-virhekasittelyn-kanssa
             (fn []
               (let [data {:ilmoitukset (mapv (fn [ilmoitus] (sanomat/rakenna-ilmoitus (konversio/alaviiva->rakenne ilmoitus))) ilmoitukset)}
                     vastaus (tee-vastaus json-skeemat/+ilmoitusten-haku+ data)]
                 (lokita-vastaus integraatioloki :hae-ilmoitukset vastaus tapahtuma-id)
                 (doseq [ilmoitus ilmoitukset]
                   (notifikaatiot/ilmoita-lahetetysta-ilmoituksesta tapahtumat (:ilmoitusid ilmoitus) :api))
                 vastaus)))
           sulje-lahetyksen-jalkeen?)))

(defn kaynnista-ilmoitusten-kuuntelu [db integraatioloki tapahtumat request]
  (aja-virhekasittelyn-kanssa
   (fn []
     (let [parametrit (:params request)
           urakka-id (when (:id parametrit) (Integer/parseInt (:id parametrit)))
           muuttunut-jalkeen (some-> parametrit (get "muuttunutJalkeen")  parametrit/pvm-aika)
           sulje-lahetyksen-jalkeen? (if (get parametrit "stream") (not (Boolean/valueOf (get parametrit "stream"))) true)
           tapahtuma-id (lokita-kutsu integraatioloki :hae-ilmoitukset request nil)
           kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))]
       (log/debug (format "Käynnistetään ilmoitusten kuuntelu urakalle id: %s. Muutosaika: %s." urakka-id muuttunut-jalkeen))
       (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
       (with-channel request kanava
         (on-close kanava
                   (fn [_]
                     (log/debug (format "Suljetaan urakan id: %s ilmoitusten kuuntelu." urakka-id))
                     (notifikaatiot/lopeta-ilmoitusten-kuuntelu tapahtumat urakka-id)))

         (let [laheta-ilmoitukset (ilmoituslahettaja integraatioloki tapahtumat kanava tapahtuma-id sulje-lahetyksen-jalkeen?)
               odottavat-ilmoitukset (and muuttunut-jalkeen (ilmoitukset/hae-muuttuneet-ilmoitukset db urakka-id muuttunut-jalkeen))]
           (when-not (empty? odottavat-ilmoitukset)
             (laheta-ilmoitukset odottavat-ilmoitukset))
           (notifikaatiot/kuuntele-urakan-ilmoituksia
            tapahtumat
            urakka-id
            (fn [ilmoitus-id]
              (laheta-ilmoitukset (ilmoitukset/hae-ilmoitukset-idlla db [(Integer/parseInt ilmoitus-id)]))))))))))

(defrecord Ilmoitukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki klusterin-tapahtumat :klusterin-tapahtumat tloik :tloik :as this}]
    (julkaise-reitti
      http :hae-ilmoitukset
      (GET "/api/urakat/:id/ilmoitukset" request
        (kaynnista-ilmoitusten-kuuntelu db integraatioloki klusterin-tapahtumat request)))

    (julkaise-reitti
      http :kirjaa-ilmoitustoimenpide
      (PUT "/api/ilmoitukset/:id/" request
        (kasittele-kutsu db integraatioloki :kirjaa-ilmoitustoimenpide request json-skeemat/+ilmoitustoimenpiteen-kirjaaminen+ json-skeemat/+kirjausvastaus+
                         (fn [parametrit data _ db] (kirjaa-ilmoitustoimenpide db tloik parametrit data)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-ilmoitukset)
    (poista-palvelut http :kirjaa-ilmoitustoimenpide)
    this))
