(ns harja.palvelin.integraatiot.tierekisteri.tietueet
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueiden-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-tietuehakuvastaus [xml url tietolaji tierekisteriosoitevali voimassaolopvm]
  (kasittele-vastaus
    xml
    (str "Tietueiden haku epäonnistui (URL: " url ") tr-osoitteella: " (pr-str tierekisteriosoitevali)
         " & tietolajitunnisteella: " tietolaji
         " & voimassaolopäivämäärällä: " voimassaolopvm ".")
    :tietueiden-haku-epaonnistui
    (str "Tietueiden haku palautti virheitä (URL: " url ") tr-osoitteella: " (pr-str tierekisteriosoitevali)
         " & tietolajitunnisteella: " tietolaji
         " & voimassaolopäivämäärällä: " voimassaolopvm ".")))

(defn hae-tietueet [db integraatioloki url tierekisteriosoitevali tietolaji voimassaolopvm tilannepvm]
  (log/debug "Haetaan tietue tierekisteriosoitteella: " (pr-str tierekisteriosoitevali)
             ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [url (str url "/haetietueet")]
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "tierekisteri" "hae-tietueet"
      (fn [konteksti]
        (let [kutsudata (kutsusanoma/muodosta-kutsu tierekisteriosoitevali tietolaji voimassaolopvm tilannepvm)
              otsikot {"Content-Type" "text/xml; charset=utf-8"}
              http-asetukset {:metodi :POST :url url :otsikot otsikot}
              {xml :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
          (kasittele-tietuehakuvastaus xml url tietolaji tierekisteriosoitevali voimassaolopvm))))))