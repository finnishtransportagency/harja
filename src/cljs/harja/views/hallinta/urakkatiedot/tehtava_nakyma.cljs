(ns harja.views.hallinta.urakkatiedot.tehtava-nakyma
  "Tuodaan tehtävät, tehtäväryhmät ja tehtäväryhmien otsikot näkyväksi."
  (:require [tuck.core :refer [tuck send-value! send-async!]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.tiedot.hallinta.tehtava-tiedot :as tiedot]))

(defn tehtavat-vetolaatikko
  "Tehtäväryhmän tehtävät"
  [e! app {:keys [id] :as rivi}]
  [grid/grid {:tunniste :id
              :piilota-muokkaus? true
              ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
              ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
              :esta-tiivis-grid? true
              :reunaviiva? true}
   [{:otsikko "Id" :nimi :id :leveys 0.5}
    {:otsikko "Tehtävä" :nimi :nimi :leveys 2}
    {:otsikko "Yksikkö" :nimi :yksikko :leveys 0.8}
    {:otsikko "Suoritettava tehtavä" :nimi :suoritettavatehtava :leveys 1}
    {:otsikko "Voim. alkuvuosi" :nimi :voimassaolo_alkuvuosi :leveys 1}
    {:otsikko "Voim. loppuvuosi" :nimi :voimassaolo_loppuvuosi :leveys 1}
    {:otsikko "Käsin lisättavä?" :nimi :kasin_lisattava_maara :leveys 1}
    {:otsikko "Aluetieto?" :nimi :aluetieto :leveys 1}]
   (:tehtavat rivi)])

(defn tehtavaryhmat-vetolaatikko
  [e! app {:keys [id] :as rivi}]
  (let [tehtavaryhmat (:tehtavaryhmat rivi)]
    [grid/grid
     {:otsikko "Tehtäväryhmät"
      :tunniste :tehtavaryhma_id
      :jarjesta :nimi
      :reunaviiva? true
      :piilota-toiminnot? false
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

     [{:tyyppi :vetolaatikon-tila :leveys 0.5}
      {:nimi :nimi
       :leveys 2
       :otsikko "Nimi"
       :tyyppi :string
       :muokattava? (constantly false)}
      {:nimi :voimassaolo_alkuvuosi
       :leveys 1
       :otsikko "Voimassaolo alkuvuosi"
       :kokonaisluku? true
       :tyyppi :positiivinen-numero}
      {:nimi :voimassaolo_loppuvuosi
       :leveys 1
       :kokonaisluku? true
       :otsikko "Voimassaolo loppuvuosi"
       :tyyppi :positiivinen-numero}
      {:nimi :yksiloiva_tunniste
       :leveys 1
       :otsikko "Yksilöivä tunniste"
       :tyyppi :string
       :muokattava? (constantly false)}]
     tehtavaryhmat]))

(defn listaus* [e! app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(do
                    (e! (tiedot/->HaeTehtavaryhmaotsikot))
                    (e! (tiedot/->HaeSuoritettavatTehtavat))))
    (fn [e! app]
      (let [tehtavaryhmaotsikot (:tehtavaryhmaotsikot app)
            suoritettavat-tehtavat (:suoritettavat-tehtavat app)]
        [:div
         ;[debug/debug app]
         [:div "Listataan tehtäväryhmäotsikot ja niille kuuluvat tehtäväryhmät ja niiden (MHU) tehtävät."]
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
            :leveys 3
            :otsikko "Otsikko"
            :tyyppi :string}]
          tehtavaryhmaotsikot]

         [:p "Suoritettävat tehtävät ovat tehtäviä, joille urakoitsijat lähettävät rajapintojen kautta ja koska niiden löytäminen ylemmästä listasta on vaikeaa, niin
         ne on nostettu tähän helpommin nähtävään muotoon."]
         [grid/grid
          ;; Opts
          {:otsikko "Suoritettavat tehtävät"
           :tunniste :id
           :jarjesta :id}
          ;; Skeema
          [{:nimi :id :leveys 0.5 :otsikko "ID" :kokonaisluku? true :tyyppi :positiivinen-numero :muokattava? (constantly false)}
           {:nimi :nimi :leveys 2 :otsikko "Nimi" :tyyppi :string :muokattava? (constantly false)}
           {:nimi :suoritettavatehtava :otsikko "Suoritettava tehtavä" :leveys 2}
           {:nimi :voimassaolo_alkuvuosi :leveys 1 :otsikko "Voim. alkuvuosi" :kokonaisluku? true :tyyppi :positiivinen-numero}
           {:nimi :voimassaolo_loppuvuosi :leveys 1 :kokonaisluku? true :otsikko "Voim. loppuvuosi" :tyyppi :positiivinen-numero}
           {:nimi :yksiloiva_tunniste :leveys 1 :otsikko "Yksilöivä tunniste" :tyyppi :string :muokattava? (constantly false)}
           {:nimi :poistettu :leveys 1 :otsikko "Poistettu" :tyyppi :string :muokattava? (constantly false)}]
          ;; Data
          suoritettavat-tehtavat]]))))


(defn tehtavat []
  [tuck tiedot/tila listaus*])
