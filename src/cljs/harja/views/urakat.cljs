(ns harja.views.urakat
  "Harjan näkymä, jossa näytetään karttaa sekä kontekstisidonnaiset asiat."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :as async :refer [chan <! >!]]
            [bootstrap :as bs]
            [harja.ui.listings :refer [filtered-listing]]
            [harja.ui.leaflet :refer [leaflet] :as leaflet]
            [harja.ui.yleiset :as yleiset]
            
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.urakat :as ur]
            
            [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]))


;; Joitain värejä... voi keksiä paremmat tai "oikeat", jos sellaiset on tiedossa
(def +varit+ ["#E04836" "#F39D41" "#8D5924" "#5696BC" "#2F5168" "wheat" "teal"])

(defn wms-url
  "Määrittelee WMS osoitteen, suhteellisena sovelluksen osoitteeseen."
  []
  (let [l (.-location js/document)
        port (.-port l)]
    (str (.-protocol l)
         "//"
         (.-hostname l)
         (when-not (or (= port "80")
                       (= port "443"))
           (str ":" port))
         "/wms/rasteriaineistot/image?")))


(defn urakat
  "Harjan karttasivu."
  []
  [:span
   ;; TODO: urakkasivun koon (col-sm-?) oltava dynaaminen perustuen kartan kokoon joka on navigaatio.cljs:ssä
   
    (let [v-hal @nav/valittu-hallintayksikko
          v-ur @nav/valittu-urakka]
      (if-not v-hal
        ;; Hallintayksikköä ei ole valittu: näytetään lista hallintayksiköistä
        [:span
         [:h5.haku-otsikko "Hae hallintayksikkö kartalta tai listasta"]
         [:div
          ^{:key "hy-lista"}
          [filtered-listing {:format :nimi :haku :nimi
                             :selection nav/valittu-hallintayksikko
                             :on-select nav/valitse-hallintayksikko}
           hal/hallintayksikot]]]
        
        ;; Hallintayksikko on valittu, mutta urakkaa ei: näytetään luettelossa urakat
        (if-not v-ur
          ;;(let [urakat (ur/hallintayksikon-urakat v-hal)]
            (if (nil? @nav/urakkalista)
              [yleiset/ajax-loader "Urakoita haetaan..."]
              [:span
               [:h5.haku-otsikko "Hae urakka kartalta tai listasta"]
               [:div
                ^{:key "ur-lista"}
                [filtered-listing {:format :nimi :haku :nimi
                                   :selection nav/valittu-urakka
                                   :on-select nav/valitse-urakka}
                 nav/urakkalista]]])
          
            ;; Urakka valittu, tähän kaikki urakan komponentit
              [:span
   [:h3 "Raportit"]
   [bs/dropdown-panel {} "Työ- ja toteumaraportit" 
    [:div "hei, ihan urakkakin valittuna? hieno juttu! ei täällä vielä mitään tosin ole"]]
   [bs/dropdown-panel {} "Laadunseuranta" "ei ole mitään"]
   [bs/dropdown-panel {} "Poikkeamaraportit" "ei poikkeamia"]
   
   
   [:h3 "Suunnittelu"]
   [bs/dropdown-panel {} "Kustannussuunnitelma: kokonaishintaiset työt" "ei vielä"]
   [bs/dropdown-panel {} "Kustannussuunnitelma: yksikköhintaiset työt" "ei vielä"]
   [bs/dropdown-panel {} "Materiaalisuunnitelma" "ei vielä"]
   
   [:h3 "Toteumat"]
   [bs/dropdown-panel {} "Toteutuneet työt" "ei vielä"]
   [bs/dropdown-panel {} "Toteutuneet materiaalit" "ei vielä"]
   
   ])))])
           
(comment
  [bs/tabs {}
             "Raportit"
             ^{:key "raportit"}
             [:div 
              [bs/dropdown-panel {} "Työ- ja toteumaraportit" 
               [:div "hei, ihan urakkakin valittuna? hieno juttu! ei täällä vielä mitään tosin ole"]]
              [bs/dropdown-panel {} "Laadunseuranta" "ei ole mitään"]
              [bs/dropdown-panel {} "Poikkeamaraportit" "ei poikkeamia"]]
             "Suunnittelu"
             ^{:key "suunnittelu"}
             [:div 
              [bs/dropdown-panel {} "Kustannussuunnitelma: kokonaishintaiset työt" "ei vielä"]
              [bs/dropdown-panel {} "Kustannussuunnitelma: yksikköhintaiset työt" "ei vielä"]
              [bs/dropdown-panel {} "Materiaalisuunnitelma" "ei vielä"]]

             "Toteumat"
             ^{:key "toteumat"}
             [:div
              [bs/dropdown-panel {} "Toteutuneet työt" "ei vielä"]
              [bs/dropdown-panel {} "Toteutuneet materiaalit" "ei vielä"]]]
)
