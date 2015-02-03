(ns harja.views.main
  "Harjan päänäkymä"
  (:require [bootstrap :as bs]
            [reagent.core :refer [atom]]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.listings :refer [filtered-listing]]
            [harja.ui.leaflet :refer [leaflet]]

            [harja.views.urakat :as urakat]
            [harja.views.hallinta :as hallinta]

            [harja.tiedot.navigaatio :refer [sivu vaihda-sivu!]]
            ))



(defn kayttajatiedot [kayttaja]
  [:a {:href "#"} (:nimi @kayttaja)])

(defn header []
  [bs/navbar {}
     [:img {
            :id "harja-brand-icon"
            :alt "HARJA"
            :src "images/harja-brand-text.png"
            :on-click #(.reload js/window.location)}]
     [:form.navbar-form.navbar-left {:role "search"}
      [:div.form-group
       [:input.form-control {:type "text" :placeholder "Hae..."}]]
      [:button.btn.btn-default {:type "button"} "Hae"]]
     
   [:a {:href "#" :on-click #(vaihda-sivu! :urakat)} "Urakat"]
   [:a {:href "#" :on-click #(vaihda-sivu! :raportit)} "Raportit"]
   [:a {:href "#" :on-click #(vaihda-sivu! :hallinta)} "Hallinta"]
     
     :right
     [kayttajatiedot istunto/kayttaja]])

(defn footer []
  [:footer#footer {:role "contentinfo"}
   [:div#footer-wrap
    [:a {:href "http://www.liikennevirasto.fi"}
     "Liikennevirasto, vaihde 0295 34 3000, faksi 0295 34 3700, etunimi.sukunimi(at)liikennevirasto.fi"]]])



(defn main
  "Harjan UI:n pääkomponentti"
  []
  [:span
   [header]
   (case @sivu
     :urakat [urakat/urakat]
     :raportit [:div "täältä kätevästi raportteihin"]
     :hallinta [hallinta/hallinta]
     )
   [footer]
   ])

