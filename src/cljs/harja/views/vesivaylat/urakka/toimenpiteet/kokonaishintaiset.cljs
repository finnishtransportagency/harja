(ns harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as yks-hint]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu-tiedot]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.ui.debug :as debug]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- kiintiovaihtoehdot [e! {:keys [valittu-kiintio-id toimenpiteet kiintiot] :as app}]
  [:div.inline-block {:style {:margin-right "10px"}}
   [yleiset/livi-pudotusvalikko
    {:valitse-fn #(e! (tiedot/->ValitseKiintio (::kiintio/id %)))
     :format-fn #(or (::kiintio/nimi %) "Valitse kiintiö")
     :class "livi-alasveto-250"
     :valinta (kiintio/kiintio-idlla kiintiot valittu-kiintio-id)
     :disabled (not (jaettu-tiedot/joku-valittu? toimenpiteet))}
    kiintiot]])

(defn- liita-kiintioon-nappi [e! {:keys [toimenpiteet kiintioon-liittaminen-kaynnissa?] :as app}]
  [napit/yleinen-ensisijainen
   (if kiintioon-liittaminen-kaynnissa?
     [yleiset/ajax-loader-pieni "Liitetään.."]
     "Liitä")
   #(e! (tiedot/->LiitaToimenpiteetKiintioon))
   {:disabled (or (not (jaettu-tiedot/joku-valittu? toimenpiteet))
                  kiintioon-liittaminen-kaynnissa?)}])

(defn- liita-kiintioon [e! app]
  [:span
   [:span {:style {:margin-right "10px"}} "Liitä valitut kiintiöön"]
   [kiintiovaihtoehdot e! app]
   [liita-kiintioon-nappi e! app]])

(defn urakkatoiminnot [e! app]
  [^{:key "siirto"}
   [jaettu/siirtonappi e! app "Siirrä yksikköhintaisiin" #(e! (tiedot/->SiirraValitutYksikkohintaisiin))]
   ^{:key "kiintio"}
   [liita-kiintioon e! app]])

(defn- kokonaishintaiset-toimenpiteet-nakyma [e! app valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->PaivitaValinnat {:urakka-id (get-in valinnat [:urakka :id])
                                                          :sopimus-id (first (:sopimus valinnat))
                                                          :aikavali (:aikavali valinnat)}))
                           (e! (tiedot/->HaeKiintiot)))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.

      [:div
       [debug/debug app]
       [jaettu/suodattimet e!
        tiedot/->PaivitaValinnat
        app (:urakka valinnat)
        tiedot/vaylahaku
        {:urakkatoiminnot (urakkatoiminnot e! app)}]
       [jaettu/tulokset e! app
        [jaettu/listaus e! app
        {:otsikko "Kokonaishintaiset toimenpiteet"
         :listaus-tunniste :kokonaishintaiset-toimenpiteet
         :paneelin-checkbox-sijainti "94.3%"
         :vaylan-checkbox-sijainti "94.3%"}]]])))

(defn- kokonaishintaiset-toimenpiteet* [e! app]
  [kokonaishintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                 :sopimus @u/valittu-sopimusnumero
                                                 :aikavali @u/valittu-aikavali}])

(defn kokonaishintaiset-toimenpiteet []
  [tuck (jaettu-tiedot/yhdista-tilat! tiedot/tila yks-hint/tila) kokonaishintaiset-toimenpiteet*])