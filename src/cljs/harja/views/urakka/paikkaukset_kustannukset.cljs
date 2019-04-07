(ns harja.views.urakka.paikkaukset-kustannukset
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.tiedot.urakka.paikkaukset-kustannukset :as tiedot]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.paikkaukset-yhteinen :as yhteinen-view]))

(defn yksikkohintaiset-kustannukset
  [e! app]
  (let [skeema [{:otsikko "Kirjaus\u00ADaika"
                 :leveys 3
                 :nimi :kirjattu
                 :fmt yhteinen-view/aika-formatteri}
                {:otsikko "Selite"
                 :leveys 5
                 :nimi :selite}
                {:otsikko "Määrä"
                 :leveys 2
                 :nimi :maara}
                {:otsikko "Yksikkö"
                 :leveys 2
                 :nimi :yksikko}
                {:otsikko "Yksikkö\u00ADhinta"
                 :leveys 2
                 :nimi :yksikkohinta}]]
    (fn [_ {:keys [paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?
                   yksikkohintaiset-grid]}]
      [:div
       [grid/grid
        {:otsikko (if (or paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?)
                    [yleiset/ajax-loader-pieni "Päivitetään listaa.."]
                    "Paikkauksien yksikköhintaiset kustannukset")
         :tunniste :paikkaustoteuma-id
         :salli-valiotsikoiden-piilotus? true
         :sivuta 50
         :tyhja "Ei yksikköhintaisia kustannuksia"}
        skeema
        yksikkohintaiset-grid]])))

(defn kokonaishintaiset-kustannukset [e! app]
  (let [skeema [{:otsikko "Kirjaus\u00ADaika"
                 :leveys 5
                 :nimi :kirjattu
                 :fmt yhteinen-view/aika-formatteri}
                {:otsikko "Selite"
                 :leveys 5
                 :nimi :selite}
                {:otsikko "Hinta"
                 :leveys 5
                 :nimi :hinta}]]
    (fn [e! {:keys [paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa? kokonaishintaiset-grid]}]
      [:div
       [grid/grid
        {:otsikko (if (or paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?)
                    [yleiset/ajax-loader-pieni "Päivitetään listaa.."]
                    "Paikkauksien kokonaishintaiset kustannukset")
         :salli-valiotsikoiden-piilotus? true
         :tunniste :paikkaustoteuma-id
         :sivuta 50
         :tyhja "Ei kokonaishintaisia kustannuksia"}
        skeema
        kokonaishintaiset-grid]])))

(defn kustannukset* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymaan (partial yhteinen-view/otsikkokomponentti
                                                       "Siirry toimenpiteisiin"
                                                       (fn [paikkauskohde-id]
                                                         (e! (tiedot/->SiirryToimenpiteisiin paikkauskohde-id))))))
                      #(e! (tiedot/->NakymastaPois)))
    (fn [e! app]
      [:div
       [debug/debug app]
       [yhteinen-view/hakuehdot
        {:nakyma :kustannukset
         :palvelukutsu-onnistui-fn #(e! (tiedot/->KustannuksetHaettu %))}]
       [kokonaishintaiset-kustannukset e! app]
       [yksikkohintaiset-kustannukset e! app]])))

(defn kustannukset [ur]
  (komp/luo
    (komp/sisaan #(yhteiset-tiedot/nakyman-urakka ur))
    (fn [_]
      [tuck/tuck tiedot/app kustannukset*])))
