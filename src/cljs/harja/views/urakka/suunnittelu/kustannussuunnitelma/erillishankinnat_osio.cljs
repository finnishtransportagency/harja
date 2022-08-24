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
  (partial grid-apurit/maarataulukko-grid "erillishankinnat" [:yhteenvedot :johto-ja-hallintokorvaukset]
    {:tallennus-onnistui-event t/->TallennaErillishankinnatOnnistui}))

;; | -- Gridit päättyy



;; -----
;; -- Erillishankinnat osion apufunktiot --

(defn- erillishankinnat-yhteenveto
  [indeksit yhteensa-summat indeksikorjatut-yhteensa-summat kuluva-hoitokausi kantahaku-valmis?]
  (if (and yhteensa-summat kantahaku-valmis?)
    [:div.summa-ja-indeksilaskuri
     [ks-yhteiset/hintalaskuri {:otsikko nil
                                :selite "Toimitilat + Kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät"
                                :hinnat (mapv (fn [summa]
                                                {:summa summa})
                                          yhteensa-summat)
                                :data-cy "erillishankinnat-hintalaskuri"}
      kuluva-hoitokausi]
     [ks-yhteiset/indeksilaskuri-ei-indeksikorjausta
      (mapv (fn [summa] {:summa summa}) indeksikorjatut-yhteensa-summat)
      indeksit
      {:data-cy "erillishankinnat-indeksilaskuri"}]]
    [yleiset/ajax-loader]))

(defn- erillishankinnat-sisalto [vahvistettu? indeksit erillishankinnat-grid erillishankinnat-yhteensa erillishankinnat-indeksikorjatut-yhteensa
                                 kantahaku-valmis? suodattimet kuluva-hoitokausi]
  (let [nayta-erillishankinnat-grid? (and kantahaku-valmis? erillishankinnat-grid)]
    [:<>
     [:h2 {:id (str (get t/hallinnollisten-idt :erillishankinnat) "-osio")} "Erillishankinnat"]
     [erillishankinnat-yhteenveto indeksit erillishankinnat-yhteensa erillishankinnat-indeksikorjatut-yhteensa
      kuluva-hoitokausi kantahaku-valmis?]

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
  [vahvistettu? indeksit erillishankinnat-grid erillishankinnat-yhteensa erillishankinnat-indeksikorjatut-yhteensa
   kantahaku-valmis? suodattimet kuluva-hoitokausi]

  [erillishankinnat-sisalto
   vahvistettu?
   indeksit erillishankinnat-grid
   erillishankinnat-yhteensa erillishankinnat-indeksikorjatut-yhteensa
   kantahaku-valmis? suodattimet kuluva-hoitokausi])
