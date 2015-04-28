(ns harja.tiedot.urakka.toteumat
  "Tämä nimiavaruus hallinnoi urakan toteumien tietoja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn hae-urakan-toteumat [urakka-id]
  (k/post! :urakan-toteumat urakka-id))

