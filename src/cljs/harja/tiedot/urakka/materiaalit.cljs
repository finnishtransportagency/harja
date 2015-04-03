(ns harja.tiedot.urakka.materiaalit
  "Urakoiden materiaalisuunnittelun tiedot."
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! chan]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce materiaalikoodit (atom nil))

(defn hae-materiaalikoodit
  "Palauttaa atomin, jossa on järjestelmän materiaalikodit.
  Jos materiaaleja ei ole vielä haettu, laukaisee niiden haun palvelimelta."
  []
  (when (nil? @materiaalikoodit)
    (go (reset! materiaalikoodit
                (<! (k/get! :hae-materiaalikoodit)))))
  materiaalikoodit)

(defn hae-urakan-materiaalit [urakka-id]
  (let [ch (chan)]
    (go (>! ch (into []
                     (<! (k/post! :hae-urakan-materiaalit urakka-id)))))
    ch))
 
(defn tallenna [urakka-id sopimus-id materiaalit] 
  (log "TALLENNETAAN MATSKUT: " (pr-str materiaalit))
  (let [ch (chan)]
    (go (>! ch (into []
                     (<! (k/post! :tallenna-urakan-materiaalit
                                  {:urakka-id urakka-id
                                   :sopimus-id sopimus-id
                                   :materiaalit (filter #(not (nil? (:maara %))) materiaalit)})))))
    ch))
