(ns harja.views.main
  "Harjan päänäkymä"
  (:require [bootstrap :as bs]
            [reagent.core :refer [atom]]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.listings :refer [suodatettu-lista]]
            [harja.ui.leaflet :refer [leaflet]]
            [harja.ui.yleiset :refer [linkki] :as yleiset]
            [harja.ui.modal :refer [modal-container]]
            
            [harja.tiedot.navigaatio :as nav]
            
            [harja.views.murupolku :as murupolku]
            
            [harja.views.urakat :as urakat]
            [harja.views.raportit :as raportit]
            [harja.views.tilannekuva :as tilannekuva]
            [harja.views.ilmoitukset :as ilmoitukset]
            [harja.views.kartta :as kartta]
            [harja.views.hallinta :as hallinta]
            [harja.views.about :as about]))



(defn kayttajatiedot [kayttaja]
  (let [{:keys [etunimi sukunimi]} @kayttaja]
    ;; FIXME: mitä oman nimen klikkaamisesta pitäisi tapahtua?
    [:a {:href "#" :on-click #(.preventDefault %)} etunimi  " " sukunimi]))

(defn header [s]
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

   ;; FIXME: active luokka valitulle sivulle
   [:ul#sivut.nav.nav-pills

    [:li {:role "presentation" :class (when (= s :urakat) "active")}
     [linkki "Urakat" #(nav/vaihda-sivu! :urakat)]]

    [:li {:role "presentation" :class (when (= s :raportit) "active")}
     [linkki "Raportit" #(nav/vaihda-sivu! :raportit)]]

    [:li {:role "presentation" :class (when (= s :tilannekuva) "active")}
     [linkki "Tilannekuva" #(nav/vaihda-sivu! :tilannekuva)]]

    [:li {:role "presentation" :class (when (= s :ilmoitukset) "active")}
     [linkki "Ilmoitukset" #(nav/vaihda-sivu! :ilmoitukset)]]

    [:li {:role "presentation" :class (when (= s :hallinta) "active")}
     [linkki "Hallinta" #(nav/vaihda-sivu! :hallinta)]]]
     :right
     [kayttajatiedot istunto/kayttaja]])

(defn footer []
   [:footer#footer.container {:role "contentinfo"} ;; ÄLÄ pistä top korkeutta footerille, sen tien päässä on vain kyyneliä
     [:div#footer-content
      [:a {:href "http://www.liikennevirasto.fi"}
       "Liikennevirasto, vaihde 0295 34 3000, faksi 0295 34 3700, etunimi.sukunimi(at)liikennevirasto.fi"]
      [:div
       [linkki "Tietoja" #(nav/vaihda-sivu! :about)]]]])

(defn ladataan []
  [:div {:style {:position "absolute" :top "50%" :left "50%"}}
   [:div {:style {:position "relative" :left "-50px" :top "-20px"}}
    [yleiset/ajax-loader "Ladataan..."]]])

(defn main
  "Harjan UI:n pääkomponentti"
  []
  (let [sivu @nav/sivu
        kartan-koko @nav/kartan-koko
        korkeus @yleiset/korkeus
        kayttaja @istunto/kayttaja]

    (if (nil? kayttaja)
      [ladataan]
      (if (or (:poistettu kayttaja)
              (empty? (:roolit kayttaja)))
        [:div.ei-kayttooikeutta "Ei Harja käyttöoikeutta. Ota yhteys pääkäyttäjään."]
      
        [:span
         [:div.container
          [header sivu]]
         [:div.container
          [murupolku/murupolku]]
  
         (let [[sisallon-luokka kartan-luokka] 
               (case kartan-koko
                 :hidden ["col-sm-12" "hide"]
                 :S ["col-sm-12" "kulma-kartta"] ;piilota-kartta"]
                 :M ["col-sm-6" "col-sm-6"]
                 :L ["hide" "col-sm-12"])]
           ;; Bootstrap grid system: http://getbootstrap.com/css/#grid
           [:div.container {:style {:min-height (max 200 (- korkeus 220))}} ; contentin minimikorkeus pakottaa footeria alemmas
            [:div.row
             [:div#sidebar-left {:class sisallon-luokka}
              (case sivu
                :urakat [urakat/urakat]
                :raportit [raportit/raportit]
                :tilannekuva [tilannekuva/tilannekuva]
                :ilmoitukset [ilmoitukset/ilmoitukset]
                :hallinta [hallinta/hallinta]
                :about [about/about]
                )]
             [:div#kartta-container {:class kartan-luokka}
              [kartta/kartta]]]])
         [footer]
         [modal-container]
         ]))))

