(ns harja.tiedot.urakat
  "Harjan urakkalistausten tietojen hallinta"
  (:require [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [chan <! >! close!]]
            [reagent.core :refer [atom]]
            [harja.ui.protokollat :refer [Haku hae]]
            [alandipert.storage-atom :refer [local-storage]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def urakka-xf
  "Backendistä tulevien urakoiden muunnos sopivaan muotoon."
  (map #(assoc % :type :ur)))

(defn hae-hallintayksikon-urakat [hallintayksikko]
  (k/post! :hallintayksikon-urakat (:id hallintayksikko) urakka-xf))

(def nayta-paattyneet-urakat? (local-storage (atom false) :nayta-paattyneet-urakat))

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

(defn poista-indeksi-kaytosta! [ur]
  (k/post! :poista-indeksi-kaytosta {:urakka-id (:id ur)}))

(defn paivita-kesa-aika! [ur kesa-ajan-alku kesa-ajan-loppu]
  (k/post! :paivita-kesa-aika {:urakka-id (:id ur)
                               :tiedot {:alkupvm kesa-ajan-alku :loppupvm kesa-ajan-loppu}}
    nil true))

