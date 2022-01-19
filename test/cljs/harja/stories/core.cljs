(ns harja.stories.core
  (:require [reagent.core :as reagent :refer [atom]]))

(defn main []
  [:div])

(defn render []
  (reagent/render-component [main] (.getElementById js/document "app")))

(render)