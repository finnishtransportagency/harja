(ns harja.palvelin.integraatiot.tierekisteri.tietueet
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueiden-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])

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

(defn hae-tietueet [db integraatioloki url tierekisteriosoitevali tietolaji voimassaolopvm]
  (log/debug "Haetaan tietue tierekisteriosoitteella: " (pr-str tierekisteriosoitevali) ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [lokittaja (integraatioloki/lokittaja integraatioloki db "tierekisteri" "hae-tietueet")
        integraatiopiste (http/luo-integraatiopiste lokittaja)
        vastauskasittelija (fn [xml _] (kasittele-tietuehakuvastaus xml url tietolaji tierekisteriosoitevali voimassaolopvm))
        kutsudata (kutsusanoma/muodosta-kutsu tierekisteriosoitevali tietolaji voimassaolopvm)
        url (str url "/haetietueet")
        otsikot {"Content-Type" "text/xml; charset=utf-8"}]
    (http/POST integraatiopiste url otsikot nil kutsudata vastauskasittelija)))