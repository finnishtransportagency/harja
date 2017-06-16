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
            [harja.palvelin.integraatiot.paikkatietojarjestelma.inspire :as inspire]
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
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.siltapalvelusopimukset :as siltapalvelusopimukset]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.net URI)))

(defn ajasta-paivitys [this paivitystunnus ajopaiva ajoaika osoite kohdetiedoston-polku paivitys]
  (log/debug (format "Ajastetaan geometria-aineiston %s päivitys ajettavaksi viikonpäivänä %s kellonaikaan %s."
                     paivitystunnus
                     ajopaiva
                     ajoaika))
  (ajastettu-tehtava/ajasta-viikonpaivana
    ajopaiva
    ajoaika
    (fn [_]
      (inspire/kaynnista-paivitys (:integraatioloki this) (:db this) paivitystunnus osoite kohdetiedoston-polku paivitys))))

(defn tarvitaanko-paikallinen-paivitys? [db paivitystunnus tiedostourl]
  (try
    (let [polku (if (not tiedostourl) nil (.substring (.getSchemeSpecificPart (URI. tiedostourl)) 2))
          tiedosto (if (not polku) nil (io/file polku))]
      (log/debug (format "Tarvitaanko paikallinen paivitys aineistolle: %s" paivitystunnus))
      (if (and
            (not (nil? tiedosto))
            (.exists tiedosto))
        (do
          (log/debug (format "Tarvitaan ajaa paikallinen geometriapäivitys: %s." paivitystunnus))
          true)
        (do
          (log/debug (format "Ei tarvita paikallista päivitystä aineistolle: %s" paivitystunnus))
          false)))
    (catch Exception e
      (log/warn e (format "Tarkistettaessa paikallista ajoa geometriapäivitykselle: %s tapahtui poikkeus." paivitystunnus))
      false)))

(defn maarittele-inspire-paivitystehtava [paivitystunnus url-avain tallennuspolku-avain shapefile-avain paivitys]
  (fn [this {:keys [tuontiaika] :as asetukset}]
    (let [url (get asetukset url-avain)
          tallennuspolku (get asetukset tallennuspolku-avain)
          shapefile (get asetukset shapefile-avain)
          {:keys [paiva aika]} tuontiaika]
      (when (and paiva
                 aika
                 url
                 tallennuspolku
                 shapefile)
        (ajasta-paivitys this
                         paivitystunnus
                         paiva
                         aika
                         url
                         tallennuspolku
                         (fn [] (paivitys (:db this) shapefile)))))))

(defn maarittele-paikallinen-paivitystehtava [paivitystunnus url-avain tallennuspolku-avain shapefile-avain paivitys]
  (fn [this {:keys [tuontiaika] :as asetukset}]
    (let [url (get asetukset url-avain)
          tallennuspolku (get asetukset tallennuspolku-avain)
          shapefile (get asetukset shapefile-avain)
          db (:db this)
          {:keys [paiva aika]} tuontiaika]
      (log/debug "Paikallinen päivitystehtävä: " paivitystunnus url-avain tallennuspolku-avain shapefile-avain paivitys)
      (when (and (not url) (not tallennuspolku))
        (log/debug "Käynnistetään paikallinen paivitystehtava tiedostosta:" shapefile)
        (ajastettu-tehtava/ajasta-viikonpaivana
          paiva
          aika
          (fn [_]
            (try
              (when (tarvitaanko-paikallinen-paivitys? db paivitystunnus shapefile)
                (log/debug (format "Ajetaan paikallinen päivitys geometria-aineistolle: %s" paivitystunnus))
                (paivitys db shapefile)
                (geometriapaivitykset/paivita-viimeisin-paivitys db paivitystunnus (harja.pvm/nyt)))
              (catch Exception e
                (log/debug e (format "Paikallisessa geometriapäivityksessä %s tapahtui poikkeus." paivitystunnus))))))))))

(def tee-tieverkon-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "tieverkko"
    :tieosoiteverkon-url
    :tieosoiteverkon-tallennuspolku
    :tieosoiteverkon-shapefile
    tieverkon-tuonti/vie-tieverkko-kantaan))

(def tee-tieverkon-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "tieverkko"
    :tieosoiteverkon-url
    :tieosoiteverkon-tallennuspolku
    :tieosoiteverkon-shapefile
    tieverkon-tuonti/vie-tieverkko-kantaan))

(def tee-pohjavesialueiden-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "pohjavesialueet"
    :pohjavesialueen-url
    :pohjavesialueen-tallennuspolku
    :pohjavesialueen-shapefile
    pohjavesialueen-tuonti/vie-pohjavesialueet-kantaan))

(def tee-pohjavesialueiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "pohjavesialueet"
    :pohjavesialueen-url
    :pohjavesialueen-tallennuspolku
    :pohjavesialueen-shapefile
    pohjavesialueen-tuonti/vie-pohjavesialueet-kantaan))

(def tee-siltojen-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "sillat"
    :siltojen-url
    :siltojen-tallennuspolku
    :siltojen-shapefile
    siltojen-tuonti/vie-sillat-kantaan))

(def tee-siltojen-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "sillat"
    :siltojen-url
    :siltojen-tallennuspolku
    :siltojen-shapefile
    siltojen-tuonti/vie-sillat-kantaan))

(def tee-talvihoidon-hoitoluokkien-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "talvihoitoluokat"
    :talvihoidon-hoitoluokkien-url
    :talvihoidon-hoitoluokkien-tallennuspolku
    :talvihoidon-hoitoluokkien-shapefile
    talvihoidon-tuonti/vie-hoitoluokat-kantaan))

(def tee-talvihoidon-hoitoluokkien-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "talvihoitoluokat"
    :talvihoidon-hoitoluokkien-url
    :talvihoidon-hoitoluokkien-tallennuspolku
    :talvihoidon-hoitoluokkien-shapefile
    talvihoidon-tuonti/vie-hoitoluokat-kantaan))

(def tee-soratien-hoitoluokkien-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "soratieluokat"
    :soratien-hoitoluokkien-url
    :soratien-hoitoluokkien-tallennuspolku
    :soratien-hoitoluokkien-shapefile
    soratien-hoitoluokkien-tuonti/vie-hoitoluokat-kantaan))

(def tee-soratien-hoitoluokkien-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "soratieluokat"
    :soratien-hoitoluokkien-url
    :soratien-hoitoluokkien-tallennuspolku
    :soratien-hoitoluokkien-shapefile
    soratien-hoitoluokkien-tuonti/vie-hoitoluokat-kantaan))

(def tee-urakoiden-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "urakat"
    :urakoiden-url
    :urakoiden-tallennuspolku
    :urakoiden-shapefile
    urakoiden-tuonti/vie-urakat-kantaan))

(def tee-urakoiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "urakat"
    :urakoiden-url
    :urakoiden-tallennuspolku
    :urakoiden-shapefile
    urakoiden-tuonti/vie-urakat-kantaan))

(def tee-elyjen-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "ely-alueet"
    :ely-alueiden-url
    :ely-alueiden-tallennuspolku
    :ely-alueiden-shapefile
    elyjen-tuonti/vie-elyt-kantaan))

(def tee-elyjen-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "ely-alueet"
    :ely-alueiden-url
    :ely-alueiden-tallennuspolku
    :ely-alueiden-shapefile
    elyjen-tuonti/vie-elyt-kantaan))

(def tee-valaistusurakoiden-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "valaistusurakat"
    :valaistusurakoiden-url
    :valaistusurakoiden-tallennuspolku
    :valaistusurakoiden-shapefile
    valaistusurakoiden-tuonti/vie-urakat-kantaan))

(def tee-valaistusurakoiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "valaistusurakat"
    :valaistusurakoiden-url
    :valaistusurakoiden-tallennuspolku
    :valaistusurakoiden-shapefile
    valaistusurakoiden-tuonti/vie-urakat-kantaan))

(def tee-paallystyspalvelusopimusten-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "paallystyspalvelusopimukset"
    :paallystyspalvelusopimusten-url
    :paallystyspalvelusopimusten-tallennuspolku
    :paallystyspalvelusopimusten-shapefile
    paallystyspalvelusopimusten-tuonti/vie-urakat-kantaan))

(def tee-paallystyspalvelusopimusten-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "paallystyspalvelusopimukset"
    :paallystyspalvelusopimusten-url
    :paallystyspalvelusopimusten-tallennuspolku
    :paallystyspalvelusopimusten-shapefile
    paallystyspalvelusopimusten-tuonti/vie-urakat-kantaan))

(def tee-tekniset-laitteet-urakoiden-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "tekniset-laitteet-urakat"
    :tekniset-laitteet-urakat-url
    :tekniset-laitteet-urakat-tallennuspolku
    :tekniset-laitteet-urakat-shapefile
    tekniset-laitteet-urakat-tuonti/vie-tekniset-laitteet-urakat-kantaan))

(def tee-tekniset-laitteet-urakoiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "tekniset-laitteet-urakat"
    :tekniset-laitteet-urakat-url
    :tekniset-laitteet-urakat-tallennuspolku
    :tekniset-laitteet-urakat-shapefile
    tekniset-laitteet-urakat-tuonti/vie-tekniset-laitteet-urakat-kantaan))

(def tee-siltojen-palvelusopimusten-inspire-paivitystehtava
  (maarittele-inspire-paivitystehtava
    "siltojen-palvelusopimukset"
    :siltojenpalvelusopimusten-url
    :siltojenpalvelusopimusten-tallennuspolku
    :siltojenpalvelusopimusten-shapefile
    siltapalvelusopimukset/vie-siltojen-palvelusopimukset-kantaan))

(def tee-siltojen-palvelusopimusten-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "siltojen-palvelusopimukset"
    :siltojenpalvelusopimusten-url
    :siltojenpalvelusopimusten-tallennuspolku
    :siltojenpalvelusopimusten-shapefile
    siltapalvelusopimukset/vie-siltojen-palvelusopimukset-kantaan))

(defrecord Geometriapaivitykset [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this
      :tieverkon-hakutehtava (tee-tieverkon-inspire-paivitystehtava this asetukset)
      :tieverkon-paivitystehtava (tee-tieverkon-paikallinen-paivitystehtava this asetukset)
      :pohjavesialueiden-hakutehtava (tee-pohjavesialueiden-inspire-paivitystehtava this asetukset)
      :pohjavesialueiden-paivitystehtava (tee-pohjavesialueiden-paikallinen-paivitystehtava this asetukset)
      :talvihoidon-hoitoluokkien-hakutehtava (tee-talvihoidon-hoitoluokkien-inspire-paivitystehtava this asetukset)
      :talvihoidon-hoitoluokkien-paivitystehtava (tee-talvihoidon-hoitoluokkien-paikallinen-paivitystehtava this asetukset)
      :soratien-hoitoluokkien-hakutehtava (tee-soratien-hoitoluokkien-inspire-paivitystehtava this asetukset)
      :soratien-hoitoluokkien-paivitystehtava (tee-soratien-hoitoluokkien-paikallinen-paivitystehtava this asetukset)
      :siltojen-hakutehtava (tee-siltojen-inspire-paivitystehtava this asetukset)
      :siltojen-paivitystehtava (tee-siltojen-paikallinen-paivitystehtava this asetukset)
      :urakoiden-hakutehtava (tee-urakoiden-inspire-paivitystehtava this asetukset)
      :urakoiden-paivitystehtava (tee-urakoiden-paikallinen-paivitystehtava this asetukset)
      :elyjen-hakutehtava (tee-elyjen-inspire-paivitystehtava this asetukset)
      :elyjen-paivitystehtava (tee-elyjen-paikallinen-paivitystehtava this asetukset)
      :valaistusurakoiden-hakutehtava (tee-valaistusurakoiden-inspire-paivitystehtava this asetukset)
      :valaistusurakoiden-paivitystehtava (tee-valaistusurakoiden-paikallinen-paivitystehtava this asetukset)
      :paallystyspalvelusopimusten-hakutehtava (tee-paallystyspalvelusopimusten-inspire-paivitystehtava this asetukset)
      :paallystyspalvelusopimusten-paivitystehtava (tee-paallystyspalvelusopimusten-paikallinen-paivitystehtava this asetukset)
      :tekniset-laitteet-urakoiden-hakutehtava (tee-tekniset-laitteet-urakoiden-inspire-paivitystehtava this asetukset)
      :tekniset-laitteet-urakoiden-paivitystehtava (tee-tekniset-laitteet-urakoiden-paikallinen-paivitystehtava this asetukset)
      :siltojen-palvelusopimusten-hakutehtava (tee-siltojen-palvelusopimusten-inspire-paivitystehtava this asetukset)
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
