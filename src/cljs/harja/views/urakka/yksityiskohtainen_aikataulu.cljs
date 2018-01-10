(ns harja.views.urakka.yksityiskohtainen-aikataulu
  "Ylläpidon urakoiden yksityiskohtainen aikataulu"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.yksityiskohtainen-aikataulu :as tiedot]
            [harja.ui.grid :as grid])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn yksityiskohtainen-aikataulu [rivi]
  (let [yksityiskohtainen-aikataulu (:yksityiskohtainen-aikataulu rivi)]
    [:div
     [grid/grid
      {:otsikko "Kohteen yksityiskohtainen aikataulu"
       :tyhja "Ei aikataulua"
       :tallenna tiedot/tallenna-aikataulu}
      [{:otsikko "Koh\u00ADde\u00ADnu\u00ADme\u00ADro" :leveys 3 :nimi :kohdenumero :tyyppi :string
        :pituus-max 128 :muokattava? voi-muokata-paallystys?}
       {:otsikko "Koh\u00ADteen nimi" :leveys 9 :nimi :nimi :tyyppi :string :pituus-max 128
        :muokattava? voi-muokata-paallystys?}
       {:otsikko "Tie\u00ADnu\u00ADme\u00ADro" :nimi :tr-numero
        :tyyppi :positiivinen-numero :leveys 3 :tasaa :oikea
        :muokattava? (constantly false)}
       {:otsikko "Ajo\u00ADrata"
        :nimi :tr-ajorata
        :muokattava? (constantly false)
        :tyyppi :string :tasaa :oikea
        :fmt #(pot/arvo-koodilla pot/+ajoradat-numerona+ %)
        :leveys 3}
       {:otsikko "Kais\u00ADta"
        :muokattava? (constantly false)
        :nimi :tr-kaista
        :tyyppi :string
        :tasaa :oikea
        :fmt #(pot/arvo-koodilla pot/+kaistat+ %)
        :leveys 3}
       {:otsikko "Aosa" :nimi :tr-alkuosa :leveys 3
        :tyyppi :positiivinen-numero
        :tasaa :oikea
        :muokattava? (constantly false)}]]]))