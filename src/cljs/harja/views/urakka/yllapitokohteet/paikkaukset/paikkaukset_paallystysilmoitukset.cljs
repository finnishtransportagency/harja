(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.debug :as debug]))

(defn paallystysilmoitukset* [e! app]
  (fn [e! app]
    [:div
     [:h1 "Paikkauskohteiden päällystysilmoitukset"]
     [debug/debug app]]))

(defn paallystysilmoitukset []
  (fn []
    [tuck/tuck tila/paikkauskohteet paallystysilmoitukset*]))
