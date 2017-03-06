(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset
  (:require [taoensso.timbre :as log]
            [chime :refer [chime-ch]]
            [chime :refer [chime-at]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [clojure.java.io :as io]
            [harja.kyselyt.geometriapaivitykset :as geometriapaivitykset]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.alk :as alk]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko :as tieverkon-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat :as siltojen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialueet :as pohjavesialueen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.soratien-hoitoluokat :as soratien-hoitoluokkien-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.talvihoidon-hoitoluokat :as talvihoidon-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.alueurakat :as urakoiden-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.elyt :as elyjen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.valaistusurakat :as valaistusurakoiden-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.paallystyspalvelusopimukset :as paallystyspalvelusopimusten-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tekniset-laitteet-urakat :as tekniset-laitteet-urakat-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.siltapalvelusopimukset :as siltapalvelusopimukset])
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
      (log/debug (format "Tarvitaanko paikallinen paivitys aineistolle: %s" paivitystunnus))
      (if (and
            (not (nil? tiedosto))
            (.exists tiedosto)
            (geometriapaivitykset/pitaako-paivittaa? db paivitystunnus tiedoston-muutospvm))
        (do
          (log/debug (format "Tarvitaan ajaa paikallinen geometriapäivitys: %s." paivitystunnus))
          true)
        (do
          (log/debug (format "Ei tarvita paikallista päivitystä aineistolle: %s" paivitystunnus))
          false)))
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
        (log/debug "Käynnistetään paikallinen paivitystehtava tiedostosta:" shapefile)
        (chime-at
          (periodic-seq (tee-alkuajastus) (-> tuontivali time/minutes))
          (fn [_]
            (try
              (when (tarvitaanko-paikallinen-paivitys? db paivitystunnus shapefile)
                (log/debug (format "Ajetaan paikallinen päivitys geometria-aineistolle: %s" paivitystunnus))
                (paivitys db shapefile)
                (geometriapaivitykset/paivita-viimeisin-paivitys db paivitystunnus (harja.pvm/nyt)))
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
    pohjavesialueen-tuonti/vie-pohjavesialueet-kantaan))

(def tee-pohjavesialueiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "pohjavesialueet"
    :pohjavesialueen-alk-osoite
    :pohjavesialueen-alk-tuontikohde
    :pohjavesialueen-shapefile
    pohjavesialueen-tuonti/vie-pohjavesialueet-kantaan))

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

(def tee-urakoiden-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "urakat"
    :urakoiden-alk-osoite
    :urakoiden-alk-tuontikohde
    :urakoiden-shapefile
    urakoiden-tuonti/vie-urakat-kantaan))

(def tee-urakoiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "urakat"
    :urakoiden-alk-osoite
    :urakoiden-alk-tuontikohde
    :urakoiden-shapefile
    urakoiden-tuonti/vie-urakat-kantaan))

(def tee-elyjen-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "ely-alueet"
    :ely-alueiden-alk-osoite
    :ely-alueiden-alk-tuontikohde
    :ely-alueiden-shapefile
    elyjen-tuonti/vie-elyt-kantaan))

(def tee-elyjen-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "ely-alueet"
    :ely-alueiden-alk-osoite
    :ely-alueiden-alk-tuontikohde
    :ely-alueiden-shapefile
    elyjen-tuonti/vie-elyt-kantaan))

(def tee-valaistusurakoiden-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "valaistusurakat"
    :valaistusurakoiden-alk-osoite
    :valaistusurakoiden-alk-tuontikohde
    :valaistusurakoiden-shapefile
    valaistusurakoiden-tuonti/vie-urakat-kantaan))

(def tee-valaistusurakoiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "valaistusurakat"
    :valaistusurakoiden-alk-osoite
    :valaistusurakoiden-alk-tuontikohde
    :valaistusurakoiden-shapefile
    valaistusurakoiden-tuonti/vie-urakat-kantaan))

(def tee-paallystyspalvelusopimusten-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "paallystyspalvelusopimukset"
    :paallystyspalvelusopimusten-alk-osoite
    :paallystyspalvelusopimusten-alk-tuontikohde
    :paallystyspalvelusopimusten-shapefile
    paallystyspalvelusopimusten-tuonti/vie-urakat-kantaan))

(def tee-paallystyspalvelusopimusten-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "paallystyspalvelusopimukset"
    :paallystyspalvelusopimusten-alk-osoite
    :paallystyspalvelusopimusten-alk-tuontikohde
    :paallystyspalvelusopimusten-shapefile
    paallystyspalvelusopimusten-tuonti/vie-urakat-kantaan))

(def tee-tekniset-laitteet-urakoiden-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "tekniset-laitteet-urakat"
    :tekniset-laitteet-urakat-alk-osoite
    :tekniset-laitteet-urakat-alk-tuontikohde
    :tekniset-laitteet-urakat-shapefile
    tekniset-laitteet-urakat-tuonti/vie-tekniset-laitteet-urakat-kantaan))

(def tee-tekniset-laitteet-urakoiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "tekniset-laitteet-urakat"
    :tekniset-laitteet-urakat-alk-osoite
    :tekniset-laitteet-urakat-alk-tuontikohde
    :tekniset-laitteet-urakat-shapefile
    tekniset-laitteet-urakat-tuonti/vie-tekniset-laitteet-urakat-kantaan))

(def tee-siltojen-palvelusopimusten-alk-paivitystehtava
  (maarittele-alk-paivitystehtava
    "siltojen-palvelusopimukset"
    :siltojenpalvelusopimusten-alk-osoite
    :siltojenpalvelusopimusten-alk-tuontikohde
    :siltojenpalvelusopimusten-shapefile
    siltapalvelusopimukset/vie-siltojen-palvelusopimukset-kantaan))

(def tee-siltojen-palvelusopimusten-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "siltojen-palvelusopimukset"
    :siltojenpalvelusopimusten-alk-osoite
    :siltojenpalvelusopimusten-alk-tuontikohde
    :siltojenpalvelusopimusten-shapefile
    siltapalvelusopimukset/vie-siltojen-palvelusopimukset-kantaan))

(defrecord Geometriapaivitykset [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this
      :tieverkon-hakutehtava (tee-tieverkon-alk-paivitystehtava this asetukset)
      :tieverkon-paivitystehtava (tee-tieverkon-paikallinen-paivitystehtava this asetukset)
      :pohjavesialueiden-hakutehtava (tee-pohjavesialueiden-alk-paivitystehtava this asetukset)
      :pohjavesialueiden-paivitystehtava (tee-pohjavesialueiden-paikallinen-paivitystehtava this asetukset)
      :talvihoidon-hoitoluokkien-hakutehtava (tee-talvihoidon-hoitoluokkien-alk-paivitystehtava this asetukset)
      :talvihoidon-hoitoluokkien-paivitystehtava (tee-talvihoidon-hoitoluokkien-paikallinen-paivitystehtava this asetukset)
      :soratien-hoitoluokkien-hakutehtava (tee-soratien-hoitoluokkien-alk-paivitystehtava this asetukset)
      :soratien-hoitoluokkien-paivitystehtava (tee-soratien-hoitoluokkien-paikallinen-paivitystehtava this asetukset)
      :siltojen-hakutehtava (tee-siltojen-alk-paivitystehtava this asetukset)
      :siltojen-paivitystehtava (tee-siltojen-paikallinen-paivitystehtava this asetukset)
      :urakoiden-hakutehtava (tee-urakoiden-alk-paivitystehtava this asetukset)
      :urakoiden-paivitystehtava (tee-urakoiden-paikallinen-paivitystehtava this asetukset)
      :elyjen-hakutehtava (tee-elyjen-alk-paivitystehtava this asetukset)
      :elyjen-paivitystehtava (tee-elyjen-paikallinen-paivitystehtava this asetukset)
      :valaistusurakoiden-hakutehtava (tee-valaistusurakoiden-alk-paivitystehtava this asetukset)
      :valaistusurakoiden-paivitystehtava (tee-valaistusurakoiden-paikallinen-paivitystehtava this asetukset)
      :paallystyspalvelusopimusten-hakutehtava (tee-paallystyspalvelusopimusten-alk-paivitystehtava this asetukset)
      :paallystyspalvelusopimusten-paivitystehtava (tee-paallystyspalvelusopimusten-paikallinen-paivitystehtava this asetukset)
      :tekniset-laitteet-urakoiden-hakutehtava (tee-tekniset-laitteet-urakoiden-alk-paivitystehtava this asetukset)
      :tekniset-laitteet-urakoiden-paivitystehtava (tee-tekniset-laitteet-urakoiden-paikallinen-paivitystehtava this asetukset)
      :siltojen-palvelusopimusten-hakutehtava (tee-siltojen-palvelusopimusten-alk-paivitystehtava this asetukset)
      :siltojen-palvelusopimusten-paivitystehtava (tee-siltojen-palvelusopimusten-paikallinen-paivitystehtava this asetukset)))

  (stop [this]
    (doseq [tehtava [:tieverkon-hakutehtava
                     :tieverkon-paivitystehtava
                     :pohjavesialueiden-hakutehtava
                     :pohjavesialueiden-paivitystehtava
                     :talvihoidon-hoitoluokkien-hakutehtava
                     :talvihoidon-hoitoluokkien-paivitystehtava
                     :soratien-hoitoluokkien-hakutehtava
                     :soratien-hoitoluokkien-paivitystehtava
                     :siltojen-hakutehtava
                     :siltojen-paivitystehtava
                     :urakoiden-hakutehtava
                     :urakoiden-paivitystehtava
                     :elyjen-hakutehtava
                     :elyjen-paivitystehtava]
            :let [lopeta-fn (get this tehtava)]]
      (when lopeta-fn (lopeta-fn)))
    this))
