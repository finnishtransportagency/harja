(ns harja.views.urakka.paallystyskohteet
  "Päällystyskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet-view]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn paallystyskohteet []
  (komp/luo
    (komp/ulos #(kartta/poista-popup!))
    (komp/lippu paallystys/paallystyskohteet-nakymassa?)
    (fn []
      [:div.paallystyskohteet
       [kartta/kartan-paikka]
       [yllapitokohteet-view/yllapitokohteet paallystys/paallystyskohteet {:otsikko "Päällystyskohteet"
                                                                           :paallystysnakyma? true
                                                                           :tallenna (fn [kohteet]
                                                                                       (go (let [urakka-id (:id @nav/valittu-urakka)
                                                                                                 [sopimus-id _] @u/valittu-sopimusnumero
                                                                                                 _ (log "PÄÄ Tallennetaan päällystyskohteet: " (pr-str kohteet))
                                                                                                 vastaus (<! (yllapitokohteet/tallenna-yllapitokohteet urakka-id sopimus-id kohteet))]
                                                                                             (log "PÄÄ päällystyskohteet tallennettu: " (pr-str vastaus))
                                                                                             (reset! paallystys/paallystyskohteet vastaus))))}]
       [yllapitokohteet-view/yllapitokohteet-yhteensa paallystys/paallystyskohteet {:paallystysnakyma? true}]])))
