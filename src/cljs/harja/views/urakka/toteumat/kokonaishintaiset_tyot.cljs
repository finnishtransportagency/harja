(ns harja.views.urakka.toteumat.kokonaishintaiset-tyot
  "Urakan 'Toteumat' välilehden 'Kokonaishintaiset työt' osio"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.core.async :refer [<! timeout]]
            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot :as tiedot]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.komponentti :as komponentti]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn kokonaishintaisten-toteumien-listaus
  "Kokonaishintaisten töiden toteumat"
  []
  (let [urakka @navigaatio/valittu-urakka]
    [:div.sanktiot
     [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide urakka]
     [grid/grid
      {:otsikko "Kokonaishintaisten töiden toteumat"
       :tyhja   (if @tiedot/haetut-toteumat "Toteumia ei löytynyt" [ajax-loader "Haetaan toteumia."])}
      [{:otsikko "Alkanut" :tyyppi :pvm :fmt pvm/pvm :nimi :alkanut :leveys "10%"}
       {:otsikko "Päättynyt" :tyyppi :pvm :fmt pvm/pvm :nimi :paattynyt :leveys "10%"}
       {:otsikko "Tehtävä" :tyyppi :string :nimi :nimi :leveys "10%"}
       {:otsikko "Määrä" :tyyppi :numero :nimi :maara :leveys "10%"}
       {:otsikko "Yksikkö" :tyyppi :numero :nimi :yksikko :leveys "10%"}
       {:otsikko "Lähde" :nimi :lahde :hae #(if (:jarjestelmanlisaama %) "Urak. järj." "Harja") :tyyppi :string :leveys "10%"}]
      @tiedot/haetut-toteumat]]))

(defn kokonaishintaiset-toteumat []
  (komponentti/luo
    (komponentti/lippu tiedot/nakymassa? tiedot/karttataso)
    (fn []
      [:span
       [kartta/kartan-paikka]
       [kokonaishintaisten-toteumien-listaus]])))
