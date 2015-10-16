(ns harja.palvelin.integraatiot.tierekisteri.tietueet
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueiden-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn hae-tietueet [integraatioloki url tierekisteriosoitevali tietolaji voimassaolopvm]
  (log/debug "Haetaan tietue tierekisteriosoitteella: " (pr-str tierekisteriosoitevali) ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [kutsudata (kutsusanoma/muodosta-kutsu tierekisteriosoitevali tietolaji voimassaolopvm)
        palvelu-url (str url "/haetietueet")
        otsikot {"Content-Type" "text/xml; charset=utf-8"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "hae-tietueet"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml _]
                        (kasittele-vastaus
                          vastaus-xml
                          (str "Tietueiden haku epäonnistui (URL: " url ") tr-osoitteella: " (pr-str tierekisteriosoitevali)
                               " & tietolajitunnisteella: " tietolaji
                               " & voimassaolopäivämäärällä: " voimassaolopvm ".")
                          :tietueiden-haku-epaonnistui
                          (str "Tietueiden haku palautti virheitä (URL: " url ") tr-osoitteella: " (pr-str tierekisteriosoitevali)
                               " & tietolajitunnisteella: " tietolaji
                               " & voimassaolopäivämäärällä: " voimassaolopvm "."))))]
    vastausdata))