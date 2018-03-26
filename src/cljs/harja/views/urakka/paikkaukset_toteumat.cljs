(ns harja.views.urakka.paikkaukset-toteumat
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.tiedot.urakka.paikkaukset-toteumat :as tiedot]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]))

(defn hakuehdot [e! {:keys [valinnat] :as app}]
  (let [tr-atomi (partial tiedot/valinta-wrap e! app)
        aikavali-atomi (partial tiedot/valinta-wrap e! app)]
    [:span
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Tierekisteriosoite"
       :kentta-params {:tyyppi :tierekisteriosoite
                       :pakollinen? true
                       #_#_:validoi [(fn [osoite {sijainti :sijainti}]
                                       (when (and (tr-osoite-taytetty? osoite)
                                                  (nil? sijainti))
                                         "Tarkista tierekisteriosoite"))]}
       :arvo-atom (tr-atomi :tr)
       :tyylit {:width "fit-content"}}]
     [valinnat/aikavali (aikavali-atomi :aikavali)]
     [:span.label-ja-kentta
      [:span.kentan-otsikko "Paikkauskohteet"]
      [:div.kentta
       [yleiset/livi-pudotusvalikko
        {:naytettava-arvo (let [valittujen-paikkauskohteiden-maara (count (filter :valittu? (:urakan-paikkauskohteet valinnat)))]
                            (str valittujen-paikkauskohteiden-maara (if (= 1 valittujen-paikkauskohteiden-maara)
                                                              " paikkauskohde valittu"
                                                              " paikkauskohdetta valittu")))
         :itemit-komponentteja? true}
        (mapv (fn [paikkauskohde]
                [:label {:on-click #(.stopPropagation %)
                         :style {:width "100%"}}
                 (:nimi paikkauskohde)
                 [:input {:style {:display "inline-block"
                                  :float "right"}
                          :type "checkbox"
                          :checked (:valittu? paikkauskohde)
                          :on-change #(let [valittu? (-> % .-target .-checked)]
                                        (e! (tiedot/->PaikkausValittu paikkauskohde valittu?)))}]])
              (:urakan-paikkauskohteet valinnat))]]]]))

(defn paikkaukset [e! app]
  (let [tierekisteriosoite-sarakkeet [{:nimi ::paikkaus/nimi :pituus-max 30}
                                      {:nimi ::tierekisteri/tie}
                                      nil nil
                                      {:nimi ::tierekisteri/aosa}
                                      {:nimi ::tierekisteri/aet}
                                      {:nimi ::tierekisteri/losa}
                                      {:nimi ::tierekisteri/let}]
        skeema (into []
                     (concat
                       (yllapitokohteet/tierekisteriosoite-sarakkeet 8 tierekisteriosoite-sarakkeet)
                       [{:otsikko "Alku\u00ADaika"
                         :leveys 3
                         :nimi ::paikkaus/alkuaika}
                        {:otsikko "Loppu\u00ADaika"
                         :leveys 3
                         :nimi ::paikkaus/loppuaika}
                        {:otsikko "Työ\u00ADmene\u00ADtelmä"
                         :leveys 1
                         :nimi ::paikkaus/tyomenetelma}
                        {:otsikko "Massa\u00ADtyyp\u00ADpi"
                         :leveys 2
                         :nimi ::paikkaus/massatyyppi}
                        {:otsikko "Leveys"
                         :leveys 3
                         :nimi ::paikkaus/leveys}
                        {:otsikko "Massa\u00ADmenek\u00ADki"
                         :leveys 2
                         :nimi ::paikkaus/massamenekki}
                        {:otsikko "Raekoko"
                         :leveys 1
                         :nimi ::paikkaus/raekoko}
                        {:otsikko "Kuula\u00ADmylly"
                         :leveys 1
                         :nimi ::paikkaus/kuulamylly}]))]
    (fn [e! {:keys [paikkauksien-haku-kaynnissa? paikkaukset]}]
      [:div
       [grid/grid
        {:otsikko (if paikkauksien-haku-kaynnissa?
                    [yleiset/ajax-loader-pieni "Päivitetään listaa.."]
                    "Paikkauksien toteumat")
         :tunniste ::paikkaus/id
         :sivuta grid/vakiosivutus
         :tyhja (if paikkauksien-haku-kaynnissa?
                  [yleiset/ajax-loader "Haku käynnissä"]
                  "Ei paikkauksia")}
        skeema
        paikkaukset]])))

(defn toteumat* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymaan))
                      #(e! (tiedot/->NakymastaPois)))
    (fn [e! app]
      [:span
       [kartta/kartan-paikka]
       [:div
        [debug/debug app]
        [hakuehdot e! app]
        [paikkaukset e! app]]])))

(defn toteumat []
  [tuck/tuck tiedot/app toteumat*])
