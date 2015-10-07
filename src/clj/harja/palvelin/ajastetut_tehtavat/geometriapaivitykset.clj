(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [chime :refer [chime-at]]
            [harja.kyselyt.geometriapaivitykset :as geometriapaivitykset]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.alk-komponentti :as alk]
            [harja.pvm :as pvm]
            [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]
    ;; poista
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :as testi]
            )
  (:use [slingshot.slingshot :only [try+ throw+]]))


(defn aja-paivitys [alk db paivitystunnus aja-paivitys]
  (let [paivityksen-tiedot (first (geometriapaivitykset/hae-paivitys db paivitystunnus))
        viimeisin-paivitys (:viimeisin_paivitys paivityksen-tiedot)
        url (:url paivityksen-tiedot)]
    (try+
      (let [tiedoston-muutospvm (alk/hae-tiedoston-muutospaivamaara alk url)]
        (println "tiedoston muutospvm:" tiedoston-muutospvm)
        (println "tiedoston muutospvm tyyppi:" (type tiedoston-muutospvm))
        (println "viimeisen muutospvm tyyppi:" (type viimeisin-paivitys))
        (when (pvm/jalkeen?
                (time-coerce/from-sql-time tiedoston-muutospvm)
                (time-coerce/from-sql-time viimeisin-paivitys))
          ;; aja-paivitys
          (println "Ny päivitellään!")))

      ;;todo: tee poikkeuskäsittely
      )

    (println "Päivityksen tiedot: " paivityksen-tiedot)
    ))

(defn tee-tieverkon-paivitystehtava [this]
  (log/debug "Ajastetaan tieverkon päivitys ajettavaksi 5 minuutin välein")
  (chime-at (periodic-seq (time/now) (-> 5 time/minutes))
            (fn [_]
              (log/debug "Tarkistetaan onko tieverkko päivittynyt")
              (aja-paivitys (:alk this) (:db this) "tieverkko" (fn [])))))

(defrecord Geometriapaivitykset []
  component/Lifecycle
  (start [this]
    (assoc this :tieverkon-paivitystehtava (tee-tieverkon-paivitystehtava this)))
  (stop [this]
    (:tieverkon-paivitystehtava this)
    this))


(defn aja-tieverkon-paivitys []
  (let [testitietokanta (apply tietokanta/luo-tietokanta testi/testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        alk (assoc (alk/->Alk) :db testitietokanta :integraatioloki integraatioloki)]
    (component/start integraatioloki)
    (component/start alk)
    (aja-paivitys alk testitietokanta "tieverkko" (fn [] (println "HILIPATIPIPPAA!")))))