(ns harja.views.urakkatilanne.sarakkeet.virheelliset
  (:require [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.views.urakka.pot-yhteinen :as pot-yhteinen]))


(defn virheelliset-tila-sarake
  [rivi]
  (for [kohde (:virheelliset_kohteet rivi)]
    ^{:key (:id kohde)}
    [yleiset/wrap-if true
     [yleiset/tooltip {} :%
      [:div
       [:p "Siirry päällystys\u00ADilmoitukseen."]
       (when (:lahetysvirhe kohde)
         [:p "Virhe: " (:lahetysvirhe kohde)])]]
     [yleiset/linkki (pot-yhteinen/paallystyskohteen-fmt kohde)
      #(siirtymat/avaa-paallystysilmoitus! {:paallystyskohde-id (:id kohde)
                                            :kohteen-urakka-id (:id rivi)})
      {:block? true}]]))
