(ns harja.views.urakka.paallystyskohteet
  "Päällystyskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                                      livi-pudotusvalikko]]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn paallystyskohteet []
  (komp/luo
    (komp/ulos #(kartta/poista-popup!))
    (komp/lippu paallystys/paallystyskohteet-nakymassa?)
    (fn []
      [yllapitokohteet/yllapitokohteet paallystys/paallystyskohteet {:paallystysnakyma? true}]
      [yllapitokohteet/yllapitokohteet-yhteensa paallystys/paallystyskohteet {:paallystysnakyma? true}])))
