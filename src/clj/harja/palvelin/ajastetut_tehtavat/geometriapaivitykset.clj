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
    ;; poista
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :as testi])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn aja-paivitys [alk db geometria-aineisto kohdepolku kohdetiedoston-polku tiedostourl tiedoston-muutospvm]
  (log/debug "Ajetaan geometriapäivitys: " geometria-aineisto)
  ;; todo: lisää ja tarkista lukko
  (kansio/poista-tiedostot kohdepolku)
  (alk/hae-tiedosto alk (str geometria-aineisto "-haku") tiedostourl kohdetiedoston-polku)
  (arkisto/pura-paketti kohdetiedoston-polku)
  ;; todo: aja päivitys
  (geometriapaivitykset/paivita-viimeisin-paivitys<! db tiedoston-muutospvm geometria-aineisto)
  (log/debug "Geometriapäivitys: " geometria-aineisto " onnistui"))

(defn onko-kohdetiedosto-ok? [kohdepolku kohdetiedoston-nimi]
  (and
    (and
      (not (empty kohdepolku))
      (not (empty kohdetiedoston-nimi)))
    (.isDirectory (clojure.java.io/file kohdepolku))))

(defn pitaako-paivittaa? [viimeisin-paivitys tiedoston-muutospvm]
  (or (not viimeisin-paivitys)
      (pvm/jalkeen?
        (time-coerce/from-sql-time tiedoston-muutospvm)
        (time-coerce/from-sql-time viimeisin-paivitys))))

(defn tarkista-paivitys [alk db geometria-aineisto tiedostourl kohdepolku kohdetiedoston-nimi]
  (let [paivityksen-tiedot (first (geometriapaivitykset/hae-paivitys db geometria-aineisto))
        viimeisin-paivitys (:viimeisin_paivitys paivityksen-tiedot)
        kohdetiedoston-polku (str kohdepolku kohdetiedoston-nimi)]

    ;; todo: tarvii todennäköisesti tehdä tarkempi tarkastus kohdetiedostolle
    (when (and (not-empty tiedostourl) (onko-kohdetiedosto-ok? kohdepolku kohdetiedoston-nimi))
      (try+
        (let [tiedoston-muutospvm (alk/hae-tiedoston-muutospaivamaara alk (str geometria-aineisto "-muutospaivamaaran-haku") tiedostourl)]
          (if (pitaako-paivittaa? viimeisin-paivitys tiedoston-muutospvm)
            (aja-paivitys alk db geometria-aineisto kohdepolku kohdetiedoston-polku tiedostourl tiedoston-muutospvm)
            (log/debug "Geometria: " geometria-aineisto ", ei ole päivittynyt viimeisimmän haun jälkeen. Päivitystä ei tehdä.")))
        (catch Exception e
          (log/error "Geometriapäivityksessä: " geometria-aineisto ", tapahtui poikkeus: " e))))))

(defn tee-tieverkon-paivitystehtava [this asetukset]
  (let [paivitystiheys-minuuteissa (:tieosoiteverkon-alk-tuontivali asetukset)]
    (log/debug " Ajastetaan tieverkon päivitys ajettavaksi " paivitystiheys-minuuteissa "minuutin välein ")
    (chime-at (periodic-seq (time/now) (-> paivitystiheys-minuuteissa time/minutes))
              (fn [_]
                (log/debug "Tarkistetaan onko tieverkko päivittynyt")
                (tarkista-paivitys (:alk this)
                                   (:db this)
                                   "tieverkko"
                                   (:tieosoiteverkon-alk-osoite asetukset)
                                   (:tieosoiteverkon-alk-tuontikohde asetukset)
                                   "Tieosoiteverkko.zip")))))

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
    (tarkista-paivitys alk testitietokanta "tieverkko" "http://185.26.50.104/Tieosoiteverkko.zip" "/Users/mikkoro/Desktop/Tieverkko-testi/" "Tieosoiteverkko.zip")))