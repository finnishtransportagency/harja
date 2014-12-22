(ns harja.ui.listings
  (:require [reagent.core :as reagent :refer [atom]]))

(defn filtered-listing
  "Luettelo, jossa on hakukenttä filtteröinnille.
  opts voi sisältää
  :term      hakutermin atomi
  :selection valitun listaitemin atomi
  :format    funktio jolla itemi muutetaan stringiksi, oletus str
  :haku      funktio jolla haetaan itemistä, kenttä jota vasten hakusuodatus (oletus :name)
  :on-select funktio, jolla valinta tehdään (oletuksena reset! valinta-atomille)

  list sisältää luettelon josta hakea."
  [opts list]
  (let [term (or (:term opts) (atom ""))
        valittu (or (:selection opts) (atom nil))
        fmt (or (:format opts) str)

        ;; Itemin hakukenttä, oletuksena :name
        haku (or (:haku opts) :name)

        ;; Jos valinnan tekemiseen on määritelty funktio, käytä sitä. Muuten reset! valinta atomille.
        on-select (or (:on-select opts) #(reset! valittu %))]
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
           (for [i (filter #(not= (.indexOf (.toLowerCase (haku %)) (.toLowerCase term)) -1) @list)]
             ^{:key (:id i)}
             [:li.haku-lista-item
              {:on-click #(do
                            (on-select i)
                            (.log js/console (str " selected on " selected)))
               :class (when (= i selected) "selected")}  
              [:div.haku-lista-item-nimi 
               (fmt i)]]))]]])))
