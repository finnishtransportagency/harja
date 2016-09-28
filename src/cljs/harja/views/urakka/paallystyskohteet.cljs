(ns harja.views.urakka.paallystyskohteet
  "Päällystyskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko tietoja]]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet-view]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as urakka]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn paallystyskohteet [ur]
  (komp/luo
    (komp/ulos #(kartta/poista-popup!))
    (komp/lippu paallystys/paallystyskohteet-nakymassa?)
    (fn [ur]
      [:div.paallystyskohteet
       [kartta/kartan-paikka]

       [yllapitokohteet-view/yllapitokohteet
        ur
        paallystys/yhan-paallystyskohteet
        {:otsikko "YHA:sta tuodut päällystyskohteet"
         :nakyma :paallystys
         :yha-sidottu? true
         :tallenna
         (yllapitokohteet/kasittele-tallennettavat-kohteet!
           #(oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
           :paallystys
           #(reset! paallystys/yhan-paallystyskohteet (filter yllapitokohteet/yha-kohde? %)))
         :kun-onnistuu (fn [_]
                         (urakka/lukitse-urakan-yha-sidonta! (:id ur)))}]

       [yllapitokohteet-view/yllapitokohteet
        ur
        paallystys/harjan-paikkauskohteet
        {:otsikko "Harjan paikkauskohteet"
         :nakyma :paikkaus
         :yha-sidottu? false
         :tallenna
         (yllapitokohteet/kasittele-tallennettavat-kohteet!
           #(oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
           :paikkaus
           #(reset! paallystys/harjan-paikkauskohteet (filter (comp not yllapitokohteet/yha-kohde?) %)))}]

       [yllapitokohteet-view/yllapitokohteet-yhteensa
        paallystys/kohteet-yhteensa {:nakyma :paallystys}]

       [:div.kohdeluettelon-paivitys
        [yha/paivita-kohdeluettelo ur oikeudet/urakat-kohdeluettelo-paallystyskohteet]
        [yha/kohdeluettelo-paivitetty ur]]])))
