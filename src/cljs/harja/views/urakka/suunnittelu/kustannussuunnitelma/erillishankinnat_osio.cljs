(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.erillishankinnat-osio
  (:require [reagent.core :as r :refer [atom]]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]
                   [harja.ui.taulukko.grid :refer [defsolu]]
                   [cljs.core.async.macros :refer [go go-loop]]))


;; -- Erillishankinnat-osioon liittyvät gridit --

;; NOTE: Gridit määritellään kustannussuunnitelma_view pääkomponentissa suoraan maarataulukko-grid apurilla.



;; -----
;; -- Erillishankinnat osion apufunktiot --

(defn- erillishankinnat-yhteenveto
  [erillishankinnat indeksit kuluva-hoitokausi kantahaku-valmis?]
  (if (and erillishankinnat kantahaku-valmis?)
    (let [yhteenveto (mapv (fn [summa]
                             {:summa summa})
                       erillishankinnat)]
      [:div.summa-ja-indeksilaskuri
       [ks-yhteiset/hintalaskuri {:otsikko nil
                                  :selite "Toimitilat + Kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät"
                                  :hinnat yhteenveto}
        kuluva-hoitokausi]
       [ks-yhteiset/indeksilaskuri yhteenveto indeksit]])
    [yleiset/ajax-loader]))

(defn- erillishankinnat-sisalto [erillishankinnat-grid erillishankinnat-yhteensa indeksit kantahaku-valmis? suodattimet kuluva-hoitokausi]
  (let [nayta-erillishankinnat-grid? (and kantahaku-valmis? erillishankinnat-grid)]
    [:<>
     [:h3 {:id (str (get t/hallinnollisten-idt :erillishankinnat) "-osio")} "Erillishankinnat"]
     [erillishankinnat-yhteenveto erillishankinnat-yhteensa indeksit kuluva-hoitokausi kantahaku-valmis?]
     [ks-yhteiset/yleis-suodatin suodattimet]

     (when nayta-erillishankinnat-grid?
       [grid/piirra erillishankinnat-grid])

     [:span "Yhteenlaskettu kk-määrä: Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät (mm. ajantasainen seuranta, suolan automaattinen seuranta)"]]))


;; ### Erillishankinnat osion pääkomponentti ###
(defn osio
  [erillishankinnat-grid erillishankinnat-yhteensa indeksit kantahaku-valmis? suodattimet kuluva-hoitokausi]
  [erillishankinnat-sisalto erillishankinnat-grid erillishankinnat-yhteensa indeksit kantahaku-valmis? suodattimet kuluva-hoitokausi])
