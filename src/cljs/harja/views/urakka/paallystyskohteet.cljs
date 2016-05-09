(ns harja.views.urakka.paallystyskohteet
  "Päällystyskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                                      livi-pudotusvalikko tietoja]]
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
            [harja.pvm :as pvm])
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
        paallystys/yhan-paallystyskohteet
        {:otsikko "YHA:sta tuodut päällystyskohteet"
         :paallystysnakyma? true
         :yha-sidottu? true
         :tallenna (fn [kohteet]
                     (go (let [urakka-id (:id @nav/valittu-urakka)
                               [sopimus-id _] @u/valittu-sopimusnumero
                               _ (log "PÄÄ Tallennetaan päällystyskohteet: " (pr-str kohteet))
                               vastaus (<! (yllapitokohteet/tallenna-yllapitokohteet! urakka-id sopimus-id kohteet))]
                           (log "PÄÄ päällystyskohteet tallennettu: " (pr-str vastaus))
                           (reset! paallystys/yhan-paallystyskohteet (filter yllapitokohteet/yha-kohde? vastaus)))))}]

       [yllapitokohteet-view/yllapitokohteet
        paallystys/harjan-paallystyskohteet
        {:otsikko "Harjan päällystyskohteet"
         :paallystysnakyma? true
         :yha-sidottu? false
         :tallenna (fn [kohteet]
                     (go (let [urakka-id (:id @nav/valittu-urakka)
                               [sopimus-id _] @u/valittu-sopimusnumero
                               _ (log "PÄÄ Tallennetaan päällystyskohteet: " (pr-str kohteet))
                               vastaus (<! (yllapitokohteet/tallenna-yllapitokohteet! urakka-id sopimus-id kohteet))]
                           (log "PÄÄ päällystyskohteet tallennettu: " (pr-str vastaus))
                           (reset! paallystys/harjan-paallystyskohteet (filter (comp not yllapitokohteet/yha-kohde?) vastaus)))))}]

       [yllapitokohteet-view/yllapitokohteet-yhteensa
        paallystys/harjan-paallystyskohteet {:paallystysnakyma? true}]

       [:div.kohdeluettelon-paivitys
        [yha/paivita-kohdeluettelo ur oikeudet/urakat-kohdeluettelo-paallystyskohteet]
        [yha/kohdeluettelo-paivitetty ur]]])))
