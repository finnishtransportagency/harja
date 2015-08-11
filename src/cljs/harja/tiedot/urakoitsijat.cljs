(ns harja.tiedot.urakoitsijat
  "Harjan urakoitsijoiden tietojen hallinta"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [tarkkaile!]]
            
            [cljs.core.async :refer [chan <! >! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def urakoitsijat "Urakoitsijat" (atom #{}))

(def urakoitsijat-hoito
  (reaction (into #{} (filter #(= (:urakkatyyppi %) "hoito") @urakoitsijat))))
(def urakoitsijat-paallystys
  (reaction (into #{} (filter #(= (:urakkatyyppi %) "paallystys") @urakoitsijat))))
(def urakoitsijat-paikkaus
  (reaction (into #{} (filter #(= (:urakkatyyppi %) "paikkaus") @urakoitsijat))))
(def urakoitsijat-tiemerkinta
  (reaction (into #{} (filter #(= (:urakkatyyppi %) "tiemerkinta") @urakoitsijat))))
(def urakoitsijat-valaistus
  (reaction (into #{} (filter #(= (:urakkatyyppi %) "valaistus") @urakoitsijat))))

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
