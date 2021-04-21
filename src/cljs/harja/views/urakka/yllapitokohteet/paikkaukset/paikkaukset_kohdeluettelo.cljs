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
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as t-paikkauskohteet-kartalle]))

(defn paikkaukset
  [ur]
  (komp/luo
    (komp/sisaan-ulos
      #(do
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M))
      #(do
         (kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
         (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? false)
         (kartta-tasot/taso-pois! :paikkaukset-toteumat)
         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)))
    (fn [ur]
      (let [hoitourakka? (or (= :hoito (:tyyppi ur)) (= :teiden-hoito (:tyyppi ur)))]
        [:span.kohdeluettelo
         [bs/tabs {:style :tabs :classes "tabs-taso2"
                   :active (nav/valittu-valilehti-atom :kohdeluettelo-paikkaukset)}

          "Paikkauskohteet"
          :paikkauskohteet
          ;; Jos urakan tyyppi on päällystys, niin aina näytetään päällystyskohteet tabi
          ;; Hoitourakoiden urakanvalvojille (aluevastaava) näytetään sekä Paikkauskohteet että Päällystysurakoiden paikkauskohteet.
          ;; Aluevastaavallse haetaan tällä välilehdellä urakkakohtauset paikkauskohteet.
          (when (and
                  (or (= :paallystys (:tyyppi ur)) hoitourakka?)
                  (oikeudet/urakat-paikkaukset-paikkauskohteet (:id ur)))
            [paikkauskohteet/paikkauskohteet ur])

          "Toteumat"
          :toteumat
          (when (and (oikeudet/urakat-paikkaukset-toteumat (:id ur))
                     (or (= :paallystys (:tyyppi ur))
                         (and hoitourakka?
                              (contains?
                                (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
                                "ELY_Urakanvalvoja"))))
            [toteumat/toteumat ur])

          "Kustannukset"
          :kustannukset
          (when (and (= :paallystys (:tyyppi ur))
                     (oikeudet/urakat-paikkaukset-kustannukset (:id ur)))
            [kustannukset/kustannukset ur])

          "Päällystysurakoiden paikkaukset"
          :paallystysurakoiden-paikkauskohteet
          ;; Tiemerkkareille ja aluevastaaville näytetään muiden paikkauksia. Tarkistetaan siis, että
          ;; urakkana ei ole päällystys ja roolina on tiemerkkari tai aluevastaava
          ;; Hoitourakoilla halutaan tälle välilehdelle hakea alueelle kuuluvat paikkauskohteet.
          (when (and
                  ;; Oikeudet paikkauskohteisiin on oltava
                  (oikeudet/urakat-paikkaukset-paikkauskohteet (:id ur))
                  (or
                    ;; Voi olla joko hoitourakka
                    hoitourakka?
                    ;; Tai Aluevastaava/Elyurakanvalvoja
                    ;; Tai tiemerkintäurakka
                    (or
                      (contains? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id)) "ELY_Urakanvalvoja")
                      (= :tiemerkinta (:tyyppi ur)))))
            [paikkauskohteet/aluekohtaiset-paikkauskohteet ur])
          ]]))))
