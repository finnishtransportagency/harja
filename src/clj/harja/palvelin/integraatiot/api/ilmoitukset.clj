(ns harja.palvelin.integraatiot.api.ilmoitukset
  "Ilmoitusten haku ja ilmoitustoimenpiteiden kirjaus"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu lokita-kutsu lokita-vastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.ilmoitusnotifikaatiot :as notifikaatiot]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-havainnolle]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [harja.palvelin.main])
  (:use [slingshot.slingshot :only [throw+]]))


(defn hae-viimeksi-haetun-ilmoituksen-jalkeen-saapuneet [db parametrit kayttaja]
  (validointi/tarkista-onko-kayttaja-organisaatiossa db ytunnus kayttaja))

(defn kirjaa-ilmoitustoimenpide [db parametrit data kayttaja]
  (validointi/tarkista-onko-kayttaja-organisaatiossa db ytunnus kayttaja))

(defn hae-ilmoitus [db ilmoitus-id]
  ;; todo: hae ilmoitus kannasta ja muodosta json
  )

(defn odota-ilmoituksia [tapahtumat kanava urakka-id]
  (notifikaatiot/kuuntele-urakan-ilmoituksia
    tapahtumat
    urakka-id
    (fn [ilmoitus-id] (send! kanava (hae-ilmoitus db ilmoitus-id)))))

(defn kaynnista-ilmoitusten-kuuntelu [db integraatioloki tapahtumat request]
  (let [urakka-id (:id (:params request))
        tapahtuma-id (lokita-kutsu integraatioloki :hae-ilmoitukset request nil)
        kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))]
    (log/debug (format "Käynnistetään ilmoitusten kuuntelu urakalle: %s" urakka-id))
    (try
      (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
      (with-channel request kanava
                    (let [vastaus-data (odota-ilmoituksia tapahtumat kanava urakka-id)
                          vastaus (tee-vastaus json-skeemat vastaus-data)]
                      (lokita-vastaus integraatioloki :hae-ilmoitukset vastaus tapahtuma-id)
                      vastaus)))
    (catch Exception e
      ;; todo: palauta hallittu response
      (log/error e "Poikkeus ilmoituskuuntelussa!"))))

(defrecord Ilmoitukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki klusterin-tapahtumat :klusterin-tapahtumat :as this}]
    (julkaise-reitti
      http :hae-ilmoitukset
      (GET "/api/urakat/:id/ilmoitukset" request
        (kaynnista-ilmoitusten-kuuntelu db integraatioloki klusterin-tapahtumat request)))

    (julkaise-reitti
      http :hae-ilmoitukset-viimeisen-onnistuneen-jalkeen
      (GET "/api/urakat/:id/ilmoitukset/:viimeksi-haettu-id" request
        (kasittele-kutsu db integraatioloki :hae-ilmoitukset-viimeisen-jalkeen request nil json-skeemat/+ilmoitusten-haku+
                         (fn [parametrit _ kayttaja db] (hae-viimeksi-haetun-ilmoituksen-jalkeen-saapuneet db parametrit kayttaja)))))

    (julkaise-reitti
      http :kirjaa-ilmoitustoimenpide
      (PUT "/api/ilmoitukset/:id/" request
           (kasittele-kutsu db integraatioloki :kirjaa-ilmoitustoimenpide request json-skeemat/+ilmoituskuittauksen-kirjaaminen+ json-skeemat/+kirjausvastaus+
                            (fn [parametrit data kayttaja db] (kirjaa-ilmoitustoimenpide db parametrit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-ilmoitukset)
    (poista-palvelut http :hae-ilmoitukset-viimeisen-jalkeen)
    (poista-palvelut http :kirjaa-ilmoitustoimenpide)
    this))
