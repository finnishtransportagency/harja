(ns harja.palvelin.integraatiot.api.turvallisuuspoikkeama
  "Turvallisuuspoikkeaman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer
             [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus
              tee-kirjausvastauksen-body]]
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
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [clj-time.core :as t]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.core.async :as async])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tarkista-ammatin-selitteen-tallennus [turvallisuuspoikkeamat]
  (when (some #(and (not= (get-in % [:henkilovahinko :tyontekijanammatti]) "muu_tyontekija")
                    (not (str/blank? (get-in % [:henkilovahinko :ammatinselite]))))
              turvallisuuspoikkeamat)
    "Ammatin selitettä ei tallennettu, sillä työntekijän ammatti ei ollut 'muu_tyontekija'."))

(defn tarkista-henkilovahingon-tallennus [turvallisuuspoikkeamat]
  (when (some #(not (some #{"henkilovahinko"} (:vahinkoluokittelu %)))
              turvallisuuspoikkeamat)
    "Henkilövahinkoja ei tallennettu, sillä turvallisuuspoikkeaman tyyppi ei ollut henkilövahinko."))

(defn kasaa-varoitukset [turvallisuuspoikkeamat]
  (keep identity (apply conj [] [(tarkista-ammatin-selitteen-tallennus turvallisuuspoikkeamat)
                                 (tarkista-henkilovahingon-tallennus turvallisuuspoikkeamat)])))

(defn vastaus [turvallisuuspoikkeamat]
  (let [varoitukset (kasaa-varoitukset turvallisuuspoikkeamat)]
    (tee-kirjausvastauksen-body {:ilmoitukset "Kaikki turvallisuuspoikkeamat kirjattu onnistuneesti"
                                 :varoitukset (when-not (empty? varoitukset) (str/join " " varoitukset))})))

(defn tallenna-ammatinselite? [turvallisuuspoikkeama]
  (= (get-in turvallisuuspoikkeama [:henkilovahinko :tyontekijanammatti]) "muu_tyontekija"))

(defn tallenna-henkilovahinko? [turvallisuuspoikkeama]
  (some #{"henkilovahinko"} (:vahinkoluokittelu turvallisuuspoikkeama)))

(defn luo-turvallisuuspoikkeama [db urakka-id kirjaaja data]
  (let [{:keys [tunniste sijainti kuvaus vaylamuoto luokittelu ilmoittaja seuraukset
                tapahtumapaivamaara otsikko kasitelty vahinkoluokittelu vakavuusaste henkilovahinko
                toteuttaja tilaaja turvallisuuskoordinaattori laatija paikan_kuvaus
                vaarallisten_aineiden_kuljetus vaarallisten_aineiden_vuoto]} data
        {:keys [tyontekijanammatti ammatinselite vahingoittuneetRuumiinosat
                aiheutuneetVammat sairauspoissaolopaivat sairaalahoitovuorokaudet sairauspoissaoloJatkuu]} henkilovahinko
        tie (:tie sijainti)
        koordinaatit (:koordinaatit sijainti)]
    (log/debug "Turvallisuuspoikkeamaa ei löytynyt ulkoisella id:llä (" (:id tunniste) "). Luodaan uusi.")
    (let [tp-id (:id (turvallisuuspoikkeamat/luo-turvallisuuspoikkeama<!
                       db
                       {:urakka urakka-id
                        :tyyppi (konv/seq->array luokittelu)
                        :tapahtunut (aika-string->java-sql-date tapahtumapaivamaara)
                        :kasitelty (aika-string->java-sql-date kasitelty)
                        :ammatti (when (tallenna-henkilovahinko? data) tyontekijanammatti)
                        :ammatti_muu (when (and (tallenna-henkilovahinko? data)
                                                (tallenna-ammatinselite? data))
                                       ammatinselite)
                        :kuvaus kuvaus
                        :vammat (when (tallenna-henkilovahinko? data) (konv/seq->array [(first aiheutuneetVammat)]))
                        :poissa (when (tallenna-henkilovahinko? data) sairauspoissaolopaivat)
                        :sairaalassa (when (tallenna-henkilovahinko? data) sairaalahoitovuorokaudet)
                        :vahinkoluokittelu (konv/seq->array vahinkoluokittelu)
                        :kayttaja (:id kirjaaja)
                        :vakavuusaste vakavuusaste
                        :toteuttaja toteuttaja
                        :tilaaja tilaaja
                        :numero (:numero tie)
                        :alkuosa (:aosa tie)
                        :alkuetaisyys (:aet tie)
                        :loppuosa (:losa tie)
                        :loppuetaisyys (:let tie)
                        :tila "avoin" ;; API:n kautta kirjattu ilmoitus on aina avoin
                        :tapahtuman_otsikko otsikko
                        :vaarallisten_aineiden_kuljetus vaarallisten_aineiden_kuljetus
                        :vaarallisten_aineiden_vuoto (if vaarallisten_aineiden_kuljetus
                                                       vaarallisten_aineiden_vuoto
                                                       false)
                        :paikan_kuvaus paikan_kuvaus
                        :sijainti (geo/geometry
                                    (geo/clj->pg {:type :point
                                                  :coordinates ((juxt :x :y) koordinaatit)}))
                        :vahingoittuneet_ruumiinosat (when (tallenna-henkilovahinko? data)
                                                       (konv/seq->array [(first vahingoittuneetRuumiinosat)]))
                        :sairauspoissaolo_jatkuu (when (tallenna-henkilovahinko? data)
                                                   sairauspoissaoloJatkuu)
                        :aiheutuneet_seuraukset seuraukset
                        :ilmoittaja_etunimi (:etunimi ilmoittaja)
                        :ilmoittaja_sukunimi (:sukunimi ilmoittaja)
                        :vaylamuoto vaylamuoto
                        :turvallisuuskoordinaattori_etunimi (:etunimi turvallisuuskoordinaattori)
                        :turvallisuuskoordinaattori_sukunimi (:sukunimi turvallisuuskoordinaattori)
                        :laatija (:id kirjaaja)
                        :ulkoinen_id (:id tunniste)
                        :ilmoitukset_lahetetty nil
                        :lahde "harja-api"}))]
      (log/debug "Luotiin uusi turvallisuuspoikkeama id:llä " tp-id)
      tp-id)))

(defn paivita-turvallisuuspoikkeama [db urakka-id kirjaaja data]
  (let [{:keys [tunniste sijainti kuvaus vaylamuoto luokittelu ilmoittaja seuraukset henkilovahinko
                tapahtumapaivamaara paattynyt kasitelty vahinkoluokittelu vakavuusaste
                otsikko paikan_kuvaus vaarallisten_aineiden_kuljetus vaarallisten_aineiden_vuoto
                toteuttaja tilaaja turvallisuuskoordinaattori]} data
        {:keys [tyontekijanammatti ammatinselite tyotehtava vahingoittuneetRuumiinosat
                aiheutuneetVammat sairauspoissaolopaivat sairaalahoitovuorokaudet sairauspoissaoloJatkuu]} henkilovahinko
        tie (:tie sijainti)
        koordinaatit (:koordinaatit sijainti)]
    (log/debug "Turvallisuuspoikkeama on olemassa ulkoisella id:llä (" (:id tunniste) "). Päivitetään.")
    (turvallisuuspoikkeamat/paivita-turvallisuuspoikkeama-ulkoisella-idlla!
      db
      {:urakka urakka-id
       :tapahtunut (aika-string->java-sql-date tapahtumapaivamaara)
       :paattynyt (aika-string->java-sql-date paattynyt)
       :kasitelty (aika-string->java-sql-date kasitelty)
       :ammatti (when (tallenna-henkilovahinko? data) tyontekijanammatti)
       :ammatti_muu (when (and (tallenna-henkilovahinko? data)
                               (tallenna-ammatinselite? data))
                      ammatinselite)
       :tehtava (when (tallenna-henkilovahinko? data) tyotehtava)
       :kuvaus kuvaus
       :vammat (when (tallenna-henkilovahinko? data) (konv/seq->array [(first aiheutuneetVammat)]))
       :poissa (when (tallenna-henkilovahinko? data) sairauspoissaolopaivat)
       :sairaalassa (when (tallenna-henkilovahinko? data) sairaalahoitovuorokaudet)
       :tyyppi (konv/seq->array luokittelu)
       :kayttaja (:id kirjaaja)
       :vahinkoluokittelu (konv/seq->array vahinkoluokittelu)
       :vakavuusaste vakavuusaste
       :toteuttaja toteuttaja
       :tilaaja tilaaja
       :x_koordinaatti (:x koordinaatit)
       :y_koordinaatti (:y koordinaatit)
       :numero (:numero tie)
       :aos (:aosa tie)
       :aet (:aet tie)
       :los (:losa tie)
       :let (:let tie)
       :tila "avoin" ;; API:n kautta kirjattu ilmoitus on aina avoin
       :tapahtuman_otsikko otsikko
       :vaarallisten_aineiden_kuljetus vaarallisten_aineiden_kuljetus
       :vaarallisten_aineiden_vuoto (if vaarallisten_aineiden_kuljetus
                                      vaarallisten_aineiden_vuoto
                                      false)
       :paikan_kuvaus paikan_kuvaus
       :vahingoittuneet_ruumiinosat
       (when (tallenna-henkilovahinko? data) (konv/seq->array [(first vahingoittuneetRuumiinosat)]))
       :sairauspoissaolo_jatkuu (when (tallenna-henkilovahinko? data) sairauspoissaoloJatkuu)
       :aiheutuneet_seuraukset seuraukset
       :ilmoittaja_etunimi (:etunimi ilmoittaja)
       :ilmoittaja_sukunimi (:sukunimi ilmoittaja)
       :vaylamuoto vaylamuoto
       :turvallisuuskoordinaattori_etunimi (:etunimi turvallisuuskoordinaattori)
       :turvallisuuskoordinaattori_sukunimi (:sukunimi turvallisuuskoordinaattori)
       :laatija (:id kirjaaja)
       :ulkoinen_id (:id tunniste)
       :luoja (:id kirjaaja)})
    (turvallisuuspoikkeamat/hae-turvallisuuspoikkeaman-id-ulkoisella-idlla
      db {:ulkoinen_id (:id tunniste)
          :luoja (:id kirjaaja)})))

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
  [db tp-id kirjaaja korjaavat]
  (log/debug "Tallennetaan turvallisuuspoikkeamalle " tp-id " " (count korjaavat) " korjaavaa toimenpidettä.")
  (doseq [korjaava korjaavat]
    (log/info "luodaana korjaava, args: " tp-id (:kuvaus korjaava)
              (aika-string->java-sql-date (:suoritettu korjaava))
              (str
                (get-in korjaava [:vastaavahenkilo :etunimi])
                " "
                (get-in korjaava [:vastaavahenkilo :sukunimi])))
    (try (turvallisuuspoikkeamat/luo-korjaava-toimenpide<! db
                                                           tp-id
                                                           (:otsikko korjaava)
                                                           "avoin" ;; API:n kautta aina avoin
                                                           (:vastuuhenkilo korjaava)
                                                           (:toteuttaja korjaava)
                                                           (:kuvaus korjaava)
                                                           (aika-string->java-sql-date (:suoritettu korjaava))
                                                           (:id kirjaaja))
         (catch Throwable t
           (log/info "Ongelma kirjattaessa korjaava toimenpide: " t)))))

(defn vaadi-turvallisuuspoikkeama-ei-tulevaisuudessa [{:keys [tapahtumapaivamaara] :as data}]
  (when (t/after? (json/aika-string->joda-time tapahtumapaivamaara) (t/now))
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+
                        :viesti "Tapahtumapäivämäärä ei voi olla tulevaisuudessa"}]})))

(defn tallenna-turvallisuuspoikkeama [liitteiden-hallinta db urakka-id kirjaaja data]
  (log/debug "Aloitetaan turvallisuuspoikkeaman tallennus.")
  (jdbc/with-db-transaction [db db]
    (vaadi-turvallisuuspoikkeama-ei-tulevaisuudessa data)
    (let [tp-id (luo-tai-paivita-turvallisuuspoikkeama db urakka-id kirjaaja data)
          kommentit (:kommentit data)
          korjaavat (:korjaavatToimenpiteet data)
          liitteet (:liitteet data)]
      (tallenna-korjaavat-toimenpiteet db tp-id kirjaaja korjaavat)
      (tallenna-kommentit db tp-id kirjaaja kommentit)
      (log/debug "Tallennetaan turvallisuuspoikkeamalle " tp-id " " (count liitteet) " liitettä.")
      (tallenna-liitteet-turvallisuuspoikkeamalle db liitteiden-hallinta urakka-id tp-id kirjaaja liitteet)
      tp-id)))

(defn laheta-poikkeamat-turin [turi idt]
  (when turi
    (doseq [id idt]
      (turi/laheta-turvallisuuspoikkeama turi id))))

(defn kirjaa-turvallisuuspoikkeama [liitteiden-hallinta turi db {id :id} {turvallisuuspoikkeamat :turvallisuuspoikkeamat} kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug (format "Kirjataan: %s uutta turvallisuuspoikkeamaa urakalle id: %s kayttäjän: %s (id: %s) tekemänä."
                       (count turvallisuuspoikkeamat)
                       urakka-id
                       (:kayttajanimi kirjaaja)
                       (:id kirjaaja)))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)


    (let [idt (mapv (fn [turvallisuuspoikkeama]
                      (tallenna-turvallisuuspoikkeama liitteiden-hallinta db urakka-id kirjaaja turvallisuuspoikkeama))
                    turvallisuuspoikkeamat)]
      (async/thread (laheta-poikkeamat-turin turi idt)))
    (vastaus turvallisuuspoikkeamat)))

(defrecord Turvallisuuspoikkeama []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta turi :turi
           integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-turvallisuuspoikkeama
      (POST "/api/urakat/:id/turvallisuuspoikkeama" request
        (kasittele-kutsu db integraatioloki :lisaa-turvallisuuspoikkeama request
                         json-skeemat/turvallisuuspoikkeamien-kirjaus json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (kirjaa-turvallisuuspoikkeama liitteiden-hallinta turi db parametrit data kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-turvallisuuspoikkeama)
    this))
