(ns harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]))

(def +xsd-polku+ "xsd/turi/")

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
  ;; todo
  [:turi:liitteet
   [:turi:liite
    [:turi:tiedostonimi
     "tilanne.png"]
    [:turi:tyyppi
     "image/png"]
    [:turi:kuvaus
     "Kuva tilanteesta"]
    [:turi:sisalto
     "...sisältö BASE 64 encoodattuna..."]]
   [:turi:liite
    [:turi:tiedostonimi
     "auto.png"]
    [:turi:tyyppi
     "image/png"]
    [:turi:kuvaus
     "Kuva autosta"]
    [:turi:sisalto
     "...sisältö BASE 64 encoodattuna..."]]])

(defn rakenna-sijainti [data]
  (when-let [koordinaatit (:coordinates (harja.geo/pg->clj (:sijainti data)))]
    (clojure.pprint/pprint koordinaatit)
    [:turi:sijainti
     [:turi:koordinaatit
      ;; todo: Tällä hetkellä ei ole vielä tarjolla tienumeroa, kun se on, pitää välittää elementissä [:turi:tienumero "1"]
      [:turi:x (first koordinaatit)]
      [:turi:y (second koordinaatit)]]]))

(defn rakenna-henkilovahinko [data]
  ;; todo
  [:turi:henkilovahinko
   [:turi:tyontekijan-ammatti
    [:turi:koodi
     "Muu työntekijä"]
    [:turi:selite
     "Aura-auton kuljettaja"]]
   [:turi:aiheutuneet-vammat
    "Ranne murtui ja päähän tuli haava"]
   [:turi:vamman-laatu
    [:turi:vamma
     "Luunmurtumat"]
    [:turi:vamma
     "Haavat ja pinnalliset vammat"]]
   [:turi:vahingoittuneet-ruumiinosat
    [:turi:ruumiinosa
     "Pään alue (pl. silmät)"]
    [:turi:ruumiinosa
     "Ranne"]]
   [:turi:sairauspoissaolopaivat
    "3"]
   [:turi:sairaalahoitovuorokaudet
    "1"]
   [:turi:jatkuuko-sairaspoissaolo
    "false"]])

(defn muodosta-viesti [data]
  [:turi:turvallisuuspoikkeama
   {:xmlns:turi "http://www.liikennevirasto.fi/xsd/turi"}
   [:turi:vaylamuoto (:vaylamuoto data)]
   [:turi:tapahtunut (when (:tapahtunut data) (xml/formatoi-aikaleima (:tapahtunut data)))]
   [:turi:paattynyt (when (:paattynyt data) (xml/formatoi-aikaleima (:paattynyt data)))]
   [:turi:kasitelty (when (:kasitelty data) (xml/formatoi-aikaleima (:kasitelty data)))]
   [:turi:toteuttaja (:toteuttaja testidata)]
   [:turi:tilaaja (:tilaaja testidata)]
   [:turi:turvallisuuskoordinaattori
    [:turi:etunimi (:turvallisuuskoordinaattorietunimi testidata)]
    [:turi:sukunimi (:turvallisuuskoordinaattorisukunimi testidata)]]
   [:turi:laatija
    [:turi:etunimi (:laatijaetunimi testidata)]
    [:turi:sukunimi (:laatijasukunimi testidata)]]
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
    (println xml)
    (if (xml/validoi +xsd-polku+ "turvallisuuspoikkeama.xsd" xml)
      xml
      (do
        (log/error "Turvallisuuspoikkeamaa ei voida lähettää. XML ei ole validia.")
        nil))))
