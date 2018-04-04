(ns harja.views.urakka.paikkaukset-kustannukset
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.tiedot.urakka.paikkaukset-kustannukset :as tiedot]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteinen-tiedot]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.paikkaukset-yhteinen :as yhteinen-view]))

(defn hakuehdot [e! {:keys [valinnat] :as app}]
  (let [tr-atomi (partial yhteinen-tiedot/valinta-wrap app #(e! (tiedot/->PaivitaValinnat %)))
        aikavali-atomi (partial yhteinen-tiedot/valinta-wrap app #(e! (tiedot/->PaivitaValinnat %)))]
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

(defn yksikkohintaiset-kustannukset
  [e! app]
  (let [skeema [{:otsikko "Kirjaus\u00ADaika"
                 :leveys 3
                 :nimi :kirjattu}
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
         :sivuta grid/vakiosivutus
         :tyhja "Ei yksikköhintaisia kustannuksia"}
        skeema
        yksikkohintaiset-grid]])))

(defn kokonaishintaiset-kustannukset [e! app]
  (let [skeema [{:otsikko "Kirjaus\u00ADaika"
                 :leveys 5
                 :nimi :kirjattu}
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
         :sivuta grid/vakiosivutus
         :tyhja "Ei kokonaishintaisia kustannuksia"}
        skeema
        kokonaishintaiset-grid]])))

(defn kustannukset* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymaan (partial yhteinen-view/otsikkokomponentti (fn [paikkauskohde-id]
                                                                                          (e! (tiedot/->SiirryToimenpiteisiin paikkauskohde-id))))))
                      #(e! (tiedot/->NakymastaPois)))
    (fn [e! app]
      [:div
       [debug/debug app]
       [yhteinen-view/hakuehdot app
        #(e! (tiedot/->PaivitaValinnat %))
        (fn [paikkauskohde valittu?]
          (e! (tiedot/->PaikkausValittu paikkauskohde valittu?)))]
       [kokonaishintaiset-kustannukset e! app]
       [yksikkohintaiset-kustannukset e! app]])))

(defn kustannukset []
  [tuck/tuck tiedot/app kustannukset*])