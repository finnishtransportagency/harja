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
            [harja.palvelin.integraatiot.paikkatietojarjestelma.ava :as ava]
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
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet :as turvalaitteet]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.kanavasulut :as kanavasulut]
            [harja.kyselyt.geometriaaineistot :as geometria-aineistot]
            [harja.domain.geometriaaineistot :as ga])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.net URI)
           (java.sql Timestamp)))

(def virhekasittely
  {:error-handler #(log/error "Käsittelemätön poikkeus ajastetussa tehtävässä:" %)})

(defn tee-alkuajastus []
  (time/plus- (time/now) (time/seconds 10)))

(defn ajasta-paivitys [this paivitystunnus tuontivali osoite kohdetiedoston-polku paivitys kayttajatunnus salasana]
  (log/debug (format "Ajastetaan geometria-aineiston %s päivitys ajettavaksi %s minuutin välein." paivitystunnus tuontivali))
  (chime-at (periodic-seq (tee-alkuajastus) (-> tuontivali time/minutes))
            (fn [_]
              (ava/kaynnista-paivitys (:integraatioloki this)
                                      (:db this)
                                      paivitystunnus
                                      osoite
                                      kohdetiedoston-polku
                                      paivitys
                                      kayttajatunnus
                                      salasana))
            virhekasittely))

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
        (do
          ;; (log/debug (format "Ei tarvita paikallista päivitystä aineistolle: %s" paivitystunnus))
          false)))
    (catch Exception e
      (log/warn e (format "Tarkistettaessa paikallista ajoa geometriapäivitykselle: %s tapahtui poikkeus." paivitystunnus))
      false)))

(defn rakenna-osoite [db aineiston-nimi osoite]
  (let [aineisto (geometria-aineistot/hae-voimassaoleva-geometria-aineisto db aineiston-nimi)]
    (if (and osoite (.contains osoite "[AINEISTO]") aineisto)
      (.replace osoite "[AINEISTO]" (::ga/tiedostonimi aineisto))
      osoite)))

(defn maarittele-paivitystehtava [paivitystunnus
                                  url-avain
                                  tuontikohdepolku-avain
                                  shapefile-avain
                                  paivitys]
  (fn [this {:keys [tuontivali] :as asetukset}]
    (let [db (:db this)
          url (rakenna-osoite db paivitystunnus (get asetukset url-avain))
          tuontikohdepolku (rakenna-osoite db paivitystunnus (get asetukset tuontikohdepolku-avain))
          shapefile (rakenna-osoite db paivitystunnus (get asetukset shapefile-avain))
          kayttajatunnus (:kayttajatunnus asetukset)
          salasana (:salasana asetukset)]
      (when (and tuontivali
                 url
                 tuontikohdepolku
                 shapefile)
        (ajasta-paivitys this
                         paivitystunnus
                         tuontivali
                         url
                         tuontikohdepolku
                         (fn [] (paivitys (:db this) shapefile))
                         kayttajatunnus
                         salasana)))))

(defn maarittele-paikallinen-paivitystehtava [paivitystunnus url-avain tuontikohdepolku-avain shapefile-avain paivitys]
  (fn [this {:keys [tuontivali] :as asetukset}]
    (let [db (:db this)
          url (rakenna-osoite db paivitystunnus (get asetukset url-avain))
          tuontikohdepolku (rakenna-osoite db paivitystunnus (get asetukset tuontikohdepolku-avain))
          shapefile (rakenna-osoite db paivitystunnus (get asetukset shapefile-avain))
          db (:db this)]
      (log/debug "Paikallinen päivitystehtävä: " paivitystunnus url-avain tuontikohdepolku-avain shapefile-avain paivitys)
      (when (and (not url) (not tuontikohdepolku))
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

;; käyttö replissä esim :
#_(let [db (:db harja.palvelin.main/harja-jarjestelma)
        shapefile "file://shp/Sillat/PTV.tl261_H.shp"]
    (siltojen-tuonti/vie-sillat-kantaan db shapefile))



(def tee-tieverkon-paivitystehtava
  (maarittele-paivitystehtava
    "tieverkko"
    :tieosoiteverkon-osoite
    :tieosoiteverkon-tuontikohde
    :tieosoiteverkon-shapefile
    tieverkon-tuonti/vie-tieverkko-kantaan))

(def tee-tieverkon-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "tieverkko"
    :tieosoiteverkon-osoite
    :tieosoiteverkon-tuontikohde
    :tieosoiteverkon-shapefile
    tieverkon-tuonti/vie-tieverkko-kantaan))

(def tee-laajennetun-tieverkon-paivitystehtava
  (fn [this asetukset]
    (clojure.core.async/thread
      (tieverkon-tuonti/vie-laajennettu-tieverkko-kantaan (:db this) (:laajennetun-tieosoiteverkon-tiedot asetukset))))
  #_(maarittele-paivitystehtava
    "laajennettu-tieverkko"
    :laajennetun-tieosoiteverkon-osoite
    :laajennetun-tieosoiteverkon-tuontikohde
    :laajennetun-tieosoiteverkon-shapefile
    tieverkon-tuonti/vie-laajennettu-tieverkko-kantaan))

(def tee-laajennetun-tieverkon-paikallinen-paivitystehtava
  #_(maarittele-paikallinen-paivitystehtava
    "laajennettu-tieverkko"
    :laajennetun-tieosoiteverkon-osoite
    :laajennetun-tieosoiteverkon-tuontikohde
    :laajennetun-tieosoiteverkon-shapefile
    tieverkon-tuonti/vie-laajennettu-tieverkko-kantaan))

(def tee-pohjavesialueiden-paivitystehtava
  (maarittele-paivitystehtava
    "pohjavesialueet"
    :pohjavesialueen-osoite
    :pohjavesialueen-tuontikohde
    :pohjavesialueen-shapefile
    pohjavesialueen-tuonti/vie-pohjavesialueet-kantaan))

(def tee-pohjavesialueiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "pohjavesialueet"
    :pohjavesialueen-osoite
    :pohjavesialueen-tuontikohde
    :pohjavesialueen-shapefile
    pohjavesialueen-tuonti/vie-pohjavesialueet-kantaan))

(def tee-siltojen-paivitystehtava
  (maarittele-paivitystehtava
    "sillat"
    :siltojen-osoite
    :siltojen-tuontikohde
    :siltojen-shapefile
    siltojen-tuonti/vie-sillat-kantaan))

(def tee-siltojen-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "sillat"
    :siltojen-osoite
    :siltojen-tuontikohde
    :siltojen-shapefile
    siltojen-tuonti/vie-sillat-kantaan))

(def tee-talvihoidon-hoitoluokkien-paivitystehtava
  (maarittele-paivitystehtava
    "talvihoitoluokat"
    :talvihoidon-hoitoluokkien-osoite
    :talvihoidon-hoitoluokkien-tuontikohde
    :talvihoidon-hoitoluokkien-shapefile
    talvihoidon-tuonti/vie-hoitoluokat-kantaan))

(def tee-talvihoidon-hoitoluokkien-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "talvihoitoluokat"
    :talvihoidon-hoitoluokkien-osoite
    :talvihoidon-hoitoluokkien-tuontikohde
    :talvihoidon-hoitoluokkien-shapefile
    talvihoidon-tuonti/vie-hoitoluokat-kantaan))

(def tee-soratien-hoitoluokkien-paivitystehtava
  (maarittele-paivitystehtava
    "soratieluokat"
    :soratien-hoitoluokkien-osoite
    :soratien-hoitoluokkien-tuontikohde
    :soratien-hoitoluokkien-shapefile
    soratien-hoitoluokkien-tuonti/vie-hoitoluokat-kantaan))

(def tee-soratien-hoitoluokkien-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "soratieluokat"
    :soratien-hoitoluokkien-osoite
    :soratien-hoitoluokkien-tuontikohde
    :soratien-hoitoluokkien-shapefile
    soratien-hoitoluokkien-tuonti/vie-hoitoluokat-kantaan))

(def tee-urakoiden-paivitystehtava
  (maarittele-paivitystehtava
    "urakat"
    :urakoiden-osoite
    :urakoiden-tuontikohde
    :urakoiden-shapefile
    urakoiden-tuonti/vie-urakat-kantaan))

(def tee-urakoiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "urakat"
    :urakoiden-osoite
    :urakoiden-tuontikohde
    :urakoiden-shapefile
    urakoiden-tuonti/vie-urakat-kantaan))

(def tee-elyjen-paivitystehtava
  (maarittele-paivitystehtava
    "ely-alueet"
    :ely-alueiden-osoite
    :ely-alueiden-tuontikohde
    :ely-alueiden-shapefile
    elyjen-tuonti/vie-elyt-kantaan))

(def tee-elyjen-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "ely-alueet"
    :ely-alueiden-osoite
    :ely-alueiden-tuontikohde
    :ely-alueiden-shapefile
    elyjen-tuonti/vie-elyt-kantaan))

(def tee-valaistusurakoiden-paivitystehtava
  (maarittele-paivitystehtava
    "valaistusurakat"
    :valaistusurakoiden-osoite
    :valaistusurakoiden-tuontikohde
    :valaistusurakoiden-shapefile
    valaistusurakoiden-tuonti/vie-urakat-kantaan))

(def tee-valaistusurakoiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "valaistusurakat"
    :valaistusurakoiden-osoite
    :valaistusurakoiden-tuontikohde
    :valaistusurakoiden-shapefile
    valaistusurakoiden-tuonti/vie-urakat-kantaan))

(def tee-paallystyspalvelusopimusten-paivitystehtava
  (maarittele-paivitystehtava
    "paallystyspalvelusopimukset"
    :paallystyspalvelusopimusten-osoite
    :paallystyspalvelusopimusten-tuontikohde
    :paallystyspalvelusopimusten-shapefile
    paallystyspalvelusopimusten-tuonti/vie-urakat-kantaan))

(def tee-paallystyspalvelusopimusten-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "paallystyspalvelusopimukset"
    :paallystyspalvelusopimusten-osoite
    :paallystyspalvelusopimusten-tuontikohde
    :paallystyspalvelusopimusten-shapefile
    paallystyspalvelusopimusten-tuonti/vie-urakat-kantaan))

(def tee-tekniset-laitteet-urakoiden-paivitystehtava
  (maarittele-paivitystehtava
    "tekniset-laitteet-urakat"
    :tekniset-laitteet-urakat-osoite
    :tekniset-laitteet-urakat-tuontikohde
    :tekniset-laitteet-urakat-shapefile
    tekniset-laitteet-urakat-tuonti/vie-tekniset-laitteet-urakat-kantaan))

(def tee-tekniset-laitteet-urakoiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "tekniset-laitteet-urakat"
    :tekniset-laitteet-urakat-osoite
    :tekniset-laitteet-urakat-tuontikohde
    :tekniset-laitteet-urakat-shapefile
    tekniset-laitteet-urakat-tuonti/vie-tekniset-laitteet-urakat-kantaan))

(def tee-siltojen-palvelusopimusten-paivitystehtava
  (maarittele-paivitystehtava
    "siltojen-palvelusopimukset"
    :siltojenpalvelusopimusten-osoite
    :siltojenpalvelusopimusten-tuontikohde
    :siltojenpalvelusopimusten-shapefile
    siltapalvelusopimukset/vie-siltojen-palvelusopimukset-kantaan))

(def tee-siltojen-palvelusopimusten-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "siltojen-palvelusopimukset"
    :siltojenpalvelusopimusten-osoite
    :siltojenpalvelusopimusten-tuontikohde
    :siltojenpalvelusopimusten-shapefile
    siltapalvelusopimukset/vie-siltojen-palvelusopimukset-kantaan))

(def tee-turvalaitteiden-paivitystehtava
  (maarittele-paivitystehtava
    "turvalaitteet"
    :turvalaitteiden-osoite
    :turvalaitteiden-tuontikohde
    :turvalaitteiden-shapefile
    turvalaitteet/vie-turvalaitteet-kantaan))

(def tee-turvalaitteiden-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "turvalaitteet"
    :turvalaitteiden-osoite
    :turvalaitteiden-tuontikohde
    :turvalaitteiden-shapefile
    turvalaitteet/vie-turvalaitteet-kantaan))

(def tee-kanavien-paivitystehtava
  (maarittele-paivitystehtava
    "kanavat"
    :kanavien-osoite
    :kanavien-tuontikohde
    :kanavien-shapefile
    kanavasulut/vie-kanavasulut-kantaan))

(def tee-kanavien-paikallinen-paivitystehtava
  (maarittele-paikallinen-paivitystehtava
    "kanavat"
    :kanavien-osoite
    :kanavien-tuontikohde
    :kanavien-shapefile
    kanavasulut/vie-kanavasulut-kantaan))


(defrecord Geometriapaivitykset [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this
      :tieverkon-hakutehtava (tee-tieverkon-paivitystehtava this asetukset)
      :tieverkon-paivitystehtava (tee-tieverkon-paikallinen-paivitystehtava this asetukset)
      :laajennetun-tieverkon-hakutehtava (tee-laajennetun-tieverkon-paivitystehtava this asetukset)
      ;:laajennetun-tieverkon-paivitystehtava (tee-laajennetun-tieverkon-paikallinen-paivitystehtava this asetukset)
      :pohjavesialueiden-hakutehtava (tee-pohjavesialueiden-paivitystehtava this asetukset)
      :pohjavesialueiden-paivitystehtava (tee-pohjavesialueiden-paikallinen-paivitystehtava this asetukset)
      :talvihoidon-hoitoluokkien-hakutehtava (tee-talvihoidon-hoitoluokkien-paivitystehtava this asetukset)
      :talvihoidon-hoitoluokkien-paivitystehtava (tee-talvihoidon-hoitoluokkien-paikallinen-paivitystehtava this asetukset)
      :soratien-hoitoluokkien-hakutehtava (tee-soratien-hoitoluokkien-paivitystehtava this asetukset)
      :soratien-hoitoluokkien-paivitystehtava (tee-soratien-hoitoluokkien-paikallinen-paivitystehtava this asetukset)
      :siltojen-hakutehtava (tee-siltojen-paivitystehtava this asetukset)
      :siltojen-paivitystehtava (tee-siltojen-paikallinen-paivitystehtava this asetukset)
      :urakoiden-hakutehtava (tee-urakoiden-paivitystehtava this asetukset)
      :urakoiden-paivitystehtava (tee-urakoiden-paikallinen-paivitystehtava this asetukset)
      :elyjen-hakutehtava (tee-elyjen-paivitystehtava this asetukset)
      :elyjen-paivitystehtava (tee-elyjen-paikallinen-paivitystehtava this asetukset)
      :valaistusurakoiden-hakutehtava (tee-valaistusurakoiden-paivitystehtava this asetukset)
      :valaistusurakoiden-paivitystehtava (tee-valaistusurakoiden-paikallinen-paivitystehtava this asetukset)
      :paallystyspalvelusopimusten-hakutehtava (tee-paallystyspalvelusopimusten-paivitystehtava this asetukset)
      :paallystyspalvelusopimusten-paivitystehtava (tee-paallystyspalvelusopimusten-paikallinen-paivitystehtava this asetukset)
      :tekniset-laitteet-urakoiden-hakutehtava (tee-tekniset-laitteet-urakoiden-paivitystehtava this asetukset)
      :tekniset-laitteet-urakoiden-paivitystehtava (tee-tekniset-laitteet-urakoiden-paikallinen-paivitystehtava this asetukset)
      :siltojen-palvelusopimusten-hakutehtava (tee-siltojen-palvelusopimusten-paivitystehtava this asetukset)
      :siltojen-palvelusopimusten-paivitystehtava (tee-siltojen-palvelusopimusten-paikallinen-paivitystehtava this asetukset)
      :turvalaitteiden-hakutehtava (tee-turvalaitteiden-paivitystehtava this asetukset)
      :turvalaitteiden-paivitystehtava (tee-turvalaitteiden-paikallinen-paivitystehtava this asetukset)
      :kanavien-hakutehtava (tee-kanavien-paivitystehtava this asetukset)
      :kanavien-paivitystehtava (tee-kanavien-paikallinen-paivitystehtava this asetukset)))

  (stop [this]
    (doseq [tehtava [:tieverkon-hakutehtava
                     :tieverkon-paivitystehtava
                     :laajennetun-tieverkon-hakutehtava
                     #_:laajennetun-tieverkon-paivitystehtava
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
                     :elyjen-paivitystehtava
                     :valaistusurakoiden-hakutehtava
                     :valaistusurakoiden-paivitystehtava
                     :paallystyspalvelusopimusten-hakutehtava
                     :paallystyspalvelusopimusten-paivitystehtava
                     :tekniset-laitteet-urakoiden-hakutehtava
                     :tekniset-laitteet-urakoiden-paivitystehtava
                     :siltojen-palvelusopimusten-hakutehtava
                     :siltojen-palvelusopimusten-paivitystehtava
                     :turvalaitteiden-hakutehtava
                     :turvalaitteiden-paivitystehtava
                     :kanavien-hakutehtava
                     :kanavien-paivitystehtava]
            :let [lopeta-fn (get this tehtava)]]
      (when lopeta-fn (lopeta-fn)))
    this))
