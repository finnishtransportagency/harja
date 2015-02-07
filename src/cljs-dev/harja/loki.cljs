(ns harja.loki
  "Apufunktioita lokittamiseen.")

(defn log [& things]
  (.apply js/console.log js/console (clj->js (vec things))))
