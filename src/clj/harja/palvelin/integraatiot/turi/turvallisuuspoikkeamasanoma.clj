(ns harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]))

(def +xsd-polku+ "xsd/turi/")

(defn rakenna-luokittelut [tyypit]
  ;; todo
  [:turi:luokittelut
   [:turi:luokittelu
    "tyotapaturma"]])

(defn rakenna-vahinkoluokittelut [vahinkoluokittelut]
  ;; todo
  [:turi:vahinkoluokittelut
   [:turi:luokittelu
    "henkilostovahinko"]
   [:turi:luokittelu
    "omaisuusvahinko"]])

(defn rakenna-korjaavat-toimenpiteet [data]
  ;; todo
  [:turi:korjaavat-toimenpiteet
   [:turi:korjaava-toimenpide
    [:turi:kuvaus
     "Kaadetaan risteystä pimentävä pensaikko"]
    [:turi:suoritettu
     "2016-01-30T12:00:00Z"]
    [:turi:vastaavahenkilo
     [:turi:etunimi
      "Henri"]
     [:turi:sukunimi
      "Hakkuri"]]]])

(defn rakenna-kommentit [data]
  ;; todo
  [:turi:kommentit
   [:turi:kommentti
    "Aika pahalta näytti."]
   [:turi:kommentti
    "Jatkossa ei tule tapahtumaan."]])

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
  ;; todo
  [:turi:sijainti
   [:turi:koordinaatit
    [:turi:tienumero
     "1"]
    [:turi:x
     "430198"]
    [:turi:y
     "7212292"]]])

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
   ;; todo: jari lisää
   [:turi:vaylamuoto "tie"]
   [:turi:tapahtunut (xml/formatoi-aikaleima (:tapahtunut data))]
   [:turi:paattynyt (xml/formatoi-aikaleima (:paattynyt data))]
   [:turi:kasitelty (xml/formatoi-aikaleima (:kasitelty data))]
   ;; todo: puuttuu?
   [:turi:toteuttaja "Yritys OY"]
   ;; todo: puuttuu?
   [:turi:tilaaja "POP ELY"]
   ;; todo: puuttuu?
   [:turi:turvallisuuskoordinaattori
    [:turi:etunimi "Tiina"]
    [:turi:sukunimi "Turvallinen"]]
   [:turi:laatija
    [:turi:etunimi "Lasse"]
    [:turi:sukunimi "Laatija"]]
   ;; todo: puuttuu?
   [:turi:ilmoittaja
    [:turi:etunimi "Irma"]
    [:turi:sukunimi "Ilmiantaja"]]
   [:turi:kuvaus (:kuvaus data)]
   [:turi:tyotehtava (:tyotehtava data)]
   (rakenna-luokittelut (:tyyppi data))
   (rakenna-vahinkoluokittelut (:vahinkoluokittelu data))
   [:turi:vakavuusaste (:vakavuusaste data)]
   ;; todo: jari lisää
   [:turi:aiheutuneet-seuraukset
    "Tehdään asialle jotain"]
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
