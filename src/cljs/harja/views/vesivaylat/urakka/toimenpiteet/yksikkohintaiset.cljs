(ns harja.views.vesivaylat.urakka.toimenpiteet.yksikkohintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as kok-hint]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu-tiedot]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn hinnoittelu-vaihtoehdot [e! {:keys [toimenpiteet] :as app}]
  [yleiset/livi-pudotusvalikko
   {:valitse-fn #(log "Painoit ryhmää")
    :valinta "Valittu hintaryhmä"
    :disabled (not (jaettu-tiedot/joku-valittu? toimenpiteet))}
   (tiedot/hintaryhmien-nimet app)])

(defn lisaysnappi [e! {:keys [toimenpiteet] :as app}]
  [napit/yleinen-ensisijainen
   "Lisää"
   #(log "Painoit nappia")
   {:disabled (not (jaettu-tiedot/joku-valittu? toimenpiteet))}])

(defn ryhman-luonti [e! {:keys [uuden-hintaryhman-lisays? uusi-hintaryhma] :as app}]
  (if uuden-hintaryhman-lisays?
    [:span
     [kentat/tee-kentta {:tyyppi :string
                         :placeholder "Ryhmän nimi"
                         :pituus-max 160}
      (r/wrap
        uusi-hintaryhma
        #(e! (tiedot/->UudenHintaryhmanNimeaPaivitetty %)))]
     [napit/yleinen-ensisijainen "Luo" #(log "Painoit nappia")]
     [napit/yleinen-ensisijainen "Peruuta" #(e! (tiedot/->UudenHintaryhmanLisays? false))]]

    [napit/yleinen-ensisijainen
     "Luo uusi ryhmä"
     #(e! (tiedot/->UudenHintaryhmanLisays? true))]))

(defn hinnoittelu [e! app]
  [:span
   [hinnoittelu-vaihtoehdot e! app]
   [lisaysnappi e! app]
   [ryhman-luonti e! app]])

(defn urakkatoiminnot [e! app]
  [^{:key "siirto"}
  [jaettu/siirtonappi e! app "Siirrä kokonaishintaisiin" #(e! (tiedot/->SiirraValitutKokonaishintaisiin))]
   ^{:key "hinnoittelu"}
   [hinnoittelu e! app]])

(defn- yksikkohintaiset-toimenpiteet-nakyma [e! app valinnat]
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
       [jaettu/suodattimet e! tiedot/->PaivitaValinnat app (:urakka valinnat) tiedot/vaylahaku
        {:urakkatoiminnot (urakkatoiminnot e! app)}]
       [jaettu/listaus e! app {:lisa-sarakkeet [{:otsikko "Hinta" :hae (constantly "TODO") :leveys 10}]
                               :jaottelu [{:otsikko "Yksikköhintaiset toimenpiteet" :jaottelu-fn identity}]
                               :paneelin-checkbox-sijainti "94.3%"
                               :vaylan-checkbox-sijainti "94.3%"}]])))

(defn- yksikkohintaiset-toimenpiteet* [e! app]
  [yksikkohintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                :sopimus @u/valittu-sopimusnumero
                                                :aikavali @u/valittu-aikavali}])

(defn yksikkohintaiset-toimenpiteet []
  [tuck (jaettu-tiedot/yhdista-tilat! tiedot/tila kok-hint/tila) yksikkohintaiset-toimenpiteet*])