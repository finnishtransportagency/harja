(ns harja-laadunseuranta.ui.yleiset.napit
  (:require [reagent.core :as reagent :refer [atom]]))

(defn nappi [nimi {:keys [on-click luokat-str disabled ikoni] :as optiot}]
  [:button
   {:class (str "nappi " luokat-str)
    :on-click on-click
    :disabled disabled}
   [:span
    (when ikoni
      [:span
       ikoni
       [:span " "]])
      [:span nimi]]])
