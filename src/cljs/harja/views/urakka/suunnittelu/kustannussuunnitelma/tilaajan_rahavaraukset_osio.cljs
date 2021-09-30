(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.tilaajan-rahavaraukset-osio
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.grid-apurit :as grid-apurit]))


;; -- Tilaajan varaukset osioon liittyvät gridit --

(def tilaajan-varaukset-grid
  (partial grid-apurit/maarataulukko-grid "tilaajan-varaukset" [:yhteenvedot :tilaajan-varaukset]
    {:paivita-kattohinta? true :indeksikorjaus? true}))

;; | -- Gridit päättyy



;; ### Tilaajan varaukset osion pääkomponentti ###

(defn osio
  [tilaajan-varaukset-grid suodattimet kantahaku-valmis?]

  (let [nayta-tilaajan-varaukset-grid? (and kantahaku-valmis? tilaajan-varaukset-grid)]
    [:<>
     [:h2 {:id (str (get t/hallinnollisten-idt :tilaajan-varaukset) "-osio")} "Tilaajan rahavaraukset"]
     [:div [:span "Varaukset mm. bonuksien laskemista varten. Näitä varauksia"] [:span.lihavoitu " ei lasketa mukaan tavoitehintaan"]]

     [:div {:data-cy "tilaajan-varaukset-taulukko-suodattimet"}
      [ks-yhteiset/yleis-suodatin suodattimet]]

     (if nayta-tilaajan-varaukset-grid?
       [grid/piirra tilaajan-varaukset-grid]
       [yleiset/ajax-loader])]))