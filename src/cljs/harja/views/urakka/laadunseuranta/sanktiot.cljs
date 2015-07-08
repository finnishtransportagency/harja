(ns harja.views.urakka.laadunseuranta.sanktiot
  "Sanktioiden listaus"
  (:require [reagent.core :refer [atom] :as r]

            [harja.views.urakka.valinnat :as urakka-valinnat]

            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.sanktiot :as tiedot]
            [harja.tiedot.navigaatio :as nav]

            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.pvm :as pvm])
  (:require-macros [harja.atom :refer [reaction<!]]))

(defn sanktion-tiedot
  []
  [:div
   [:button.nappi-ensisijainen
    {:on-click #(reset! tiedot/valittu-sanktio nil)}
    "Palaa"]

   [:div "Sanktion tiedot tänne"]])

(defn sanktiolistaus
  []
  [:div.sanktiot
   [urakka-valinnat/urakan-hoitokausi-ja-toimenpide @nav/valittu-urakka]

   [grid/grid
    {:otsikko "Sanktiot"
     :tyhja   (if @tiedot/haetut-sanktiot "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
     :rivi-klikattu #(reset! tiedot/valittu-sanktio %)}
    [{:otsikko "Päivämäärä" :nimi :aika :fmt pvm/pvm-aika :leveys 1}
     {:otsikko "Kohde" :nimi :kohde :leveys 1}
     {:otsikko "Kuvaus" :nimi :kuvaus :leveys 3}
     {:otsikko "Tekijä" :nimi :tekija :leveys 1}
     {:otsikko "Päätös" :nimi :paatos :leveys 2}]
    @tiedot/haetut-sanktiot
    ]])


(defn sanktiot []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)

  (fn []
    (if @tiedot/valittu-sanktio
      [sanktion-tiedot]
      [sanktiolistaus]))))


  

  
