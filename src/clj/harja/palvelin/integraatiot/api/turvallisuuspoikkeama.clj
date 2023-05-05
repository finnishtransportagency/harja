(ns harja.palvelin.integraatiot.api.turvallisuuspoikkeama
  "Turvallisuuspoikkeaman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [clojure.spec.alpha :as s]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer
             [tee-kirjausvastauksen-body kasittele-kutsu kasittele-get-kutsu]]
            [harja.pvm :as pvm]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-turvallisuuspoikkeamalle]]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
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
            [harja.palvelin.integraatiot.turi.sanomat.turvallisuuspoikkeama :as turi-sanoma]
            [harja.domain.turvallisuuspoikkeama :as turpodomain]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liitteet]
            [harja.tyokalut.xml :as xml]

            [clojure.core.async :as async])
  (:import (java.text SimpleDateFormat))
  (:use [slingshot.slingshot :only [throw+]]))


;; Sallitaan ajalle kaksi eri formaattia
(def pvm-aika-muoto1 "yyyy-MM-dd'T'HH:mm:ssX")
(def pvm-aika-muoto2 "yyyy-MM-dd'T'HH:mm:ss.SSSX")
(defn valid-aikamuoto? [string]
  (try
    (if (< (count string) 25)
      (inst? (.parse (SimpleDateFormat. pvm-aika-muoto1) string))
      (inst? (.parse (SimpleDateFormat. pvm-aika-muoto2) string)))
    (catch Exception e
      false)))
(s/def ::alkuaika #(and (string? %) (> (count %) 20) (valid-aikamuoto? %)))
(s/def ::loppuaika #(and (string? %) (> (count %) 20) (valid-aikamuoto? %)))



(defn- tarkista-turpohaun-parametrit [parametrit]
  (parametrivalidointi/tarkista-parametrit
    parametrit
    {:alkuaika "Alkuaika puuttuu"
     :loppuaika "Loppuaika puuttuu"})
  (when (not (s/valid? ::alkuaika (:alkuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Alkuaika väärässä muodossa: %s
       Anna muodossa: yyyy-MM-dd'T'HH:mm:ssX tai yyyy-MM-dd'T'HH:mm:ss.SSSX tai yyyy-MM-dd'T'HH:mm:ss.SSSZ
       esim: 2005-01-01T03:00:00+03 tai 2005-01-01T03:00:00.123+03 tai 2005-01-01T00:00:00.123Z" (:alkuaika parametrit))}))
  (when (not (s/valid? ::loppuaika (:loppuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Loppuaika väärässä muodossa: %s
       Anna muodossa: yyyy-MM-dd'T'HH:mm:ssX tai yyyy-MM-dd'T'HH:mm:ss.SSSX tai yyyy-MM-dd'T'HH:mm:ss.SSSZ
       esim: 2005-01-02T03:00:00+03 tai 2005-01-01T03:00:00.123+03 tai 2005-01-01T00:00:00.123Z" (:loppuaika parametrit))})))


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
                vaarallisten_aineiden_kuljetus vaarallisten_aineiden_vuoto
                juurisyy1 juurisyy1-selite
                juurisyy2 juurisyy2-selite
                juurisyy3 juurisyy3-selite]} data
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
                        :lahde "harja-api"
                        :juurisyy1 juurisyy1
                        :juurisyy1-selite juurisyy1-selite
                        :juurisyy2 juurisyy2
                        :juurisyy2-selite juurisyy2-selite
                        :juurisyy3 juurisyy3
                        :juurisyy3-selite juurisyy3-selite}))]
      (log/debug "Luotiin uusi turvallisuuspoikkeama id:llä " tp-id)
      tp-id)))

(defn paivita-turvallisuuspoikkeama [db urakka-id kirjaaja data]
  (let [{:keys [tunniste sijainti kuvaus vaylamuoto luokittelu ilmoittaja seuraukset henkilovahinko
                tapahtumapaivamaara paattynyt kasitelty vahinkoluokittelu vakavuusaste
                otsikko paikan_kuvaus vaarallisten_aineiden_kuljetus vaarallisten_aineiden_vuoto
                toteuttaja tilaaja turvallisuuskoordinaattori
                juurisyy1 juurisyy1-selite
                juurisyy2 juurisyy2-selite
                juurisyy3 juurisyy3-selite]} data
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
       :luoja (:id kirjaaja)
       :juurisyy1 juurisyy1
       :juurisyy1-selite juurisyy1-selite
       :juurisyy2 juurisyy2
       :juurisyy2-selite juurisyy2-selite
       :juurisyy3 juurisyy3
       :juurisyy3-selite juurisyy3-selite})
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

(defn juurisyy-parametreilla [{:keys [juurisyy1 juurisyy2 juurisyy3] :as data}]
  (merge data
         {:juurisyy1 (get juurisyy1 :juurisyy)
          :juurisyy1-selite (get juurisyy1 :selite)
          :juurisyy2 (get juurisyy2 :juurisyy)
          :juurisyy2-selite (get juurisyy2 :selite)
          :juurisyy3 (get juurisyy3 :juurisyy)
          :juurisyy3-selite (get juurisyy3 :selite)}))

(defn tallenna-turvallisuuspoikkeama [liitteiden-hallinta db urakka-id kirjaaja data]
  (log/debug "Aloitetaan turvallisuuspoikkeaman tallennus.")
  (jdbc/with-db-transaction [db db]
    (vaadi-turvallisuuspoikkeama-ei-tulevaisuudessa data)
    (let [data (juurisyy-parametreilla data)
          tp-id (luo-tai-paivita-turvallisuuspoikkeama db urakka-id kirjaaja data)
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

(def urakan-vaylamuoto
  {:tie "Tie"
   :rautatie "Rautatie"
   :vesi "Vesiväylä"})
(defn- lahde [data]
  {:lahde
   {:lahdejarjestelma "Harja"
    :lahdeid (str (:id data))}})

(defn poikkeamatyypit->tekstit [tyypit]
  (for [tyyppi tyypit]
    (turi-sanoma/poikkeamatyyppi->teksti tyyppi)))

(defn- tapahtumatiedot [{:keys [turi-id hanke-nimi hanke-sampoid tilaajanvastuuhenkilo-kayttajatunnus
                                tilaajanvastuuhenkilo-etunimi tilaajanvastuuhenkilo-sukunimi
                                tilaajanvastuuhenkilo-sposti urakka-nimi urakka-sampoid
                                urakka-loppupvm vaylamuoto urakka-tyyppi urakka-ely alueurakkanro
                                tyyppi tapahtunut kuvaus]}]
  {:tapahtumantiedot
   (merge
     (when urakka-ely
       {:elyalue (str urakka-ely " ELY")})
     (when-let [turi-id turi-id]
       {:id turi-id})
     {:tyyppi (poikkeamatyypit->tekstit tyyppi)}
     {
      :sampohankenimi hanke-nimi
      :sampohankeid hanke-sampoid
      :tilaajanvastuuhenkilokayttajatunnus tilaajanvastuuhenkilo-kayttajatunnus
      :tilaajanvastuuhenkiloetunimi tilaajanvastuuhenkilo-etunimi
      :tilaajanvastuuhenkilosukunimi tilaajanvastuuhenkilo-sukunimi
      :tilaajanvastuuhenkilosposti tilaajanvastuuhenkilo-sposti
      :sampourakkanimi (xml/escape-xml-varten urakka-nimi)
      :sampourakkaid urakka-sampoid
      :urakanpaattymispvm (xml/formatoi-paivamaara urakka-loppupvm)
      :urakkavaylamuoto (turi-sanoma/urakan-vaylamuoto vaylamuoto)
      :urakkatyyppi (if (= urakka-tyyppi "teiden-hoito")
                      "hoito"
                      urakka-tyyppi)
      :alueurakkanro alueurakkanro
      :tapahtumapvm (xml/formatoi-paivamaara tapahtunut)
      :tapahtumaaika (xml/formatoi-kellonaika tapahtunut)
      :kuvaus (xml/escape-xml-varten kuvaus)})})

(defn- tapahtumapaikka [{sijainti :sijainti
                         tieosoite :tr
                         paikan-kuvaus :paikan-kuvaus}]
  (let [[x y] (some-> sijainti geo/pisteet first)]
    {:tapahtumapaikka
     (merge
       (when y {:eureffinn y})
       (when x {:eureffine x})
       {:paikka (xml/escape-xml-varten paikan-kuvaus)}
       (when (:numero tieosoite) {:tienumero (:numero tieosoite)})
       (when (:alkuosa tieosoite) {:tieaosa (:alkuosa tieosoite)})
       (when (:loppuosa tieosoite) {:tielosa (:loppuosa tieosoite)})
       (when (:alkuetaisyys tieosoite) {:tieaet (:alkuetaisyys tieosoite)})
       (when (:loppuetaisyys tieosoite) {:tielet (:loppuetaisyys tieosoite)}))}))

(defn- vammat->numerot [vammat]
  ;; TODO: Turi tukee tällä hetkellä vain yhtä arvoa tässä.
  ;; Lähetetään (satunnainen) ensimmäinen arvo ja myöhemmin toivottavasti kaikki.
  (let [vamma (take 1 vammat)] ;; pudota take 1 pois, jos halutaan kaikki
    {:vammanlaatu (turi-sanoma/vamma->numero vamma)}))

(defn- vahingoittuneet-ruumiinosat->numerot [vahingoittuneet-ruumiinosat]
  ;; TODO: Turi tukee tällä hetkellä vain yhtä arvoa tässä.
  ;; Lähetetään (satunnainen) ensimmäinen arvo ja myöhemmin toivottavasti kaikki.
  (let [vahingoittunut-ruumiinosa (take 1 vahingoittuneet-ruumiinosat)]
    {:vahingoittunutruumiinosa (turi-sanoma/vahingoittunut-ruumiinosa->numero vahingoittunut-ruumiinosa)}))

(def ammatti->teksti
  {:aluksen_paallikko "Aluksen päällikkö"
   :asentaja "Asentaja"
   :asfalttityontekija "Asfalttityöntekijä"
   :harjoittelija "Harjoittelija"
   :hitsaaja "Hitsaaja"
   :kunnossapitotyontekija "Kunnossapitotyöntekijä"
   :kansimies "Kansimies"
   :kiskoilla_liikkuvan_tyokoneen_kuljettaja "Kiskoilla liikkuvan työkoneen kuljettaja"
   :konemies "Konemies"
   :kuorma-autonkuljettaja "Kuorma-auton kuljettaja"
   :liikenteenohjaaja "Liikenteenohjaaja"
   :mittamies "Mittamies"
   :panostaja "Panostaja"
   :peramies "Perämies"
   :porari "Porari"
   :rakennustyontekija "Rakennustyöntekijä"
   :ratatyontekija "Ratatyöntekijä"
   :ratatyosta_vastaava "Ratatyöstä vastaava"
   :sukeltaja "Sukeltaja"
   :sahkotoiden_ammattihenkilo "Sähkötöiden ammattihenkilö"
   :tilaajan_edustaja "Tilaajan edustaja"
   :turvalaiteasentaja "Turvalaiteasentaja"
   :turvamies "Turvamies"
   :tyokoneen_kuljettaja "Työkoneenkuljettaja"
   :tyonjohtaja "Työnjohtaja"
   :valvoja "Valvoja"
   :veneenkuljettaja "Veneenkuljettaja"
   :vaylanhoitaja "Väylänhoitaja"
   :muu_tyontekija "Muu työntekijä"
   :tyomaan_ulkopuolinen "Työmaan ulkopuolinen"})

(defn- syyt-ja-seuraukset [data]
  {:syytjaseuraukset
   (merge
     {:seuraukset (xml/escape-xml-varten (:seuraukset data))}
     (when (turi-sanoma/ammatti->numero (:tyontekijanammatti data))
       {:ammatti (ammatti->teksti (:tyontekijanammatti data))})
     (when-let [ammatti-muu (:tyontekijanammattimuu data)]
       {:ammattimuutarkenne ammatti-muu})
     (vammat->numerot (:vammat data))
     (vahingoittuneet-ruumiinosat->numerot (:vahingoittuneetruumiinosat data))
     {:sairauspoissaolot (or (:sairauspoissaolopaivat data) 0)
      :sairauspoissaolojatkuu (true? (:sairauspoissaolojatkuu data))
      :sairaalahoitovuorokaudet (or (:sairaalavuorokaudet data) 0)}
     ;; Juuri syyt
     (when (:juurisyy1 data)
       {:juurisyy1 (turpodomain/juurisyy->teksti (:juurisyy1 data))})
     (when (and (:juurisyy1 data) (:juurisyy1-selite data))
       {:juurisyy1selite (xml/escape-xml-varten (:juurisyy1-selite data))})
     (when (:juurisyy2 data)
       {:juurisyy2 (turpodomain/juurisyy->teksti (:juurisyy2 data))})
     (when (and (:juurisyy2 data) (:juurisyy2-selite data))
       {:juurisyy2selite (xml/escape-xml-varten (:juurisyy2-selite data))})
     (when (:juurisyy3 data)
       {:juurisyy3 (turpodomain/juurisyy->teksti (:juurisyy3 data))})
     (when (and (:juurisyy3 data) (:juurisyy3-selite data))
       {:juurisyy3selite (xml/escape-xml-varten (:juurisyy3-selite data))}))})

(defn- tapahtumakasittely [{:keys [tapahtuman-otsikko luotu tila]}]
  {:tapahtumankasittely
   {:otsikko (xml/escape-xml-varten tapahtuman-otsikko)
    :luontipvm (xml/formatoi-paivamaara luotu)
    :tila (turi-sanoma/turvallisuuspoikkeaman-tila tila)}})

(defn- poikkeamatoimenpide [{korjaavat-toimenpiteet :korjaavattoimenpiteet}]
  (let [tulos {:poikkeamatoimenpide (for [{:keys [otsikko kuvaus
                                                  vastuuhenkilokayttajatunnus vastuuhenkiloetunimi
                                                  vastuuhenkilosukunimi vastuuhenkilosposti
                                                  toteuttaja tila]} korjaavat-toimenpiteet]

                                      {:otsikko (xml/escape-xml-varten otsikko)
                                       :kuvaus (xml/escape-xml-varten kuvaus)
                                       :vastuuhenkilokayttajatunnus (xml/escape-xml-varten vastuuhenkilokayttajatunnus)
                                       :vastuuhenkiloetunimi vastuuhenkiloetunimi
                                       :vastuuhenkilosukunimi vastuuhenkilosukunimi
                                       :vastuuhenkilosposti vastuuhenkilosposti
                                       :toteuttaja (xml/escape-xml-varten toteuttaja)
                                       :tila (turi-sanoma/korjaava-toimenpide-tila->teksti tila)})}]
    tulos))

(defn- poikkeamaliite [{:keys [liitteet]}]
  {:poikkeamaliite (for [{:keys [nimi data]} liitteet]
                     {:tiedostonimi (when nimi (xml/escape-xml-varten nimi))
                      :tiedosto (when data (String. (liitteet/enkoodaa-base64 data)))})})

(defn- turvallisuuspoikkeamaviesti-json [turvallisuuspoikkeama]
  (merge
    {:imp:poikkeama {:xmlns:imp "http://restimport.xml.turi.oikeatoliot.fi"}}
    (lahde turvallisuuspoikkeama)
    (tapahtumatiedot turvallisuuspoikkeama)
    (tapahtumapaikka turvallisuuspoikkeama)
    (syyt-ja-seuraukset turvallisuuspoikkeama)
    (tapahtumakasittely turvallisuuspoikkeama)
    (poikkeamatoimenpide turvallisuuspoikkeama)
    (poikkeamaliite turvallisuuspoikkeama)))
(defn hae-turvallisuuspoikkeamat [db {:keys [alkuaika loppuaika] :as parametrit} kayttaja]
  (log/debug "hae-turvallisuuspoikkeamat :: parametrit" (pr-str parametrit))
  (tarkista-turpohaun-parametrit parametrit)
  (let [
        turpot (turvallisuuspoikkeamat/hae-turvallisuuspoikkeamat-lahetettavaksi-analytiikalle db {:alku (pvm/rajapinta-str-aika->sql-timestamp alkuaika)
                                                                                              :loppu (pvm/rajapinta-str-aika->sql-timestamp loppuaika)})
        ;; Konvertoidaan turpot sellaiseen muotoon, että ne voidaan kääntää kutsukäsittelyssä jsoniksi. Tässä vaiheessa ne ovat mäppeineä nimestään huolimatta
        json-turbot (map
                          #(turvallisuuspoikkeamaviesti-json %)
                          (konv/sarakkeet-vektoriin
                               (into [] turvallisuuspoikkeamat/turvallisuuspoikkeama-xf turpot)
                               {:korjaavatoimenpide :korjaavattoimenpiteet
                                :liite :liitteet
                                :kommentti :kommentit}))]
    {:turvallisuuspoikkeamat json-turbot}))

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

(defrecord Turvallisuuspoikkeama [kehitysmoodi?]
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
    (julkaise-reitti
      http :hae-turvallisuuspoikkeamat
      (GET "/api/turvallisuuspoikkeamat/:alkuaika/:loppuaika" request
        (kasittele-get-kutsu db integraatioloki :analytiikka-hae-turvallisuuspoikkeamat request
          json-skeemat/+turvallisuuspoikkeamien-vastaus+
          (fn [parametrit kayttaja db]
            (hae-turvallisuuspoikkeamat db parametrit kayttaja))
          ;; Tarkista sallitaanko admin käyttälle API:en käyttöoikeus
          (not kehitysmoodi?))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-turvallisuuspoikkeama)
    (poista-palvelut http :hae-turvallisuuspoikkeamat)
    this))
