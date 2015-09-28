(ns harja.palvelin.integraatiot.tierekisteri.tietue
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-hakukutsu :as haku-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu :as lisays-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus :as vastaussanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn hae-tietue [integraatioloki url id tietolaji]
  (log/debug "Haetaan tietue: " id ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [kutsudata (haku-kutsusanoma/muodosta-kutsu id tietolaji)
        palvelu-url (str url "/haetietue")
        otsikot {"Content-Type" "text/xml"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "hae-tietue"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml]
                        (kasittele-vastaus vastaus-xml
                                           (str "Tietueen haku epäonnistui (URL: " url ") tunnisteella: " id
                                                " & tietolajitunnisteella: " tietolaji ".")
                                           :tietueen-haku-epaonnistui
                                           (str "Tietueen haku palautti virheitä (URL: " url ") tunnisteella: " id
                                                " & tietolajitunnisteella: " tietolaji "."))))]
    vastausdata))

(defn lisaa-tietue [integraatioloki url tietue]
  (log/debug "Lisätään tietue")
  (let [kutsudata (lisays-kutsusanoma/muodosta-kutsu tietue)
        palvelu-url (str url "/lisaatietue")
        otsikot {"Content-Type" "text/xml"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "lisaa-tietue"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml]
                        (kasittele-vastaus vastaus-xml
                                           (str "Tietueen lisäys epäonnistui (URL: " url ")")
                                           :tietueen-lisays-epaonnistui
                                           (str "Tietueen lisäys palautti virheitä (URL: " url ")"))))]
    vastausdata))

(defn poista-tietue [integraatioloki url tietue]
  (log/debug "Poistetaan tietue")
  (let [kutsudata (lisays-kutsusanoma/muodosta-kutsu tietue)
        palvelu-url (str url "/poistatietue")
        otsikot {"Content-Type" "text/xml"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "poista-tietue"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml]
                        (kasittele-vastaus vastaus-xml
                                           (str "Tietueen poisto epäonnistui (URL: " url ")")
                                           :tietueen-poisto-epaonnistui
                                           (str "Tietueen poisto palautti virheitä (URL: " url ")"))))]
    vastausdata))