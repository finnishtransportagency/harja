(ns harja-laadunseuranta.ui.napit
  (:require [reagent.core :as reagent :refer [atom]]))

(defn- nappi [nimi {:keys [on-click luokat-str] :as optiot}]
  [:button
   {:class (str "nappi " luokat-str)
    :on-click on-click}
   nimi])