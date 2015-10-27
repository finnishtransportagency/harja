(ns harja.tiedot.tierekisteri
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]])
  (:require-macros
    [reagent.ratom :refer [reaction run!]]
    [cljs.core.async.macros :refer [go]]))

(def karttataso-tr-alkuosoite (atom true))

(def valittu-alkupiste (atom nil))
(def tr-alkupiste-kartalla (reaction
                             (when @valittu-alkupiste
                               (-> @valittu-alkupiste
                                   (assoc :type :circle)
                                   (assoc :color "green")
                                   (assoc :radius 100)
                                   (assoc :stroke {:color "black" :width 10})))))

(tarkkaile! "Kartalla: " tr-alkupiste-kartalla)