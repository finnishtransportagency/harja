(ns harja.views.hallinta.urakkatiedot.vesivayla-tehtava-nakyma
  "Tuodaan vesiväylätehtävät, tehtäväryhmät ja otsikot näkyväksi."
  (:require [tuck.core :refer [tuck]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.tiedot.hallinta.urakkatiedot.vesivayla-tehtava-tiedot :as tiedot]))

(defn tehtavat-vetolaatikko
  "Tehtäväryhmän tehtävät"
  [e! app {:keys [id] :as rivi}]
  [grid/grid {:tunniste :id
              :voi-poistaa? (constantly false)
              :voi-lisata? false
              ::mahdollista-rivin-valinta? true
              :paneelikomponentit [(fn [] [:div.valittujen-tehtavien-toiminnot
                                           (let [valitut-maara (count (:valitut-tehtavat @tiedot/tila))]
                                             [:span.margin-right-8 (str "Valittuna: " valitut-maara " tehtävää")])
                                           (when (pos? (count (:valitut-tehtavat @tiedot/tila)))
                                             [:<>
                                              [napit/nappi "Tulosta valitut tehtävät migraatiota varten"
                                               #(e! (tiedot/->TulostaKaikkiValitut))
                                               {:luokka "nappi-toissijainen"}]
                                              [napit/nappi "Tyhjennä valinnat"
                                               #(e! (tiedot/->TyhjaaValitutTehtavat))
                                               {:luokka "nappi-toissijainen"}]])
                                           (when (seq (:tulostetut-tehtavat @tiedot/tila))
                                             [:div.tulostetut-tehtavat
                                              [:h4 "Tulostetut tehtävät:"]
                                              [:ul
                                               (for [[idx tehtava] (map-indexed vector (:tulostetut-tehtavat @tiedot/tila))]
                                                 ^{:key (str "tulostettu-" idx)}
                                                 [:li (str "'"(:nimi tehtava)"'" "," "'"(:yksiloiva_tunniste tehtava)"'")])]])])]
              :tallenna (fn [muokatut-rivit _arvo]
                          ;; Tallenna funktion pitää aina palauttaa kanava, passaa muokkaa funktiolle nil
                          (tuck-apurit/e-kanavalla! e! tiedot/->MuokkaaTehtavat muokatut-rivit))
              :tallenna-vain-muokatut false
              ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
              ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
              :esta-tiivis-grid? true
              :reunaviiva? true}
   [(grid/rivinvalintasarake
     {:otsikko "Valitse"
      :otsikkovalinta? false
      :leveys 0.5
      :rivi-valittu?-fn (fn [rivi]
                          (tiedot/tehtava-valittu? (:id rivi)))
      :rivi-valittu-fn (fn [rivi valittu?]
                         (e! (tiedot/->ValitseTehtava rivi valittu?)))})
    {:otsikko "Id" :nimi :id :leveys 0.5 :muokattava? (constantly false)}
    {:otsikko "Tehtävä" :nimi :nimi :leveys 2 :muokattava? (constantly false)}
    {:otsikko "Yksikkö" :nimi :yksikko :leveys 0.8 :muokattava? (constantly false)}
    {:otsikko "Voim. alkuvuosi" :nimi :voimassaolo_alkuvuosi :leveys 1 :muokattava? (constantly false)}
    {:otsikko "Voim. loppuvuosi" :nimi :voimassaolo_loppuvuosi :leveys 1 :muokattava? (constantly false)}
    {:otsikko "Käsin lisättavä?" :nimi :kasin_lisattava_maara :leveys 1 :muokattava? (constantly false)}
    {:otsikko "Määrämitattava?" :nimi :maaramitattava? :leveys 1 :tyyppi :valinta :muokattava true
     :valinnat {true "Kyllä"
                false "Ei"}
     :valinta-arvo first
     :valinta-nayta second}]
   (:tehtavat rivi)])

(defn tehtavaryhmat-vetolaatikko
  [e! app {:keys [id] :as rivi}]
  (let [tehtavaryhmat (:tehtavaryhmat rivi)]
    [grid/grid
     {:otsikko "Tehtäväryhmät"
      :tunniste :tehtavaryhma_id
      :jarjesta :nimi
      :reunaviiva? true
      :piilota-toiminnot? true
      :tallenna-vain-muokatut true
      :voi-poistaa? (constantly false)
      :voi-lisata? false
      :tallenna (fn [muokatut-rivit _arvo]
                  ;; Tallenna funktion pitää aina palauttaa kanava, passaa muokkaa funktiolle nil
                  (tuck-apurit/e-kanavalla! e! tiedot/->MuokkaaTehtavaryhmat muokatut-rivit))
      ;; Tehtävät listataan tehtäväryhmittäin tässä määriteltävään avautuvaan toiseen taulukkoon
      :vetolaatikot (into {}
                      (map (juxt :tehtavaryhma_id
                             (fn [rivi] [tehtavat-vetolaatikko e! app rivi])))
                      tehtavaryhmat)}

     [{:tyyppi :vetolaatikon-tila :leveys "5%" :muokattava? (constantly false)}
      {:nimi :nimi :leveys 2 :otsikko "Nimi" :tyyppi :string :muokattava? (constantly false)}
      {:nimi :voimassaolo_alkuvuosi :leveys 1 :otsikko "Voimassaolo alkuvuosi" :kokonaisluku? true :tyyppi :positiivinen-numero}
      {:nimi :voimassaolo_loppuvuosi :leveys 1 :otsikko "Voimassaolo loppuvuosi" :tyyppi :positiivinen-numero}
      {:nimi :yksiloiva_tunniste :leveys 1 :otsikko "Yksilöivä tunniste" :tyyppi :string :muokattava? (constantly false)}
      {:nimi :toimenpide :leveys 1 :otsikko "Toimenpide" :tyyppi :string :muokattava? (constantly false)}]
     tehtavaryhmat]))

(defn listaus* [e! app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTehtavaryhmaotsikot)))
    (fn [e! app]
      (let [tehtavaryhmaotsikot (:tehtavaryhmaotsikot app)]
        [:div
         [:div "Listataan Tehtävä- ja määräluettelossa käytetyt väliotsikot ja niihin liittyvät tehtäväryhmät sekä tehtäväryhmiin kytkeytyvät, Vesiväylä-hoito-urakoissa relevantit tehtävät."]
         [grid/grid
          {:otsikko "Tehtäväryhmäotsikot"
           :tunniste :tehtavaryhmaotsikko_id
           :jarjesta :otsikko
           ;; Tehtäväryhmät listataan tässä määriteltävään avautuvaan toiseen taulukkoon
           :vetolaatikot (into {}
                           (map (juxt :tehtavaryhmaotsikko_id
                                  (fn [rivi] [tehtavaryhmat-vetolaatikko e! app rivi])))
                           tehtavaryhmaotsikot)}

          [{:tyyppi :vetolaatikon-tila :leveys 0.5}
           {:nimi :otsikko
            :leveys 10
            :otsikko "Otsikko"
            :tyyppi :string}]
          tehtavaryhmaotsikot]]))))

(defn vesivayla-tehtavat []
  [tuck tiedot/tila listaus*])
