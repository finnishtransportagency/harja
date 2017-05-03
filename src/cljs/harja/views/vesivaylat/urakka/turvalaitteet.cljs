(ns harja.views.vesivaylat.urakka.turvalaitteet
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.vesivaylat.urakka.turvalaitteet :as tiedot]
            [tuck.core :refer [tuck]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn turvalaitteet* []
  [:div {:style {:padding "10px"}}
   [:img {:src "images/harja_favicon.png"}]
   [:div {:style {:color "orange"}} "Work In Progress"]])

(defn turvalaitteet []
  [tuck tiedot/tila turvalaitteet*])