(ns harja.views.main
  "Harjan päänäkymä"
  (:require [bootstrap :as bs]
            [reagent.core :refer [atom]]

            [harja.tiedot.istunto :as istunto]
            ))


(def page (atom :kartta))

(defn set-page!
  "Vaihda nykyinen sivu haluttuun."
  [new-page]
  (.log js/console "new page is: "
        (reset! page new-page)))

(defn kayttajatiedot [kayttaja]
  [:a {:href "#"} (:nimi @kayttaja)])

(defn header []
  [bs/navbar {}
     "Harja"
     [:form.navbar-form.navbar-left {:role "search"}
      [:div.form-group
       [:input.form-control {:type "text" :placeholder "Hae..."}]]
      [:button.btn.btn-default {:type "button"} "Hae"]]
     
     [:a {:href "#" :on-click #(set-page! :kartta)} "Kartta"]
     [:a {:href "#" :on-click #(set-page! :urakat)} "Urakat"]
     [:a {:href "#" :on-click #(set-page! :raportit)} "Raportit"]
     
     :right
     [kayttajatiedot istunto/kayttaja]])

(defn footer []
  [:footer {:role "contentinfo"}
   "Liikenneviraston HARJA, FIXME: muuta footer tietoa tänne"])

; TODO: poista leikkidata kunhan saadaan oikeaa tialle
(def urakat 
  (atom [{:id 1 :name "Espoon alueurakka"}
         {:id 2 :name "Kuhmon alueurakka"}
         {:id 3 :name "Oulun alueurakka"}
         {:id 4 :name "Suomussalmen alueurakka"}
         {:id 5 :name "Vetelin alueurakka"}
         {:id 6 :name "Siikalatvan alueurakka"}
         {:id 7 :name "Raahe-Ylivieska alueurakka"}
         {:id 8 :name "Iin alueurakka"}
         {:id 9 :name "Kuopion alueurakka"}]))

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
               (fmt i)]]))]]
       [:div.haku-tulokset "tulokset"]]))
  )

(defn kartta
  "Harjan karttakomponentti"
  []
  [:span
   [:div#sidebar-left.col-sm-4
    [:h5.haku-otsikko "Hae alueurakka kartalta tai listasta"]
    [:div [filtered-listing {:format :name} urakat]]]
   [:div#kartta-container.col-sm-4 "kartta"]
   ])

(defn main
  "Harjan UI:n pääkomponentti"
  []
  [:span
   [header]
   (case @page
     :kartta [:div [kartta]]
     :urakat [:div "jotain urakoita täällä"]
     :raportit [:div "täältä kätevästi raportteihin"])
   [footer]
   ])

