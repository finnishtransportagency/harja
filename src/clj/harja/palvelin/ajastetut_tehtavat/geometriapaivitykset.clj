(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [chime :refer [chime-at]]
            [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]
            [harja.kyselyt.geometriapaivitykset :as geometriapaivitykset]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.alk-komponentti :as alk]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.kansio :as kansio]
            [harja.palvelin.tyokalut.arkisto :as arkisto]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tieverkon-tuonti :as tieverkon-tuonti]
    ;; poista
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :as testi])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.util UUID)))

(defn aja-paivitys [alk db geometria-aineisto kohdepolku kohdetiedoston-polku tiedostourl tiedoston-muutospvm paivitys]
  (log/debug "Ajetaan geometriapäivitys: " geometria-aineisto)
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
    (log/debug "Geometria-aineisto: " geometria-aineisto " on muuttunut ja tarvitaan päivittää")
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

(defn tee-tieverkon-paivitystehtava [this {:keys [tieosoiteverkon-alk-tuontivali
                                                  tieosoiteverkon-alk-osoite
                                                  tieosoiteverkon-alk-tuontikohde
                                                  tieosoiteverkon-shapefile]}]
  (when (and tieosoiteverkon-alk-tuontivali
             tieosoiteverkon-alk-osoite
             tieosoiteverkon-alk-tuontikohde
             tieosoiteverkon-shapefile)
    (ajasta-paivitys this
                     "tieverkko"
                     tieosoiteverkon-alk-tuontivali
                     tieosoiteverkon-alk-osoite
                     tieosoiteverkon-alk-tuontikohde
                     "Tieosoiteverkko.zip"
                     (fn [] (tieverkon-tuonti/vie-tieverkko-kantaan (:db this) tieosoiteverkon-shapefile)))))

(defrecord Geometriapaivitykset [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this :tieverkon-paivitystehtava (tee-tieverkon-paivitystehtava this asetukset)))
  (stop [this]
    (:tieverkon-paivitystehtava this)
    this))

(defn aja-tieverkon-paivitys []
  (let [testitietokanta (apply tietokanta/luo-tietokanta testi/testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        alk (assoc (alk/->Alk) :db testitietokanta :integraatioloki integraatioloki)]
    (component/start integraatioloki)
    (component/start alk)
    (tarkista-paivitys alk
                       testitietokanta
                       "tieverkko"
                       "http://185.26.50.104/Tieosoiteverkko.zip"
                       "/Users/mikkoro/Desktop/Tieverkko-testi/"
                       "Tieosoiteverkko.zip"
                       (fn []
                         (tieverkon-tuonti/vie-tieverkko-kantaan testitietokanta
                                                                 "file:///Users/mikkoro/Desktop/Tieverkko-testi/Tieosoiteverkko.shp")))))