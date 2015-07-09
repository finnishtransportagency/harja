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
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake])
  (:require-macros [harja.atom :refer [reaction<!]]))

(defn sanktion-tiedot
  []

  (let [muokattu tiedot/valittu-sanktio]
    [:div
     [:button.nappi-ensisijainen
      {:on-click #(reset! tiedot/valittu-sanktio nil)}
      "Palaa"]

     [lomake/lomake
      {:luokka :horizontal}
      [{:otsikko "Päivämäärä" :nimi :perintapvm :fmt pvm/pvm-aika :leveys 1}
       {:otsikko "Kohde" :nimi :kohde :hae (comp :kohde :havainto) :leveys 1}
       {:otsikko "Kuvaus" :nimi :kuvaus :hae (comp :kuvaus :havainto) :leveys 3}
       {:otsikko "Tekijä" :nimi :tekija :hae (comp :tekijanimi :havainto) :leveys 1}
       {:otsikko "Päätös" :nimi :paatos :hae (comp :paatos :paatos :havainto) :leveys 2}]
      @muokattu]]))

(defn sanktiolistaus
  []
  [:div.sanktiot
   [urakka-valinnat/urakan-hoitokausi-ja-toimenpide @nav/valittu-urakka]

   [grid/grid
    {:otsikko       "Sanktiot"
     :tyhja         (if @tiedot/haetut-sanktiot "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
     :rivi-klikattu #(reset! tiedot/valittu-sanktio %)}
    [{:otsikko "Päivämäärä" :nimi :perintapvm :fmt pvm/pvm-aika :leveys 1}
     {:otsikko "Kohde" :nimi :kohde :hae (comp :kohde :havainto) :leveys 1}
     {:otsikko "Kuvaus" :nimi :kuvaus :hae (comp :kuvaus :havainto) :leveys 3}
     {:otsikko "Tekijä" :nimi :tekija :hae (comp :tekijanimi :havainto) :leveys 1}
     {:otsikko "Päätös" :nimi :paatos :hae (comp :paatos :paatos :havainto) :leveys 2}]
    @tiedot/haetut-sanktiot
    ]])


(defn sanktiot []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)

    (fn []
      (if @tiedot/valittu-sanktio
        [sanktion-tiedot]
        [sanktiolistaus]))))


  

  
