(ns harja.palvelin.integraatiot.tierekisteri.tietue
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-hakukutsu :as haku-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu :as lisays-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-paivityskutsu :as paivitys-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-poistokutsu :as poisto-kutsusanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn aseta-tunniste-arvoihin [tiedot]
  (assoc-in tiedot
            [:tietue :tietolaji :arvot]
            (.replaceAll
              (get-in tiedot [:tietue :tietolaji :arvot])
              "----livitunniste----"
              (get-in tiedot [:tietue :tunniste]))))


(defn kasittele-tietueen-hakuvastaus [url id tietolaji xml]
  (kasittele-vastaus
    xml
    (str "Tietueen haku epäonnistui (URL: " url ") tunnisteella: " id
         " & tietolajitunnisteella: " tietolaji ".")
    :tietueen-haku-epaonnistui
    (str "Tietueen haku palautti virheitä (URL: " url ") tunnisteella: " id
         " & tietolajitunnisteella: " tietolaji ".")))

(defn kasittele-tietueen-lisaysvastaus [xml url]
  (kasittele-vastaus
    xml
    (str "Tietueen lisäys epäonnistui (URL: " url ")")
    :tietueen-lisays-epaonnistui
    (str "Tietueen lisäys palautti virheitä (URL: " url ")")))

(defn kasittele-tietueen-paivitysvastaus [xml url]
  (kasittele-vastaus
    xml
    (str "Tietueen päivitys epäonnistui (URL: " url ")")
    :tietueen-paivitys-epaonnistui
    (str "Tietueen päivitys palautti virheitä (URL: " url ")")))

(defn kasittele-tietueen-poistovastaus [xml url]
  (kasittele-vastaus
    xml
    (str "Tietueen poisto epäonnistui (URL: " url ")")
    :tietueen-poisto-epaonnistui
    (str "Tietueen poisto palautti virheitä (URL: " url ")")))

(defn luo-integraatiopiste [db integraatioloki integraatio]
  (http/luo-integraatiopiste (integraatioloki/lokittaja integraatioloki db "tierekisteri" integraatio)))

(defn hae-tietue [db integraatioloki url id tietolaji]
  (log/debug "Haetaan tietue: " id ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [integraatiopiste (luo-integraatiopiste db integraatioloki "hae-tietue")
        vastauskasittelija (fn [xml _] (kasittele-tietueen-hakuvastaus url id tietolaji xml))
        kutsudata (haku-kutsusanoma/muodosta-kutsu id tietolaji)
        url (str url "/haetietue")
        otsikot {"Content-Type" "text/xml; charset=utf-8"}]
    (http/POST integraatiopiste url otsikot nil kutsudata vastauskasittelija)))

(defn lisaa-tietue [db integraatioloki url tiedot]
  (log/debug "Lisätään tietue")
  (let [integraatiopiste (luo-integraatiopiste db integraatioloki "lisaa-tietue")
        vastauskasittelija (fn [xml _] (kasittele-tietueen-lisaysvastaus xml url))
        tiedot (aseta-tunniste-arvoihin tiedot)
        kutsudata (lisays-kutsusanoma/muodosta-kutsu tiedot)
        url (str url "/lisaatietue")
        otsikot {"Content-Type" "text/xml"}]
    (http/POST integraatiopiste url otsikot nil kutsudata vastauskasittelija)))

(defn paivita-tietue [db integraatioloki url tiedot]
  (log/debug "Päivitetään tietue")
  (let [integraatiopiste (luo-integraatiopiste db integraatioloki "paivita-tietue")
        vastauskasittelija (fn [xml _] (kasittele-tietueen-paivitysvastaus xml url))
        kutsudata (paivitys-kutsusanoma/muodosta-kutsu tiedot)
        url (str url "/paivitatietue")
        otsikot {"Content-Type" "text/xml"}]
    (http/POST integraatiopiste url otsikot nil kutsudata vastauskasittelija)))



(defn poista-tietue [db integraatioloki url tiedot]
  (log/debug "Poistetaan tietue")
  (let [integraatiopiste (luo-integraatiopiste db integraatioloki "poista-tietue")
        vastauskasittelija (fn [xml _] (kasittele-tietueen-poistovastaus xml url))
        kutsudata (poisto-kutsusanoma/muodosta-kutsu tiedot)
        url (str url "/poistatietue")
        otsikot {"Content-Type" "text/xml"}]
    (http/POST integraatiopiste url otsikot nil kutsudata vastauskasittelija)))