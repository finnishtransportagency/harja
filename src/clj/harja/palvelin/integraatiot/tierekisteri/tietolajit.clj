(ns harja.palvelin.integraatiot.tierekisteri.tietolajit
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus :as vastaussanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn hae-tietolajit [integraatioloki url tunniste muutospvm]
  (log/debug "Hae tietolajin: " tunniste " ominaisuudet muutospäivämäärällä: " muutospvm " Tierekisteristä")
  (let [kutsudata (kutsusanoma/muodosta-kutsu tunniste muutospvm)
        palvelu-url (str url "/haetietolajit")
        otsikot {"Content-Type" "text/xml"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "hae-tietolaji"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml _]
                        (kasittele-vastaus
                          vastaus-xml
                          (str "Tietolajin haku epäonnistui (URL: " url ") tunnisteella: " tunniste
                               " & muutospäivämäärällä: " muutospvm ".")
                          :tietolaji-haku-epaonnistui
                          (str "Tietolajin haku palautti virheitä (URL: " url ") tunnisteella: " tunniste
                               " & muutospäivämäärällä: " muutospvm "."))))]
    vastausdata))