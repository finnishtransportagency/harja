(ns harja.palvelin.integraatiot.api.laatupoikkeamat
  "Laatupoikkeamajen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as xml-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.palvelin.komponentit.liitteet :refer [->Liitteet] :as liitteet]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-laatupoikkeamalle]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Kaikki laatupoikkeamat kirjattu onnistuneesti"}]
    vastauksen-data))

(defn tallenna-laatupoikkeama [db urakka-id kirjaaja data]
  (let [{:keys [tunniste sijainti kuvaus kohde paivamaara]} data
        tie (:tie sijainti)
        koordinaatit (:koordinaatit sijainti)]
    (if (laatupoikkeamat/onko-olemassa-ulkoisella-idlla? db (:id tunniste) (:id kirjaaja))
      (:id (laatupoikkeamat/paivita-laatupoikkeama-ulkoisella-idlla<!
             db
             (pvm-string->java-sql-date paivamaara)
             kohde
             kuvaus
             (:x koordinaatit)
             (:y koordinaatit)
             (:numero tie)
             (:aosa tie)
             (:losa tie)
             (:aet tie)
             (:let tie)
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
             (:x koordinaatit)
             (:y koordinaatit)
             (:numero tie)
             (:aosa tie)
             (:losa tie)
             (:aet tie)
             (:let tie)
             (:id tunniste))))))

(defn tallenna-kommentit [db laatupoikkeama-id kirjaaja kommentit]
  (doseq [kommentin-data kommentit]
    (let [kommentti (kommentit/luo-kommentti<! db "urakoitsija" (:kommentti kommentin-data) nil (:id kirjaaja))
          kommentti-id (:id kommentti)]
      (laatupoikkeamat/liita-kommentti<! db laatupoikkeama-id kommentti-id))))

(defn tallenna [liitteiden-hallinta db urakka-id kirjaaja data]
  (jdbc/with-db-transaction [transaktio db]
    (let [laatupoikkeama-id (tallenna-laatupoikkeama transaktio urakka-id kirjaaja data)
          kommentit (:kommentit data)
          liitteet (:liitteet data)]
      (log/info "LIITE " (count liitteet))
      (tallenna-kommentit transaktio laatupoikkeama-id kirjaaja kommentit)
      (tallenna-liitteet-laatupoikkeamalle transaktio liitteiden-hallinta urakka-id laatupoikkeama-id kirjaaja liitteet))))

(defn kirjaa-laatupoikkeama [liitteiden-hallinta db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi laatupoikkeama urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna liitteiden-hallinta db urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))

(defrecord Laatupoikkeamat []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-laatupoikkeama
      (POST "/api/urakat/:id/laatupoikkeama" request
            (kasittele-kutsu db integraatioloki
                             :lisaa-laatupoikkeama request
                             xml-skeemat/+havainnon-kirjaus+ xml-skeemat/+kirjausvastaus+
                             (fn [parametrit data kayttaja db]
                               (kirjaa-laatupoikkeama liitteiden-hallinta db parametrit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-laatupoikkeama)
    this))
