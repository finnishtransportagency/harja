(ns harja.tiedot.urakat
  "Harjan urakkalistausten tietojen hallinta"
  (:require [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [chan <! >! close!]]
            [harja.pvm :as pvm]
            [harja.ui.protokollat :refer [Haku hae]]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def urakka-xf
  "Backendistä tulevien urakoiden muunnos sopivaan muotoon."
  (map #(assoc % :type :ur)))

(defn hae-hallintayksikon-urakat [hallintayksikko]
  (k/post! :hallintayksikon-urakat (:id hallintayksikko) urakka-xf))

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

(defn- pvm-str->pvm [pv]
  (pvm/parsi pvm/fi-pvm-parse (if (str/ends-with? pv ".") (str pv "2000") (str pv ".2000"))))
(defn paivita-kesa-aika! [ur kesa-ajan-alku kesa-ajan-loppu]
  (let [alku (pvm-str->pvm kesa-ajan-alku)
        loppu (pvm-str->pvm kesa-ajan-loppu)]
    (k/post! :paivita-kesa-aika {:urakka-id (:id ur) :tiedot {:alkupvm alku :loppupvm loppu}}
      nil true)))

