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
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.ui.kentat :as kentat]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;;;;;;
;; Urakkatoiminnot: Hintaryhmän valitseminen

(defn- hinnoittelu-vaihtoehdot [e! {:keys [valittu-hintaryhma toimenpiteet hintaryhmat] :as app}]
  [yleiset/livi-pudotusvalikko
   {:valitse-fn #(e! (tiedot/->ValitseHintaryhma %))
    :format-fn #(or (::h/nimi %) "Valitse hintaryhmä")
    :class "livi-alasveto-250 inline-block"
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

;;;;;;;;;;;;
;; Hinnan antamisen leijuke

(defn- kenttarivi [e! app* otsikko]
  [:tr
   [:td [:b otsikko]]
   [:td
    [:span
     [tee-kentta {:tyyppi :numero :kokonaisosan-maara 7}
      (r/wrap (tiedot/hinnan-maara app* otsikko)
              (fn [uusi]
                (e! (tiedot/->HinnoitteleToimenpideKentta {::hinta/otsikko otsikko
                                                           ::hinta/maara uusi}))))]
     [:span " "]
     "€"]]
   [:td
    (when (= otsikko "Yleiset materiaalit")
      [tee-kentta {:tyyppi :checkbox}
       (r/wrap (tiedot/hinnan-yleiskustannuslisa app* otsikko)
               (fn [uusi]
                 (e! (tiedot/->HinnoitteleToimenpideKentta {::hinta/otsikko otsikko
                                                            ::hinta/yleiskustannuslisa uusi}))))])]])

(defn- hinnoittele-toimenpide [e! app* rivi]
  (let [hinnoittele-toimenpide-id (get-in app* [:hinnoittele-toimenpide ::to/id])]
    [:div
     (if (and hinnoittele-toimenpide-id
              (= hinnoittele-toimenpide-id (::to/id rivi)))
       [:div
        [:span "Hinta: 0€"]
        [leijuke {:otsikko "Hinnoittele toimenpide"
                  :sulje! #(e! (tiedot/->PeruToimenpiteenHinnoittelu))}
         [:div.vv-toimenpiteen-hinnoittelutiedot
          {:on-click #(.stopPropagation %)}

          [:table.vv-toimenpiteen-hinnoittelutiedot-grid
           [:thead
            [:tr
             [:th {:style {:width "45%"}}]
             [:th {:style {:width "35%"}} "Hinta"]
             [:th {:style {:width "20%"}} "Yleis\u00ADkustan\u00ADnusli\u00ADsä"]]]
           [:tbody
            (kenttarivi e! app* "Työ")
            (kenttarivi e! app* "Komponentit")
            (kenttarivi e! app* "Yleiset materiaalit")
            (kenttarivi e! app* "Matkat")
            (kenttarivi e! app* "Muut kulut")]]

          [:div {:style {:margin-top "1em" :margin-bottom "1em"}}
           [:span
            [:b "Yhteensä:"]
            [:span " "]
            (fmt/euro-opt (hinta/kokonaishinta
                            (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit])))]]

          [:footer.vv-toimenpiteen-hinnoittelu-footer
           [napit/yleinen-ensisijainen
            "Valmis"
            #(e! (tiedot/->HinnoitteleToimenpide (:hinnoittele-toimenpide app*)))
            {:disabled (:hinnoittelun-tallennus-kaynnissa? app*)}]]]]]

       (grid/erikoismuokattava-kentta
         {:ehto-fn #(not (to/toimenpiteella-oma-hinnoittelu? rivi))
          :nappi-teksti "Hinnoittele"
          :toiminto-fn #(e! (tiedot/->AloitaToimenpiteenHinnoittelu (::to/id rivi)))
          :nappi-optiot {:disabled (:infolaatikko-nakyvissa? app*)}
          :arvo (fmt/euro-opt (hinta/kokonaishinta
                                (get-in rivi [::to/oma-hinnoittelu ::h/hinnat])))}))]))

(defn- hintaryhman-hinnoittelu [e! app hintaryhma]
  (let [hinnoitellaan? (get-in app [:hinnoittele-hintaryhma ::h/id])]
    [:div
     (if hinnoitellaan?
       [:div
        [napit/yleinen-ensisijainen
         "Valmis"
         #(log "DOH VALMIS!")
         {:luokka "pull-right"}]
        [napit/yleinen-ensisijainen
         "Peruuta"
         #(log "DOH PERUUTA!")
         {:luokka "pull-right"}]]
       [napit/yleinen-ensisijainen
        "Määrittele yksi hinta koko ryhmälle"
        #(log "DOH!")
        {:luokka "pull-right"}])]))

(defn- yksikkohintaiset-toimenpiteet-nakyma [e! app valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->PaivitaValinnat {:urakka-id (get-in valinnat [:urakka :id])
                                                          :sopimus-id (first (:sopimus valinnat))
                                                          :aikavali (:aikavali valinnat)}))
                           (e! (tiedot/->HaeHintaryhmat)))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! {:keys [toimenpiteet] :as app}]
      (let [toimenpiteet-ryhmissa (to/toimenpiteet-hintaryhmissa toimenpiteet)]
        @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.

        [:div
         [jaettu/suodattimet e! tiedot/->PaivitaValinnat app (:urakka valinnat) tiedot/vaylahaku
          {:urakkatoiminnot (urakkatoiminnot e! app)}]

         (for [[hintaryhma hintaryhman-toimenpiteet] toimenpiteet-ryhmissa
               :let [app* (assoc app :toimenpiteet hintaryhman-toimenpiteet)]]
           ^{:key (str "yksikkohintaiset-toimenpiteet-" (::h/nimi hintaryhma))}
           [jaettu/listaus e! app*
            {:lisa-sarakkeet [{:otsikko "Hinta" :tyyppi :komponentti :leveys 10
                               :komponentti (fn [rivi]
                                              [hinnoittele-toimenpide e! app* rivi])}]
             :footer (when hintaryhma
                       [hintaryhman-hinnoittelu e! app hintaryhma])
             :otsikko (or (to/hintaryhman-otsikko hintaryhma hintaryhman-toimenpiteet)
                          "Kokonaishintaisista siirretyt, valitse hintaryhmä.")
             :hintaryhma hintaryhma
             :paneelin-checkbox-sijainti "95.2%"
             :vaylan-checkbox-sijainti "95.2%"}])]))))

(defn- yksikkohintaiset-toimenpiteet* [e! app]
  [yksikkohintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                :sopimus @u/valittu-sopimusnumero
                                                :aikavali @u/valittu-aikavali}])

(defn yksikkohintaiset-toimenpiteet []
  [tuck (jaettu-tiedot/yhdista-tilat! tiedot/tila kok-hint/tila) yksikkohintaiset-toimenpiteet*])