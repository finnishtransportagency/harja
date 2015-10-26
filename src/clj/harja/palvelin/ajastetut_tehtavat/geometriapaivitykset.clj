(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset
  (:require [taoensso.timbre :as log]
            [chime :refer [chime-ch]]
            [chime :refer [chime-at]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]
            [harja.kyselyt.geometriapaivitykset :as geometriapaivitykset]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.alk-komponentti :as alk]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.palvelin.tyokalut.kansio :as kansio]
            [harja.palvelin.tyokalut.arkisto :as arkisto]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko :as tieverkon-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialueet :as pohjavesialueen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.soratien-hoitoluokat :as soratien-hoitoluokkien-tuonti]
            [clojure.java.io :as io]
            [clj-time.coerce :as coerce])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.net URI)))

(defn aja-alk-paivitys [alk db paivitystunnus kohdepolku kohdetiedoston-polku tiedostourl tiedoston-muutospvm paivitys]
  (log/debug "Geometria-aineisto: " paivitystunnus " on muuttunut ja tarvitaan päivittää")
  (kansio/poista-tiedostot kohdepolku)
  (alk/hae-tiedosto alk (str paivitystunnus "-haku") tiedostourl kohdetiedoston-polku)
  (arkisto/pura-paketti kohdetiedoston-polku)
  (paivitys)
  (geometriapaivitykset/paivita-viimeisin-paivitys<! db tiedoston-muutospvm paivitystunnus)
  (log/debug "Geometriapäivitys: " paivitystunnus " onnistui"))

(defn onko-kohdetiedosto-ok? [kohdepolku kohdetiedoston-nimi]
  (and
    (not (empty kohdepolku))
    (not (empty kohdetiedoston-nimi))
    (.isDirectory (clojure.java.io/file kohdepolku))))

(defn pitaako-paivittaa? [db paivitystunnus tiedoston-muutospvm]
  (let [paivityksen-tiedot (first (geometriapaivitykset/hae-paivitys db paivitystunnus))
        viimeisin-paivitys (:viimeisin_paivitys paivityksen-tiedot)]
    (or (nil? viimeisin-paivitys)
        (pvm/jalkeen?
          (time-coerce/from-sql-time tiedoston-muutospvm)
          (time-coerce/from-sql-time viimeisin-paivitys)))))

(defn tarvitaanko-paikallinen-paivitys? [db paivitystunnus tiedostourl]
  (log/debug "Tarkistetaan tarviiko ajaa paikallinen geometriapäivitys:" paivitystunnus)
  (try
    (let [polku (if (not tiedostourl) nil (.substring (.getSchemeSpecificPart (URI. tiedostourl)) 2))
          tiedosto (if (not polku) nil (io/file polku))
          tiedoston-muutospvm (if (not tiedosto) nil (.lastModified tiedosto))]
      ;; todo: poista!
      (println "----------------->" polku)
      (println "----------------->" tiedosto)
      (println "----------------->" tiedoston-muutospvm)
      (when (and
              (not (nil? tiedosto))
              (.exists tiedosto)
              (pitaako-paivittaa? db paivitystunnus (coerce/from-long tiedoston-muutospvm)))
        (log/debug "Tarvitaan ajaa paikallinen geometriapäivitys: " paivitystunnus)
        true))
    (catch Exception e
      (log/warn "Tarkistettaessa paikallista ajoa geometriapäivitykselle: " paivitystunnus ", tapahtui poikkeus: " e)
      false)))

(defn kaynnista-alk-paivitys [alk db paivitystunnus tiedostourl kohdepolku kohdetiedoston-nimi paivitys]
  (log/debug "Tarkistetaan onko geometria-aineisto: " paivitystunnus " päivittynyt")
  (let [kohdetiedoston-polku (str kohdepolku kohdetiedoston-nimi)]
    ;; todo: tarvii todennäköisesti tehdä tarkempi tarkastus kohdetiedostolle
    (when (and (not-empty tiedostourl) (onko-kohdetiedosto-ok? kohdepolku kohdetiedoston-nimi))
      (try+
        (let [tiedoston-muutospvm (alk/hae-tiedoston-muutospaivamaara alk (str paivitystunnus "-muutospaivamaaran-haku") tiedostourl)
              alk-paivitys (fn [] (aja-alk-paivitys alk db paivitystunnus kohdepolku kohdetiedoston-polku tiedostourl tiedoston-muutospvm paivitys))]
          (if (pitaako-paivittaa? db paivitystunnus tiedoston-muutospvm)
            (lukko/aja-lukon-kanssa db paivitystunnus alk-paivitys)
            (log/debug "Geometria-aineisto: " paivitystunnus ", ei ole päivittynyt viimeisimmän haun jälkeen. Päivitystä ei tehdä.")))
        (catch Exception e
          (log/error "Geometria-aineiston päivityksessä: " paivitystunnus ", tapahtui poikkeus: " e))))))

(defn tee-alkuajastus []
  (time/plus- (time/now) (time/seconds 10)))

(defn ajasta-paivitys [this paivitystunnus tuontivali osoite kohdepolku kohdetiedosto paivitys]
  (log/debug " Ajastetaan geometria-aineiston " paivitystunnus " päivitys ajettavaksi " tuontivali "minuutin välein ")
  (chime-at (periodic-seq (time/now) (-> tuontivali time/minutes))
            (fn [_]
              (kaynnista-alk-paivitys (:alk this) (:db this) paivitystunnus osoite kohdepolku kohdetiedosto paivitys))))

(defn tee-tieverkon-alk-paivitystehtava
  [this {:keys [tuontivali
                tieosoiteverkon-alk-osoite
                tieosoiteverkon-alk-tuontikohde
                tieosoiteverkon-shapefile]}]
  (when (and tuontivali
             tieosoiteverkon-alk-osoite
             tieosoiteverkon-alk-tuontikohde
             tieosoiteverkon-shapefile)
    (ajasta-paivitys this
                     "tieverkko"
                     tuontivali
                     tieosoiteverkon-alk-osoite
                     tieosoiteverkon-alk-tuontikohde
                     "tieosoiteverkko.zip"
                     (fn [] (tieverkon-tuonti/vie-tieverkko-kantaan (:db this) tieosoiteverkon-shapefile)))))

(defn tee-tieverkon-paikallinen-paivitystehtava
  [{:keys [db]}
   {:keys [tieosoiteverkon-alk-osoite
           tieosoiteverkon-alk-tuontikohde
           tieosoiteverkon-shapefile
           tuontivali]}]
  (when (not (and tieosoiteverkon-alk-osoite tieosoiteverkon-alk-tuontikohde))
    (chime-at
      (periodic-seq (tee-alkuajastus) (-> tuontivali time/minutes))
      (fn [_]
        (try
          (when (tarvitaanko-paikallinen-paivitys? db "tieverkko" tieosoiteverkon-shapefile)
            (log/debug "Ajetaan tieverkon paikallinen päivitys")
            (tieverkon-tuonti/vie-tieverkko-kantaan db tieosoiteverkon-shapefile)
            (geometriapaivitykset/paivita-viimeisin-paivitys<! db (harja.pvm/nyt) "tieverkko"))
          (catch Exception e
            (log/debug "Tieosoiteverkon paikallisessa tuonnissa tapahtui poikkeus: " e)))))))

(defn tee-pohjavesialueiden-alk-paivitystehtava
  [this {:keys [tuontivali
                pohjavesialueen-alk-osoite
                pohjavesialueen-alk-tuontikohde
                pohjavesialueen-shapefile]}]
  (when (and tuontivali
             pohjavesialueen-alk-osoite
             pohjavesialueen-alk-tuontikohde
             pohjavesialueen-shapefile)
    (ajasta-paivitys this
                     "pohjavesialueet"
                     tuontivali
                     pohjavesialueen-alk-osoite
                     pohjavesialueen-alk-tuontikohde
                     "pohjavesialue.zip"
                     (fn [] (pohjavesialueen-tuonti/vie-pohjavesialue-kantaan (:db this) pohjavesialueen-shapefile)))))

(defn tee-pohjavesialueiden-paikallinen-paivitystehtava
  [{:keys [db]}
   {:keys [pohjavesialueen-alk-osoite
           pohjavesialueen-alk-tuontikohde
           pohjavesialueen-shapefile
           tuontivali]}]
  (when (not (and pohjavesialueen-alk-osoite pohjavesialueen-alk-tuontikohde))
    (chime-at
      (periodic-seq (tee-alkuajastus) (-> tuontivali time/minutes))
      (fn [_]
        (try
          (when (tarvitaanko-paikallinen-paivitys? db "pohjavesialueet" pohjavesialueen-shapefile)
            (log/debug "Ajetaan pohjavesialueiden paikallinen päivitys")
            (pohjavesialueen-tuonti/vie-pohjavesialue-kantaan db pohjavesialueen-shapefile)
            (geometriapaivitykset/paivita-viimeisin-paivitys<! db (harja.pvm/nyt) "pohjavesialueet"))
          (catch Exception e
            (log/debug "Pohjavesialueiden paikallisessa tuonnissa tapahtui poikkeus: " e)))))))

(defn tee-soratien-hoitoluokkien-alk-paivitystehtava
  [this {:keys [tuontivali
                soratien-hoitoluokkien-alk-osoite
                soratien-hoitoluokkien-alk-tuontikohde
                soratien-hoitoluokkien-shapefile]}]
  (when (and tuontivali
             soratien-hoitoluokkien-alk-osoite
             soratien-hoitoluokkien-alk-tuontikohde
             soratien-hoitoluokkien-shapefile)
    (ajasta-paivitys this
                     "soratieluokat"
                     tuontivali
                     soratien-hoitoluokkien-alk-osoite
                     soratien-hoitoluokkien-alk-tuontikohde
                     "soratien-hoitoluokat.tgz"
                     (fn [] (soratien-hoitoluokkien-tuonti/vie-hoitoluokat-kantaan (:db this) soratien-hoitoluokkien-shapefile)))))

(defn tee-soratien-hoitoluokkien-paikallinen-paivitystehtava
  [{:keys [db]}
   {:keys [tuontivali
           soratien-hoitoluokkien-alk-osoite
           soratien-hoitoluokkien-alk-tuontikohde
           soratien-hoitoluokkien-shapefile]}]
  (when (not (and soratien-hoitoluokkien-alk-osoite soratien-hoitoluokkien-alk-tuontikohde))
    (chime-at (periodic-seq (tee-alkuajastus) (-> tuontivali time/minutes))
              (fn [_]
                (try
                  (when (tarvitaanko-paikallinen-paivitys? db "soratieluokat" soratien-hoitoluokkien-shapefile)
                    (log/debug "Ajetaan sorateiden hoitoluokkien paikallinen päivitys")
                    (soratien-hoitoluokkien-tuonti/vie-hoitoluokat-kantaan db soratien-hoitoluokkien-shapefile)
                    (geometriapaivitykset/paivita-viimeisin-paivitys<! db (harja.pvm/nyt) "soratieluokat"))
                  (catch Exception e
                    (log/debug "Tieosoiteverkon paikallisessa tuonnissa tapahtui poikkeus: " e)))))))

(defrecord Geometriapaivitykset [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this :tieverkon-hakutehtava (tee-tieverkon-alk-paivitystehtava this asetukset))
    (assoc this :tieverkon-paivitystehtava (tee-tieverkon-paikallinen-paivitystehtava this asetukset))
    (assoc this :pohjavesialueiden-hakutehtava (tee-pohjavesialueiden-alk-paivitystehtava this asetukset))
    (assoc this :pohjavesialueiden-paivitystehtava (tee-pohjavesialueiden-paikallinen-paivitystehtava this asetukset))
    (assoc this :soratien-hoitoluokkien-hakutehtava (tee-soratien-hoitoluokkien-alk-paivitystehtava this asetukset))
    (assoc this :soratien-hoitoluokkien-paivitystehtava (tee-soratien-hoitoluokkien-paikallinen-paivitystehtava this asetukset)))
  (stop [this]
    (apply (:tieverkon-hakutehtava this) [])
    (apply (:soratien-hoitoluokkien-hakutehtava this) [])
    (apply (:pohjavesialueiden-hakutehtava this) [])
    (apply (:pohjavesialueiden-paivitystehtava this) [])
    (apply (:tieverkon-paivitystehtava this) [])
    (apply (:soratien-hoitoluokkien-paivitystehtava this) [])
    this))
