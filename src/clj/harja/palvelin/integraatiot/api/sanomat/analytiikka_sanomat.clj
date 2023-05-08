(ns harja.palvelin.integraatiot.api.sanomat.analytiikka-sanomat
  (:require [harja.geo :as geo]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.turi.sanomat.turvallisuuspoikkeama :as turi-sanoma]
            [harja.domain.turvallisuuspoikkeama :as turpodomain]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liitteet]))

;; Helpperit
(def urakan-vaylamuoto
  {:tie "Tie"
   :rautatie "Rautatie"
   :vesi "Vesiväylä"})

(def poikkeamatyyppi->teksti
  {:tyotapaturma "Työtapaturma"
   :vaaratilanne "Vaaratilanne"
   :turvallisuushavainto "Turvallisuushavainto"
   :muu "Muu turvallisuuspoikkeama"})

(defn poikkeamatyypit->tekstit [tyypit]
  (for [tyyppi tyypit]
    (poikkeamatyyppi->teksti tyyppi)))

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

(def vamma->teksti
  {:haavat_ja_pinnalliset_vammat "Haavat ja pinnalliset vammat"
   :luunmurtumat "Luunmurtumat"
   :sijoiltaan_menot_nyrjahdykset_ja_venahdykset "Sijoiltaan menot, nyrjahdykset ja venahdykset"
   :amputoitumiset_ja_irti_repeamiset "Amputoitumiset ja irti repeamiset"
   :tarahdykset_ja_sisaiset_vammat_ruhjevammat "Tärähdykset ja sisaiset vammat/ruhjevammat"
   :palovammat_syopymat_ja_paleltumat "Palovammat, syöpymät ja paleltumat"
   :myrkytykset_ja_tulehdukset "Myrkytykset ja tulehdukset"
   :hukkuminen_ja_tukehtuminen "Hukkuminen ja tukehtuminen"
   :aanen_ja_varahtelyn_vaikutukset "Äänen ja värähtelyn vaikutukset"
   :aarilampotilojen_valon_ja_sateilyn_vaikutukset "Äärilampotilojen valon ja säteilyn vaikutukset"
   :sokki "Sokki"
   :useita_samantasoisia_vammoja "Useita samantasoisia vammoja"
   :muut "Muut"
   :ei_tietoa "Ei tietoa"})

(defn- vammat->tekstit
  "Tietokantaan ei koskaan tallenneta enempää kuin yksi vamma. Mutta, jos se joskus korjaantuu, niin lähetetään kaikki saadut vammat."
  [vammat]
  {:vammanlaatu
   (for [vamma vammat]
     (vamma->teksti vamma))})

(def vahingoittunut-ruumiinosa->teksti
  {:paan_alue "Pään alue"
   :silmat "Silmät"
   :niska_ja_kaula "Niska ja kaula"
   :selka "Selkä"
   :vartalo "Vartalo"
   :sormi_kammen "Sormi/kammen"
   :ranne "Ranne"
   :muu_kasi "Muu käsi"
   :nilkka "Nilkka"
   :jalkatera_ja_varvas "Jalkaterä ja varvas"
   :muu_jalka "Muu jalka"
   :koko_keho "Koko keho"
   :ei_tietoa "Ei tietoa"})

(defn- vahingoittuneet-ruumiinosat->tekstit [vahingoittuneet-ruumiinosat]
  ;; Harjaan lähetetään vain yksi vamma, joten lisätään vain yksi vamma lähtevään aineistoon.
  (let [vahingoittunut-ruumiinosa (first vahingoittuneet-ruumiinosat)]
    {:vahingoittunutruumiinosa (vahingoittunut-ruumiinosa->teksti vahingoittunut-ruumiinosa)}))

(def korjaava-toimenpide-tila->teksti
  {:avoin "Avoin"
   :siirretty "Siirretty"
   :suljettu "Suljettu"
   :toteutettu "Toteutettu"})

(defn- lahde [data]
  {:lahde
   {:lahdejarjestelma "Harja"
    :lahdeid (str (:id data))}})

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
      :urakkavaylamuoto (urakan-vaylamuoto vaylamuoto)
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

(defn- syyt-ja-seuraukset [data]
  {:syytjaseuraukset
   (merge
     {:seuraukset (xml/escape-xml-varten (:seuraukset data))}
     (when (turi-sanoma/ammatti->numero (:tyontekijanammatti data))
       {:ammatti (ammatti->teksti (:tyontekijanammatti data))})
     (when-let [ammatti-muu (:tyontekijanammattimuu data)]
       {:ammattimuutarkenne ammatti-muu})
     (vammat->tekstit (:vammat data))
     ;; Puhutaan yksikököstä, koska tietokantaan ei tallenneta koskaan kuin yksi, vaikka niitä voisi olla useitakin
     (vahingoittuneet-ruumiinosat->tekstit (:vahingoittuneetruumiinosat data))
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
                                       :tila (korjaava-toimenpide-tila->teksti tila)})}]
    tulos))

(defn- poikkeamaliite [{:keys [liitteet]}]
  {:poikkeamaliite (for [{:keys [nimi pikkukuva]} liitteet]
                     {:tiedostonimi (when nimi (xml/escape-xml-varten nimi))
                      :tiedosto (when pikkukuva (String. (liitteet/enkoodaa-base64 pikkukuva)))})})

(defn turvallisuuspoikkeamaviesti-json [turvallisuuspoikkeama]
  (merge
    {:imp:poikkeama {:xmlns:imp "http://restimport.xml.turi.oikeatoliot.fi"}}
    (lahde turvallisuuspoikkeama)
    (tapahtumatiedot turvallisuuspoikkeama)
    (tapahtumapaikka turvallisuuspoikkeama)
    (syyt-ja-seuraukset turvallisuuspoikkeama)
    (tapahtumakasittely turvallisuuspoikkeama)
    (poikkeamatoimenpide turvallisuuspoikkeama)
    (poikkeamaliite turvallisuuspoikkeama)))
