(ns harja.ui.listings
  (:require [reagent.core :as reagent :refer [atom]]))

(defn filtered-listing
  "Luettelo, jossa on hakukenttä filtteröinnille.
  opts voi sisältää
  :term hakutermin atomi
  :selection valitun listaitemin atomi
  :format funktio jolla itemi muutetaan stringiksi, oletus str
  list sisältää luettelon josta hakea."
  [opts list]
  (let [term (or (:term opts) (atom ""))
        valittu (or (:selection opts) (atom nil))
        fmt (or (:format opts) str)]
    (fn []
      [:div.haku-container
       [:input.haku-input.form-control
        {:type "text"
         :value @term
         :on-change #(do
                       (reset! term (.-value (.-target %)))
                       (.log js/console (-> % .-target .-value)))}]
       [:div.haku-lista-container
        [:ul.haku-lista
         (let [selected @valittu term @term]
           (for [i (filter #(not= (.indexOf (.toLowerCase (% :name)) (.toLowerCase term)) -1) @list)]
             ^{:key (:id i)}
             [:li.haku-lista-item
              {:on-click #(do
                            (reset! valittu i)
                            (.log js/console (str " selected on " selected)))
               :class (when (= i selected) "selected")}  
              [:div.haku-lista-item-nimi 
               (fmt i)]]))]]])))