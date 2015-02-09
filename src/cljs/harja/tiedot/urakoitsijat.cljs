(ns harja.tiedot.urakoitsijat
  "Harjan urakoitsijoiden tietojen hallinta"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [tarkkaile!]]
            
            [cljs.core.async :refer [chan <! >! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def urakoitsijat "Urakoitsijat" (atom nil))

(tarkkaile! "urakoitsijat" urakoitsijat)

(defn ^:export hae-urakoitsijat []
  (let [ch (chan)]
    (go
      (let [res (<! (k/post! :hae-urakoitsijat nil))]
        (>! ch res))
      (close! ch))
    ch))

(t/kuuntele! :harja-ladattu (fn [_]
                              (go (reset! urakoitsijat (<! (k/post! :hae-urakoitsijat
                                                                     nil))))))
