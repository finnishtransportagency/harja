(ns harja.views.vesivaylat.urakka.turvalaitteet
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.vesivaylat.urakka.turvalaitteet :as tiedot]
            [tuck.core :refer [tuck]]
            [harja.ui.komponentti :as komp])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn turvalaitteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div {:style {:padding "10px"}}
       [:img {:src "images/harja_favicon.png"}]
       [:div {:style {:color "orange"}} "Työmaa"]])))

(defn turvalaitteet []
  [tuck tiedot/tila turvalaitteet*])
