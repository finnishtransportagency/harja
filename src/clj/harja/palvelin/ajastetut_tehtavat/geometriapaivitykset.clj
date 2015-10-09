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
            [harja.palvelin.tyokalut.kansio :as kansio]
            [harja.palvelin.tyokalut.arkisto :as arkisto]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko :as tieverkon-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.soratien-hoitoluokat :as soratien-hoitoluokkien-tuonti])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.util UUID)))

(defn aja-paivitys [alk db geometria-aineisto kohdepolku kohdetiedoston-polku tiedostourl tiedoston-muutospvm paivitys]
  (log/debug "Geometria-aineisto: " geometria-aineisto " on muuttunut ja tarvitaan päivittää")
  (kansio/poista-tiedostot kohdepolku)
  (alk/hae-tiedosto alk (str geometria-aineisto "-haku") tiedostourl kohdetiedoston-polku)
  (arkisto/pura-paketti kohdetiedoston-polku)
  (paivitys)
  (geometriapaivitykset/paivita-viimeisin-paivitys<! db tiedoston-muutospvm geometria-aineisto)
  (log/debug "Geometriapäivitys: " geometria-aineisto " onnistui"))

(defn onko-kohdetiedosto-ok? [kohdepolku kohdetiedoston-nimi]
  (and
    (not (empty kohdepolku))
    (not (empty kohdetiedoston-nimi))
    (.isDirectory (clojure.java.io/file kohdepolku))))

(defn pitaako-paivittaa? [db geometria-aineisto tiedoston-muutospvm]
  (let [paivityksen-tiedot (first (geometriapaivitykset/hae-paivitys db geometria-aineisto))
        viimeisin-paivitys (:viimeisin_paivitys paivityksen-tiedot)
        lukko (str (UUID/randomUUID))]
    (and
      (or (not viimeisin-paivitys)
          (pvm/jalkeen?
            (time-coerce/from-sql-time tiedoston-muutospvm)
            (time-coerce/from-sql-time viimeisin-paivitys)))
      (= 1 (geometriapaivitykset/lukitse-paivitys! db lukko geometria-aineisto)))))

(defn tarkista-paivitys [alk db geometria-aineisto tiedostourl kohdepolku kohdetiedoston-nimi paivitys]
  (log/debug "Tarkistetaan onko geometria-aineisto: " geometria-aineisto " päivittynyt")
  (let [kohdetiedoston-polku (str kohdepolku kohdetiedoston-nimi)]
    ;; todo: tarvii todennäköisesti tehdä tarkempi tarkastus kohdetiedostolle
    (when (and (not-empty tiedostourl) (onko-kohdetiedosto-ok? kohdepolku kohdetiedoston-nimi))
      (do
        (try+
          (let [tiedoston-muutospvm (alk/hae-tiedoston-muutospaivamaara alk (str geometria-aineisto "-muutospaivamaaran-haku") tiedostourl)]
            (if (pitaako-paivittaa? db geometria-aineisto tiedoston-muutospvm)
              (aja-paivitys alk db geometria-aineisto kohdepolku kohdetiedoston-polku tiedostourl tiedoston-muutospvm paivitys)
              (log/debug "Geometria-aineisto: " geometria-aineisto ", ei ole päivittynyt viimeisimmän haun jälkeen. Päivitystä ei tehdä.")))
          (catch Exception e
            (log/error "Geometria-aineiston päivityksessä: " geometria-aineisto ", tapahtui poikkeus: " e)))
        (geometriapaivitykset/avaa-paivityksen-lukko! db geometria-aineisto)))))

(defn ajasta-paivitys [this geometria-aineisto tuontivali osoite kohdepolku kohdetiedosto paivitys]
  (log/debug " Ajastetaan geometria-aineiston " geometria-aineisto " päivitys ajettavaksi " tuontivali "minuutin välein ")
  (chime-at (periodic-seq (time/now) (-> tuontivali time/minutes))
            (fn [_]
              (tarkista-paivitys (:alk this) (:db this) geometria-aineisto osoite kohdepolku kohdetiedosto paivitys))))

(defn tee-tieverkon-hakutehtava [this {:keys [tuontivali
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
                     "Tieosoiteverkko.zip"
                     (fn [] (tieverkon-tuonti/vie-tieverkko-kantaan (:db this) tieosoiteverkon-shapefile)))))

(defn tee-tieverkon-paivitystehtava [this {:keys [tieosoiteverkon-alk-osoite
                                                  tieosoiteverkon-alk-tuontikohde
                                                  tieosoiteverkon-shapefile
                                                  tuontivali]}]
  (when (not (and tieosoiteverkon-alk-osoite tieosoiteverkon-alk-tuontikohde))
    (chime-at
      (periodic-seq (time/now) (-> tuontivali time/minutes))
      (fn [_]
        (try
          (tieverkon-tuonti/vie-tieverkko-kantaan (:db this) tieosoiteverkon-shapefile)
          (catch Exception e
            (log/debug "Tieosoiteverkon tuonnissa tapahtui poikkeus: " e)))))))

(defn tee-soratien-hoitoluokkien-hakutehtava [this {:keys [tuontivali
                                                           soratien-hoitoluokkien-alk-osoite
                                                           soratien-hoitoluokkien-alk-tuontikohde
                                                           soratien-hoitoluokkien-shapefile]}]
  (when (and tuontivali
             soratien-hoitoluokkien-alk-osoite
             soratien-hoitoluokkien-alk-tuontikohde
             soratien-hoitoluokkien-shapefile)
    (ajasta-paivitys this
                     "soratiehoitoluokat"
                     tuontivali
                     soratien-hoitoluokkien-alk-osoite
                     soratien-hoitoluokkien-alk-tuontikohde
                     "Hoitoluokat.tgz"
                     (fn [] (soratien-hoitoluokkien-tuonti/vie-hoitoluokat-kantaan (:db this) soratien-hoitoluokkien-shapefile)))))

(defn tee-soratien-hoitoluokkien-paivitystehtava [this {:keys [tuontivali
                                                               soratien-hoitoluokkien-alk-osoite
                                                               soratien-hoitoluokkien-alk-tuontikohde
                                                               soratien-hoitoluokkien-shapefile]}]
  (when (not (and soratien-hoitoluokkien-alk-osoite soratien-hoitoluokkien-alk-tuontikohde))
    (chime-at (periodic-seq (time/now) (-> tuontivali time/minutes))
              (fn [_]
                (try
                  (soratien-hoitoluokkien-tuonti/vie-hoitoluokat-kantaan (:db this) soratien-hoitoluokkien-shapefile)
                  (catch Exception e
                    (log/debug "Tieosoiteverkon tuonnissa tapahtui poikkeus: " e)))))))

(defrecord Geometriapaivitykset [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this :tieverkon-hakutehtava (tee-tieverkon-hakutehtava this asetukset))
    (assoc this :tieverkon-paivitystehtava (tee-tieverkon-paivitystehtava this asetukset))
    (assoc this :soratien-hoitoluokkien-hakutehtava (tee-soratien-hoitoluokkien-hakutehtava this asetukset))
    (assoc this :soratien-hoitoluokkien-paivitystehtava (tee-soratien-hoitoluokkien-paivitystehtava this asetukset)))
  (stop [this]
    (:tieverkon-hakutehtava this)
    (:soratien-hoitoluokkien-hakutehtava this)
    this))