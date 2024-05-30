(ns harja.views.hallinta.urakkahenkilot
  (:require [clojure.string :as str]
            [harja.ui.ikonit :as ikonit]
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

(defn th-elementti [e! rivi {:keys [sarake suunta]}]
  [:th
   [:button
    {:tab-index 0
     :on-click #(e! (tiedot/->JarjestaTaulukko rivi))}
    (case rivi
      :urakka "Urakka"
      :nimi "Nimi"
      :puhelin "Puhelinnumero"
      :sahkoposti "Sähköposti"
      :rooli "Rooli")
    (when (= sarake rivi)
      (if (= :ylos suunta)
        [ikonit/harja-icon-action-sort-ascending]
        [ikonit/harja-icon-action-sort-descending]))]])

(defn urakkahenkilot* [e! app]
  (e! (tiedot/->HaeUrakkahenkilot :hoito false))
  (fn [e! {:keys [urakkahenkilot jarjestys] :as app}]
    [:div.urakkahenkilot-hallinta
     [harja.ui.debug/debug app]
     [valinnat/urakkatyyppi
      yhteiset/valittu-urakkatyyppi
      nav/+urakkatyypit+
      #(reset! yhteiset/valittu-urakkatyyppi %)]
     ;; todo: raksiboksi - päättynyt?
     ;; todo: Kopioi csv leikepöydälle ehkä???
     [:h2 "Urakoiden vastuuhenkilöt ja urakanvalvojat"]
     [:div.livi-grid
      [:table
       [:thead
        [:tr
         [th-elementti e! :urakka jarjestys]
         [th-elementti e! :nimi jarjestys]
         [th-elementti e! :puhelin jarjestys]
         [th-elementti e! :sahkoposti jarjestys]
         [th-elementti e! :rooli jarjestys]]]
       [:tbody
        (for* [{:keys [urakka nimi puhelin sahkoposti rooli]} urakkahenkilot]
          [:tr
           [:td urakka]
           [:td nimi]
           [:td puhelin]
           [:td sahkoposti]
           [:td rooli]])]]]]))

(defn urakkahenkilot []
  [tuck tiedot/tila urakkahenkilot*])
