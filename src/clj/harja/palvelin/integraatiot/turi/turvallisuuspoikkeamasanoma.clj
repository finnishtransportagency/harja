(ns harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liitteet]))

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
                     [:turi:suoritettu (when (:suoritettu %) (xml/formatoi-aikaleima (:suoritettu %)))]
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
  (when-let [koordinaatit (:coordinates (geo/pg->clj (:sijainti data)))]
    [:turi:sijainti
     [:turi:koordinaatit
      ;; todo: Tällä hetkellä ei ole vielä tarjolla tienumeroa, kun se on, pitää välittää elementissä [:turi:tienumero "1"]
      [:turi:x (first koordinaatit)]
      [:turi:y (second koordinaatit)]]]))

(defn rakenna-henkilovahinko [data]
  [:turi:henkilovahinko
   [:turi:tyontekijan-ammatti
    [:turi:koodi (:tyontekijanammatti data)]
    [:turi:selite (:tyontekijanammattimuu data)]]
   [:turi:aiheutuneet-vammat "Ranne murtui ja päähän tuli haava"]
   (rakenna-lista :turi:vamman-laatu :turi:vamma (:vammat data))
   (rakenna-lista :turi:vahingoittuneet-ruumiinosat :turi:ruumiinosa (:vahingoittuneetruumiinosat data))
   [:turi:sairauspoissaolopaivat (:sairauspoissaolopaivat data)]
   [:turi:sairaalahoitovuorokaudet (:sairaalavuorokaudet data)]
   [:turi:jatkuuko-sairaspoissaolo (true? (:sairauspoissaolojatkuu data))]])

(defn muodosta-viesti [data]
  [:turi:turvallisuuspoikkeama
   {:xmlns:turi "http://www.liikennevirasto.fi/xsd/turi"}
   [:turi:tunniste (:id data)]
   [:turi:vaylamuoto (:vaylamuoto data)]
   [:turi:tapahtunut (when (:tapahtunut data) (xml/formatoi-aikaleima (:tapahtunut data)))]
   [:turi:paattynyt (when (:paattynyt data) (xml/formatoi-aikaleima (:paattynyt data)))]
   [:turi:kasitelty (when (:kasitelty data) (xml/formatoi-aikaleima (:kasitelty data)))]
   [:turi:toteuttaja (:toteuttaja data)]
   [:turi:tilaaja (:tilaaja data)]
   [:turi:turvallisuuskoordinaattori
    [:turi:etunimi (:turvallisuuskoordinaattorietunimi data)]
    [:turi:sukunimi (:turvallisuuskoordinaattorisukunimi data)]]
   [:turi:laatija
    [:turi:etunimi (:laatijaetunimi data)]
    [:turi:sukunimi (:laatijasukunimi data)]]
   [:turi:ilmoittaja
    [:turi:etunimi (:ilmoittaja_etunimi data)]
    [:turi:sukunimi (:ilmoittaja_sukunimi data)]]
   [:turi:kuvaus (:kuvaus data)]
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

(defn muodosta [data]
  (let [sisalto (muodosta-viesti data)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "turvallisuuspoikkeama.xsd" xml)
      xml
      (do
        (log/error "Turvallisuuspoikkeamaa ei voida lähettää. XML ei ole validia.")
        nil))))
