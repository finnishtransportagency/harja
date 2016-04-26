(ns harja.views.urakka.paikkauskohteet
  "Paikkauskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                                      livi-pudotusvalikko]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka.paikkaus :as paikkaus]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet-view]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn paikkauskohteet []
  (komp/luo
    (komp/ulos #(kartta/poista-popup!))
    (komp/lippu paikkaus/paikkauskohteet-nakymassa?)
    (fn []
      [:div
       [kartta/kartan-paikka]
       [yllapitokohteet-view/yllapitokohteet paikkaus/paikkauskohteet {:otsikko "Paikkauskohteet"
                                                                       :paikkausnakyma? true
                                                                       :tallenna (fn [kohteet]
                                                                                   (go (let [urakka-id (:id @nav/valittu-urakka)
                                                                                             [sopimus-id _] @u/valittu-sopimusnumero
                                                                                             _ (log "PÄÄ Tallennetaan paikkauskohteet: " (pr-str kohteet))
                                                                                             vastaus (<! (yllapitokohteet/tallenna-yllapitokohteet urakka-id sopimus-id kohteet))]
                                                                                         (log "PÄÄ päällystyskohteet tallennettu: " (pr-str vastaus))
                                                                                         (reset! paikkaus/paikkauskohteet vastaus))))}]
       [yllapitokohteet-view/yllapitokohteet-yhteensa paikkaus/paikkauskohteet {:paikkausnakyma? true}]])))
