(ns harja.palvelin.integraatiot.turi.sanomat.turvallisuuspoikkeama
  (:require [taoensso.timbre :as log]
            [clojure.walk :as walk]
            [harja.tyokalut.xml :as xml]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liitteet]
            [harja.domain.turvallisuuspoikkeama :as turpodomain])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/turi/")

(def urakan-vaylamuoto
  {:tie "Tie"
   :rautatie "Rautatie"
   :vesi "Vesiväylä"})

(def poikkeamatyyppi->numero
  {:tyotapaturma 8
   :vaaratilanne 32
   :turvallisuushavainto 64
   :muu 16})

(defn poikkeamatyypit->numerot [tyypit]
  (for [tyyppi tyypit]
    [:tyyppi (poikkeamatyyppi->numero tyyppi)]))

(def ammatti->numero
  {:aluksen_paallikko 1
   :asentaja 2
   :asfalttityontekija 3
   :harjoittelija 4
   :hitsaaja 5
   :kunnossapitotyontekija 6
   :kansimies 7
   :kiskoilla_liikkuvan_tyokoneen_kuljettaja 8
   :konemies 9
   :kuorma-autonkuljettaja 10
   :liikenteenohjaaja 11
   :mittamies 12
   :panostaja 13
   :peramies 14
   :porari 15
   :rakennustyontekija 16
   :ratatyontekija 17
   :ratatyosta_vastaava 18
   :sukeltaja 19
   :sahkotoiden_ammattihenkilo 20
   :tilaajan_edustaja 21
   :turvalaiteasentaja 22
   :turvamies 23
   :tyokoneen_kuljettaja 24
   :tyonjohtaja 25
   :valvoja 26
   :veneenkuljettaja 27
   :vaylanhoitaja 28
   :muu_tyontekija 29
   :tyomaan_ulkopuolinen 30})

(def vamma->numero
  {:haavat_ja_pinnalliset_vammat 1
   :luunmurtumat 2
   :sijoiltaan_menot_nyrjahdykset_ja_venahdykset 3
   :amputoitumiset_ja_irti_repeamiset 4
   :tarahdykset_ja_sisaiset_vammat_ruhjevammat 5
   :palovammat_syopymat_ja_paleltumat 6
   :myrkytykset_ja_tulehdukset 7
   :hukkuminen_ja_tukehtuminen 8
   :aanen_ja_varahtelyn_vaikutukset 9
   :aarilampotilojen_valon_ja_sateilyn_vaikutukset 10
   :sokki 11
   :useita_samantasoisia_vammoja 12
   :muut 13
   :ei_tietoa 14})

(defn- vammat->numerot [vammat]
  ;; TODO: Turi tukee tällä hetkellä vain yhtä arvoa tässä.
  ;; Lähetetään (satunnainen) ensimmäinen arvo ja myöhemmin toivottavasti kaikki.
  (for [vamma (take 1 vammat)] ;; pudota take 1 pois, jos halutaan kaikki
    [:vammanlaatu (vamma->numero vamma)]))

(def vahingoittunut-ruumiinosa->numero
  {:paan_alue 1
   :silmat 2
   :niska_ja_kaula 3
   :selka 4
   :vartalo 5
   :sormi_kammen 6
   :ranne 7
   :muu_kasi 8
   :nilkka 9
   :jalkatera_ja_varvas 10
   :muu_jalka 11
   :koko_keho 12
   :ei_tietoa 13})

(defn- vahingoittuneet-ruumiinosat->numerot [vahingoittuneet-ruumiinosat]
  ;; TODO: Turi tukee tällä hetkellä vain yhtä arvoa tässä.
  ;; Lähetetään (satunnainen) ensimmäinen arvo ja myöhemmin toivottavasti kaikki.
  (for [vahingoittunut-ruumiinosa (take 1 vahingoittuneet-ruumiinosat)]
    [:vahingoittunutruumiinosa (vahingoittunut-ruumiinosa->numero vahingoittunut-ruumiinosa)]))

(def korjaava-toimenpide-tila->numero
  {:avoin 0
   :siirretty 1
   :suljettu 2
   :toteutettu 2})

(def turvallisuuspoikkeaman-tila
  {:avoin "Avoin"
   :kasitelty "Käsitelty"
   :taydennetty "Täydennetty"
   :suljettu "Suljettu"})

(defn- lahde [data]
  [:lahde
   [:lahdejarjestelma "Harja"]
   [:lahdeid (:id data)]])

(defn- tapahtumatiedot [{:keys [turi-id hanke-nimi hanke-sampoid tilaajanvastuuhenkilo-kayttajatunnus
                               tilaajanvastuuhenkilo-etunimi tilaajanvastuuhenkilo-sukunimi
                               tilaajanvastuuhenkilo-sposti urakka-nimi urakka-sampoid
                               urakka-loppupvm vaylamuoto urakka-tyyppi urakka-ely alueurakkanro
                               tyyppi tapahtunut kuvaus]}]
  [:tapahtumantiedot
   (when-let [turi-id turi-id]
     [:id turi-id])
   [:sampohankenimi hanke-nimi]
   [:sampohankeid hanke-sampoid]
   [:tilaajanvastuuhenkilokayttajatunnus tilaajanvastuuhenkilo-kayttajatunnus]
   [:tilaajanvastuuhenkiloetunimi tilaajanvastuuhenkilo-etunimi]
   [:tilaajanvastuuhenkilosukunimi tilaajanvastuuhenkilo-sukunimi]
   [:tilaajanvastuuhenkilosposti tilaajanvastuuhenkilo-sposti]
   [:sampourakkanimi urakka-nimi]
   [:sampourakkaid urakka-sampoid]
   [:urakanpaattymispvm (xml/formatoi-paivamaara urakka-loppupvm)]
   [:urakkavaylamuoto (urakan-vaylamuoto vaylamuoto)]
   [:urakkatyyppi (if (= urakka-tyyppi "teiden-hoito")
                    "hoito"
                    urakka-tyyppi)]
   (when urakka-ely
     [:elyalue (str urakka-ely " ELY")])
   [:alueurakkanro alueurakkanro]
   (poikkeamatyypit->numerot tyyppi)
   [:tapahtumapvm (xml/formatoi-paivamaara tapahtunut)]
   [:tapahtumaaika (xml/formatoi-kellonaika tapahtunut)]
   [:kuvaus kuvaus]])

(defn- tapahtumapaikka [{sijainti :sijainti
                        tieosoite :tr
                        paikan-kuvaus :paikan-kuvaus}]
  (let [[x y] (some-> sijainti geo/pisteet first)]
    [:tapahtumapaikka
     [:paikka paikan-kuvaus]
     (when y [:eureffinn y])
     (when x [:eureffine x])
     (when (:numero tieosoite) [:tienumero (:numero tieosoite)])
     (when (:alkuosa tieosoite) [:tieaosa (:alkuosa tieosoite)])
     (when (:loppuosa tieosoite) [:tielosa (:loppuosa tieosoite)])
     (when (:alkuetaisyys tieosoite) [:tieaet (:alkuetaisyys tieosoite)])
     (when (:loppuetaisyys tieosoite) [:tielet (:loppuetaisyys tieosoite)])]))

(defn- juurisyyt [{:keys [juurisyy1 juurisyy1-selite
                          juurisyy2 juurisyy2-selite
                          juurisyy3 juurisyy3-selite]}]
  (remove
   nil?
   (list (when juurisyy1
           [:juurisyy1 (turpodomain/juurisyyn-koodi juurisyy1)])
         (when (and juurisyy1 juurisyy1-selite)
           [:juurisyy1selite juurisyy1-selite])
         (when juurisyy2
           [:juurisyy2 (turpodomain/juurisyyn-koodi juurisyy2)])
         (when (and juurisyy2 juurisyy2-selite)
           [:juurisyy2selite juurisyy2-selite])
         (when juurisyy3
           [:juurisyy3 (turpodomain/juurisyyn-koodi juurisyy3)])
         (when (and juurisyy3 juurisyy3-selite)
           [:juurisyy3selite juurisyy3-selite]))))

(defn- syyt-ja-seuraukset [data]
  [:syytjaseuraukset
   [:seuraukset (:seuraukset data)]
   (when (ammatti->numero (:tyontekijanammatti data)) [:ammatti (ammatti->numero (:tyontekijanammatti data))])
   (when-let [ammatti-muu (:tyontekijanammattimuu data)]
     [:ammattimuutarkenne ammatti-muu])
   (vammat->numerot (:vammat data))
   (vahingoittuneet-ruumiinosat->numerot (:vahingoittuneetruumiinosat data))
   [:sairauspoissaolot (or (:sairauspoissaolopaivat data) 0)]
   [:sairauspoissaolojatkuu (true? (:sairauspoissaolojatkuu data))]
   [:sairaalahoitovuorokaudet (or (:sairaalavuorokaudet data) 0)]
   (juurisyyt data)])

(defn- tapahtumakasittely [{:keys [tapahtuman-otsikko luotu tila]}]
  [:tapahtumankasittely
   [:otsikko tapahtuman-otsikko]
   [:luontipvm (xml/formatoi-paivamaara luotu)]
   [:tila (turvallisuuspoikkeaman-tila tila)]])

(defn- poikkeamatoimenpide [{korjaavat-toimenpiteet :korjaavattoimenpiteet}]
  (for [{:keys [otsikko kuvaus
                vastuuhenkilokayttajatunnus vastuuhenkiloetunimi
                vastuuhenkilosukunimi vastuuhenkilosposti
                toteuttaja tila]} korjaavat-toimenpiteet]
    [:poikkeamatoimenpide
     [:otsikko otsikko]
     [:kuvaus kuvaus]
     [:vastuuhenkilokayttajatunnus vastuuhenkilokayttajatunnus]
     [:vastuuhenkiloetunimi vastuuhenkiloetunimi]
     [:vastuuhenkilosukunimi vastuuhenkilosukunimi]
     [:vastuuhenkilosposti vastuuhenkilosposti]
     [:toteuttaja toteuttaja]
     [:tila (korjaava-toimenpide-tila->numero tila)]]))

(defn- poikkeamaliite [{:keys [liitteet]}]
  (for [{:keys [nimi data]} liitteet]
    [:poikkeamaliite
     [:tiedostonimi nimi]
     [:tiedosto (String. (liitteet/enkoodaa-base64 data))]]))

(defn- turvallisuuspoikkeamaviesti [turvallisuuspoikkeama]
  [:imp:poikkeama {:xmlns:imp "http://restimport.xml.turi.oikeatoliot.fi"}
   (lahde turvallisuuspoikkeama)
   (tapahtumatiedot turvallisuuspoikkeama)
   (tapahtumapaikka turvallisuuspoikkeama)
   (syyt-ja-seuraukset turvallisuuspoikkeama)
   (tapahtumakasittely turvallisuuspoikkeama)
   (poikkeamatoimenpide turvallisuuspoikkeama)
   (poikkeamaliite turvallisuuspoikkeama)])

(defn muodosta
  "Muodostaa annetusta turvallisuuspoikkeamasta XML-viestin ja validoi, että se on skeeman mukainen.
  Palauttaa XML-viestin merkkijonona."
  [turvallisuuspoikkeama]
  (let [sisalto (turvallisuuspoikkeamaviesti turvallisuuspoikkeama)
        xml (xml/tee-xml-sanoma sisalto)]
    (if-let [virheet (xml/validoi-xml +xsd-polku+ "poikkeama-rest.xsd" xml)]
      (let [virheviesti (format "Turvallisuuspoikkeaman TURI-lähetyksen XML ei ole validia.\n
                                 Validointivirheet: %s\n
                                 Muodostettu sanoma:\n
                                 %s"
                                virheet
                                (xml/tee-xml-sanoma
                                  ;; Poistetaan tiedoston logitus sen takia, kun sen logitus voi jumittaa koko prosessin
                                  (walk/prewalk (fn [osa]
                                                  (if (and (vector? osa)
                                                           (= :tiedosto (first osa)))
                                                    [:tiedosto "<<EI LOKITETA TIEDOSTOA>>"]
                                                    osa))
                                                sisalto)))]
        (log/error virheviesti)
        (throw+ {:type :invalidi-turvallisuuspoikkeama-xml
                 :error virheviesti}))
      xml)))
