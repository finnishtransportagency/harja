(ns harja.palvelin.integraatiot.tierekisteri.tietue
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-hakukutsu :as haku-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu :as lisays-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-paivityskutsu :as paivitys-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-poistokutsu :as poisto-kutsusanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn aseta-tunniste-arvoihin [tiedot]
  (assoc-in tiedot
            [:tietue :tietolaji :arvot]
            (.replaceAll
              (get-in tiedot [:tietue :tietolaji :arvot] )
              "----livitunniste----"
              (get-in tiedot [:tietue :tunniste] ))))


(defn hae-tietue [integraatioloki url id tietolaji]
  (log/debug "Haetaan tietue: " id ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [kutsudata (haku-kutsusanoma/muodosta-kutsu id tietolaji)
        palvelu-url (str url "/haetietue")
        otsikot {"Content-Type" "text/xml; charset=utf-8"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "hae-tietue"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml _]
                        (kasittele-vastaus vastaus-xml
                                           (str "Tietueen haku epäonnistui (URL: " url ") tunnisteella: " id
                                                " & tietolajitunnisteella: " tietolaji ".")
                                           :tietueen-haku-epaonnistui
                                           (str "Tietueen haku palautti virheitä (URL: " url ") tunnisteella: " id
                                                " & tietolajitunnisteella: " tietolaji "."))))]
    vastausdata))

(defn lisaa-tietue [integraatioloki url tiedot]
  (log/debug "Lisätään tietue")
  (let [tiedot (aseta-tunniste-arvoihin tiedot)
        kutsudata (lisays-kutsusanoma/muodosta-kutsu tiedot)
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
                      (fn [vastaus-xml _]
                        (kasittele-vastaus vastaus-xml
                                           (str "Tietueen lisäys epäonnistui (URL: " url ")")
                                           :tietueen-lisays-epaonnistui
                                           (str "Tietueen lisäys palautti virheitä (URL: " url ")"))))]
    vastausdata))

(defn paivita-tietue [integraatioloki url tiedot]
  (log/debug "Päivitetään tietue")
  (let [kutsudata (paivitys-kutsusanoma/muodosta-kutsu tiedot)
        palvelu-url (str url "/paivitatietue")
        otsikot {"Content-Type" "text/xml"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "paivita-tietue"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml _]
                        (kasittele-vastaus vastaus-xml
                                           (str "Tietueen päivitys epäonnistui (URL: " url ")")
                                           :tietueen-paivitys-epaonnistui
                                           (str "Tietueen päivitys palautti virheitä (URL: " url ")"))))]
    vastausdata))

(defn poista-tietue [integraatioloki url tiedot]
  (log/debug "Poistetaan tietue")
  (let [kutsudata (poisto-kutsusanoma/muodosta-kutsu tiedot)
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
                      (fn [vastaus-xml _]
                        (kasittele-vastaus vastaus-xml
                                           (str "Tietueen poisto epäonnistui (URL: " url ")")
                                           :tietueen-poisto-epaonnistui
                                           (str "Tietueen poisto palautti virheitä (URL: " url ")"))))]
    vastausdata))