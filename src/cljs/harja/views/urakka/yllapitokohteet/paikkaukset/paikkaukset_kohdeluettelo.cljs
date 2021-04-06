(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-kohdeluettelo
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.domain.roolit :as roolit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumat :as toteumat]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-kustannukset :as kustannukset]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as paikkauskohteet]
            ))

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
        ;; Jos urakan tyyppi on päällystys, niin aina näytetään päällystyskohteet tabi
        (when (and
                (= :paallystys (:tyyppi ur))
                (oikeudet/urakat-paikkaukset-paikkauskohteet (:id ur)))
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
          [kustannukset/kustannukset ur])

        "Päällystyurakoiden paikkaukset"
        :paikkauskohteet
        ;; Tiemerkkareille ja aluevastaaville näytetään muiden paikkauksia. Tarkistetaan siis, että
        ;; urakkana ei ole päällystys ja roolina on tiemerkkari tai aluevastaava
        (when (and
                (not= :paallystys (:tyyppi ur))
                (or
                  (contains? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id)) "ELY_Urakanvalvoja")
                  (= :tiemerkinta (:tyyppi ur)))
                (oikeudet/urakat-paikkaukset-paikkauskohteet (:id ur)))
          [paikkauskohteet/paikkauskohteet ur])
        ]])))
