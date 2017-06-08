(ns harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as yks-hint]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu-tiedot]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn urakkatoiminnot [e! app]
  [^{:key "siirto"}
   [jaettu/siirtonappi e! app "Siirrä yksikköhintaisiin" #(e! (tiedot/->SiirraValitutYksikkohintaisiin))]])

(defn- kokonaishintaiset-toimenpiteet-nakyma [e! app valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->PaivitaValinnat {:urakka-id (get-in valinnat [:urakka :id])
                                                          :sopimus-id (first (:sopimus valinnat))
                                                          :aikavali (:aikavali valinnat)})))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.

      [:div
       [jaettu/suodattimet e!
        tiedot/->PaivitaValinnat
        app (:urakka valinnat)
        tiedot/vaylahaku
        {:urakkatoiminnot (urakkatoiminnot e! app)}]
       [jaettu/listaus e! app
        {:otsikko "Kokonaishintaiset toimenpiteet"
         :paneelin-checkbox-sijainti "94.3%"
         :vaylan-checkbox-sijainti "94.3%"}]])))

(defn- kokonaishintaiset-toimenpiteet* [e! app]
  [kokonaishintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                 :sopimus @u/valittu-sopimusnumero
                                                 :aikavali @u/valittu-aikavali}])

(defn kokonaishintaiset-toimenpiteet []
  [tuck (jaettu-tiedot/yhdista-tilat! tiedot/tila yks-hint/tila) kokonaishintaiset-toimenpiteet*])