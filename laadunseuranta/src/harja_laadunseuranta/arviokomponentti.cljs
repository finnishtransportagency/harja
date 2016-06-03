(ns harja-laadunseuranta.arviokomponentti
  (:require [reagent.core :as reagent :refer [atom]])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(defn arviokomponentti [model]
  [:div.arviokomponentti
   [:nav#btn1 {:class (when (= 1 @model) "active") :on-click #(reset! model 1)} "1"]
   [:nav#btn2 {:class (when (= 2 @model) "active") :on-click #(reset! model 2)} "2"]
   [:nav#btn3 {:class (when (= 3 @model) "active") :on-click #(reset! model 3)} "3"]
   [:nav#btn4 {:class (when (= 4 @model) "active") :on-click #(reset! model 4)} "4"]
   [:nav#btn5 {:class (when (= 5 @model) "active") :on-click #(reset! model 5)} "5"]])

(def testimodel (atom nil))

(defcard kitkamittaus-card
  (fn [model _]
    (reagent/as-element [arviokomponentti model]))
  testimodel
  {:watch-atom true
   :inspect-data true})
