(ns harja.views.urakat
  "Harjan näkymä, jossa näytetään karttaa sekä kontekstisidonnaiset asiat."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :as async :refer [chan <! >!]]
            [bootstrap :as bs]
            [harja.ui.listings :refer [suodatettu-lista]]
            [harja.ui.leaflet :refer [leaflet] :as leaflet]
            [harja.ui.yleiset :as yleiset]
            
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.urakat :as ur]
            [harja.loki :refer [log]]
            
            [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]

            [harja.views.urakka :as urakka])
  (:require-macros [cljs.core.async.macros :refer [go]]))



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
          v-ur @nav/valittu-urakka
          urakkalista @nav/urakkalista]
      (if-not v-hal
        ;; Hallintayksikköä ei ole valittu: näytetään lista hallintayksiköistä
        [:span
         [:h5.haku-otsikko "Valitse hallintayksikkö"]
         [:div
          ^{:key "hy-lista"}
          [suodatettu-lista {:format :nimi :haku :nimi
                             :selection nav/valittu-hallintayksikko
                             :on-select nav/valitse-hallintayksikko
                             :aputeksti "Kirjoita hallintayksikön nimi tähän"}
           @hal/hallintayksikot]]]
        
        ;; Hallintayksikko on valittu, mutta urakkaa ei: näytetään luettelossa urakat
        (if-not v-ur
            (if (nil? urakkalista)
              [yleiset/ajax-loader "Urakoita haetaan..."]
              [:span
               [:h5.haku-otsikko "Valitse hallintayksikön urakka"]
               [:div
                ^{:key "ur-lista"}
                [suodatettu-lista {:format :nimi :haku :nimi
                                   :selection nav/valittu-urakka
                                   :on-select nav/valitse-urakka
                                   :aputeksti "Kirjoita urakan nimi tähän"}
                 @nav/suodatettu-urakkalista]]])
          
            ;; Urakka valittu, tähän kaikki urakan komponentit
            [urakka/urakka v-ur])))])
