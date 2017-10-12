(ns harja.views.hallinta.jarjestelma-asetukset
  (:require [harja.ui.komponentti :as komp]
            [tuck.core :refer [tuck]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallinta.jarjestelma-asetukset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [harja.domain.geometriaaineistot :as geometria-aineistot]
            [harja.ui.debug :refer [debug]]
            [harja.loki :refer [log]]))

(defn geometria-aineistot [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeGeometria-aineistot)))
    (fn [e! {:keys [geometria-aineistot haku-kaynnissa?] :as app}]
      (log "--->>>" (pr-str geometria-aineistot))

      (let [geometria-aineistot (atom (zipmap (iterate inc 1) geometria-aineistot))]
        [:div
        ;; todo: lisää toimintopainikkeet
        [grid/muokkaus-grid
         {:otsikko "Geometria-aineistot"
          :voi-muokata? (constantly true)
          :voi-poistaa? (constantly true)
          :piilota-toiminnot? false
          :tyhja "Ei geometria-aineistoja"
          :jarjesta ::geometria-aineistot/nimi
          :tunniste ::geometria-aineistot/id
          :uusi-rivi (fn [rivi]
                       ;todo
                       )}
         [{:otsikko "Nimi" :nimi ::geometria-aineistot/nimi :tyyppi :string}
          {:otsikko "Tiedostonimi" :nimi ::geometria-aineistot/tiedostonimi :tyyppi :string}
          {:otsikko "Voimassaolo alkaa" :nimi ::geometria-aineistot/voimassaolo-alkaa :tyyppi :pvm :fmt pvm/pvm-opt}
          {:otsikko "Voimassaolo päättyy" :nimi ::geometria-aineistot/voimassaolo-paattyy :tyyppi :pvm :fmt pvm/pvm-opt}]
         geometria-aineistot]]))))

(defn jarjestelma-asetukset* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! app]
      [:div
       [debug app]
       [geometria-aineistot e! app]])))

(defn jarjestelma-asetukset []
  [tuck tiedot/tila jarjestelma-asetukset*])