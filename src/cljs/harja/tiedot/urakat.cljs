(ns harja.tiedot.urakat
  "Harjan urakkalistausten tietojen hallinta"
  (:require [harja.asiakas.kommunikaatio :as k]
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [chan <! >! close!]]
            [harja.pvm :as pvm]
            [harja.ui.protokollat :refer [Haku hae]]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def urakka-xf
  "Backendistä tulevien urakoiden muunnos sopivaan muotoon."
  (map #(assoc % :type :ur)))

(defn hae-hallintayksikon-urakat [hallintayksikko]
  (let [ch (chan)]
    (go
      (let [res (<! (k/post! :hallintayksikon-urakat (:id hallintayksikko)))]
        (>! ch (into [] urakka-xf res)))
      (close! ch))
    ch))

(def urakka-haku
  "Yleinen urakoista hakeva hakulähde."
  (reify Haku
    (hae [_ teksti]
      (let [ch (chan)]
        ;; PENDING: tässä voisimme cachettaa tulokset tekstin mukaan
        (go (let [res (<! (k/post! :hae-urakoita teksti))]
              (>! ch (into [] urakka-xf res))
              (close! ch)))
        ch))))
