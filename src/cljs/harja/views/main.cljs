(ns harja.views.main
  "Harjan päänäkymä"
  (:require [bootstrap :as bs]
            [reagent.core :refer [atom]]))


(def page (atom :kartta))

(defn set-page!
  "Vaihda nykyinen sivu haluttuun."
  [new-page]
  (.log js/console "new page is: "
        (reset! page new-page)))

(defn header []
  [bs/navbar {}
   "Harja"
   [:form.navbar-form.navbar-left {:role "search"}
    [:div.form-group
     [:input.form-control {:type "text" :placeholder "Hae..."}]]
    [:button.btn.btn-default {:type "button"} "Hae"]]

   [:a {:href "#" :on-click #(set-page! :kartta)} "Kartta"]
   [:a {:href "#" :on-click #(set-page! :urakat)} "Urakat"]
   [:a {:href "#" :on-click #(set-page! :raportit)} "Raportit"]])

(defn footer []
  [:footer {:role "contentinfo"}
   "Liikenneviraston HARJA, FIXME: muuta footer tietoa tänne"])

(defn main
  "Harjan UI:n pääkomponentti"
  []
  [:span
   [header]
   (case @page
     :kartta [:div "karttahan se siinä!"]
     :urakat [:div "jotain urakoita täällä"]
     :raportit [:div "täältä kätevästi raportteihin"])
   [footer]
   ])

