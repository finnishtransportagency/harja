(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-kohdeluettelo
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumat :as toteumat]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-kustannukset :as kustannukset]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as paikkauskohteet]

            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]))

(defn paikkaukset
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

        "Paikkauskohteet"
        :paikkauskohteet
        (when (oikeudet/urakat-paikkaukset-paikkauskohteet (:id ur))
          [paikkauskohteet/paikkauskohteet ur])

        "Toteumat"
        :toteumat
        (when (and (= :paallystys (:tyyppi ur))
                   (oikeudet/urakat-paikkaukset-toteumat (:id ur)))
          [toteumat/toteumat ur])

        "Kustannukset"
        :kustannukset
        (when (and (= :paallystys (:tyyppi ur))
                   (oikeudet/urakat-paikkaukset-kustannukset (:id ur)))
          [kustannukset/kustannukset ur])]])))
