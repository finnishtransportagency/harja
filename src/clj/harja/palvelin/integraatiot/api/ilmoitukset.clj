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
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-havainnolle]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [harja.kyselyt.ilmoitukset :as ilmoitukset]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.api.sanomat.ilmoitus-sanomat :as sanomat]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tarkista-ilmoitus [db ilmoitusid]
  (when-not (ilmoitukset/onko-olemassa? db ilmoitusid)
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi  :tuntematon-ilmoitus
       :viesti (format "Ilmoitus id:llä %s. ei löydy ilmoitusta." ilmoitusid)})))

(defn tee-onnistunut-ilmoitustoimenpidevastaus []
  (let [vastauksen-data {:ilmoitukset "Ilmoitustoimenpide kirjattu onnistuneesti"}]
    vastauksen-data))

(defn kirjaa-ilmoitustoimenpide [db tloik parametrit data]
  (println "-----------------> parametrit" parametrit)
  (println "-----------------> data" data)

  (let [ilmoitustoimenpide (:ilmoitustoimenpide data)
        ilmoitusid (:ilmoitusid ilmoitustoimenpide)]
    (println "-----------------> ilmoitustoimenpide" ilmoitustoimenpide)
    (tarkista-ilmoitus db ilmoitusid)
    (ilmoitukset/luo-ilmoitustoimenpide<!
      db)

    ;; todo: tallenna kantaan
    ;; todo: lähetä t-loikn
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
  (let [parametrit (:params request)
        urakka-id (when (:id parametrit) (Integer/parseInt (:id parametrit)))
        viimeisin-id (when (get parametrit "viimeisinId") (Integer/parseInt (get parametrit "viimeisinId")))
        sulje-lahetyksen-jalkeen? (if (get parametrit "stream") (not (Boolean/valueOf (get parametrit "stream"))) true)
        tapahtuma-id (lokita-kutsu integraatioloki :hae-ilmoitukset request nil)
        kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))]
    (log/debug (format "Käynnistetään ilmoitusten kuuntelu urakalle id: %s. Viimeisin haettu ilmoitus id: %s." urakka-id viimeisin-id))
    (aja-virhekasittelyn-kanssa
      (fn []
        (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
        (with-channel request kanava
          (on-close kanava
                    (fn [_]
                      (log/debug (format "Suljetaan urakan id: %s ilmoitusten kuuntelu." urakka-id))
                      (notifikaatiot/lopeta-ilmoitusten-kuuntelu tapahtumat urakka-id)))

          (let [laheta-ilmoitukset (ilmoituslahettaja integraatioloki tapahtumat kanava tapahtuma-id sulje-lahetyksen-jalkeen?)
                odottavat-ilmoitukset (and viimeisin-id (ilmoitukset/hae-ilmoituksen-jalkeen-saapuneet-ilmoitukset db urakka-id viimeisin-id))]
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
