(ns harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liitteet])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/turi/")

(defn rakenna-lista [lista-avain elementti-avain data]
  (when data
    (let [lista (vec (.getArray data))]
      (when (not-empty lista)
        (apply conj []
               lista-avain
               (mapv #(vector elementti-avain %) lista))))))

(defn rakenna-luokittelulista [lista-elementti elementti tyypit]
  (let [tyypit (vec (.getArray tyypit))]
    (apply conj [] lista-elementti (mapv #(vector elementti %) tyypit))))

(defn rakenna-korjaavat-toimenpiteet [data]
  (let [korjaavat-toimenpiteet (:korjaavat-toimenpiteet data)]
    (when (not-empty korjaavat-toimenpiteet)
      (apply conj []
             :turi:korjaavat-toimenpiteet
             (mapv #(vector
                     :turi:korjaava-toimenpide
                     [:turi:kuvaus (:kuvaus %)]
                     [:turi:suoritettu (when (:suoritettu %) (xml/formatoi-paivamaara (:suoritettu %)))]
                     [:turi:vastaavahenkilo
                      [:turi:etunimi (:vastaavahenkilo %)]
                      [:turi:sukunimi (:vastaavahenkilo %)]]) korjaavat-toimenpiteet)))))

(defn rakenna-kommentit [data]
  (let [kommentit (:kommentit data)]
    (when (not-empty kommentit)
      (apply conj []
             :turi:kommentit
             (mapv #(vector :turi:kommentti (:kommentti %)) kommentit)))))

(defn rakenna-liitteet [data]
  (let [liitteet (:liitteet data)]
    (when (not-empty liitteet)
      (apply conj []
             :turi:liitteet
             (mapv #(vector
                     :turi:liite
                     [:turi:tiedostonimi (:nimi %)]
                     [:turi:tyyppi (:tyyppi %)]
                     [:turi:kuvaus (:kuvaus %)]
                     [:turi:sisalto (String. (liitteet/enkoodaa-base64 (:data (:sisalto %))))])
                   liitteet)))))

(defn rakenna-sijainti [data]
  [:turi:sijainti
   (if-let [koordinaatit (:coordinates (geo/pg->clj (:sijainti data)))]
     [:turi:koordinaatit
      (when (:tr_numero data) [:turi:tienumero (:tr_numero data)])
      [:turi:x (first koordinaatit)]
      [:turi:y (second koordinaatit)]]
     [:turi:tierekisteriosoite
      [:turi:tienumero (:tr_numero data)]
      [:turi:aosa (:tr_alkuosa data)]
      [:turi:aet (:tr_alkuetaisyys data)]
      [:turi:losa (:tr_loppuosa data)]
      [:turi:let (:tr_loppuetaisyys data)]])])

(defn rakenna-henkilovahinko [data]
  [:turi:henkilovahinko

   (when (:tyontekijanammatti data)
     [:turi:tyontekijan-ammatti
      [:turi:koodi (:tyontekijanammatti data)]
      [:turi:selite (:tyontekijanammattimuu data)]])

   (rakenna-lista :turi:vamman-laatu :turi:vamma (:vammat data))
   (rakenna-lista :turi:vahingoittuneet-ruumiinosat :turi:ruumiinosa (:vahingoittuneetruumiinosat data))

   (when (:sairauspoissaolopaivat data) [:turi:sairauspoissaolopaivat (:sairauspoissaolopaivat data)])
   (when (:sairaalavuorokaudet data) [:turi:sairaalahoitovuorokaudet (:sairaalavuorokaudet data)])

   [:turi:jatkuuko-sairaspoissaolo (true? (:sairauspoissaolojatkuu data))]])

(defn rakenna-turvallisuuskoordinaattori [data]
  [:turi:turvallisuuskoordinaattori
   [:turi:etunimi (:turvallisuuskoordinaattorietunimi data)]
   [:turi:sukunimi (:turvallisuuskoordinaattorisukunimi data)]])

(defn rakenna-laatija [data]
  [:turi:laatija
   [:turi:etunimi (:laatijaetunimi data)]
   [:turi:sukunimi (:laatijasukunimi data)]])

(defn rakenna-ilmoittaja [data]
  [:turi:ilmoittaja
   [:turi:etunimi (:ilmoittaja_etunimi data)]
   [:turi:sukunimi (:ilmoittaja_sukunimi data)]]
  [:turi:kuvaus (:kuvaus data)])



(defn muodosta-viesti [data]
  [:turi:turvallisuuspoikkeama
   {:xmlns:turi "http://www.liikennevirasto.fi/xsd/turi"}
   [:turi:tunniste (:id data)]
   [:turi:vaylamuoto (:vaylamuoto data)]
   (when (:tapahtunut data) [:turi:tapahtunut (xml/formatoi-aikaleima (:tapahtunut data))])
   (when (:paattynyt data) [:turi:paattynyt (xml/formatoi-aikaleima (:paattynyt data))])
   (when (:kasitelty data) [:turi:kasitelty (xml/formatoi-aikaleima (:kasitelty data))])
   [:turi:toteuttaja (:toteuttaja data)]
   [:turi:tilaaja (:tilaaja data)]
   (rakenna-turvallisuuskoordinaattori data)
   (rakenna-laatija data)
   (rakenna-ilmoittaja data)
   [:turi:tyotehtava (:tyotehtava data)]
   (rakenna-luokittelulista :turi:luokittelut :turi:luokittelu (:tyyppi data))
   (rakenna-luokittelulista :turi:vahinkoluokittelut :turi:luokittelu (:vahinkoluokittelu data))
   [:turi:vakavuusaste (:vakavuusaste data)]
   [:turi:aiheutuneet-seuraukset (:aiheutuneet_seuraukset data)]
   (rakenna-korjaavat-toimenpiteet data)
   (rakenna-kommentit data)
   (rakenna-liitteet data)
   (rakenna-sijainti data)
   (rakenna-henkilovahinko data)])

;; ----- Yllä oleva koodi on wanhaa ------ ;;

(def poikkeama-tyyppi
  {:tyotapaturma 8
   :vaaratilanne 32
   :turvallisuushavainto 64
   :muu 16})

(defn poikkeama-tyypit [tyypit]
  (mapv
    (fn [tyyppi] [:tyyppi (poikkeama-tyyppi tyyppi)])
    tyypit))

(defn rakenna-tapahtumatiedot [data]
  (into [:tapahtumantiedot]
        (concat
          (poikkeama-tyypit (:tyyppi data))
          [[:tapahtumapvm
           "2007-10-26"]
          [:tapahtumaaika
           "20:44:14"]
          [:poikkeamalaji
           "2"]
          [:tapahtuma
           "2"]
          [:yksikko
           "64"]
          [:vastapuolenyksikko
           "32"]
          [:ohitusmatkametreina
           "3"]
          [:raiteellaeste
           "true"]
          [:jannitekatkotyyppi
           "2"]
          [:viestintatyyppi
           "1"]
          [:vaarakulkutietyyppi
           "2"]
          [:vaarakulkutiesyy
           "5"]
          [:kuumakayntityyppi
           "2"]
          [:vakvaunuja
           [:vakaine
            "string"]
           [:vakseuraus
            "2"]
           [:vakvuotolaji
            "2"]]
          [:raideliikenneosapuoli
           "2"]
          [:tieliikenneosapuoli
           "4"]
          [:tasoristeyksenvarustelu
           "4"]
          [:katkeamisvali
           "2"]
          [:ovenpaikka
           "4"]
          [:tahallisuus
           "1"]
          [:luvallisuus
           "1"]
          [:henkiloonnettomuustyyppi
           "4"]
          [:ratavauriotyyppi
           "1"]
          [:ratavauriopaikka
           "1"]
          [:radanjaturvalaitteidenviantarkennus
           "4"]
          [:tapahtumanlisatieto
           "string"]
          [:jarjestyshairionpaikka
           "2"]
          [:ilkivallankohde
           "4"]
          [:tulipalonkohde
           "4"]
          [:varkaudenkohde
           "string"]
          [:vaaratilanteentyyppi
           "1024"]
          [:urakoitsija
           "string"]
          [:ratatyostavastaava
           "string"]
          [:junanumero
           "3"]
          [:vaihtotyonvastuuyksikko
           "32"]
          [:syy
           "1"]
          [:teknisenviantyyppi
           "2"]
          [:virheentyyppi
           "4"]
          [:ulkopuolisensyyntyyppi
           "1"]
          [:muunsyynkuvaus
           "string"]
          [:kuvaus
           "string"]])))

(defn rakenna-tapahtumapaikka [data]
  [:tapahtumapaikka
   [:tapahtumapaikkaradalla
    "2"]
   [:ratapaikka
    "Rsn"]
   [:paikantarkenne
    "128"]
   [:kmsijainti
    "3"]
   [:paikka
    "string"]
   [:tapahtumaalue
    "8"]])

(defn rakenna-syyt-ja-seuraukset [data]
  [:syytjaseuraukset
   [:arviosyista
    "string"]
   [:tapahtumaanliittyy
    "1"]
   [:seuraukset
    "string"]
   [:liikennekatkonkesto
    "3"]
   [:tehtava
    "string"]
   [:ammatti
    "28"]
   [:ammattimuutarkenne
    "anyType"]
   [:vammanlaatu
    "11"]
   [:vahingoittunutruumiinosa
    "10"]
   [:sairauspoissaolot
    "3"]
   [:sairauspoissaolojatkuu
    "true"]
   [:sairaalahoitovuorokaudet
    "3"]
   [:toimenpiteet
    "string"]
   [:eho
    "string"]])

(defn rakenna-tapahtumakasittely [data]
  [:tapahtumankasittely
   [:tuttiid
    "string"]
   [:otsikko
    "string"]
   [:luontipvm
    "2017-08-18"]
   [:ilmoittaja
    "string"]
   [:ilmoittajaorganisaatio
    "string"]
   [:vuorossaolija
    "string"]
   [:ilmoitettupoliisille
    "true"]
   [:tila
    "Käsitelty"]
   [:lisatiedot
    "string"]
   [:palautepyydetty
    "true"]
   [:palautepyydettyosoite
    "string"]
   [:palauteannettu
    "false"]
   [:palautekanava
    "string"]])

(defn rakenna-poikkeamatoimenpide [data]
  [:poikkeamatoimenpide
   [:otsikko
    "string"]
   [:kuvaus
    "string"]
   [:toteuttaja
    "string"]
   [:toteutus
    "string"]
   [:tila
    "1"]
   [:pakollinen
    "false"]
   [:maarapvm
    "2004-01-03"]])

(defn rakenna-poikkeamaliite [data]
  [:poikkeamaliite
   [:tiedostonimi
    "string"]
   [:tiedosto
    "ZGVkaXQ="]])

(defn muodosta-viesti [data]
  [:imp:poikkeama
   {:xmlns:imp "http://importexport.xml.turi.oikeatoliot.fi"}
   (rakenna-tapahtumatiedot data)
   (rakenna-tapahtumapaikka data)
   (rakenna-syyt-ja-seuraukset data)
   (rakenna-tapahtumakasittely data)
   (rakenna-poikkeamatoimenpide data)
   (rakenna-poikkeamaliite data)])

(defn muodosta [data]
  (let [sisalto (muodosta-viesti data)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "poikkeama-rest.xsd" xml)
      xml
      (let [virheviesti "Turvallisuuspoikkeamaa ei voida lähettää. XML ei ole validia."]
        (log/error virheviesti)
        (throw+ {:type :invalidi-turvallisuuspoikkeama-xml
                 :error virheviesti})))))
