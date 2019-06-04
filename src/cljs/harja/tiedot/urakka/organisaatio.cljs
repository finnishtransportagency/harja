(ns harja.tiedot.urakka.organisaatio
  "Tämä nimiavaruus hallinnoi urakan organisaatiota."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.ui.protokollat :refer [Haku hae]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-organisaatio [urakka-id]
  (k/post! :hae-urakan-organisaatio urakka-id))
