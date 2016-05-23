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
            [harja.ui.viesti :as viesti])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn paikkauskohteet [ur]
  (komp/luo
    (komp/ulos #(kartta/poista-popup!))
    (komp/lippu paikkaus/paikkauskohteet-nakymassa?)
    (fn [ur]
      [:div.paikkauskohteet
       [kartta/kartan-paikka]
       [yllapitokohteet-view/yllapitokohteet paikkaus/paikkauskohteet
        {:otsikko "Paikkauskohteet"
         :nakyma :paikkaus
         :tallenna (fn [kohteet]
                     (go (let [urakka-id (:id @nav/valittu-urakka)
                               [sopimus-id _] @u/valittu-sopimusnumero
                               _ (log "[PAIK] Tallennetaan paikkauskohteet: " (pr-str kohteet))
                               vastaus (<! (yllapitokohteet/tallenna-yllapitokohteet!
                                             urakka-id sopimus-id
                                             (mapv #(assoc % :tyyppi :paikkaus)
                                                   kohteet)))]
                           (if (k/virhe? vastaus)
                             (viesti/nayta! "Kohteiden tallentaminen epännistui" :warning viesti/viestin-nayttoaika-keskipitka)
                             (do
                               (log "[PAIK]paikkaustyskohteet tallennettu: " (pr-str vastaus))
                               (reset! paikkaus/paikkauskohteet vastaus))))))
         :kun-onnistuu (fn [_]
                         (urakka/lukitse-urakan-yha-sidonta! (:id ur)))}]

       [yllapitokohteet-view/yllapitokohteet-yhteensa
        paikkaus/paikkauskohteet {:nakyma :paikkaus}]

       [:div.kohdeluettelon-paivitys
        [yha/paivita-kohdeluettelo ur oikeudet/urakat-kohdeluettelo-paikkauskohteet]
        [yha/kohdeluettelo-paivitetty ur]]])))
