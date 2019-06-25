(ns harja.palvelin.integraatiot.api.tyokalut.apurit
  (:require [harja.pvm :as pvm]))

(defn muuta-mapin-avaimet-keywordeiksi
  "Palauttaa mapin, jossa avaimet ovat keywordeja"
  [map]
  (reduce (fn [eka toka]
            (assoc
              eka
              (keyword toka)
              (get map toka)))
          {}
          (keys map)))
