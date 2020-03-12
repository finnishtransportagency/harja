(ns harja.views.urakka.paikkaukset-kustannukset
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.tiedot.urakka.paikkaukset-kustannukset :as tiedot]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.ui.debug :as debug]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.paikkaukset-yhteinen :as yhteinen-view]))

(defn paikkauksien-kokonaishinta-tyomenetelmittain [e! app]
  (let [skeema [{:otsikko "Kohde"
                 :leveys 15
                 :nimi :kohde}
                {:otsikko "Työmenetelmä"
                 :leveys 5
                 :nimi :selite}
                {:otsikko "Valmistumispvm"
                 :leveys 5
                 :nimi :valmistumispvm}
                {:otsikko "Kokonaishinta"
                 :leveys 5
                 :nimi :kokonaishinta}]]
    (fn [e! {:keys [paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa? paikkauksien-kokonaishinta-tyomenetelmittain-grid]}]
      [:div
       [grid/grid
        {:otsikko (if (or paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?)
                    [yleiset/ajax-loader-pieni "Päivitetään listaa.."]
                    "Paikkauksien kokonaishintaiset kustannukset työmenetelmittäin - HUOM! ominaisuus on vasta työnalla!")
         :tunniste :id
         :sivuta 50
         :tyhja "Ei kustannuksia"}
        skeema
        paikkauksien-kokonaishinta-tyomenetelmittain-grid]])))

(defn kustannukset* [e! app]
  (komp/luo
    (komp/ulos #(e! (tiedot/->NakymastaPois)))
    (fn [e! app]
      [:div
       [debug/debug app]
       [yhteinen-view/hakuehdot
        {:nakyma :kustannukset
         :palvelukutsu-onnistui-fn #(e! (tiedot/->KustannuksetHaettu %))}]
      [paikkauksien-kokonaishinta-tyomenetelmittain e! app]])))

(defn kustannukset [ur]
  (komp/luo
    (komp/sisaan #(yhteiset-tiedot/nakyman-urakka ur))
    (fn [_]
      [tuck/tuck tiedot/app kustannukset*])))
