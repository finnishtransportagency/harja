(ns harja.math
  "Yleiskäyttöiset matematiikka-apurit")

(defn osuus-prosentteina
  [osoittaja nimittaja]
  (if (not= nimittaja 0)
    (* (/ osoittaja
          nimittaja)
       100.0)
    0.0))