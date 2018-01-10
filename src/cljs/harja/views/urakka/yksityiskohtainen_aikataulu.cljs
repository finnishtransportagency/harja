(ns harja.views.urakka.yksityiskohtainen-aikataulu
  "Ylläpidon urakoiden yksityiskohtainen aikataulu"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.domain.yllapitokohde :as ypk]
            [harja.tiedot.urakka.yksityiskohtainen-aikataulu :as tiedot]
            [harja.ui.grid :as grid])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn yksityiskohtainen-aikataulu [rivi]
  (let [yksityiskohtainen-aikataulu (atom (:yksityiskohtainen-aikataulu rivi))]
    (fn [rivi]
      [:div
       [grid/grid
        {:otsikko "Kohteen yksityiskohtainen aikataulu"
         :tyhja "Ei aikataulua"
         :tallenna tiedot/tallenna-aikataulu}
        [{:otsikko "Toimenpide"
          :leveys 10
          :nimi :kohdenumero
          :tyyppi :valinta
          :valinnat ypk/tarkan-aikataulun-toimenpiteet
          :valinta-nayta #(if % ypk/tarkan-aikataulun-toimenpiide-fmt "- valitse -")
          :pituus-max 128}
         {:otsikko "Kuvaus"
          :leveys 10
          :nimi :kohdenumero
          :tyyppi :string
          :pituus-max 1024}
         {:otsikko "Alku"
          :leveys 5X
          :nimi :kohdenumero
          :tyyppi :pvm}
         {:otsikko "Loppu"
          :leveys 5
          :nimi :kohdenumero
          :tyyppi :pvm}]
        yksityiskohtainen-aikataulu]])))