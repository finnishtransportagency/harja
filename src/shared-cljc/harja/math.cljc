(ns harja.math
  "Yleiskäyttöiset matematiikka-apurit")

(defn osuus-prosentteina
  [osoittaja nimittaja]
  (if (not= nimittaja 0)
    (* (/ osoittaja
          nimittaja)
       100.0)
    0.0))

;; TODO APureista kulma-funktio tänne

(defn ms->sec
  "Muuntaa millisekunnit sekunneiksi"
  [s]
  (/ s 1000))

(defn pisteiden-kulma-radiaaneina
  "Palauttaa pisteiden välisen kulman radiaaneina"
  [[x1 y1] [x2 y2]]
  (when (and x1 y1 x2 y2)
    (Math/atan2 (- y2 y1) (- x2 x1))))

(defn pisteiden-etaisyys [[x1 y1] [x2 y2]]
  (Math/sqrt (+ (Math/pow (- x2 x1) 2)
                (Math/pow (- y2 y1) 2))))