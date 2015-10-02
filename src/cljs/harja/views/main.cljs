(ns harja.views.main
  "Harjan päänäkymä"
  (:require [bootstrap :as bs]
            [reagent.core :refer [atom]]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.komponentti :as komp]
            [harja.ui.listings :refer [suodatettu-lista]]
            [harja.ui.leaflet :refer [leaflet]]
            [harja.ui.yleiset :refer [linkki elementti-idlla sijainti] :as yleiset]
            [harja.ui.modal :refer [modal-container]]
            [harja.ui.viesti :refer [viesti-container]]
            [harja.tiedot.navigaatio :as nav]
            [harja.loki :refer [log logt]]
            [harja.views.murupolku :as murupolku]
            [harja.views.haku :as haku]
            [harja.fmt :as fmt]

            [harja.views.urakat :as urakat]
            [harja.views.raportit :as raportit]
            [harja.views.tilannekuva.tilannekuva :as tilannekuva]
            [harja.views.ilmoitukset :as ilmoitukset]
            [harja.views.kartta :as kartta]
            [harja.views.hallinta :as hallinta]
            [harja.views.about :as about]))



(defn kayttajatiedot [kayttaja]
  (let [{:keys [etunimi sukunimi]} @kayttaja]
    ;; FIXME: mitä oman nimen klikkaamisesta pitäisi tapahtua?
    [:a {:href "#" :on-click #(.preventDefault %)} etunimi " " sukunimi]))

(defn header [s]
  [bs/navbar {}
   [:img#harja-brand-icon {:alt      "HARJA"
                           :src      "images/harja_logo_soft.svg"
                           :on-click #(.reload js/window.location)}]
   [haku/haku]

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
  [:footer#footer.container {:role "contentinfo"}           ;; ÄLÄ pistä top korkeutta footerille, sen tien päässä on vain kyyneliä
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
  (komp/luo
    (fn []
      (let [sivu @nav/sivu
            aikakatkaistu? @istunto/istunto-aikakatkaistu
            korkeus @yleiset/korkeus
            kayttaja @istunto/kayttaja]

        (if aikakatkaistu?
          [:div "Harjan käyttö aikakatkaistu kahden tunnin käyttämättömyyden takia. Lataa sivu uudelleen."]
          (if (nil? kayttaja)
            [ladataan]
            (if (or (:poistettu kayttaja)
                    (empty? (:roolit kayttaja)))
              [:div.ei-kayttooikeutta "Ei Harja käyttöoikeutta. Ota yhteys pääkäyttäjään."]

              [:div
               [:div.container
                [header sivu]]

               [:div.container
                [murupolku/murupolku]]

               

               [:div.container.sisalto {:style {:min-height (max 200 (- korkeus 220))}} ; contentin minimikorkeus pakottaa footeria alemmas
                [:div.row.row-sisalto
                 [:div {:class (when-not (= sivu :tilannekuva) "col-sm-12")}
                  (case sivu
                    :urakat [urakat/urakat]
                    :raportit [raportit/raportit]
                    :ilmoitukset [ilmoitukset/ilmoitukset]
                    :hallinta [hallinta/hallinta]
                    :tilannekuva [tilannekuva/tilannekuva]
                    :about [about/about])]]]

               
               
               [footer]
               [modal-container]
               [viesti-container]

               ;; kartta luodaan ja liitetään DOM:iin tässä. Se asemoidaan muualla #kartan-paikka divin avulla
               [:div#kartta-container 
                [kartta/kartta]]])))))))

