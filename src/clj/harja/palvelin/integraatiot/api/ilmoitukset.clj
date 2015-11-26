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
            [harja.palvelin.integraatiot.api.ilmoitusnotifikaatio :as notifikaatiot]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-havainnolle]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]])
  (:use [slingshot.slingshot :only [throw+]]))


(defn hae-viimeksi-haetun-ilmoituksen-jalkeen-saapuneet [db [:keys id] kayttaja]
  (validointi/tarkista-onko-kayttaja-organisaatiossa db id kayttaja))

(defn kirjaa-ilmoitustoimenpide [db parametrit data kayttaja])

(defn hae-ilmoitus [db integraatiotapahtuma-id ilmoitus-id]
  {
   :ilmoitukset [{:ilmoitus {
                             :ilmoitusId         "3213123"
                             :ilmoitettu         (harja.pvm/nyt)
                             :ilmoitustyyppi     "toimenpidepyynto"
                             :yhteydenottopyynto "false"
                             :ilmoittaja         {
                                                  :etunimi       "Matti"
                                                  :sukunimi      "Meikäläinen"
                                                  :puhelinnumero "08023394852"
                                                  :email         "matti.meikalainen@palvelu.fi"
                                                  }
                             :lahettaja          {
                                                  :etunimi       "Pekka"
                                                  :sukunimi      "Päivystäjä"
                                                  :puhelinnumero "929304449282"
                                                  :email         "pekka.paivystaja@livi.fi"
                                                  }
                             :seliteet           {:selite [
                                                           {:koodi "auraustarve"}
                                                           :koodi "aurausvallitNakemaesteena "
                                                           ]}

                             :sijainti           {
                                                  :koordinaatit {
                                                                 :x 41.40338
                                                                 :y 2.17403
                                                                 }
                                                  }
                             :vapaateksti        "Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti."}
                  }]})

(defn kaynnista-ilmoitusten-kuuntelu [db integraatioloki tapahtumat request]
  (let [urakka-id (Integer/parseInt (:id (:params request)))
        tapahtuma-id (lokita-kutsu integraatioloki :hae-ilmoitukset request nil)
        kayttaja (hae-kayttaja db (get (:headers request) "oam_remote_user"))]
    (log/debug (format "Käynnistetään ilmoitusten kuuntelu urakalle id: %s." urakka-id))
    (aja-virhekasittelyn-kanssa
      (fn []
        (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
        (with-channel request kanava
          (notifikaatiot/kuuntele-urakan-ilmoituksia
            tapahtumat
            urakka-id
            (fn [ilmoitus-id]
              (log/debug (format "Vastaanotettiin ilmoitus id:llä %s urakalle id:llä %s." ilmoitus-id urakka-id))
              (send! kanava
                     (aja-virhekasittelyn-kanssa
                       (fn []
                         (let [data (hae-ilmoitus db tapahtuma-id ilmoitus-id)
                               vastaus (tee-vastaus json-skeemat/+ilmoitusten-haku+ data)]
                           (lokita-vastaus integraatioloki :hae-ilmoitukset vastaus tapahtuma-id)
                           vastaus))))))
          (on-close kanava
                    (fn [_]
                      (log/debug (format "Suljetaan urakan id: %s ilmoitusten kuuntelu." urakka-id))
                      (notifikaatiot/lopeta-ilmoitusten-kuuntelu tapahtumat urakka-id))))))))

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
