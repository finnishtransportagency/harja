(ns harja.views.vesivaylat.urakka.laadunseuranta
  (:require [reagent.core :refer [atom]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn laadunseuranta []
  [:div {:style {:padding "10px"}}
   [:img {:src "images/harja_favicon.png"}]
   [:div {:style {:color "orange"}} "Work In Progress"]])