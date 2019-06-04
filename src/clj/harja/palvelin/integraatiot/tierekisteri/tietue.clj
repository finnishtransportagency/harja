(ns harja.palvelin.integraatiot.tierekisteri.tietue
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-hakukutsu :as haku-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu :as lisays-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-paivityskutsu :as paivitys-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-poistokutsu :as poisto-kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely :refer :all]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +otsikot+
  {"Content-Type" "text/xml; charset=utf-8"})

(defn http-asetukset[url]
  {:metodi :POST :url url :otsikot +otsikot+})

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


(defn hae-tietue [db integraatioloki url id tietolaji tilannepvm]
  (log/debug "Haetaan tietue: " id ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [url (str url "/haetietue")]
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "tierekisteri" "hae-tietue"
      (fn [konteksti]
        (let [kutsudata (haku-kutsusanoma/muodosta-kutsu id tietolaji tilannepvm)
              http-asetukset (http-asetukset url)
              {xml :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset  kutsudata)]
          (kasittele-tietueen-hakuvastaus url id tietolaji xml))))))

(defn lisaa-tietue [db integraatioloki url tiedot]
  (log/debug "Lisätään tietue")
  (let [url (str url "/lisaatietue")]
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "tierekisteri" "lisaa-tietue"
      (fn [konteksti]
        (let [kutsudata (lisays-kutsusanoma/muodosta-kutsu tiedot)
              http-asetukset (http-asetukset url)
              {xml :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
          (kasittele-tietueen-lisaysvastaus xml url))))))

(defn paivita-tietue [db integraatioloki url tiedot]
  (log/debug "Päivitetään tietue")
  (let [url (str url "/paivitatietue")]
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "tierekisteri" "paivita-tietue"
      (fn [konteksti]
        (let [kutsudata (paivitys-kutsusanoma/muodosta-kutsu tiedot)
              http-asetukset (http-asetukset url)
              {xml :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
          (kasittele-tietueen-paivitysvastaus xml url))))))

(defn poista-tietue [db integraatioloki url tiedot]
  (log/debug "Poistetaan tietue")
  (let [url (str url "/poistatietue")]
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "tierekisteri" "poista-tietue"
      (fn [konteksti]
        (let [kutsudata (poisto-kutsusanoma/muodosta-kutsu tiedot)
              http-asetukset (http-asetukset url)
              {xml :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
          (kasittele-tietueen-poistovastaus xml url))))))
