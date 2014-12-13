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

; TODO: poista leikkidata kunhan saadaan oikeaa tialle
(def urakat 
  (atom [{:id 1 
          :name "Espoon alueurakka"
          }
         {:id 2 
          :name "Kuhmon alueurakka"
          }{:id 3 
          :name "Oulun alueurakka"
          }
         {:id 4 
          :name "Suomussalmen alueurakka"
          }
         {:id 5 
          :name "Vetelin alueurakka"
          }{:id 6 
          :name "Siikalatvan alueurakka"
          }
         {:id 7 
          :name "Raahe-Ylivieska alueurakka"
          }
         {:id 8 
          :name "Iin alueurakka"
          }{:id 9 
          :name "Kuopion alueurakka"
          }
         ]))

(defn haku-komponentti [lista]
  "haku-komponentti sisältää haku-toiminnallisuuden. Parametrisoituna taipuvainen
   monenlaiseen, kohta puolin..."
  [:div.haku-container
   [:input.haku-input.form-control {:type: "text"}]
   [:div.haku-lista-container
    [:ul.haku-lista
    (for [i @lista]
      ^{:key (:id i)}
      [:li.haku-lista-item {:on-click #(.log js/console (+ (-> % .-target .-className) " valittu"))} 
       [:div.haku-lista-item-nimi 
        (:name i)]])]
    ]
   [:div.haku-tulokset "tulokset"]
   ])

(defn kartta
  "Harjan karttakomponentti"
  []
  [:span
   [:div#sidebar-left.col-sm-4
    [:h5.haku-otsikko "Hae alueurakka kartalta tai listasta"]
    [:div [haku-komponentti urakat]]]
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

