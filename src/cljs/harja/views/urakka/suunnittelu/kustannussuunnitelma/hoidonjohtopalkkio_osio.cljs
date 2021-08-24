(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.hoidonjohtopalkkio-osio
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]))


;; -- Hoidonjohtopalkkio osioon liittyvät gridit --

;; NOTE: Gridit määritellään kustannussuunnitelma_view pääkomponentissa suoraan maarataulukko-grid apurilla.

;; | -- Gridit päättyy



;; -----
;; -- Hoidonjohtopalkkio osion apufunktiot --

(defn hoidonjohtopalkkio [hoidonjohtopalkkio-grid kantahaku-valmis?]
  (if (and hoidonjohtopalkkio-grid kantahaku-valmis?)
    [grid/piirra hoidonjohtopalkkio-grid]
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio-yhteenveto
  [hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    (let [hinnat (mapv (fn [hjp]
                         {:summa hjp})
                   hoidonjohtopalkkio-yhteensa)]
      [:div.summa-ja-indeksilaskuri
       [ks-yhteiset/hintalaskuri {:otsikko nil
                                  :selite "Hoidonjohtopalkkio"
                                  :hinnat hinnat}
        kuluva-hoitokausi]
       [ks-yhteiset/indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio-sisalto
  [hoidonjohtopalkkio-grid
   suodattimet hoidonjohtopalkkio-yhteensa kuluva-hoitokausi
   indeksit
   kantahaku-valmis?]
  [:<>
   [:h3 {:id (str (get t/hallinnollisten-idt :hoidonjohtopalkkio) "-osio")} "Hoidonjohtopalkkio"]
   [hoidonjohtopalkkio-yhteenveto hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
   [ks-yhteiset/yleis-suodatin suodattimet]
   [hoidonjohtopalkkio hoidonjohtopalkkio-grid kantahaku-valmis?]])


;; ### Hoidonjohtopalkkio osion pääkomponentti ###

(defn osio
  [hoidonjohtopalkkio-grid
   hoidonjohtopalkkio-yhteensa
   indeksit
   kuluva-hoitokausi
   suodattimet
   kantahaku-valmis?]
  [hoidonjohtopalkkio-sisalto hoidonjohtopalkkio-grid suodattimet hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?])
