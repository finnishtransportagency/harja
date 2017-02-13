(ns harja.views.urakka.paallystyskohteet
  "Päällystyskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.tiedot.urakka.paallystys :as paallystys-tiedot]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet-view]
            [harja.views.urakka.muut-kustannukset :as muut-kustannukset-view]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [vihje-elementti]]
            [harja.views.kartta :as kartta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defn paallystyskohteet [ur]
  (komp/luo
    (fn [ur]
      [:div.paallystyskohteet
       [kartta/kartan-paikka]

       [valinnat/vuosi {}
        (t/year (:alkupvm ur))
        (t/year (:loppupvm ur))
        urakka/valittu-urakan-vuosi
        urakka/valitse-urakan-vuosi!]

       [yllapitokohteet-view/yllapitokohteet
        ur
        paallystys-tiedot/yhan-paallystyskohteet
        {:otsikko "YHA:sta tuodut päällystyskohteet"
         :kohdetyyppi :paallystys
         :yha-sidottu? true
         :tallenna
         (yllapitokohteet/kasittele-tallennettavat-kohteet!
           #(oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
           :paallystys
           #(reset! paallystys-tiedot/yhan-paallystyskohteet (filter yllapitokohteet/yha-kohde? %)))
         :kun-onnistuu (fn [_]
                         (urakka/lukitse-urakan-yha-sidonta! (:id ur)))}]

       [yllapitokohteet-view/yllapitokohteet
        ur
        paallystys-tiedot/harjan-paikkauskohteet
        {:otsikko "Harjan paikkauskohteet"
         :kohdetyyppi :paikkaus
         :yha-sidottu? false
         :tallenna
         (yllapitokohteet/kasittele-tallennettavat-kohteet!
           #(oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
           :paikkaus
           #(reset! paallystys-tiedot/harjan-paikkauskohteet (filter (comp not yllapitokohteet/yha-kohde?) %)))}]

       [muut-kustannukset-view/muut-kustannukset ur]

       [yllapitokohteet-view/yllapitokohteet-yhteensa
        paallystys-tiedot/kohteet-yhteensa {:nakyma :paallystys}]

       [vihje-elementti [:span
                         [:span "Huomioi etumerkki hinnanmuutoksissa. Ennustettuja määriä sisältävät kentät on värjätty "]
                         [:span.grid-solu-ennustettu "sinisellä"]
                         [:span "."]]]

       [:div.kohdeluettelon-paivitys
        [yha/paivita-kohdeluettelo ur oikeudet/urakat-kohdeluettelo-paallystyskohteet]
        [yha/kohdeluettelo-paivitetty ur]]])))
