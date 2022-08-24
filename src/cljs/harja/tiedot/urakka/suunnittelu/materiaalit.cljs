(ns harja.tiedot.urakka.suunnittelu.materiaalit
  "Urakoiden materiaalisuunnittelun tiedot."
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! chan]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce materiaalikoodit (atom nil))

(defonce materiaalinakymassa? (atom false))

(defn hae-materiaalikoodit
  "Palauttaa atomin, jossa on järjestelmän materiaalikodit.
  Jos materiaaleja ei ole vielä haettu, laukaisee niiden haun palvelimelta."
  []
  (when (nil? @materiaalikoodit)
    (go (reset! materiaalikoodit
                (<! (k/get! :hae-materiaalikoodit)))))
  materiaalikoodit)


(defn hae-urakan-materiaalit [urakka-id]
  (k/post! :hae-urakan-materiaalit urakka-id))

(defn hae-urakassa-kaytetyt-materiaalit [urakka-id alku loppu sopimus-id]
  (log "Haetaan urakassa ("urakka-id") käytetyt materiaalit ajalta "(pvm/pvm alku))
  (log (str "ALKU: " alku "\nLOPPU: " loppu))
  (k/post! :hae-urakassa-kaytetyt-materiaalit {:urakka-id urakka-id :hk-alku alku :hk-loppu loppu :sopimus sopimus-id}))

(defn hae-toteumat-materiaalille [urakka-id materiaali-id hoitokausi sopimus]
  (k/post! :hae-urakan-toteumat-materiaalille {:urakka-id urakka-id
                                               :materiaali-id materiaali-id
                                               :hoitokausi hoitokausi
                                               :sopimus sopimus}))

(defn hae-toteuman-materiaalitiedot [urakka-id toteuma-id]
  (k/post! :hae-toteuman-materiaalitiedot {:urakka-id urakka-id :toteuma-id toteuma-id}))

(defn tallenna-toteuma-materiaaleja [urakka-id toteumamateriaalit hoitokausi sopimus-id]
  (k/post! :tallenna-toteuma-materiaaleja! {:urakka-id urakka-id
                                            :toteumamateriaalit toteumamateriaalit
                                            :hoitokausi hoitokausi
                                            :sopimus sopimus-id}))
 
(defn tallenna [urakka-id sopimus-id hoitokausi hoitokaudet tuleville-valittu materiaalit]
  (log "TALLENNETAAN MATSKUT: " (pr-str materiaalit))
  (k/post! :tallenna-suunnitellut-materiaalit
           {:urakka-id   urakka-id
            :sopimus-id  sopimus-id
            :hoitokausi  hoitokausi
            :hoitokaudet hoitokaudet
            :tulevat-hoitokaudet-mukana? tuleville-valittu
            :materiaalit (filter #(or (:pohjavesialue %)
                                      (not (nil? (:maara %)))) materiaalit)}))
