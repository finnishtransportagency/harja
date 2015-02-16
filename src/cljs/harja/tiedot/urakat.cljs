(ns harja.tiedot.urakat
  "Harjan urakkalistausten tietojen hallinta"
  (:require [harja.asiakas.kommunikaatio :as k]
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [chan <! >! close!]]
            [harja.pvm :as pvm]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-hallintayksikon-urakat [hallintayksikko]
  (let [ch (chan)]
    (go
      (let [res (<! (k/post! :hallintayksikon-urakat (:id hallintayksikko)))]
        (>! ch (into [] (comp (map #(pvm/muunna-aika % :alkupvm :loppupvm))
                              (map #(assoc % :type :ur))) res)))
      (close! ch))
    ch))

(defn hae-urakkatyypin-urakoitsijat [urakkatyyppi]
  (let [ch (chan)]
    (go
      (let [res (<! (k/post! :urakkatyypin-urakoitsijat urakkatyyppi))]
        (>! ch (mapv (fn [ur]
                       (assoc ur :type :ur))
                     res)))
      (close! ch))
    ch))

(defn hae-yllapidon-urakoitsijat [urakkatyyppi]
  (let [ch (chan)]
    (go
      (let [res (<! (k/post! :yllapidon-urakoitsijat nil))]
        (>! ch (mapv (fn [ur]
                       (assoc ur :type :ur))
                     res)))
      (close! ch))
    ch))
