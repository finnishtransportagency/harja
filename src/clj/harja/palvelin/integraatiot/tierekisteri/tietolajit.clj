(ns harja.palvelin.integraatiot.tierekisteri.tietolajit
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma])
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
  (let [url (str url "/haetietolaji")
        otsikot {"Content-Type" "text/xml; charset=utf-8"}
        http-asetukset {:metodi :POST :url url :otsikot otsikot}
        tyonkulku (fn [konteksti]
                    (let [kutsudata (kutsusanoma/muodosta-kutsu tunniste muutospvm)
                          {vastaus-xml :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
                      (kasittele-tietolajin-hakuvastaus url tunniste muutospvm vastaus-xml)))]
    (integraatiotapahtuma/suorita-integraatio db integraatioloki "tierekisteri" "hae-tietolaji" tyonkulku)))