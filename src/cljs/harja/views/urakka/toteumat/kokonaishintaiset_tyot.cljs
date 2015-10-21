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
            [harja.tiedot.urakka.toteumat.kokonaishintaiset-toteumat :as tiedot]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.komponentti :as komponentti]
            )
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
       :tyhja   (if @toteumat/haetut-toteumat "Toteumia ei löytynyt" [ajax-loader "Haetaan toteumia."])}
      [{:otsikko "Tapahtunut" :nimi :suorittajan_ytunnus :leveys "15%" :tyyppi :string}]
      @toteumat/haetut-toteumat]]))

(defn kokonaishintaiset-toteumat []
  (komponentti/luo
    (komponentti/lippu toteumat/kokonaishintaiset-toteumat-nakymassa? toteumat/karttataso-kokonaishintainen-toteuma)
    (fn []
      [:span
       [kartta/kartan-paikka]
       [kokonaishintaisten-toteumien-listaus]])))
