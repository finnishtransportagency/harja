(ns harja.views.urakka.paikkauskohteet
  "Paikkauskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka.paikkaus :as paikkaus]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet-view]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as urakka]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn paikkauskohteet [ur]
  (komp/luo
    (fn [ur]
      [:div.paikkauskohteet
       [kartta/kartan-paikka]

       [valinnat/vuosi {}
        (t/year (:alkupvm ur))
        (t/year (:loppupvm ur))
        urakka/valittu-urakan-vuosi
        urakka/valitse-urakan-vuosi!]

       [yllapitokohteet-view/yllapitokohteet
        ur
        paikkaus/paikkauskohteet
        {:otsikko      "Paikkauskohteet"
         :kohdetyyppi  :paikkaus
         :tallenna     (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paikkauskohteet (:id ur))
                         (fn [kohteet]
                           (yllapitokohteet/kasittele-tallennettavat-kohteet!
                             kohteet
                             :paikkaus
                             #(reset! paikkaus/paikkauskohteet %)
                             (constantly nil))))            ;; Paikkauskohteissa ei ole validointeja palvelinpäässä
         :kun-onnistuu (fn [_]
                         (urakka/lukitse-urakan-yha-sidonta! (:id ur)))}]

       [yllapitokohteet-view/yllapitokohteet-yhteensa
        paikkaus/paikkauskohteet {:nakyma :paikkaus}]

       [:div.kohdeluettelon-paivitys
        [yha/paivita-kohdeluettelo ur oikeudet/urakat-kohdeluettelo-paikkauskohteet]
        [yha/kohdeluettelo-paivitetty ur]]])))
