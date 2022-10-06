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
    [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
    [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn hae-tiedosto
  ([integraatioloki db integraatio url kohde]
   (hae-tiedosto integraatioloki db integraatio url kohde nil nil))
  ([integraatioloki db integraatio url kohde kayttajatunnus salasana]
   (log/debug "Haetaan tiedosto AVA:sta URL:lla: " url " kohteeseen: " kohde)
   (tiedosto/lataa-tiedosto integraatioloki db "ptj" integraatio url kohde kayttajatunnus salasana)))

(defn aja-paivitys [integraatioloki
                    db
                    paivitystunnus
                    kohdetiedoston-polku
                    tiedostourl
                    paivitystyyppi
                    paivitys
                    kayttajatunnus
                    salasana]
  (log/debug (format "Päivitetään geometria-aineisto: %s. Päivitystyyppi on %s." paivitystunnus paivitystyyppi))
      (when (= :palvelimelta paivitystyyppi)
            (kansio/poista-tiedostot (.getParent (io/file kohdetiedoston-polku)))
            (hae-tiedosto integraatioloki db (str paivitystunnus "-haku") tiedostourl kohdetiedoston-polku kayttajatunnus salasana)
            (arkisto/pura-paketti kohdetiedoston-polku))
      (paivitys)
      (geometriapaivitykset/paivita-viimeisin-paivitys db paivitystunnus (pvm/nyt)))

(defn kohdetiedosto-ok? [kohdepolku]
  (let [file (io/file kohdepolku)
        kansio (.getParent file)
        tiedosto (.getName file)]
    (if (and (not (empty kansio)) (not (empty tiedosto)))
      (do
        (kansio/luo-jos-ei-olemassa kansio)
        true)
      false)))

;; Tiedoston muutospäivää (ladattavan aineiston päivittymisajankohta) ei saada enää tutkittua.
;; Tiedoston päivittymistarvetta voi hallita GEOMETRIAPAIVITYS-taulun seuraava_paivitys-sarakkeessa.
;; Päivitystyypit ovat :palvelimelta ja :paikallinen. Paikallinen päivitys ei vaatisi tiedostourlia eikä kohdetiedoston polkua, mutta
;; koska paikallinen päivitystapa on poikkeus, tehdään tarkistukset jotka varmistavat, että asetukset ovat oikein ja kansio olemassa.
(defn kaynnista-paivitys [integraatioloki db paivitystunnus tiedostourl kohdetiedoston-polku paivitys kayttajatunnus salasana]
  (log/debug (format "Tarkistetaan halutaanko geometria-aineisto: %s paivittaa." paivitystunnus))
  (when (and (not-empty tiedostourl) (kohdetiedosto-ok? kohdetiedoston-polku))
    (try+
      (let [paivitystyyppi      (geometriapaivitykset/pitaako-paivittaa? db paivitystunnus)
            ava-paivitys (fn [] (aja-paivitys
                                  integraatioloki
                                  db
                                  paivitystunnus
                                  kohdetiedoston-polku
                                  tiedostourl
                                  paivitystyyppi
                                  paivitys
                                  kayttajatunnus
                                  salasana))]
        (if paivitystyyppi
          (lukko/yrita-ajaa-lukon-kanssa db paivitystunnus ava-paivitys)
          (log/debug (format "Geometria-aineiston %s seuraava päivitysajankohta on määritelty myöhemmäksi. Päivitystä ei tehdä." paivitystunnus))))
      (catch Exception e
        (log/warn e (format "Geometria-aineiston päivityksessä: %s tapahtui poikkeus. Tarkista konfiguraatio asetukset-tiedostossa ja tietokantatauluissa." paivitystunnus))))))
