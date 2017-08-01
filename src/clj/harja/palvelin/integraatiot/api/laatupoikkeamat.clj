(ns harja.palvelin.integraatiot.api.laatupoikkeamat
  "Laatupoikkeamajen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.palvelin.komponentit.liitteet :refer [->Liitteet]]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-laatupoikkeamalle]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.sijainnit :as sijainnit]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus [varoitukset]
  (tee-kirjausvastauksen-body {:ilmoitukset "Laatupoikkeama kirjattu onnistuneesti"
                               :varoitukset (when-not (empty? varoitukset) varoitukset)}))

(defn tallenna-laatupoikkeama [db urakka-id kirjaaja data tr-osoite geometria]
  (let [{:keys [tunniste kuvaus kohde aika sisaltaa-poikkeamaraportin]} data]
    (if (laatupoikkeamat/onko-olemassa-ulkoisella-idlla? db (:id tunniste) (:id kirjaaja))
      (:id (laatupoikkeamat/paivita-laatupoikkeama-ulkoisella-idlla<!
             db
             (aika-string->java-sql-date aika)
             kohde
             kuvaus
             geometria
             (:tie tr-osoite)
             (:aosa tr-osoite)
             (:losa tr-osoite)
             (:aet tr-osoite)
             (:let tr-osoite)
             (:id kirjaaja)
             sisaltaa-poikkeamaraportin
             (:id tunniste)
             (:id kirjaaja)))
      (:id (laatupoikkeamat/luo-laatupoikkeama<!
             db
             "harja-api"
             urakka-id
             (aika-string->java-sql-date aika)
             "urakoitsija"
             kohde
             true
             (:id kirjaaja)
             kuvaus
             geometria
             (:numero tr-osoite)
             (:aosa tr-osoite)
             (:losa tr-osoite)
             (:aet tr-osoite)
             (:let tr-osoite)
             nil
             sisaltaa-poikkeamaraportin
             (:id tunniste))))))

(defn tallenna-kommentit [db laatupoikkeama-id kirjaaja kommentit]
  (doseq [kommentin-data kommentit]
    (let [kommentti (kommentit/luo-kommentti<! db "urakoitsija" (:kommentti kommentin-data) nil (:id kirjaaja))
          kommentti-id (:id kommentti)]
      (laatupoikkeamat/liita-kommentti<! db laatupoikkeama-id kommentti-id))))

(defn tallenna [liitteiden-hallinta db urakka-id kirjaaja data]
  (let [tr-osoite (sijainnit/hae-tierekisteriosoite db (:alkusijainti data) (:loppusijainti data))
        geometria (sijainnit/tee-geometria (:alkusijainti data) (:loppusijainti data))]
    (jdbc/with-db-transaction [db db]
      (let [laatupoikkeama-id (tallenna-laatupoikkeama db urakka-id kirjaaja data tr-osoite geometria)
            kommentit (:kommentit data)
            liitteet (:liitteet data)]
        (tallenna-kommentit db laatupoikkeama-id kirjaaja kommentit)
        (tallenna-liitteet-laatupoikkeamalle db liitteiden-hallinta urakka-id laatupoikkeama-id kirjaaja liitteet)))
    (when-not tr-osoite (format "Annetulla sijainnilla ei voitu päätellä sijaintia tieverkolla (alku: %s, loppu %s)."
                                (:alkusijainti data) (:loppusijainti data)))))

(defn kirjaa-laatupoikkeama [liitteiden-hallinta db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi laatupoikkeama urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tee-onnistunut-vastaus (tallenna liitteiden-hallinta db urakka-id kirjaaja data))))

(defrecord Laatupoikkeamat []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-laatupoikkeama
      (POST "/api/urakat/:id/laatupoikkeama" request
        (kasittele-kutsu db integraatioloki
                         :lisaa-laatupoikkeama request
                         json-skeemat/laatupoikkeaman-kirjaus json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (kirjaa-laatupoikkeama liitteiden-hallinta db parametrit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-laatupoikkeama)
    this))
