(ns harja.palvelin.integraatiot.tierekisteri.tietueet
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueiden-hakukutsu
             :as tietueiden-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.urakan-tietueiden-hakukutsu
             :as urakan-tietuiden-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-tietuehakuvastaus [xml url tietolajitunniste tierekisteriosoitevali voimassaolopvm]
  (let [virheviesti (str "Tietueiden haku epäonnistui (URL: " url ")
                         tr-osoitteella: " (pr-str tierekisteriosoitevali)
                         " & tietolajitunnisteella: " tietolajitunniste
                         " & voimassaolopäivämäärällä: " voimassaolopvm ".")]
    (kasittele-vastaus
      xml
      virheviesti
      :tietueiden-haku-epaonnistui
      virheviesti)))

(defn hae-tietueet [db integraatioloki url tierekisteriosoitevali tietolaji voimassaolopvm tilannepvm]
  (log/debug "Haetaan tietue tierekisteriosoitteella: " (pr-str tierekisteriosoitevali)
             ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [url (str url "/haetietueet")]
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "tierekisteri" "hae-tietueet"
      (fn [konteksti]
        (let [kutsudata (tietueiden-kutsusanoma/muodosta-kutsu
                          tierekisteriosoitevali
                          tietolaji
                          voimassaolopvm
                          tilannepvm)
              otsikot {"Content-Type" "text/xml; charset=utf-8"}
              http-asetukset {:metodi :POST :url url :otsikot otsikot}
              {xml :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
          (kasittele-tietuehakuvastaus xml url tietolaji tierekisteriosoitevali voimassaolopvm))))))

(defn kasittele-urakan-tietuehakuvastaus [xml url alueurakkanumero tietolajitunniste tilannepvm]
  (let [virheviesti (str "Urakan tietueiden haku epäonnistui urakalle " alueurakkanumero " (URL: " url ")
                      tietolajitunnisteella: " tietolajitunniste
                      " & tilannepäivämäärällä: " tilannepvm ".")]
    (kasittele-vastaus
      xml
      virheviesti
      :urakan-tietueiden-haku-epaonnistui
      virheviesti)))

(defn hae-urakan-tietueet [db integraatioloki url alueurakkanumero tietolajitunniste tilannepvm]
  (log/debug "Haetaan tietue urakalle: " (pr-str alueurakkanumero)
             ", tietolajitunnisteella " tietolajitunniste)
  (let [url (str url "/haeurakantietueet")]
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "tierekisteri" "hae-urakan-tietueet"
      (fn [konteksti]
        (let [kutsudata (urakan-tietuiden-kutsusanoma/muodosta-kutsu
                          alueurakkanumero
                          tietolajitunniste
                          tilannepvm)
              otsikot {"Content-Type" "text/xml; charset=utf-8"}
              http-asetukset {:metodi :POST :url url :otsikot otsikot}
              {xml :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
          (kasittele-urakan-tietuehakuvastaus xml url alueurakkanumero tietolajitunniste tilannepvm))))))
