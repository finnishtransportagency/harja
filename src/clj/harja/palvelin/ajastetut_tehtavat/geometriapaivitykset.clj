(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset
  (:require [taoensso.timbre :as log]
            [chime :refer [chime-ch]]
            [chime :refer [chime-at]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [clj-time.core :as time]
            [harja.kyselyt.geometriapaivitykset :as geometriapaivitykset]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.alk :as alk]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko :as tieverkon-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat :as siltojen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialueet :as pohjavesialueen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.soratien-hoitoluokat :as soratien-hoitoluokkien-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.talvihoidon-hoitoluokat :as talvihoidon-tuonti]
            [clojure.java.io :as io]
            [clj-time.coerce :as coerce])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.net URI)
           (java.sql Timestamp)))

(defn tee-alkuajastus []
  (time/plus- (time/now) (time/seconds 10)))

(defn ajasta-paivitys [this paivitystunnus tuontivali osoite kohdetiedoston-polku paivitys]
  (log/debug (format "Ajastetaan geometria-aineiston %s päivitys ajettavaksi %s minuutin välein." paivitystunnus tuontivali))
  (chime-at (periodic-seq (tee-alkuajastus) (-> tuontivali time/minutes))
            (fn [_]
              (alk/kaynnista-paivitys (:integraatioloki this) (:db this) paivitystunnus osoite kohdetiedoston-polku paivitys))))

(defn tarvitaanko-paikallinen-paivitys? [db paivitystunnus tiedostourl]
  (try
    (let [polku (if (not tiedostourl) nil (.substring (.getSchemeSpecificPart (URI. tiedostourl)) 2))
          tiedosto (if (not polku) nil (io/file polku))
          tiedoston-muutospvm (if (not tiedosto) nil (coerce/to-sql-time (Timestamp. (.lastModified tiedosto))))]
      (if (and
            (not (nil? tiedosto))
            (.exists tiedosto)
            (geometriapaivitykset/pitaako-paivittaa? db paivitystunnus tiedoston-muutospvm))
        (do
          (log/debug (format "Tarvitaan ajaa paikallinen geometriapäivitys: %s." paivitystunnus))
          true)
        false))
    (catch Exception e
      (log/warn e (format "Tarkistettaessa paikallista ajoa geometriapäivitykselle: %s tapahtui poikkeus." paivitystunnus))
      false)))

(defn maarittele-alk-paivitystehtava [paivitystunnus alk-osoite-avain alk-tuontikohde-avain shapefile-avain paivitys]
  (fn [this {:keys [tuontivali] :as asetukset}]
    (let [alk-osoite (get asetukset alk-osoite-avain)
          alk-tuontikohde (get asetukset alk-tuontikohde-avain)
          shapefile (get asetukset shapefile-avain)]
      (when (and tuontivali
                 alk-osoite
                 alk-tuontikohde
                 shapefile)
        (ajasta-paivitys this
                         paivitystunnus
                         tuontivali
                         alk-osoite
                         alk-tuontikohde
                         (fn [] (paivitys (:db this) shapefile)))))))

(defn maarittele-paikallinen-paivitystehtava [paivitystunnus alk-osoite-avain alk-tuontikohde-avain shapefile-avain paivitys]
  (fn [this {:keys [tuontivali] :as asetukset}]
    (let [alk-osoite (get asetukset alk-osoite-avain)
          alk-tuontikohde (get asetukset alk-tuontikohde-avain)
          shapefile (get asetukset shapefile-avain)
          db (:db this)]
      (log/debug "Paikallinen päivitystehtävä: " paivitystunnus alk-osoite-avain alk-tuontikohde-avain shapefile-avain paivitys)
      (when (and (not alk-osoite) (not alk-tuontikohde))
        (chime-at
          (periodic-seq (tee-alkuajastus) (-> tuontivali time/minutes))
          (fn [_]
            (try
              (when (tarvitaanko-paikallinen-paivitys? db paivitystunnus shapefile)
                (log/debug (format "Ajetaan paikallinen päivitys geometria-aineistolle: %s" paivitystunnus))
                (paivitys db shapefile)
                (geometriapaivitykset/paivita-viimeisin-paivitys<! db (harja.pvm/nyt) paivitystunnus))
              (catch Exception e
                (log/debug e (format "Paikallisessa geometriapäivityksessä %s tapahtui poikkeus." paivitystunnus))))))))))

(def tee-tieverkon-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "tieverkko"
    :tieosoiteverkon-alk-osoite
    :tieosoiteverkon-alk-tuontikohde
    :tieosoiteverkon-shapefile
    tieverkon-tuonti/vie-tieverkko-kantaan))

(def tee-tieverkon-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "tieverkko"
    :tieosoiteverkon-alk-osoite
    :tieosoiteverkon-alk-tuontikohde
    :tieosoiteverkon-shapefile
    tieverkon-tuonti/vie-tieverkko-kantaan))

(def tee-pohjavesialueiden-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "pohjavesialueet"
    :pohjavesialueen-alk-osoite
    :pohjavesialueen-alk-tuontikohde
    :pohjavesialueen-shapefile
    pohjavesialueen-tuonti/vie-pohjavesialue-kantaan))

(def tee-pohjavesialueiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "pohjavesialueet"
    :pohjavesialueen-alk-osoite
    :pohjavesialueen-alk-tuontikohde
    :pohjavesialueen-shapefile
    pohjavesialueen-tuonti/vie-pohjavesialue-kantaan))

(def tee-siltojen-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "sillat"
    :siltojen-alk-osoite
    :siltojen-alk-tuontikohde
    :siltojen-shapefile
    siltojen-tuonti/vie-sillat-kantaan))

(def tee-siltojen-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "sillat"
    :siltojen-alk-osoite
    :siltojen-alk-tuontikohde
    :siltojen-shapefile
    siltojen-tuonti/vie-sillat-kantaan))

(def tee-talvihoidon-hoitoluokkien-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "talvihoitoluokat"
    :talvihoidon-hoitoluokkien-alk-osoite
    :talvihoidon-hoitoluokkien-alk-tuontikohde
    :talvihoidon-hoitoluokkien-shapefile
    talvihoidon-tuonti/vie-hoitoluokat-kantaan))

(def tee-talvihoidon-hoitoluokkien-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "talvihoitoluokat"
    :talvihoidon-hoitoluokkien-alk-osoite
    :talvihoidon-hoitoluokkien-alk-tuontikohde
    :talvihoidon-hoitoluokkien-shapefile
    talvihoidon-tuonti/vie-hoitoluokat-kantaan))

(def tee-soratien-hoitoluokkien-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "soratieluokat"
    :soratien-hoitoluokkien-alk-osoite
    :soratien-hoitoluokkien-alk-tuontikohde
    :soratien-hoitoluokkien-shapefile
    soratien-hoitoluokkien-tuonti/vie-hoitoluokat-kantaan))

(def tee-soratien-hoitoluokkien-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "soratieluokat"
    :soratien-hoitoluokkien-alk-osoite
    :soratien-hoitoluokkien-alk-tuontikohde
    :soratien-hoitoluokkien-shapefile
    soratien-hoitoluokkien-tuonti/vie-hoitoluokat-kantaan))

(defrecord Geometriapaivitykset [asetukset]
  component/Lifecycle
  (start [this]
    (-> this
        (assoc :tieverkon-hakutehtava (tee-tieverkon-alk-paivitystehtava this asetukset))
        (assoc :tieverkon-paivitystehtava (tee-tieverkon-paikallinen-paivitystehtava this asetukset))
        (assoc :pohjavesialueiden-hakutehtava (tee-pohjavesialueiden-alk-paivitystehtava this asetukset))
        (assoc :pohjavesialueiden-paivitystehtava (tee-pohjavesialueiden-paikallinen-paivitystehtava this asetukset))
        (assoc :talvihoidon-hoitoluokkien-hakutehtava (tee-talvihoidon-hoitoluokkien-alk-paivitystehtava this asetukset))
        (assoc :talvihoidon-hoitoluokkien-paivitystehtava (tee-talvihoidon-hoitoluokkien-paikallinen-paivitystehtava this asetukset))
        (assoc :soratien-hoitoluokkien-hakutehtava (tee-soratien-hoitoluokkien-alk-paivitystehtava this asetukset))
        (assoc :soratien-hoitoluokkien-paivitystehtava (tee-soratien-hoitoluokkien-paikallinen-paivitystehtava this asetukset))
        (assoc :siltojen-hakutehtava (tee-siltojen-alk-paivitystehtava this asetukset))
        (assoc :siltojen-paivitystehtava (tee-siltojen-paikallinen-paivitystehtava this asetukset))))
  (stop [this]
    ((:tieverkon-hakutehtava this))
    ((:soratien-hoitoluokkien-hakutehtava this))
    ((:pohjavesialueiden-hakutehtava this))
    ((:pohjavesialueiden-paivitystehtava this))
    ((:tieverkon-paivitystehtava this))
    ((:soratien-hoitoluokkien-paivitystehtava this))
    this))
