(ns harja.views.urakka.paikkauskohteet
  "Paikkauskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                                      livi-pudotusvalikko]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka.paikkaus :as paikkaus]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn paikkauskohteet []
  (komp/luo
    (komp/ulos #(kartta/poista-popup!))
    (komp/lippu paikkaus/paikkauskohteet-nakymassa?)
    (fn []
      [yllapitokohteet/yllapitokohteet paikkaus/paikkauskohteet]
      [yllapitokohteet/yllapitokohteet-yhteensa paikkaus/paikkauskohteet])))
