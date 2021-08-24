(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.tilaajan-varaukset-osio
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]))


;; -- Tilaajan varaukset osioon liittyvät gridit --

;; NOTE: Gridit määritellään kustannussuunnitelma_view pääkomponentissa suoraan maarataulukko-grid apurilla.

;; | -- Gridit päättyy



;; ### Tilaajan varaukset osion pääkomponentti ###

(defn osio
  [tilaajan-varaukset-grid suodattimet kantahaku-valmis?]

  (let [nayta-tilaajan-varaukset-grid? (and kantahaku-valmis? tilaajan-varaukset-grid)]
    [:<>
     [:h2 {:id (str (get t/hallinnollisten-idt :tilaajan-varaukset) "-osio")} "Tilaajan rahavaraukset"]
     [:div [:span "Varaukset mm. bonuksien laskemista varten. Näitä varauksia"] [:span.lihavoitu " ei lasketa mukaan tavoitehintaan"]]
     [ks-yhteiset/yleis-suodatin suodattimet]

     (if nayta-tilaajan-varaukset-grid?
       [grid/piirra tilaajan-varaukset-grid]
       [yleiset/ajax-loader])]))