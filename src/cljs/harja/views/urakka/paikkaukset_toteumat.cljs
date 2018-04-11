(ns harja.views.urakka.paikkaukset-toteumat
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.tiedot.urakka.paikkaukset-toteumat :as tiedot]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.views.urakka.paikkaukset-yhteinen :as yhteinen-view]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn hakuehdot [e! {:keys [valinnat] :as app}]
  (let [tr-atomi (partial tiedot/valinta-wrap e! app)
        aikavali-atomi (partial tiedot/valinta-wrap e! app)]
    [:span
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Tierekisteriosoite"
       :kentta-params {:tyyppi :tierekisteriosoite
                       :tr-otsikot? false}
       :arvo-atom (tr-atomi :tr)
       :tyylit {:width "fit-content"}}]
     [valinnat/aikavali (aikavali-atomi :aikavali)]
     [:span.label-ja-kentta
      [:span.kentan-otsikko "Näytettävät paikkauskohteet"]
      [:div.kentta
       [valinnat/checkbox-pudotusvalikko
        (:urakan-paikkauskohteet valinnat)
        (fn [paikkauskohde valittu?]
          (e! (tiedot/->PaikkausValittu paikkauskohde valittu?)))
        [" paikkauskohde valittu" " paikkauskohdetta valittu"]]]]]))

(defn paikkaukset-vetolaatikko
  [e! {tienkohdat ::paikkaus/tienkohdat materiaalit ::paikkaus/materiaalit id ::paikkaus/id :as rivi}
   app]
  (let [nayta-numerot #(apply str (interpose ", " %))
        tienkohdat-skeema [{:otsikko "Ajo\u00ADrata"
                            :leveys 1
                            :nimi ::paikkaus/ajorata}
                           {:otsikko "Reu\u00ADnat"
                            :leveys 1
                            :nimi ::paikkaus/reunat
                            :fmt nayta-numerot}
                           {:otsikko "Ajo\u00ADurat"
                            :leveys 1
                            :nimi ::paikkaus/ajourat
                            :fmt nayta-numerot}
                           {:otsikko "Ajoura\u00ADvälit"
                            :leveys 1
                            :nimi ::paikkaus/ajouravalit
                            :fmt nayta-numerot}
                           {:otsikko "Kes\u00ADkisau\u00ADmat"
                            :leveys 1
                            :nimi ::paikkaus/keskisaumat
                            :fmt nayta-numerot}]
        materiaalit-skeema [{:otsikko "Esiin\u00ADtymä"
                             :leveys 1
                             :nimi ::paikkaus/esiintyma}
                            {:otsikko "Kuu\u00ADlamyl\u00ADly\u00ADarvo"
                             :leveys 2
                             :nimi ::paikkaus/kuulamyllyarvo}
                            {:otsikko "Muoto\u00ADarvo"
                             :leveys 2
                             :nimi ::paikkaus/muotoarvo}
                            {:otsikko "Side\u00ADaine\u00ADtyyp\u00ADpi"
                             :leveys 2
                             :nimi ::paikkaus/sideainetyyppi}
                            {:otsikko "Pitoi\u00ADsuus"
                             :leveys 1
                             :nimi ::paikkaus/pitoisuus}
                            {:otsikko "Lisä\u00ADaineet"
                             :leveys 2
                             :nimi ::paikkaus/lisa-aineet}]]
    [:div
     [grid/grid
      {:otsikko "Tienkohdat"
       :tunniste ::paikkaus/tienkohta-id
       :sivuta grid/vakiosivutus
       :tyhja "Ei tietoja"}
      tienkohdat-skeema
      tienkohdat]
     [grid/grid
      {:otsikko "Materiaalit"
       :tunniste ::paikkaus/materiaali-id
       :sivuta grid/vakiosivutus
       :tyhja "Ei tietoja"}
      materiaalit-skeema
      materiaalit]]))

(defn paikkaukset [e! app]
  (let [tierekisteriosoite-sarakkeet [nil
                                      {:nimi ::tierekisteri/tie}
                                      nil nil
                                      {:nimi ::tierekisteri/aosa}
                                      {:nimi ::tierekisteri/aet}
                                      {:nimi ::tierekisteri/losa}
                                      {:nimi ::tierekisteri/let}]
        skeema (into []
                     (concat
                       [{:tyyppi :vetolaatikon-tila :leveys 1}]
                       (yllapitokohteet/tierekisteriosoite-sarakkeet 5 tierekisteriosoite-sarakkeet)
                       [{:otsikko "Alku\u00ADaika"
                         :leveys 10
                         :nimi ::paikkaus/alkuaika
                         :fmt yhteinen-view/aika-formatteri}
                        {:otsikko "Loppu\u00ADaika"
                         :leveys 10
                         :nimi ::paikkaus/loppuaika
                         :fmt yhteinen-view/aika-formatteri}
                        {:otsikko "Työ\u00ADmene\u00ADtelmä"
                         :leveys 10
                         :nimi ::paikkaus/tyomenetelma}
                        {:otsikko "Massa\u00ADtyyp\u00ADpi"
                         :leveys 10
                         :nimi ::paikkaus/massatyyppi}
                        {:otsikko "Leveys"
                         :leveys 5
                         :nimi ::paikkaus/leveys}
                        {:otsikko "Massa\u00ADmenek\u00ADki"
                         :leveys 5
                         :nimi ::paikkaus/massamenekki}
                        {:otsikko "Raekoko"
                         :leveys 5
                         :nimi ::paikkaus/raekoko}
                        {:otsikko "Kuula\u00ADmylly"
                         :leveys 5
                         :nimi ::paikkaus/kuulamylly}]))]
    (fn [e! {:keys [paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?
                    paikkaukset-grid paikkauket-vetolaatikko] :as app}]
      [:div
       [grid/grid
        {:otsikko (if (or paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?)
                    [yleiset/ajax-loader-pieni "Päivitetään listaa.."]
                    "Paikkauksien toteumat")
         :salli-valiotsikoiden-piilotus? true
         :tunniste ::paikkaus/id
         :sivuta grid/vakiosivutus
         :tyhja (if paikkauksien-haku-kaynnissa?
                  [yleiset/ajax-loader "Haku käynnissä"]
                  "Ei paikkauksia")
         :vetolaatikot
         (into {}
               (map (juxt
                      ::paikkaus/id
                      (fn [rivi]
                        [paikkaukset-vetolaatikko e! rivi app])))
               paikkauket-vetolaatikko)}
        skeema
        paikkaukset-grid]])))

(defn toteumat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymaan (partial yhteinen-view/otsikkokomponentti
                                                       "Siirry kustannuksiin"
                                                       (fn [paikkauskohde-id]
                                                         (e! (tiedot/->SiirryKustannuksiin paikkauskohde-id))))))
                      #(e! (tiedot/->NakymastaPois)))
    (fn [e! app]
      [:span
       [kartta/kartan-paikka]
       [:div
        [debug/debug app]
        [yhteinen-view/hakuehdot app
         {:paivita-valinnat-fn #(e! (tiedot/->PaivitaValinnat %))
          :paikkaus-valittu-fn (fn [paikkauskohde valittu?]
                                 (e! (tiedot/->PaikkausValittu paikkauskohde valittu?)))
          :aikavali-otsikko "Alkuajan väli"
          :voi-valita-trn-kartalta? true}]
        [paikkaukset e! app]]])))

(defn toteumat []
  (fn []
    [tuck/tuck tiedot/app toteumat*]))
