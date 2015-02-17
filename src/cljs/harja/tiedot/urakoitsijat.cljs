(ns harja.tiedot.urakoitsijat
  "Harjan urakoitsijoiden tietojen hallinta"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [tarkkaile!]]
            
            [cljs.core.async :refer [chan <! >! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def urakoitsijat "Urakoitsijat" (atom #{}))

(def urakoitsijat-hoito "Hoidon urakoitsijat" (atom #{}))

(def urakoitsijat-yllapito "Ylläpidon urakoitsijat" (atom #{}))

(tarkkaile! "urakoitsijat-hoito" urakoitsijat-hoito)
(tarkkaile! "urakoitsijat-yllapito" urakoitsijat-yllapito)

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

(defn hae-urakkatyypin-urakoitsijat [urakkatyyppi]
  (let [ch (chan)]
    (go
      (let [res (<! (k/post! :urakkatyypin-urakoitsijat urakkatyyppi))]
        (>! ch res))
      (close! ch))
    ch))

(defn hae-yllapidon-urakoitsijat []
  (let [ch (chan)]
    (go
      (let [res (<! (k/get! :yllapidon-urakoitsijat))]
        (>! ch res))
      (close! ch))
    ch))

(t/kuuntele! :harja-ladattu (fn [_]
                              (go (reset! urakoitsijat (<! (k/post! :hae-urakoitsijat
                                                                     nil))))))

(t/kuuntele! :harja-ladattu (fn [_]
                              (go (reset! urakoitsijat-hoito (<! (k/post! :urakkatyypin-urakoitsijat
                                                                     :hoito))))))

(t/kuuntele! :harja-ladattu (fn [_]
                              (go (reset! urakoitsijat-yllapito (<! (k/get! :yllapidon-urakoitsijat))))))