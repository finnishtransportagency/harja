(ns harja.views.tilannekuva.tilannekuvien-yhteiset-komponentit
  (:require [harja.tiedot.navigaatio :as nav]))

(defn nayta-hallinnolliset-tiedot []
  [:div
     "Koko maa"
     (cond
       (and @nav/valittu-hallintayksikko @nav/valittu-urakka)
       [:div (str "-> " (:nimi @nav/valittu-hallintayksikko))
        [:div (str "-> " (:nimi @nav/valittu-urakka))]]

       (or @nav/valittu-hallintayksikko)
       [:div (str "-> " (:nimi @nav/valittu-hallintayksikko))])])