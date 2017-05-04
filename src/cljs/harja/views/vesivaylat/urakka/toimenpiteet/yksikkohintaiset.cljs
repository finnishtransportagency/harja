(ns harja.views.vesivaylat.urakka.toimenpiteet.yksikkohintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [harja.ui.komponentti :as komp])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn yksikkohintaiset-toimenpiteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div {:style {:padding "10px"}}
       [:img {:src "images/harja_favicon.png"}]
       [:div {:style {:color "orange"}} "Työmaa"]])))

(defn yksikkohintaiset-toimenpiteet []
  [tuck tiedot/tila yksikkohintaiset-toimenpiteet*])