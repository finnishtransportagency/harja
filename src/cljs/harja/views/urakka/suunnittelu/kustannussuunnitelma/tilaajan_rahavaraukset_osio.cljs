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
    {:paivita-kattohinta? false :indeksikorjaus? false}))

;; | -- Gridit päättyy



;; ### Tilaajan varaukset osion pääkomponentti ###

(defn osio
  [tilaajan-varaukset-grid suodattimet kantahaku-valmis?]

  (let [nayta-tilaajan-varaukset-grid? (and kantahaku-valmis? tilaajan-varaukset-grid)]
    [:<>
     [:h2 {:id (str (get t/hallinnollisten-idt :tilaajan-varaukset) "-osio")} "Tavoitehinnan ulkopuoliset rahavaraukset"]
     [:div [:span "Tilaajan tekemät rahavaraukset, jotka eivät vaikuta tavoitehintaan."]]

     [:div {:data-cy "tilaajan-varaukset-taulukko-suodattimet"}
      [ks-yhteiset/yleis-suodatin suodattimet]]

     (if nayta-tilaajan-varaukset-grid?
       [grid/piirra tilaajan-varaukset-grid]
       [yleiset/ajax-loader])]))