(ns harja.views.urakka.yllapitokohteet.paikkaukset-kohdeluettelo
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.paikkaukset-toteumat :as toteumat]
            [harja.views.urakka.paikkaukset-kustannukset :as kustannukset]

            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]))

(defn kohdeluettelo
  "Kohdeluettelo-pääkomponentti"
  [ur]
  (komp/luo
    (komp/sisaan-ulos
      #(do
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M))
      #(do
         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)))
    (fn [ur]
      [:span.kohdeluettelo
       [bs/tabs {:style :tabs :classes "tabs-taso2"
                 :active (nav/valittu-valilehti-atom :kohdeluettelo-paikkaukset)}

        "Toteumat"
        :toteumat
        (when (oikeudet/urakat-paikkaukset-toteumat (:id ur))
          [toteumat/toteumat])

        "Kustannukset"
        :kustannukset
        (when (oikeudet/urakat-paikkaukset-kustannukset (:id ur))
          [kustannukset/kustannukset])]])))
