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
            [harja.ui.leijuke :refer [leijuke]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.ui.kentat :as kentat]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.fmt :as fmt])
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

(defn- kenttarivi [app e! otsikko]
  [:tr
   [:td [:b otsikko]]
   [:td
    [:span
     [tee-kentta {:tyyppi :numero :kokonaisosan-maara 6}
      (r/wrap (->> (get-in app [:hinnoittele-toimenpide ::h/hinta-elementit])
                   (filter #(= (::hinta/otsikko %) otsikko))
                   (first)
                   (::hinta/maara))
              (fn [uusi]
                (e! (tiedot/->HinnoitteleToimenpideKentta {::hinta/otsikko otsikko ::hinta/maara uusi}))))]
     [:span " "]
     "€"]]
   [:td
    (when (= otsikko "Yleiset materiaalit")
      [tee-kentta {:tyyppi :checkbox}
       (r/wrap (atom false)
               (fn [uusi]
                 ;; TODO Käsittele ja tallenna arvo tuck-tilaan
                 (log "UUSI ARVO: " (pr-str uusi))))])]])

(defn- laske-hinnoittelun-kokonaishinta [hinnoittelutiedot]
  (reduce + 0 (map ::hinta/maara hinnoittelutiedot)))

(defn- hinnoittele-toimenpide [app e! rivi]
  [:div
   (if (and (get-in app [:hinnoittele-toimenpide ::to/id])
            (= (get-in app [:hinnoittele-toimenpide ::to/id])
               (::to/id rivi)))
     [:div
      [:span "Hinta: 0€"]
      [leijuke {:otsikko "Hinnoittele toimenpide"
                :sulje! #(e! (tiedot/->PeruToimenpiteenHinnoittelu))}
       [:div.vv-toimenpiteen-hinnoittelutiedot
        {:on-click #(.stopPropagation %)}

        [:table.vv-toimenpiteen-hinnoittelutiedot-grid
         [:thead
          [:tr
           [:th {:style {:width "50%"}}]
           [:th {:style {:width "25%"}}]
           [:th {:style {:width "25%"}} "Yleis\u00ADkustan\u00ADnusli\u00ADsä"]]]
         [:tbody
          (kenttarivi app e! "Työ")
          (kenttarivi app e! "Komponentit")
          (kenttarivi app e! "Yleiset materiaalit")
          (kenttarivi app e! "Matkat")
          (kenttarivi app e! "Muut kulut")]]

        [:div {:style {:margin-top "1em" :margin-bottom "1em"}}
         [:span
          [:b "Yhteensä:"]
          [:span " "]
          (fmt/euro-opt (laske-hinnoittelun-kokonaishinta
                          (get-in app [:hinnoittele-toimenpide ::h/hinta-elementit])))]]

        [:footer.vv-toimenpiteen-hinnoittelu-footer
         [napit/yleinen-ensisijainen
          "Valmis"
          #(e! (tiedot/->HinnoitteleToimenpide (:hinnoittele-toimenpide app)))
          {:disabled (:hinnoittelun-tallennus-kaynnissa? app)}]]]]]

     [napit/yleinen-ensisijainen
      "Hinnoittele"
      #(e! (tiedot/->AloitaToimenpiteenHinnoittelu (::to/id rivi)))
      {:luokka "nappi-grid"
       :disabled (:infolaatikko-nakyvissa? app)}])])

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