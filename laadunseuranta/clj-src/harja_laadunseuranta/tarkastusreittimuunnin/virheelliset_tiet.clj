(ns harja-laadunseuranta.tarkastusreittimuunnin.virheelliset-tiet
  "Projisoi tien pisteet edelliselle tielle, jos tielle on osunut vain pieni määrä
  pisteitä ja ne kaikki ovat lähellä edellistä tietä. Tarkoituksena korjata
  tilanteet, joissa muutama yksittäinen piste osuu eri tielle esim. siltojen
  ja risteysten kohdalla"
  (:require [taoensso.timbre :as log]
            [harja.math :as math]))

(defn korjaa-virheelliset-tiet [merkinnat]
  ;; TODO
  merkinnat)