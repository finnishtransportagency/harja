(ns harja.views.urakka.paikkaukset-kustannukset
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.tiedot.urakka.paikkaukset-kustannukset :as tiedot]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]))

(defn otsikkokomponentti
  [e! id]
  [{:tyyli {:float "right"
            :position "relative"}
    :sisalto
    (fn [_]
      [napit/yleinen-ensisijainen
       "Siirry toimenpiteisiin"
       #(e! (tiedot/->SiirryToimenpiteisiin id))])}])

(defn hakuehdot [e! {:keys [valinnat] :as app}]
  (let [tr-atomi (partial tiedot/valinta-wrap e! app (partial otsikkokomponentti e!))
        aikavali-atomi (partial tiedot/valinta-wrap e! app (partial otsikkokomponentti e!))]
    [:span
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Tierekisteriosoite"
       :kentta-params {:tyyppi :tierekisteriosoite
                       :tr-otsikot? false
                       #_#_:validoi [(fn [osoite {sijainti :sijainti}]
                                       (when (and (tr-osoite-taytetty? osoite)
                                                  (nil? sijainti))
                                         "Tarkista tierekisteriosoite"))]}
       :arvo-atom (tr-atomi :tr)
       :tyylit {:width "fit-content"}}]
     [valinnat/aikavali (aikavali-atomi :aikavali)]
     [:span.label-ja-kentta
      [:span.kentan-otsikko "Näytettävät paikkauskohteet"]
      [:div.kentta
       [valinnat/checkbox-pudotusvalikko
        (:urakan-paikkauskohteet valinnat)
        (fn [paikkauskohde valittu?]
          (e! (tiedot/->PaikkausValittu paikkauskohde valittu? (partial otsikkokomponentti e!))))
        [" paikkauskohde valittu" " paikkauskohdetta valittu"]]]]]))

(defn yksikkohintaiset-kustannukset
  [e! {tienkohdat ::paikkaus/tienkohdat materiaalit ::paikkaus/materiaalit id ::paikkaus/id :as rivi}]
  (let [nayta-numerot #(apply str (interpose ", " %))
        skeema [{:otsikko "Ajo\u00ADrata"
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
                 :fmt nayta-numerot}
                {:otsikko "Esiin\u00ADtymä"
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
                 :nimi ::paikkaus/lisa-aineet}]
        yhdistetty [(apply merge {::paikkaus/id id} (concat tienkohdat materiaalit))]]
    [:div
     [grid/grid
      {:otsikko "Tienkohdat & Materiaalit"
       :tunniste ::paikkaus/id
       :sivuta grid/vakiosivutus
       :tyhja "Ei tietoja"}
      skeema
      yhdistetty]
     [napit/yleinen-ensisijainen
      "Siirry kustannuksiin"
      #(e! (tiedot/->SiirryKustannuksiin id))]]))

(defn kokonaishintaiset-kustannukset [e! app]
  (let [tierekisteriosoite-sarakkeet [nil
                                      {:nimi ::tierekisteri/tie}
                                      nil nil
                                      {:nimi ::tierekisteri/aosa}
                                      {:nimi ::tierekisteri/aet}
                                      {:nimi ::tierekisteri/losa}
                                      {:nimi ::tierekisteri/let}]
        skeema [{:otsikko "Alku\u00ADaika"
                 :leveys 5
                 :nimi ::paikkaus/alkuaika}
                {:otsikko "Loppu\u00ADaika"
                 :leveys 5
                 :nimi ::paikkaus/loppuaika}
                {:otsikko "Työ\u00ADmene\u00ADtelmä"
                 :leveys 5
                 :nimi ::paikkaus/tyomenetelma}
                {:otsikko "Massa\u00ADtyyp\u00ADpi"
                 :leveys 5
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
                 :nimi ::paikkaus/kuulamylly}]]
    (fn [e! {:keys [paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa? paikkaukset-grid paikkauket-vetolaatikko]}]
      [:div
       [grid/grid
        {:otsikko (if (or paikkauksien-haku-kaynnissa? paikkauksien-haku-tulee-olemaan-kaynnissa?)
                    [yleiset/ajax-loader-pieni "Päivitetään listaa.."]
                    "Paikkauksien kokonaishintaiset kustannukset")
         :salli-valiotsikoiden-piilotus? true
         :tunniste ::paikkaus/id
         :listaus-tunniste ::paikkaus/nimi
         :sivuta grid/vakiosivutus
         :tyhja (if paikkauksien-haku-kaynnissa?
                  [yleiset/ajax-loader "Haku käynnissä"]
                  "Ei paikkauksia")}
        skeema
        paikkaukset-grid]])))

(defn kustannukset* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymaan (partial otsikkokomponentti e!)))
                      #(e! (tiedot/->NakymastaPois)))
    (fn [e! app]
      [:div
       [debug/debug app]
       [hakuehdot e! app]
       [kokonaishintaiset-kustannukset e! app]
       [yksikkohintaiset-kustannukset e! app]])))

(defn kustannukset []
  [tuck/tuck tiedot/app kustannukset*])