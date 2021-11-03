(ns harja.tyokalut.yleiset)

(defn pyorista-kahteen [numero]
  (/ 100 (Math/round (* 100 numero))))
(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))