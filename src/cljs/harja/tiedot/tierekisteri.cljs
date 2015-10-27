(ns harja.tiedot.tierekisteri
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]])
  (:require-macros
    [reagent.ratom :refer [reaction run!]]
    [cljs.core.async.macros :refer [go]]))

(def karttataso-tr-alkuosoite (atom true))

(def valittu-alkupiste (atom nil))
(def tr-alkupiste-kartalla (reaction
                             (when (and @karttataso-tr-alkuosoite @valittu-alkupiste)
                               [{:sijainti (assoc @valittu-alkupiste
                                             :alue {:type   :circle
                                                    :color  "green"
                                                    :coordinates (:coordinates @valittu-alkupiste)
                                                    :radius 5000
                                                    :stroke {:color "black" :width 10}})}])))

(tarkkaile! "TR-alkuosoite kartalla: " tr-alkupiste-kartalla)