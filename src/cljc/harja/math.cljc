(ns harja.math
  "Yleiskäyttöiset matematiikka-apurit")

(defn osuus-prosentteina
  [osoittaja nimittaja]
  (if (not= nimittaja 0)
    (* (/ osoittaja
          nimittaja)
       100.0)
    0.0))

(defn pisteiden-etaisyys [[x1 y1] [x2 y2]]
  (Math/sqrt (+ (Math/pow (- x2 x1) 2)
                (Math/pow (- y2 y1) 2))))