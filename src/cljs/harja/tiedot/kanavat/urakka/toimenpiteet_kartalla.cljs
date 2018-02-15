(ns harja.tiedot.kanavat.urakka.toimenpiteet-kartalla
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce karttataso-kohteet (atom false))
(defonce kohteet-kartalla
  (reaction
    (when @karttataso-kohteet
      ;(kartalla-esitettavaan-muotoon
      ;  (map #(-> %
      ;            (set/rename-keys {::osa/sijainti :sijainti})
      ;            (assoc :tyyppi-kartalla :kohteenosa))
      ;       (:haetut-kohteenosat @tila))
      ;  #(osa-kuuluu-valittuun-kohteeseen? % @tila))
      )))