(ns harja.palvelin.integraatiot.api.laatupoikkeamat
  "Laatupoikkeamajen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.palvelin.komponentit.liitteet :refer [->Liitteet]]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-laatupoikkeamalle]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.sijainnit :as sijainnit]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus [varoitukset]
  (let [vastauksen-data {:ilmoitukset "Laatupoikkeama kirjattu onnistuneesti"}]
    (if varoitukset
      (assoc vastauksen-data :varoitukset varoitukset)
      vastauksen-data)))

(defn tallenna-laatupoikkeama [db urakka-id kirjaaja data sijainti]
  (let [{:keys [tunniste kuvaus kohde paivamaara]} data]
    (if (laatupoikkeamat/onko-olemassa-ulkoisella-idlla? db (:id tunniste) (:id kirjaaja))
      (:id (laatupoikkeamat/paivita-laatupoikkeama-ulkoisella-idlla<!
             db
             (pvm-string->java-sql-date paivamaara)
             kohde
             kuvaus
             (:geometria sijainti)
             (:tie sijainti)
             (:aosa sijainti)
             (:losa sijainti)
             (:aet sijainti)
             (:let sijainti)
             (:id kirjaaja)
             (:id tunniste)
             (:id kirjaaja)))
      (:id (laatupoikkeamat/luo-laatupoikkeama<!
             db
             urakka-id
             (pvm-string->java-sql-date paivamaara)
             "urakoitsija"
             kohde
             true
             (:id kirjaaja)
             kuvaus
             (:geometria sijainti)
             (:numero sijainti)
             (:aosa sijainti)
             (:losa sijainti)
             (:aet sijainti)
             (:let sijainti)
             (:id tunniste))))))

(defn tallenna-kommentit [db laatupoikkeama-id kirjaaja kommentit]
  (doseq [kommentin-data kommentit]
    (let [kommentti (kommentit/luo-kommentti<! db "urakoitsija" (:kommentti kommentin-data) nil (:id kirjaaja))
          kommentti-id (:id kommentti)]
      (laatupoikkeamat/liita-kommentti<! db laatupoikkeama-id kommentti-id))))

(defn tallenna [liitteiden-hallinta db urakka-id kirjaaja data]
  (let [sijainti (sijainnit/hae-sijainti db (:alkusijainti data) (:loppusijainti data))]
    (jdbc/with-db-transaction [transaktio db]
      (let [laatupoikkeama-id (tallenna-laatupoikkeama transaktio urakka-id kirjaaja data sijainti)
            kommentit (:kommentit data)
            liitteet (:liitteet data)]
        (log/info "LIITE " (count liitteet))
        (tallenna-kommentit transaktio laatupoikkeama-id kirjaaja kommentit)
        (tallenna-liitteet-laatupoikkeamalle transaktio liitteiden-hallinta urakka-id laatupoikkeama-id kirjaaja liitteet)))
    (when-not sijainti "Annetulla sijainnilla ei voitu päätellä sijaintia tieverkolla.")))

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
                         json-skeemat/+laatupoikkeaman-kirjaus+ json-skeemat/+kirjausvastaus+
                         (fn [parametrit data kayttaja db]
                           (kirjaa-laatupoikkeama liitteiden-hallinta db parametrit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-laatupoikkeama)
    this))
