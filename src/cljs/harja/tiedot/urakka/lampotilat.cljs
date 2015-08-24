(ns harja.tiedot.urakka.lampotilat
  "Tämän nimiavaruuden avulla voidaan hakea urakan lämpötiloja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn hae-lampotilat-ilmatieteenlaitokselta [urakka-id vuosi]
  (k/post! :hae-lampotilat-ilmatieteenlaitokselta {:urakka-id urakka-id
                                                   :vuosi vuosi}))


(defn hae-urakan-suolasakot-ja-lampotilat [urakka-id]
  (k/post! :hae-urakan-suolasakot-ja-lampotilat urakka-id))