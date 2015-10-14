(ns harja.palvelin.integraatiot.tierekisteri.tietueet
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueiden-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn hae-tietueet [integraatioloki url tierekisteriosoitevali tietolaji muutospvm]
  (log/debug "Haetaan tietue tierekisteriosoitteella: " (pr-str tierekisteriosoitevali) ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [kutsudata (kutsusanoma/muodosta-kutsu tierekisteriosoitevali tietolaji muutospvm)
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
                               " & muutospäivämäärällä: " muutospvm ".")
                          :tietueiden-haku-epaonnistui
                          (str "Tietueiden haku palautti virheitä (URL: " url ") tr-osoitteella: " (pr-str tierekisteriosoitevali)
                               " & tietolajitunnisteella: " tietolaji
                               " & muutospäivämäärällä: " muutospvm "."))))]
    vastausdata))