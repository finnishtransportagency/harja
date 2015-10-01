(ns harja.palvelin.integraatiot.tierekisteri.tietueet
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueiden-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus :as vastaussanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all]
            [harja.testi :as testi])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn hae-tietueet [integraatioloki url tr tietolaji muutospvm]
  (log/debug "Haetaan tietue tierekisteriosoitteella: " (pr-str tr) ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [kutsudata (kutsusanoma/muodosta-kutsu tr tietolaji muutospvm)
        palvelu-url (str url "/haetietueet")
        otsikot {"Content-Type" "text/xml"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "hae-tietueet"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml]
                        (kasittele-vastaus
                          vastaus-xml
                          (str "Tietueiden haku epäonnistui (URL: " url ") tr-osoitteella: " (pr-str tr)
                               " & tietolajitunnisteella: " tietolaji
                               " & muutospäivämäärällä: " muutospvm ".")
                          :tietueiden-haku-epaonnistui
                          (str "Tietueiden haku palautti virheitä (URL: " url ") tr-osoitteella: " (pr-str tr)
                               " & tietolajitunnisteella: " tietolaji
                               " & muutospäivämäärällä: " muutospvm "."))))]
    vastausdata))