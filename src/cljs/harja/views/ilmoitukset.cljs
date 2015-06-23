(ns harja.views.ilmoitukset
  "Harjan ilmoituksien pääsivu."
  (:require [reagent.core :refer [atom]]

            [harja.tiedot.ilmoitukset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :refer [lomake]]))

(defn ilmoituksen-tiedot
  []
  [:div "Ilmoituksen tarkemmat tiedot"])

(defn ilmoitusten-paanakyma
  []
  [:div
   [:h3 "Ilmoitukset"]
   (when @tiedot/valittu-urakka
     [:button.nappi-toissijainen "Urakan sivulle"])

   [:span "Tähän lomake"]])

(defn ilmoitukset []
  (komp/luo
    (komp/lippu tiedot/ilmoitusnakymassa?)

    (fn []
      (if @tiedot/valittu-ilmoitus
        [ilmoituksen-tiedot]
        [ilmoitusten-paanakyma]))))