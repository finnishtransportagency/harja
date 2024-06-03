(ns harja.views.hallinta.urakkahenkilot
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as transit]
            [reagent.core :as r]
            [clojure.string :as str]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
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

(defn urakkahenkilot* [e! _app]
  (e! (tiedot/->HaeUrakkahenkilot))
  (fn [e! {:keys [urakkatyyppi urakkahenkilot jarjestys paattyneet?]}]
    [:div.urakkahenkilot-hallinta
     [:div.valinnat
      [valinnat/urakkatyyppi
       (r/wrap urakkatyyppi
         ;; Tätä vain dereffataan, ei tarvetta tehdä muutosfunktiota
         (constantly nil))
       nav/+urakkatyypit+
       #(e! (tiedot/->ValitseUrakkatyyppi %))]
      [kentat/tee-otsikollinen-kentta
       {:otsikko "Näytä päättyneet urakat"
        :arvo-atom paattyneet?
        :otsikon-tag :div
        :kentta-params
        {:tyyppi :checkbox
         :vayla-tyyli? true
         :valitse! #(e! (tiedot/->PaattyneetValittu (not paattyneet?)))}}]]
     [:div.flex-row.tasaa-alas
      [:h2 "Urakoiden vastuuhenkilöt ja urakanvalvojat"]
      [:span.inline-block
       [:form {:style {:margin-left "auto"}
               :target "_blank" :method "POST"
               :action (k/excel-url :hae-urakkahenkilot-exceliin)}
        [:input {:type "hidden" :name "parametrit"
                 :value (transit/clj->transit {:urakkatyyppi (:arvo urakkatyyppi)
                                               :paattyneet? paattyneet?})}]
        [:button {:type "submit"
                  :class #{"nappi-toissijainen nappi-reunaton"}}
         [ikonit/ikoni-ja-teksti (ikonit/livicon-download) "Vie exceliin"]]]]]
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
