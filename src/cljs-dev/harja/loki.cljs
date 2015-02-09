(ns harja.loki
  "Apufunktioita lokittamiseen.")

(defn log [& things]
  (.apply js/console.log js/console (apply array things)))

(defn tarkkaile!
  [nimi atomi]
  (add-watch atomi :tarkkailija (fn [_ _ vanha uusi]
                                  (log nimi ": " (pr-str vanha) " => " (pr-str uusi))
                                  )))