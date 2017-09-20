(ns harja.palvelin.integraatiot.paikkatietojarjestelma.ava
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

(defn hae-tiedoston-muutospaivamaara [db integraatioloki integraatio url kayttajatunnus salasana]
  (log/debug "Haetaan tiedoston muutospäivämäärä AVA:sta URL:lla: " url)
  (let [http-asetukset {:metodi :HEAD
                        :url url
                        :kayttajatunnus kayttajatunnus
                        :salasana salasana}]
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "ptj" integraatio
      (fn [konteksti]
        (let [{otsikot :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-tiedoston-muutospaivamaaran-hakuvastaus otsikot))))))

(defn hae-tiedosto [integraatioloki db integraatio url kohde kayttajatunnus salasana]
  (log/debug "Haetaan tiedosto AVA:sta URL:lla: " url " kohteeseen: " kohde)
  (tiedosto/lataa-tiedosto-http integraatioloki db "ptj" integraatio url kohde kayttajatunnus salasana))

(defn aja-paivitys [integraatioloki
                    db
                    paivitystunnus
                    kohdetiedoston-polku
                    tiedostourl
                    tiedoston-muutospvm
                    paivitys
                    kayttajatunnus
                    salasana]
  (log/debug (format "Päivitetään geometria-aineisto: %s" paivitystunnus))
  (kansio/poista-tiedostot (.getParent (io/file kohdetiedoston-polku)))
  (hae-tiedosto integraatioloki db (str paivitystunnus "-haku") tiedostourl kohdetiedoston-polku kayttajatunnus salasana)
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

(defn kaynnista-paivitys [integraatioloki db paivitystunnus tiedostourl kohdetiedoston-polku paivitys kayttajatunnus salasana]
  (log/debug (format "Tarkistetaan onko geometria-aineisto: %s päivittynyt AVA:ssa." paivitystunnus))
  (when (and (not-empty tiedostourl) (onko-kohdetiedosto-ok? kohdetiedoston-polku))
    (try+
      (let [integraatio (str paivitystunnus "-muutospaivamaaran-haku")
            tiedoston-muutospvm (hae-tiedoston-muutospaivamaara
                                  db
                                  integraatioloki
                                  integraatio
                                  tiedostourl
                                  kayttajatunnus
                                  salasana)
            ava-paivitys (fn [] (aja-paivitys
                                  integraatioloki
                                  db
                                  paivitystunnus
                                  kohdetiedoston-polku
                                  tiedostourl
                                  tiedoston-muutospvm
                                  paivitys
                                  kayttajatunnus
                                  salasana))]
        (if (geometriapaivitykset/pitaako-paivittaa? db paivitystunnus tiedoston-muutospvm)
          (lukko/yrita-ajaa-lukon-kanssa db paivitystunnus ava-paivitys)
          (log/debug (format "Geometria-aineisto: %s, ei ole päivittynyt viimeisimmän haun jälkeen. Päivitystä ei tehdä." paivitystunnus))))
      (catch Exception e
        (log/error e (format "Geometria-aineiston päivityksessä: %s tapahtui poikkeus." paivitystunnus))))))