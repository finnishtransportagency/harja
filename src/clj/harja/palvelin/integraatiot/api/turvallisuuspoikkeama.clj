(ns harja.palvelin.integraatiot.api.turvallisuuspoikkeama
  "Turvallisuuspoikkeaman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]

            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer
             [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-turvallisuuspoikkeamalle]]

            [harja.kyselyt.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
            [harja.kyselyt.kommentit :as kommentit]

            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Kaikki turvallisuuspoikkeamat kirjattu onnistuneesti"}]
    vastauksen-data))

(defn tarkista-ammatin-selite [turvallisuuspoikkeama]
  (cond
    (and (not= (:tyontekijanammatti turvallisuuspoikkeama) "muu_tyontekija")
         (not (str/blank? (:ammatinselite turvallisuuspoikkeama))))
    (throw+ {:type    virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+ :viesti "Ylimääräinen kenttä 'ammatinselite'. Tämä annetaan vain jos työntekijän ammatti on 'muu_tyontekija'."}]})))

(defn luo-turvallisuuspoikkeama [db urakka-id kirjaaja data]
  (let [{:keys [tunniste sijainti kuvaus kohde vaylamuoto luokittelu ilmoittaja
                tapahtumapaivamaara paattynyt kasitelty tyontekijanammatti ammatinselite tyotehtava
                aiheutuneetVammat sairauspoissaolopaivat sairaalahoitovuorokaudet vahinkoluokittelu vakavuusaste]} data
        tie (:tie sijainti)
        koordinaatit (:koordinaatit sijainti)]
    (log/debug "Turvallisuuspoikkeamaa ei löytynyt ulkoisella id:llä (" (:id tunniste) "). Luodaan uusi.")
    (let [tp-id (:id (turvallisuuspoikkeamat/luo-turvallisuuspoikkeama<!
                       db
                       urakka-id
                       (aika-string->java-sql-date tapahtumapaivamaara)
                       (aika-string->java-sql-date paattynyt)
                       (aika-string->java-sql-date kasitelty)
                       tyontekijanammatti
                       ammatinselite
                       tyotehtava
                       kuvaus
                       aiheutuneetVammat
                       sairauspoissaolopaivat
                       sairaalahoitovuorokaudet
                       (konv/vec->array luokittelu)
                       (:id kirjaaja)
                       (konv/vec->array vahinkoluokittelu)
                       vakavuusaste))]
      (log/debug "Luotiin uusi turvallisuuspoikkeama id:llä " tp-id)
      (turvallisuuspoikkeamat/aseta-ulkoinen-id<! db (:id tunniste) tp-id)
      (turvallisuuspoikkeamat/aseta-turvallisuuspoikkeaman-sijainti-ulkoisella-idlla<!
        db
        (:x koordinaatit)
        (:y koordinaatit)
        (:numero tie)
        (:aet tie)
        (:let tie)
        (:aos tie)
        (:los tie)
        (:id tunniste)
        (:id kirjaaja))
      tp-id)))

(defn paivita-turvallisuuspoikkeama [db urakka-id kirjaaja data]
  (let [{:keys [tunniste sijainti kuvaus kohde vaylamuoto luokittelu ilmoittaja
                tapahtumapaivamaara paattynyt kasitelty tyontekijanammatti ammatinselite tyotehtava
                aiheutuneetVammat sairauspoissaolopaivat sairaalahoitovuorokaudet vahinkoluokittelu vakavuusaste]} data
        tie (:tie sijainti)
        koordinaatit (:koordinaatit sijainti)]
    (log/debug "Selite: " (pr-str ammatinselite))
    (log/debug "Turvallisuuspoikkeama on olemassa ulkoisella id:llä (" (:id tunniste) "). Päivitetään.")
    (turvallisuuspoikkeamat/paivita-turvallisuuspoikkeama-ulkoisella-idlla<!
      db
      urakka-id
      (aika-string->java-sql-date tapahtumapaivamaara)
      (aika-string->java-sql-date paattynyt)
      (aika-string->java-sql-date kasitelty)
      tyontekijanammatti
      ammatinselite
      tyotehtava
      kuvaus
      aiheutuneetVammat
      sairauspoissaolopaivat
      sairaalahoitovuorokaudet
      (konv/vec->array luokittelu)
      (:id kirjaaja)
      (konv/vec->array vahinkoluokittelu)
      vakavuusaste
      (:id tunniste)
      (:id kirjaaja))
    (:id (turvallisuuspoikkeamat/aseta-turvallisuuspoikkeaman-sijainti-ulkoisella-idlla<!
           db
           (:x koordinaatit)
           (:y koordinaatit)
           (:numero tie)
           (:aet tie)
           (:let tie)
           (:aos tie)
           (:los tie)
           (:id tunniste)
           (:id kirjaaja)))))

(defn luo-tai-paivita-turvallisuuspoikkeama [db urakka-id kirjaaja data]
  (let [{:keys [tunniste sijainti]} data]
    (log/debug "Onko turvallisuuspoikkeama jo olmessa?")
    (if (turvallisuuspoikkeamat/onko-olemassa-ulkoisella-idlla? db (:id tunniste) (:id kirjaaja))
      (paivita-turvallisuuspoikkeama db urakka-id kirjaaja data)
      (luo-turvallisuuspoikkeama db urakka-id kirjaaja data))))

(defn tallenna-kommentit [db tp-id kirjaaja kommentit]
  (log/debug "Tallennetaan turvallisuuspoikkeamalle " tp-id " " (count kommentit) " kommenttia.")
  (doseq [kommentin-data kommentit]
    (let [kommentti (kommentit/luo-kommentti<! db "urakoitsija" (:kommentti kommentin-data) nil (:id kirjaaja))
          kommentti-id (:id kommentti)]
      (turvallisuuspoikkeamat/liita-kommentti<! db tp-id kommentti-id))))

(defn tallenna-korjaavat-toimenpiteet
  "Luo tarkoituksella aina uudet korjaavat toimenpiteet. Halutaan, ettei tuleva uusi päivitys turvallisuuspoikkeamaan
  ylikirjoita mahdollisesti UI:n kautta kirjattuja juttuja."
  [db tp-id _ korjaavat]
  (log/debug "Tallennetaan turvallisuuspoikkeamalle " tp-id " " (count korjaavat) " korjaavaa toimenpidettä.")
  (doseq [korjaava korjaavat]
    (turvallisuuspoikkeamat/luo-korjaava-toimenpide<! db tp-id (:kuvaus korjaava)
                                                      (aika-string->java-sql-date (:suoritettu korjaava))
                                                      (str
                                                        (get-in korjaava [:vastaavahenkilo :etunimi])
                                                        " "
                                                        (get-in korjaava [:vastaavahenkilo :sukunimi])))))

(defn tallenna-turvallisuuspoikkeama [liitteiden-hallinta db urakka-id kirjaaja data]
  (log/debug "Aloitetaan turvallisuuspoikkeaman tallennus.")
  (jdbc/with-db-transaction [db db]
    (let [tp-id (luo-tai-paivita-turvallisuuspoikkeama db urakka-id kirjaaja data)
          kommentit (:kommentit data)
          korjaavat (:korjaavatToimenpiteet data)
          liitteet (:liitteet data)]
      (tallenna-korjaavat-toimenpiteet db tp-id kirjaaja korjaavat)
      (tallenna-kommentit db tp-id kirjaaja kommentit)
      (log/debug "Tallennetaan turvallisuuspoikkeamalle " tp-id " " (count liitteet) " liitettä.")
      (tallenna-liitteet-turvallisuuspoikkeamalle db liitteiden-hallinta urakka-id tp-id kirjaaja liitteet))))

(defn kirjaa-turvallisuuspoikkeama [liitteiden-hallinta db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan " (count (:turvallisuuspoikkeamat data))
               " uutta turvallisuuspoikkeamaa urakalle id:" urakka-id " kayttäjän:"
               (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) ") tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (doseq [turvallisuuspoikkeama (:turvallisuuspoikkeamat data)]
      (tarkista-ammatin-selite turvallisuuspoikkeama))
    (doseq [turvallisuuspoikkeama (:turvallisuuspoikkeamat data)]
      (tallenna-turvallisuuspoikkeama liitteiden-hallinta db urakka-id kirjaaja turvallisuuspoikkeama))
    (tee-onnistunut-vastaus)))

(defrecord Turvallisuuspoikkeama []
  component/Lifecycle
  (start [{http            :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta
           integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-turvallisuuspoikkeama
      (POST "/api/urakat/:id/turvallisuuspoikkeama" request
        (kasittele-kutsu db integraatioloki :lisaa-turvallisuuspoikkeama request
                         json-skeemat/+turvallisuuspoikkeama-kirjaus+ json-skeemat/+kirjausvastaus+
                         (fn [parametrit data kayttaja db]
                           (kirjaa-turvallisuuspoikkeama liitteiden-hallinta db parametrit data kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-turvallisuuspoikkeama)
    this))