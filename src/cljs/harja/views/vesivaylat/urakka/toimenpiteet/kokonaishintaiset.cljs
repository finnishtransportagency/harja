(ns harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.ui.komponentti :as komp])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn kokonaishintaiset-toimenpiteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div {:style {:padding "10px"}}
       [:img {:src "images/harja_favicon.png"}]
       [:div {:style {:color "orange"}} "Työmaa"]])))

(defn kokonaishintaiset-toimenpiteet []
  [tuck tiedot/tila kokonaishintaiset-toimenpiteet*])