(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.erillishankinnat-osio
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.grid-apurit :as grid-apurit]))


;; -- Erillishankinnat-osioon liittyvät gridit --

(def erillishankinnat-grid
  (partial grid-apurit/maarataulukko-grid "erillishankinnat" [:yhteenvedot :johto-ja-hallintokorvaukset]))

;; | -- Gridit päättyy



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
                                  :hinnat yhteenveto
                                  :data-cy "erillishankinnat-hintalaskuri"}
        kuluva-hoitokausi]
       [ks-yhteiset/indeksilaskuri yhteenveto indeksit
        {:data-cy "erillishankinnat-indeksilaskuri"}]])
    [yleiset/ajax-loader]))

(defn- erillishankinnat-sisalto [vahvistettu? erillishankinnat-grid erillishankinnat-yhteensa indeksit kantahaku-valmis? suodattimet kuluva-hoitokausi]
  (let [nayta-erillishankinnat-grid? (and kantahaku-valmis? erillishankinnat-grid)]
    [:<>
     [:h3 {:id (str (get t/hallinnollisten-idt :erillishankinnat) "-osio")} "Erillishankinnat"]
     [erillishankinnat-yhteenveto erillishankinnat-yhteensa indeksit kuluva-hoitokausi kantahaku-valmis?]

     [:div {:data-cy "erillishankinnat-taulukko-suodattimet"}
      [ks-yhteiset/yleis-suodatin suodattimet]]

     (if nayta-erillishankinnat-grid?
       ;; FIXME: "Osio-vahvistettu" luokka on väliaikainen hack, jolla osion input kentät saadaan disabloitua kunnes muutosten seuranta ehditään toteuttaa.
       [:div {:class (when vahvistettu? "osio-vahvistettu")}
        [grid/piirra erillishankinnat-grid]]
       [yleiset/ajax-loader])

     [:span "Yhteenlaskettu kk-määrä: Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät (mm. ajantasainen seuranta, suolan automaattinen seuranta)"]]))


;; ### Erillishankinnat osion pääkomponentti ###
(defn osio
  [vahvistettu? erillishankinnat-grid erillishankinnat-yhteensa indeksit kantahaku-valmis? suodattimet kuluva-hoitokausi]
  [erillishankinnat-sisalto
   vahvistettu?
   erillishankinnat-grid erillishankinnat-yhteensa indeksit kantahaku-valmis? suodattimet kuluva-hoitokausi])
