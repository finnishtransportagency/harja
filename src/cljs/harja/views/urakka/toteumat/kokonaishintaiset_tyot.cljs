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
            [harja.ui.komponentti :as komp]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn tee-taulukko []
  (let [toteumat @tiedot/haetut-toteumat]
    [:span
     [grid/grid
      {:otsikko "Kokonaishintaisten töiden toteumat"
       :tyhja   (if @tiedot/haetut-toteumat "Toteumia ei löytynyt" [ajax-loader "Haetaan toteumia."])
       :tunniste :toteumaid}
      [{:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm :nimi :alkanut :leveys "20%"}
       {:otsikko "Tehtävä" :tyyppi :string :nimi :nimi :leveys "40%"}
       {:otsikko "Määrä" :tyyppi :numero :nimi :maara :leveys "10%"}
       {:otsikko "Yksikkö" :tyyppi :numero :nimi :yksikko :leveys "10%"}
       {:otsikko "Lähde" :nimi :lahde :hae #(if (:jarjestelmanlisaama %) "Urak. järj." "Harja") :tyyppi :string :leveys "20%"}]
      (take 500 toteumat)]
     (when (> (count toteumat) 500)
       [:div.alert-warning "Toteumia löytyi yli 500. Tarkenna hakurajausta."])]))

(defn tee-valinnat []
  [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide @navigaatio/valittu-urakka]
  (let [urakka @navigaatio/valittu-urakka]
    [:span
     (urakka-valinnat/urakan-sopimus urakka)
     (urakka-valinnat/urakan-hoitokausi-ja-kuukausi urakka)
     (urakka-valinnat/urakan-kokonaishintainen-toimenpide-ja-tehtava)]))

(defn kokonaishintaisten-toteumien-listaus
  "Kokonaishintaisten töiden toteumat"
  []
  [:div.sanktiot
   (tee-valinnat)
   (tee-taulukko)])

(defn kokonaishintaiset-toteumat []
  (komp/luo
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-kokonaishintainen-toteuma)
    (komp/kuuntelija :toteuma-klikattu #(reset! tiedot/valittu-toteuma %2))
    (komp/ulos (kartta/kuuntele-valittua! tiedot/valittu-toteuma)) ;;Palauttaa funktion jolla kuuntelu lopetetaan

    (fn []
      [:span
       [kartta/kartan-paikka]
       [kokonaishintaisten-toteumien-listaus]])))
