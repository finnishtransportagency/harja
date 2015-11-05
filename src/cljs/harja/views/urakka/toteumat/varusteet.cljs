(ns harja.views.urakka.toteumat.varusteet
  "Urakan 'Toteumat' välilehden 'Varusteet' osio"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.core.async :refer [<! timeout]]
            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.urakka.toteumat.varusteet :as varustetiedot]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.komponentti :as komp]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn toteumataulukko []
  (let [toteumat @varustetiedot/haetut-toteumat]
    [:span
     [grid/grid
      {:otsikko "Varustetoteumat"
       :tyhja   (if @varustetiedot/haetut-toteumat "Toteumia ei löytynyt" [ajax-loader "Haetaan toteumia."])
       :tunniste :id}
      [{:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm :nimi :alkupvm :leveys "20%"}
       {:otsikko "Tunniste" :nimi :tunniste :tyyppi :string :leveys "10%"}
       {:otsikko "Tietolaji" :nimi :tietolaji :tyyppi :string :leveys "10%"}
       {:otsikko "Toimenpide" :nimi :toimenpide :tyyppi :string :leveys "10%"}
       {:otsikko "Tie" :nimi :tie :tyyppi :positiivinen-numero :leveys "10%"}
       {:otsikko "Aosa" :nimi :aosa :tyyppi :positiivinen-numero :leveys "10%"}
       {:otsikko "Aet" :nimi :aet :tyyppi :positiivinen-numero :leveys "10%"}
       {:otsikko "Let" :nimi :let :tyyppi :positiivinen-numero :leveys "10%"}
       {:otsikko "Losa" :nimi :losa :tyyppi :positiivinen-numero :leveys "10%"}]
      toteumat]]))

(defn valinnat []
  [:span ""]) ; FIXME Selvitä mitä filttereitä on

(defn varusteet []
  (komp/luo
    (komp/lippu varustetiedot/nakymassa?)

    (fn []
      [:span
       [kartta/kartan-paikka]
       [valinnat]
       [toteumataulukko]])))
