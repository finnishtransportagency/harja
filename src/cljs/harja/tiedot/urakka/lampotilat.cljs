(ns harja.tiedot.urakka.lampotilat
  "Tämän nimiavaruuden avulla voidaan hakea urakan lämpötiloja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn hae-urakan-lampotilat [urakka-id]
  (k/post! :urakan-lampotilat urakka-id))

(defn tallenna-lampotilat! [id urakka-id [alku loppu] keskilampo pitkalampo]
  (k/post! :tallenna-lampotilat! {:urakka urakka-id
                                  :id id
                                  :alku alku
                                  :loppu loppu
                                  :keskilampo (str keskilampo)
                                  :pitkalampo (str pitkalampo)}))

(defn hae-lampotilat-ilmatieteenlaitokselta [urakka-id vuosi]
  (k/post! :hae-lampotilat-ilmatieteenlaitokselta {:urakka-id urakka-id
                                                   :vuosi vuosi}))
