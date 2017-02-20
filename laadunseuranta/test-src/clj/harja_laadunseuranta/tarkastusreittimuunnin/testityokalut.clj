(ns harja-laadunseuranta.tarkastusreittimuunnin.testityokalut
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]))

(defn aseta-merkintojen-tarkkuus
  "Etsii merkintöjen GPS-tarkkuudeksi annetun tarkkuuden."
  [merkinnat tarkkuus]
  (assert (and (number? tarkkuus)
               (>= tarkkuus 0)) "Virheellinen tarkkuus")
  (mapv #(assoc % :gps-tarkkuus tarkkuus) merkinnat))