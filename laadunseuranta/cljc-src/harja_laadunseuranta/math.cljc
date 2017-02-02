(ns harja-laadunseuranta.math)

;; TODO Pitäisi yhdistää harja.math:iin...

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