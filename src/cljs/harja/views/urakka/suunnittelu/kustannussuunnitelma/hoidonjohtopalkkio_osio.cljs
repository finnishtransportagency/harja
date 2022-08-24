(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.hoidonjohtopalkkio-osio
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.grid-apurit :as grid-apurit]))


;; -- Hoidonjohtopalkkio osioon liittyvät gridit --

(def hoidonjohtopalkkio-grid
  (partial grid-apurit/maarataulukko-grid "hoidonjohtopalkkio" [:yhteenvedot :johto-ja-hallintokorvaukset]
    {:tallennus-onnistui-event t/->TallennaHoidonjohtopalkkioOnnistui}))

;; | -- Gridit päättyy



;; -----
;; -- Hoidonjohtopalkkio osion apufunktiot --

(defn hoidonjohtopalkkio [hoidonjohtopalkkio-grid kantahaku-valmis?]
  (if (and hoidonjohtopalkkio-grid kantahaku-valmis?)
    [grid/piirra hoidonjohtopalkkio-grid]
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio-yhteenveto
  [hoidonjohtopalkkio-yhteensa hoidonjohtopalkkio-yhteensa-indeksikorjattu kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    (let [hinnat (mapv (fn [hjp]
                         {:summa hjp})
                   hoidonjohtopalkkio-yhteensa)
          hinnat-indeksikorjattu (mapv (fn [hjp]
                                         {:summa hjp})
                                   hoidonjohtopalkkio-yhteensa-indeksikorjattu)]
      [:div.summa-ja-indeksilaskuri
       [ks-yhteiset/hintalaskuri {:otsikko nil
                                  :selite "Hoidonjohtopalkkio"
                                  :hinnat hinnat
                                  :data-cy "hoidonjohtopalkkio-hintalaskuri"}
        kuluva-hoitokausi]
       [ks-yhteiset/indeksilaskuri-ei-indeksikorjausta hinnat-indeksikorjattu indeksit
        {:data-cy "hoidonjohtopalkkio-indeksilaskuri"}]])
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio-sisalto
  [vahvistettu?
   hoidonjohtopalkkio-grid
   suodattimet hoidonjohtopalkkio-yhteensa
   hoidonjohtopalkkio-yhteensa-indeksikorjattu kuluva-hoitokausi
   indeksit
   kantahaku-valmis?]
  [:<>
   [:h2 {:id (str (get t/hallinnollisten-idt :hoidonjohtopalkkio) "-osio")} "Hoidonjohtopalkkio"]
   [hoidonjohtopalkkio-yhteenveto hoidonjohtopalkkio-yhteensa hoidonjohtopalkkio-yhteensa-indeksikorjattu kuluva-hoitokausi indeksit kantahaku-valmis?]

   [:div {:data-cy "hoidonjohtopalkkio-taulukko-suodattimet"}
    [ks-yhteiset/yleis-suodatin suodattimet]]

   ;; FIXME: "Osio-vahvistettu" luokka on väliaikainen hack, jolla osion input kentät saadaan disabloitua kunnes muutosten seuranta ehditään toteuttaa.
   [:div {:class (when vahvistettu? "osio-vahvistettu")}
    [hoidonjohtopalkkio hoidonjohtopalkkio-grid kantahaku-valmis?]]])


;; ### Hoidonjohtopalkkio osion pääkomponentti ###

(defn osio
  [vahvistettu?
   hoidonjohtopalkkio-grid
   hoidonjohtopalkkio-yhteensa
   hoidonjohtopalkkio-yhteensa-indeksikorjattu
   indeksit
   kuluva-hoitokausi
   suodattimet
   kantahaku-valmis?]
  [hoidonjohtopalkkio-sisalto
   vahvistettu?
   hoidonjohtopalkkio-grid suodattimet hoidonjohtopalkkio-yhteensa hoidonjohtopalkkio-yhteensa-indeksikorjattu kuluva-hoitokausi indeksit kantahaku-valmis?])
