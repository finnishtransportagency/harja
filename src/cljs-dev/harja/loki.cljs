(ns harja.loki
  "Apufunktioita lokittamiseen."
  (:require [devtools.core :as devtools]))

(devtools/install!)

(defn log [& things]
  (.apply js/console.log js/console (apply array things)))

(defn tarkkaile!
  [nimi atomi]
  (add-watch atomi :tarkkailija (fn [_ _ vanha uusi]
                                  (log nimi ": " (pr-str vanha) " => " (pr-str uusi))
                                  )))
