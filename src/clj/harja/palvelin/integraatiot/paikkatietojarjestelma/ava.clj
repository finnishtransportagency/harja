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


(defn tiedosto-loytyy? [kohdepolku]
      (kansio/onko-tiedosto-olemassa? kohdepolku))

(defn kohdekansio-ok? [kohdepolku]
      (let [file (io/file kohdepolku)
            kansio (.getParent file)
            tiedosto (.getName file)]
           (if (and (not (empty kansio)) (not (empty tiedosto)))
             (do
               (kansio/luo-jos-ei-olemassa kansio)
               true)
             false)))

(defn hae-tiedosto
  ([integraatioloki db integraatio url kohde]
   (hae-tiedosto integraatioloki db integraatio url kohde nil nil))
  ([integraatioloki db integraatio url kohde kayttajatunnus salasana]
   (log/debug "Haetaan tiedosto AVA:sta URL:lla: " url " kohteeseen: " kohde)
   (tiedosto/lataa-tiedosto integraatioloki db "ptj" integraatio url kohde kayttajatunnus salasana)))

(defn aja-paivitys [integraatioloki
                    db
                    paivitystunnus
                    tiedostourl
                    kohdetiedoston-polku
                    shapefile
                    paivitystyyppi
                    paivitys
                    kayttajatunnus
                    salasana]
      (log/debug (format "Päivitetään geometria-aineisto: %s. Päivitystyyppi on %s." paivitystunnus paivitystyyppi))

      ;; Hae geometria-aineisto palvelimelta, jos niin määritelty
      (when (= :palvelimelta paivitystyyppi)
            (kansio/poista-tiedostot (.getParent (io/file kohdetiedoston-polku)))
            (hae-tiedosto integraatioloki db (str paivitystunnus "-haku") tiedostourl kohdetiedoston-polku kayttajatunnus salasana)
            (arkisto/pura-paketti kohdetiedoston-polku))
      ;; Päivitä tiedoston sisältö kantaan
      (paivitys)
      (geometriapaivitykset/paivita-viimeisin-paivitys db paivitystunnus (pvm/nyt)))

;; Tiedoston muutospäivää (ladattavan aineiston päivittymisajankohta) ei saada enää tutkittua.
;; Tiedoston päivittymistarvetta voi hallita GEOMETRIAPAIVITYS-taulun seuraava_paivitys-sarakkeessa.
;; Päivitystyypit ovat :palvelimelta ja :paikallinen. Paikallinen päivitys ei vaatisi tiedostourlia eikä kohdetiedoston polkua, mutta
;; koska paikallinen päivitystapa on poikkeus, tehdään tarkistukset jotka varmistavat, että asetukset ovat oikein ja kansio olemassa.
(defn kaynnista-paivitys [integraatioloki db paivitystunnus tiedostourl kohdetiedoston-polku shapefile paivitys kayttajatunnus salasana]
      (log/debug (format "[KÄYNNISTETTY-GEOMETRIAPAIVITYS] %s" paivitystunnus))
      (try
        (let [paivitystyyppi (geometriapaivitykset/pitaako-paivittaa? db paivitystunnus)
              ava-paivitys (fn [] (aja-paivitys
                                    integraatioloki
                                    db
                                    paivitystunnus
                                    tiedostourl
                                    kohdetiedoston-polku
                                    shapefile
                                    paivitystyyppi
                                    paivitys
                                    kayttajatunnus
                                    salasana))]
             ;; Tehdään esitarkastukset päivitystyypin mukaan
             (case paivitystyyppi
                   :palvelimelta
                   (when (or (empty tiedostourl) (not (kohdekansio-ok? kohdetiedoston-polku)))
                         (throw (Exception. "Virhe geometria-aineston haun osoitteessa tai kohdekansiossa.")))
                   :ei-paivitystarvetta
                   (log/debug (format "Geometria-aineiston %s seuraava päivitysajankohta on määritelty myöhemmäksi. Päivitystä ei tehdä." paivitystunnus))
                   :ei-kaytossa
                   (log/warn (format "Geometriapäivitystä %s ei ajeta lainkaan. Päivitä geometriapaivitys-taulun tiedot, jos päivitys täytyy ajaa." paivitystunnus))
                   nil)
             ;; Päivitetään jos tarvetta
             (when (#{:palvelimelta :paikallinen} paivitystyyppi)
               (if (lukko/yrita-ajaa-lukon-kanssa db paivitystunnus ava-paivitys)
                 (log/debug (format "[ONNISTUNUT-GEOMETRIAPAIVITYS] %s" paivitystunnus))
                 (log/debug (format "[EPÄONNISTUNUT-GEOMETRIAPAIVITYS] %s. Päivitystä ei ajettu LUKKO-taulun konfiguraation takia." paivitystunnus)))))
        (catch Exception e
          (do (log/warn (format "[EPÄONNISTUNUT-GEOMETRIAPAIVITYS] Geometria-aineiston päivityksessä: %s tapahtui poikkeus. %s Tarkista konfiguraatio asetukset.edn-tiedostossa ja tietokantatauluissa." paivitystunnus (.getMessage e)))
              (geometriapaivitykset/paivita-viimeisin-paivitys db paivitystunnus nil)))))
