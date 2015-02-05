(ns harja.views.main
  "Harjan päänäkymä"
  (:require [bootstrap :as bs]
            [reagent.core :refer [atom]]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.listings :refer [filtered-listing]]
            [harja.ui.leaflet :refer [leaflet]]
            [harja.ui.yleiset :refer [linkki]]
            
            [harja.tiedot.navigaatio :as nav]
            [harja.views.murupolku :as murupolku]
            
            [harja.views.urakat :as urakat]
            [harja.views.raportit :as raportit]
            [harja.views.tilannekuva :as tilannekuva]
            [harja.views.ilmoitukset :as ilmoitukset]
            [harja.views.kartta :as kartta]
            [harja.views.hallinta :as hallinta]))



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
     
   [linkki "Urakat" #(nav/vaihda-sivu! :urakat)]
   [linkki "Raportit" #(nav/vaihda-sivu! :raportit)]
   [linkki "Tilannekuva" #(nav/vaihda-sivu! :tilannekuva)]
   [linkki "Ilmoitukset" #(nav/vaihda-sivu! :ilmoitukset)]
   [linkki "Hallinta" #(nav/vaihda-sivu! :hallinta)]
     
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
  [murupolku/murupolku]
  
  (let [[sisallon-luokka kartan-luokka] (case @nav/kartan-koko
                                          :hidden ["col-sm-12" "hide"]
                                          :S ["col-sm-10" "col-sm-2 kartta-s"]
                                          :M ["col-sm-6" "col-sm-6 kartta-m"]
                                          :L ["hide" "col-sm-12 kartta-l"])]
    [:span
     [:div#sidebar-left {:class sisallon-luokka}
      (case @nav/sivu ;;tänne dynaaminen koko...
        :urakat [urakat/urakat]
        :raportit [raportit/raportit]
        :tilannekuva [tilannekuva/tilannekuva]
        :ilmoitukset [ilmoitukset/ilmoitukset]
        :hallinta [hallinta/hallinta]
        )]
     ;; TODO: kartan containerin koon (col-sm-?) oltava dynaaminen perustuen kartan kokoon joka on navigaatio.cljs:ssä
     [:div#kartta-container {:class kartan-luokka}
      [kartta/kartta]]])
  [footer]
  ])

