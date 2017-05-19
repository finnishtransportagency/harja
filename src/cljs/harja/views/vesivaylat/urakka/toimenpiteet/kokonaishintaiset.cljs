(ns harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.ui.napit :as napit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [reagent.core :as r]
            [harja.ui.kentat :as kentat])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn toolbar-napit [e! app]
  [^{:key "siirto"}
  [napit/yleinen-ensisijainen "Siirrä valitut yksikköhintaisiin"
   #(log "Painoit nappia")
   {:disabled (not (some :valittu? (:toimenpiteet app)))}]])

(defn- kokonaishintaiset-toimenpiteet-nakyma [e! app valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))
                                    (e! (tiedot/->HaeToimenpiteet {}))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->PaivitaValinnat {:urakka-id (get-in valinnat [:urakka :id])
                                                          :sopimus-id (first (:sopimus valinnat))
                                                          :aikavali (:aikavali valinnat)}))
                           (e! (tiedot/->HaeToimenpiteet {})))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.

      [:div
       [jaettu/suodattimet e! app (:urakka valinnat)
        tiedot/vaylahaku
        [[kentat/tee-kentta {:tyyppi :checkbox
                             :teksti "Näytä vain vikailmoituksista tulleet toimenpiteet"}
          (r/wrap (get-in app [:valinnat :vain-vikailmoitukset?])
                  (fn [uusi]
                    (e! (tiedot/->PaivitaValinnat {:vain-vikailmoitukset? uusi}))))]]
        (toolbar-napit e! app)]
       [jaettu/listaus e! app]])))

(defn- kokonaishintaiset-toimenpiteet* [e! app]
  [kokonaishintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                 :sopimus @u/valittu-sopimusnumero
                                                 :aikavali @u/valittu-aikavali}])

(defn kokonaishintaiset-toimenpiteet []
  [tuck tiedot/tila kokonaishintaiset-toimenpiteet*])