(ns harja.views.vesivaylat.urakka.toimenpiteet.yksikkohintaiset
  (:require [reagent.core :refer [atom]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn yksikkohintaiset-toimenpiteet []
  [:div {:style {:padding "10px"}}
   [:img {:src "images/harja_favicon.png"}]
   [:div {:style {:color "orange"}} "Work In Progress"]])