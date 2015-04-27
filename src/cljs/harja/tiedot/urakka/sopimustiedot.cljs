(ns harja.tiedot.urakka.sopimustiedot
  "Tämä nimiavaruus hallinnoi urakan yhteystietoja ja päivystäjiä."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn tallenna-sopimustyyppi
  [urakka-id sopimustyyppi]
  (k/post! :tallenna-urakan-sopimustyyppi
           {:urakka-id urakka-id
            :sopimustyyppi sopimustyyppi}))

(def +sopimustyypit+
  [:palvelusopimus :kokonaisurakka])
