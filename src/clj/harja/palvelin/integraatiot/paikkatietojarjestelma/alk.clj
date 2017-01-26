(ns harja.palvelin.integraatiot.paikkatietojarjestelma.alk
  (:require
    [taoensso.timbre :as log]
    [clojure.java.io :as io]
    [clj-time.coerce :as time-coerce]
    [harja.palvelin.tyokalut.lukot :as lukko]
    [harja.palvelin.tyokalut.kansio :as kansio]
    [harja.palvelin.tyokalut.arkisto :as arkisto]
    [harja.palvelin.integraatiot.integraatiopisteet.tiedosto :as tiedosto]
    [harja.kyselyt.geometriapaivitykset :as geometriapaivitykset]
    [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-tiedoston-muutospaivamaaran-hakuvastaus [otsikko]
  (let [muutospaivamaara (:last-modified otsikko)]
    (if muutospaivamaara
      (time-coerce/to-sql-time (java.util.Date. muutospaivamaara))
      nil)))

(defn hae-tiedoston-muutospaivamaara [db integraatioloki integraatio url]
  (log/debug "Haetaan tiedoston muutospäivämäärä ALK:sta URL:lla: " url)
  (let [http-asetukset {:metodi :HEAD :url url}]
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "ptj" integraatio
      (fn [konteksti]
        (let [{otsikot :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-tiedoston-muutospaivamaaran-hakuvastaus otsikot))))))

(defn hae-tiedosto [integraatioloki integraatio url kohde]
  (log/debug "Haetaan tiedosto ALK:sta URL:lla: " url " kohteeseen: " kohde)
  (tiedosto/lataa-tiedosto integraatioloki "ptj" integraatio url kohde))

(defn aja-alk-paivitys [integraatioloki db paivitystunnus kohdetiedoston-polku tiedostourl tiedoston-muutospvm paivitys]
  (log/debug (format "Päivitetään geometria-aineisto: %s" paivitystunnus))
  (kansio/poista-tiedostot (.getParent (io/file kohdetiedoston-polku)))
  (hae-tiedosto integraatioloki (str paivitystunnus "-haku") tiedostourl kohdetiedoston-polku)
  (arkisto/pura-paketti kohdetiedoston-polku)
  (paivitys)
  (geometriapaivitykset/paivita-viimeisin-paivitys db paivitystunnus tiedoston-muutospvm))

(defn onko-kohdetiedosto-ok? [kohdepolku]
  (let [file (io/file kohdepolku)
        kansio (.getParent file)
        tiedosto (.getName file)]
    (if (and (not (empty kansio)) (not (empty tiedosto)))
      (do
        (kansio/luo-jos-ei-olemassa kansio)
        true)
      false)))

(defn kaynnista-paivitys [integraatioloki db paivitystunnus tiedostourl kohdetiedoston-polku paivitys]
  (log/debug (format "Tarkistetaan onko geometria-aineisto: %s päivittynyt ALK:ssa." paivitystunnus))
  (when (and (not-empty tiedostourl) (onko-kohdetiedosto-ok? kohdetiedoston-polku))
    (try+
      (let [integraatio (str paivitystunnus "-muutospaivamaaran-haku")
            tiedoston-muutospvm (hae-tiedoston-muutospaivamaara db integraatioloki integraatio tiedostourl)
            alk-paivitys (fn [] (aja-alk-paivitys integraatioloki db paivitystunnus kohdetiedoston-polku tiedostourl tiedoston-muutospvm paivitys))]
        (if (geometriapaivitykset/pitaako-paivittaa? db paivitystunnus tiedoston-muutospvm)
          (lukko/yrita-ajaa-lukon-kanssa db paivitystunnus alk-paivitys)
          (log/debug (format "Geometria-aineisto: %s, ei ole päivittynyt viimeisimmän haun jälkeen. Päivitystä ei tehdä." paivitystunnus))))
      (catch Exception e
        (log/error e (format "Geometria-aineiston päivityksessä: %s tapahtui poikkeus." paivitystunnus))))))