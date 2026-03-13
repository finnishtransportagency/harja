(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset
  (:require [taoensso.timbre :as log]
            [chime :refer [chime-at]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [clj-time.core :as time]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.ava :as ava]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko :as tieverkon-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieturvallisuusverkko :as tieturvallisuusverkon-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.paallysteen-korjausluokat :as paallysteen-korjausluokat]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat :as siltojen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialueet :as pohjavesialueen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.soratien-hoitoluokat :as soratien-hoitoluokkien-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.talvihoidon-hoitoluokat :as talvihoidon-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.alueurakat :as urakoiden-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.elyt :as elyjen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.elinvoimakeskukset :as elinvoimakeskusten-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.valaistusurakat :as valaistusurakoiden-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.paallystyspalvelusopimukset :as paallystyspalvelusopimusten-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tekniset-laitteet-urakat :as tekniset-laitteet-urakat-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.siltapalvelusopimukset :as siltapalvelusopimukset]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.kanavasulut :as kanavasulut]
            [harja.kyselyt.geometriaaineistot :as geometria-aineistot]
            [harja.domain.geometriaaineistot :as ga]
            [clojure.core.async :as async]))

(def virhekasittely
  {:error-handler #(log/error "Käsittelemätön poikkeus ajastetussa tehtävässä:" %)})

(defn tee-alkuajastus []
  (time/plus- (time/now) (time/seconds 10)))

(defn ajasta-paivitys [this paivitystunnus tuontivali osoite kohdetiedoston-polku shapefile paivitys kayttajatunnus salasana]
  (log/debug (format "[AJASTETTU-GEOMETRIAPAIVITYS] %s päivitystarve ajastetaan tarkistettavaksi %s minuutin välein. Päivitystarpeeseen vaikuttavat GEOMETRIAPAIVITYS-taulun tiedot sekä LUKKO-taulu." paivitystunnus tuontivali))
  (chime-at (periodic-seq (tee-alkuajastus) (-> tuontivali time/minutes))
    (fn [_]
      (ava/kaynnista-paivitys (:integraatioloki this)
        (:db this)
        paivitystunnus
        osoite
        kohdetiedoston-polku
        shapefile
        paivitys
        kayttajatunnus
        salasana))
    virhekasittely))


(defn rakenna-osoite [db aineiston-nimi osoite]
  (let [aineisto (geometria-aineistot/hae-voimassaoleva-geometria-aineisto db aineiston-nimi)]
    (if (and osoite (.contains osoite "[AINEISTO]") aineisto)
      (.replace osoite "[AINEISTO]" (::ga/tiedostonimi aineisto))
      osoite)))

;; Päivitystehtävä hakee aineiston rajapinnan kautta, tallentaa sen hakemistoon ja
;; käsittelee ja tallentaa tietosisällön tietokantaan. Käsittelyssä ja tallennuksessa hyödynnetään
;; parametrina saatua paivitys-funktiota.
;; Määrittele hakuun liittyvät tiedot asetukset.edn-tiedostossa ja ajasta GEOMETRIAPAIVITYS-taulussa.
;; Lue lisää geometriapäivityksistä confluencesta.
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
          shapefile
          (fn [] (paivitys (:db this) shapefile))
          kayttajatunnus
          salasana)))))

;; Tallennustehtävä suorittaa vain viimeisen geometriapäivitykseen liittyvän askeleen: tallentaa tiedoston tietosisällön
;; tietokantaan paivitys-funktion avulla. Tiedosto on toimitettava tietosisältöön erikseen.
;; Parametrit asetukset.edn-tiedostossa, ajastus GEOMETRIAPAIVITYS-taulussa. Lue lisää geometriapäivityksistä confluencesta.
;; Tätä tarvitaan kaista-aineiston (laajennettu tieverkko) sisäänluvussa. Kaista-aineiston rajapintahausta huolehtii lambda.
(defn maarittele-tallennustehtava [paivitystunnus
                                   tiedostoavain
                                   paivitys]
  (fn [this {:keys [tuontivali] :as asetukset}]
    (let [tiedosto (get asetukset tiedostoavain)]
      (when (and paivitystunnus
              tuontivali
              tiedosto)
        (ajasta-paivitys this
          paivitystunnus
          tuontivali
          nil
          nil
          tiedosto
          (fn [] (paivitys (:db this) tiedosto))
          nil
          nil)))))


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

(def tee-tieturvallisuusverkon-paivitystehtava
  (maarittele-paivitystehtava
    "tieturvallisuusverkko"
    :tieturvallisuustarkastus-tieverkko-osoite
    :tieturvallisuustarkastus-tieverkko-tuontikohde
    :tieturvallisuustarkastus-tieverkko-shapefile
    tieturvallisuusverkon-tuonti/vie-tieturvallisuusverkko-kantaan))

(def tee-paallysteen-korjausluokka-paivitystehtava
  (maarittele-paivitystehtava
    "paallysteen-korjausluokka"
    :paallysteen-korjausluokka-osoite
    :paallysteen-korjausluokka-tuontikohde
    :paallysteen-korjausluokka-shapefile
    paallysteen-korjausluokat/vie-korjausluokat-kantaan))

(def tee-laajennetun-tieverkon-paivitystehtava
  (maarittele-tallennustehtava
    "laajennettu-tieverkko"
    :laajennetun-tieosoiteverkon-tiedot
    tieverkon-tuonti/vie-laajennettu-tieverkko-kantaan))

(def tee-pohjavesialueiden-paivitystehtava
  (maarittele-paivitystehtava
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

(def tee-talvihoidon-hoitoluokkien-paivitystehtava
  (maarittele-paivitystehtava
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

(def tee-urakoiden-paivitystehtava
  (maarittele-paivitystehtava
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

(def tee-elinvoimakeskusten-paivitystehtava
  (maarittele-paivitystehtava
    "elinvoimakeskukset"
    :elinvoimakeskusten-osoite
    :elinvoimakeskusten-tuontikohde
    :elinvoimakeskusten-shapefile
    elinvoimakeskusten-tuonti/vie-elinvoimakeskukset-kantaan))

(def tee-valaistusurakoiden-paivitystehtava
  (maarittele-paivitystehtava
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

(def tee-tekniset-laitteet-urakoiden-paivitystehtava
  (maarittele-paivitystehtava
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

(def tee-kanavien-paivitystehtava
  (maarittele-paivitystehtava
    "kanavat"
    :kanavien-osoite
    :kanavien-tuontikohde
    :kanavien-shapefile
    kanavasulut/vie-kanavasulut-kantaan))


(defrecord Geometriapaivitykset [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this
      :tieverkon-paivitys (tee-tieverkon-paivitystehtava this asetukset)
      :tieturvallisuusverkon-paivitys (tee-tieturvallisuusverkon-paivitystehtava this asetukset)
      :paallysteen-korjausluokka-paivitys (tee-paallysteen-korjausluokka-paivitystehtava this asetukset)
      :laajennetun-tieverkon-paivitys (tee-laajennetun-tieverkon-paivitystehtava this asetukset)
      :pohjavesialueiden-paivitys (tee-pohjavesialueiden-paivitystehtava this asetukset)
      :talvihoidon-hoitoluokkien-paivitys (tee-talvihoidon-hoitoluokkien-paivitystehtava this asetukset)
      :soratien-hoitoluokkien-paivitys (tee-soratien-hoitoluokkien-paivitystehtava this asetukset)
      :siltojen-paivitys (tee-siltojen-paivitystehtava this asetukset)
      :urakoiden-paivitys (tee-urakoiden-paivitystehtava this asetukset)
      :elyjen-paivitys (tee-elyjen-paivitystehtava this asetukset)
      :elinvoimakeskusten-paivitys (tee-elinvoimakeskusten-paivitystehtava this asetukset)
      :valaistusurakoiden-paivitys (tee-valaistusurakoiden-paivitystehtava this asetukset)
      :paallystyspalvelusopimusten-paivitys (tee-paallystyspalvelusopimusten-paivitystehtava this asetukset)
      :tekniset-laitteet-urakoiden-paivitys (tee-tekniset-laitteet-urakoiden-paivitystehtava this asetukset)
      :siltojen-palvelusopimusten-paivitys (tee-siltojen-palvelusopimusten-paivitystehtava this asetukset)
      :kanavien-paivitys (tee-kanavien-paivitystehtava this asetukset)))

  (stop [this]
    (doseq [tehtava [:tieverkon-paivitys
                     :tieturvallisuusverkon-paivitys
                     :paallysteen-korjausluokka-paivitys
                     :laajennetun-tieverkon-paivitys
                     :pohjavesialueiden-paivitys
                     :talvihoidon-hoitoluokkien-paivitys
                     :soratien-hoitoluokkien-paivitys
                     :siltojen-paivitys
                     :urakoiden-paivitys
                     :elyjen-paivitys
                     :elinvoimakeskusten-paivitys
                     :valaistusurakoiden-paivitys
                     :paallystyspalvelusopimusten-paivitys
                     :tekniset-laitteet-urakoiden-paivitys
                     :siltojen-palvelusopimusten-paivitys
                     :kanavien-paivitys]
            :let [lopeta-fn (get this tehtava)]]
      (cond
        (fn? lopeta-fn) (lopeta-fn)
        (nil? lopeta-fn) nil
        :else (async/close! lopeta-fn)))
    this))
