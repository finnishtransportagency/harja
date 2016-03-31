(ns harja.palvelin.integraatiot.tierekisteri.tietolajit
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-tietolajin-hakuvastaus [url tunniste muutospvm vastaus-xml]
  (kasittele-vastaus
    vastaus-xml
    (str "Tietolajin haku epäonnistui (URL: " url ") tunnisteella: " tunniste
         " & muutospäivämäärällä: " muutospvm ".")
    :tietolaji-haku-epaonnistui
    (str "Tietolajin haku palautti virheitä (URL: " url ") tunnisteella: " tunniste
         " & muutospäivämäärällä: " muutospvm ".")))

(defn hae-tietolajit [db integraatioloki url tunniste muutospvm]
  (log/debug "Hae tietolajin: " tunniste " ominaisuudet muutospäivämäärällä: " muutospvm " Tierekisteristä")
  (let [lokittaja (integraatioloki/lokittaja integraatioloki db "tierekisteri" "hae-tietolaji")
        integraatiopiste (http/luo-integraatiopiste lokittaja)
        kutsudata (kutsusanoma/muodosta-kutsu tunniste muutospvm)
        vastauskasittelija (fn [vastaus-xml _] (kasittele-tietolajin-hakuvastaus url tunniste muutospvm vastaus-xml))
        palvelu-url (str url "/haetietolaji")
        otsikot {"Content-Type" "text/xml; charset=utf-8"}]
    (http/POST integraatiopiste palvelu-url otsikot nil kutsudata vastauskasittelija)))