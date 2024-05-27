(ns harja.views.hallinta.urakkahenkilot
  (:require [clojure.string :as str]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [tuck.core :refer [tuck]]
            [harja.tiedot.hallinta.yhteiset :as yhteiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.tiedot.hallinta.urakkahenkilot :as tiedot])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn urakkahenkilot* [e! app]
  (e! (tiedot/->HaeUrakkahenkilot :hoito false))
  (fn [e! {:keys [urakkahenkilot] :as app}]
    [:<>
     [valinnat/urakkatyyppi
      yhteiset/valittu-urakkatyyppi
      nav/+urakkatyypit+
      #(reset! yhteiset/valittu-urakkatyyppi %)
      ;; todo: raksiboksi - päättynyt?
      ;; todo: Kopioi csv leikepöydälle ehkä???
      ]
     [:h2 "Urakoiden vastuuhenkilöt ja urakanvalvojat" ]
     [:table
      [:thead
       [:tr
        [:th "Urakka"]
        [:th "Nimi"]
        [:th "Puhelinnumero"]
        [:th "Sähköposti"]
        [:th "Rooli"]]]
      [:tbody
       (for* [{:keys [urakka etunimi sukunimi puhelin sahkoposti rooli]} urakkahenkilot]
         [:tr
          [:td (:nimi urakka)]
          [:td (str etunimi " " sukunimi)]
          [:td puhelin]
          [:td sahkoposti]
          [:td rooli]])]]]))

(defn urakkahenkilot []
  [tuck tiedot/tila urakkahenkilot*])
