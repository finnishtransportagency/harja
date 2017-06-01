(ns harja.views.vesivaylat.urakka.toimenpiteet.yksikkohintaiset
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as kok-hint]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu-tiedot]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.ui.kentat :as kentat]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.toimenpide :as to])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- hinnoittelu-vaihtoehdot [e! {:keys [valittu-hintaryhma toimenpiteet hintaryhmat] :as app}]
  [yleiset/livi-pudotusvalikko
   {:valitse-fn #(e! (tiedot/->ValitseHintaryhma %))
    :format-fn #(or (::h/nimi %) "Valitse hintaryhmä")
    :class "livi-alasveto-250 livi-alasveto-inline-block"
    :valinta valittu-hintaryhma
    :disabled (not (jaettu-tiedot/joku-valittu? toimenpiteet))}
   hintaryhmat])

(defn- lisaysnappi [e! {:keys [toimenpiteet valittu-hintaryhma
                               hintaryhmien-liittaminen-kaynnissa?] :as app}]
  [napit/yleinen-ensisijainen
   (if hintaryhmien-liittaminen-kaynnissa?
     [yleiset/ajax-loader-pieni "Liitetään.."]
     "Liitä")
   #(e! (tiedot/->LiitaValitutHintaryhmaan
          valittu-hintaryhma
          (jaettu-tiedot/valitut-toimenpiteet toimenpiteet)))
   {:disabled (or (not (jaettu-tiedot/joku-valittu? toimenpiteet))
                  hintaryhmien-liittaminen-kaynnissa?)}])

(defn- ryhman-luonti [e! {:keys [uuden-hintaryhman-lisays? uusi-hintaryhma
                                 hintaryhman-tallennus-kaynnissa?] :as app}]
  (if uuden-hintaryhman-lisays?
    [:span
     [tee-kentta {:tyyppi :string
                  :placeholder "Ryhmän nimi"
                  :pituus-max 160}
      (r/wrap
        uusi-hintaryhma
        #(e! (tiedot/->UudenHintaryhmanNimeaPaivitetty %)))]
     [napit/yleinen-ensisijainen
      (if hintaryhman-tallennus-kaynnissa? [yleiset/ajax-loader-pieni "Luodaan.."] "Luo")
      #(e! (tiedot/->LuoHintaryhma uusi-hintaryhma))
      {:disabled hintaryhman-tallennus-kaynnissa?}]
     [napit/yleinen-ensisijainen "Peruuta" #(e! (tiedot/->UudenHintaryhmanLisays? false))]]

    [napit/yleinen-ensisijainen
     "Luo uusi ryhmä"
     #(e! (tiedot/->UudenHintaryhmanLisays? true))]))

(defn- hinnoittelu [e! app]
  [:span
   [:span {:style {:margin-right "10px"}} "Siirrä valitut ryhmään"]
   [hinnoittelu-vaihtoehdot e! app]
   [lisaysnappi e! app]
   [ryhman-luonti e! app]])

(defn- urakkatoiminnot [e! app]
  [^{:key "siirto"}
  [:span {:style {:margin-right "10px"}}
   [jaettu/siirtonappi e! app "Siirrä kokonaishintaisiin" #(e! (tiedot/->SiirraValitutKokonaishintaisiin))]]
   ^{:key "hinnoittelu"}
   [hinnoittelu e! app]])

(defn- kenttarivi [otsikko]
  [otsikko [tee-kentta {:tyyppi :numero} (atom 0)]])

(defn- hinnoittele-toimenpide [app e! rivi]
  [:div.vv-toimenpiteen-hinnoittelu
   (if (and (get-in app [:hinnoittele-toimenpide ::to/id])
            (= (get-in app [:hinnoittele-toimenpide ::to/id])
               (::to/id rivi)))
     [:div.vv-toimenpiteen-hinnoittelutiedot-wrapper
      "Hinta: 0€"
      [:div.vv-toimenpiteen-hinnoittelutiedot
       {:on-click #(.stopPropagation %)}
       (into [yleiset/tietoja {}] (concat (kenttarivi "Työ")
                                          (kenttarivi "Komponentit")
                                          (kenttarivi "Yleiset materiaalit")
                                          (kenttarivi "Matkat")
                                          (kenttarivi "Muut kulut")))]]
     [napit/yleinen-ensisijainen
      "Hinnoittele"
      #(e! (tiedot/->HinnoitteleToimenpide (::to/id rivi)))
      {:luokka "nappi-grid"}])])

(defn- yksikkohintaiset-toimenpiteet-nakyma [e! app valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->HaeHintaryhmat))
                           (e! (tiedot/->PaivitaValinnat {:urakka-id (get-in valinnat [:urakka :id])
                                                          :sopimus-id (first (:sopimus valinnat))
                                                          :aikavali (:aikavali valinnat)})))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.

      [:div
       [jaettu/suodattimet e! tiedot/->PaivitaValinnat app (:urakka valinnat) tiedot/vaylahaku
        {:urakkatoiminnot (urakkatoiminnot e! app)}]
       [jaettu/listaus e! app {:lisa-sarakkeet [{:otsikko "Hinta" :tyyppi :komponentti :leveys 10
                                                 :komponentti (fn [rivi]
                                                                [hinnoittele-toimenpide app e! rivi])}]
                               :jaottelu [{:otsikko "Yksikköhintaiset toimenpiteet" :jaottelu-fn identity}]
                               :paneelin-checkbox-sijainti "95.2%"
                               :vaylan-checkbox-sijainti "95.2%"}]])))

(defn- yksikkohintaiset-toimenpiteet* [e! app]
  [yksikkohintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                :sopimus @u/valittu-sopimusnumero
                                                :aikavali @u/valittu-aikavali}])

(defn yksikkohintaiset-toimenpiteet []
  [tuck (jaettu-tiedot/yhdista-tilat! tiedot/tila kok-hint/tila) yksikkohintaiset-toimenpiteet*])