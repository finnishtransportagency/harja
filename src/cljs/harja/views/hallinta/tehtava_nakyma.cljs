(ns harja.views.hallinta.tehtava-nakyma
  "Tuodaan tehtävät ja tehtäväryhmät näkyväksi."
  (:require [tuck.core :refer [tuck send-value! send-async!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.hallinta.tehtava-tiedot :as tiedot])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn tehtavat-vetolaatikko
  "Tehtäväryhmän tehtävät"
  [e! app {:keys [id] :as rivi}]
  (let [valittu-tehtavaryhma (some #(when (= id (:id %)) %) (:tehtavaryhmat app))
        tehtavat (:tehtavat valittu-tehtavaryhma)]
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
     tehtavat]))

(defn listaus* [e! app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTehtavaryhmat)))
    (fn [e! app]
      (let [tehtavaryhmat (:tehtavaryhmat app)]
        [:div
         [:div "Listataan ensin tehtäväryhmät ja niille kuuluvat (MHU) tehtävät."]
         [:div
          ;[debug/debug app]
          [grid/grid
           {:otsikko "Tehtäväryhmät"
            :tunniste :id
            :jarjesta :nimi
            :reunaviiva? true
            ;; Tehtävät listataan tehtäväryhmittäin tässä määriteltävään avautuvaan toiseen taulukkoon
            :vetolaatikot (into {}
                            (map (juxt :id
                                   (fn [rivi] [tehtavat-vetolaatikko e! app rivi])))
                            tehtavaryhmat)}

           [{:tyyppi :vetolaatikon-tila :leveys 0.5}
            {:nimi :otsikko
             :leveys 3
             :otsikko "Otsikko"
             :tyyppi :string}
            {:nimi :nimi
             :leveys 2
             :otsikko "Nimi"
             :tyyppi :string}
            {:nimi :yksiloiva_tunniste
             :leveys 1
             :otsikko "Yksilöivä tunniste"
             :tyyppi :string}
            ]

           tehtavaryhmat]]]))))

(defn tehtavat []
  [tuck tiedot/tila listaus*])
