(ns harja.views.urakka.kulut.laskut
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.kulut.laskut :as laskut]
            [tuck.core :as tuck]
            [harja.ui.yleiset :refer [ajax-loader]]))

(defn laskut-listaus []
  (tuck/tuck laskut/tila
    (fn [e! app]
      (komp/luo
        (komp/sisaan #(e! (laskut/->HaeLaskut)))
        (fn [e! {:keys [nakymassa? laskut haku-kaynnissa?] :as app}]
          [:div.laskut
           [grid/grid
            {:otsikko "Laskut"
             :tyhja (if haku-kaynnissa?
                      [ajax-loader "Laskuja haetaan..."]
                      "Ei laskuja")}
            [{:otsikko "Päivämäärä" :nimi :pvm :tyyppi :pvm :leveys "20%"}
             {:otsikko "Toimenpide" :nimi :toimenpide :tyyppi :string :leveys "40%"}
             {:otsikko "Määrä" :nimi :maara :tyyppi :numero :leveys "20%"}]
            laskut]])))))
