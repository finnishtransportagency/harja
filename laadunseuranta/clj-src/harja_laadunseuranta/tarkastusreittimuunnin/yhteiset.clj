(ns harja-laadunseuranta.tarkastusreittimuunnin.yhteiset
  "Tarkastusreittimuuntimen eri analyysien yhteiset koodit"
  (:require [taoensso.timbre :as log]))

(defn- merkinnat-korjatulla-osalla
  "Korvaa indeksistä eteenpäin löytyvät merkinnät annetuilla merkinnöillä"
  [kaikki-merkinnat alku-indeksi uudet-merkinnat]
  (let [merkinnat-ennen-indeksia (take alku-indeksi kaikki-merkinnat)
        merkinnat-indeksin-jalkeen (drop (+ alku-indeksi (count uudet-merkinnat)) kaikki-merkinnat)]
    (vec (concat
           merkinnat-ennen-indeksia
           uudet-merkinnat
           merkinnat-indeksin-jalkeen))))