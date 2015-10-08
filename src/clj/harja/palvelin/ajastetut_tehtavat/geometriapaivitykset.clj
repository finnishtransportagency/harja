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
            [harja.testi :as testi]
            [clojure.java.io :as io])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn onko-kohdetiedosto-ok? [kohdepolku kohdetiedoston-nimi]
  (and
    (and
      (not (empty kohdepolku))
      (not (empty kohdetiedoston-nimi)))
    (.isDirectory (clojure.java.io/file kohdepolku))))

(defn aja-paivitys [alk db paivitystunnus tiedostourl kohdepolku kohdetiedoston-nimi]
  (let [paivityksen-tiedot (first (geometriapaivitykset/hae-paivitys db paivitystunnus))
        viimeisin-paivitys (:viimeisin_paivitys paivityksen-tiedot)
        kohdetiedoston-polku (str kohdepolku kohdetiedoston-nimi)]

    ;; todo: tarvii todennäköisesti tehdä tarkempi tarkastus kohdetiedostolle
    (when (and (not-empty tiedostourl) (onko-kohdetiedosto-ok? kohdepolku kohdetiedoston-nimi))
      (try+
        (let [tiedoston-muutospvm (alk/hae-tiedoston-muutospaivamaara alk (str paivitystunnus "-muutospaivamaaran-haku") tiedostourl)]
          (when (or (not viimeisin-paivitys)
                    (pvm/jalkeen?
                      (time-coerce/from-sql-time tiedoston-muutospvm)
                      (time-coerce/from-sql-time viimeisin-paivitys)))
            ;; todo: lisää ja tarkista lukko
            (kansio/poista-tiedostot kohdepolku)
            (alk/hae-tiedosto alk (str paivitystunnus "-haku") tiedostourl kohdetiedoston-polku)
            (arkisto/pura-paketti kohdetiedoston-polku)
            ;; todo: pura paketti
            ;; todo: aja päivitys
            ;; todo: päivitä viimeisin hakuaika
            ))
        (catch Exception e
          ;; todo: tee parempi poikkeuskäsittely
          (log/debug "Poikkeus: " e))))))

(defn tee-tieverkon-paivitystehtava [this asetukset]
  (log/debug "Ajastetaan tieverkon päivitys ajettavaksi 5 minuutin välein")
  (chime-at (periodic-seq (time/now) (-> 5 time/minutes))
            (fn [_]
              (log/debug "Tarkistetaan onko tieverkko päivittynyt")
              (aja-paivitys (:alk this)
                            (:db this)
                            "tieverkko"
                            (:tieosoiteverkon-alk-osoite asetukset)
                            (:tieosoiteverkon-alk-tuontikohde asetukset)
                            "Tieosoiteverkko.zip"))))

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
    (aja-paivitys alk testitietokanta "tieverkko" "http://185.26.50.104/Tieosoiteverkko.zip" "/Users/mikkoro/Desktop/Tieverkko-testi/" "Tieosoiteverkko.zip")))