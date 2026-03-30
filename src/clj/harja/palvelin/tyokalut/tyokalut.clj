(ns harja.palvelin.tyokalut.tyokalut
  (:import (java.math RoundingMode)))

(defn arityt
  "Palauttaa funktion eri arityt. Esim. #{0 1} jos funktio tukee nollan ja yhden parametrin arityjä."
  [f]
  (->> f class .getDeclaredMethods
       (map #(-> % .getParameterTypes alength))
       (into #{})))

(defn pyorista-kahteen-decimaaliin [arvo]
  (when (not (nil? arvo))
    (.setScale
      (with-precision 2 (bigdec arvo)) 2 RoundingMode/HALF_UP)))
