(ns harja.views.vesivaylat.urakka.toimenpiteet.yksikkohintaiset
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as kok-hint]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu-tiedot]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
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
            [harja.ui.grid :as grid]
            [harja.ui.debug :as debug]
            [harja.domain.oikeudet :as oikeudet])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;;;;;;
;; Urakkatoiminnot: Hintaryhmän valitseminen

(defn- hinnoitteluvaihtoehdot [e! {:keys [valittu-hintaryhma toimenpiteet hintaryhmat] :as app}]
  [:div.inline-block {:style {:margin-right "10px"}}
   [yleiset/livi-pudotusvalikko
    {:valitse-fn #(e! (tiedot/->ValitseHintaryhma %))
     :format-fn #(or (::h/nimi %) "Valitse tilaus")
     :class "livi-alasveto-250"
     :valinta valittu-hintaryhma
     :disabled (not (jaettu-tiedot/joku-valittu? toimenpiteet))}
    hintaryhmat]])

(defn- siirra-hinnoitteluun-nappi [e! {:keys [toimenpiteet valittu-hintaryhma
                                              hintaryhmien-liittaminen-kaynnissa?] :as app}]
  [napit/yleinen-ensisijainen
   (if hintaryhmien-liittaminen-kaynnissa?
     [yleiset/ajax-loader-pieni "Liitetään.."]
     "Siirrä")
   #(e! (tiedot/->LiitaValitutHintaryhmaan
          valittu-hintaryhma
          (jaettu-tiedot/valitut-toimenpiteet toimenpiteet)))
   {:disabled (or (not (jaettu-tiedot/joku-valittu? toimenpiteet))
                  (not valittu-hintaryhma)
                  hintaryhmien-liittaminen-kaynnissa?
                  (not (oikeudet/on-muu-oikeus? "siirrä-tilaukseen"
                                                oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                (:id @nav/valittu-urakka))))}])

(defn- hintaryhman-luonti [e! {:keys [hintaryhmat uuden-hintaryhman-lisays? uusi-hintaryhma
                                      hintaryhman-tallennus-kaynnissa?] :as app}]
  (if uuden-hintaryhman-lisays?
    [:span
     [:div.inline-block {:style {:margin-right "10px"}}
      [tee-kentta {:tyyppi :string
                   :placeholder "Tilauksen nimi"
                   :pituus-max 160}
       (r/wrap
         uusi-hintaryhma
         #(e! (tiedot/->UudenHintaryhmanNimeaPaivitetty %)))]]
     [napit/yleinen-ensisijainen
      (if hintaryhman-tallennus-kaynnissa? [yleiset/ajax-loader-pieni "Luodaan.."] "Luo")
      #(e! (tiedot/->LuoHintaryhma uusi-hintaryhma))
      {:disabled (or ;; Disabloidaan nappi jos nimi on jo olemassa, liittäminen menossa tai teksti puuttuu
                   ((set (map ::h/nimi hintaryhmat)) uusi-hintaryhma)
                   (empty? uusi-hintaryhma)
                   hintaryhman-tallennus-kaynnissa?)}]
     [napit/peruuta "Peruuta" #(e! (tiedot/->UudenHintaryhmanLisays? false))]]

    [napit/yleinen-ensisijainen
     "Luo uusi tilaus"
     #(e! (tiedot/->UudenHintaryhmanLisays? true))
     {:disabled (not (oikeudet/on-muu-oikeus? "tilausten-muokkaus"
                                              oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                              (:id @nav/valittu-urakka)))}]))

(defn- hinnoittelu [e! app]
  [:span
   [:span {:style {:margin-right "10px"}} "Siirrä valitut tilaukseen"]
   [hinnoitteluvaihtoehdot e! app]
   [siirra-hinnoitteluun-nappi e! app]
   [hintaryhman-luonti e! app]])

(defn- urakkatoiminnot [e! app]
  [^{:key "siirto"}
  [jaettu/siirtonappi e! app
   "Siirrä kokonaishintaisiin"
   #(e! (tiedot/->SiirraValitutKokonaishintaisiin))
   #(oikeudet/on-muu-oikeus? "siirrä-kokonaishintaisiin"
                             oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                             (:id @nav/valittu-urakka))]
   ^{:key "hinnoittelu"}
   [hinnoittelu e! app]])

;;;;;;;;;;;;
;; Hinnan antamisen leijuke

(defn- kenttarivi
  [e! app* otsikko]
  ;; HOX Otsikko on sekä UI:lla näkyvä otsikko että hinnan nimi kannassa.
  ;; Jos otsikkoa muutetaan, joudutaan myös hintojen nimet kannassa migratoimaan.
  [:tr
   [:td [:b otsikko]]
   [:td
    [:span
     [tee-kentta {:tyyppi :numero :kokonaisosan-maara 7}
      (r/wrap (hinta/hinnan-maara
                (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit])
                otsikko)
              (fn [uusi]
                (e! (tiedot/->HinnoitteleToimenpideKentta {::hinta/otsikko otsikko
                                                           ::hinta/maara uusi}))))]
     [:span " "]
     "€"]]
   [:td
    [tee-kentta {:tyyppi :checkbox}
     (r/wrap (if-let [yleiskustannuslisa (hinta/hinnan-yleiskustannuslisa
                                           (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit])
                                           otsikko)]
               (pos? yleiskustannuslisa)
               false)
             (fn [uusi]
               (e! (tiedot/->HinnoitteleToimenpideKentta
                     {::hinta/otsikko otsikko
                      ::hinta/yleiskustannuslisa (if uusi
                                                   hinta/yleinen-yleiskustannuslisa
                                                   0)}))))]]])

(defn- hinnoittele-toimenpide [e! app* rivi listaus-tunniste]
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
            [kenttarivi e! app* "Työ"]
            [kenttarivi e! app* "Komponentit"]
            [kenttarivi e! app* "Yleiset materiaalit"]
            [kenttarivi e! app* "Matkakulut"]
            [kenttarivi e! app* "Muut kulut"]]]

          [:div {:style {:margin-top "1em" :margin-bottom "1em"}}
           [yleiset/tietoja {:tietokentan-leveys "180px"}
            "Perushinta:" (fmt/euro-opt (hinta/perushinta
                                          (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit])))
            "Yleiskustannuslisät (12%):" (fmt/euro-opt (hinta/yleiskustannuslisien-osuus
                                                         (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit])))
            "Yhteensä:" (fmt/euro-opt (hinta/kokonaishinta-yleiskustannuslisineen
                                        (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit])))]]

          [:footer.vv-toimenpiteen-hinnoittelu-footer
           [napit/tallenna
            "Valmis"
            #(e! (tiedot/->HinnoitteleToimenpide (:hinnoittele-toimenpide app*)))
            {:disabled (or (:toimenpiteen-hinnoittelun-tallennus-kaynnissa? app*)
                           (not (oikeudet/on-muu-oikeus? "hinnoittele-toimenpide"
                                                         oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                         (:id @nav/valittu-urakka))))}]]]]]

       (grid/arvo-ja-nappi
         {:voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                  (get-in app* [:valinnat :urakka-id]))
          :ehto-fn #(not (to/toimenpiteella-oma-hinnoittelu? rivi))
          :nappi-teksti "Hinnoittele"
          :uusi-fn #(e! (tiedot/->AloitaToimenpiteenHinnoittelu (::to/id rivi)))
          :muokkaa-fn #(e! (tiedot/->AloitaToimenpiteenHinnoittelu (::to/id rivi)))
          :nappi-optiot {:disabled (or (listaus-tunniste (:infolaatikko-nakyvissa app*))
                                       (not (oikeudet/on-muu-oikeus? "hinnoittele-toimenpide"
                                                                     oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                                     (:id @nav/valittu-urakka))))}
          :arvo (fmt/euro-opt (hinta/kokonaishinta-yleiskustannuslisineen
                                (get-in rivi [::to/oma-hinnoittelu ::h/hinnat])))
          :ikoninappi? true}))]))

(defn- hintaryhman-hinnoittelu [e! app* hintaryhma]
  (let [hinnoittelu-id (get-in app* [:hinnoittele-hintaryhma ::h/id])
        hintaryhman-toimenpiteet (:toimenpiteet app*)
        hintaryhman-toimenpiteiden-omat-hinnat (remove
                                                 nil?
                                                 (mapcat #(get-in % [::to/oma-hinnoittelu ::h/hinnat])
                                                         hintaryhman-toimenpiteet))
        hintaryhman-toimenpiteiden-yhteishinta (hinta/kokonaishinta-yleiskustannuslisineen
                                                 hintaryhman-toimenpiteiden-omat-hinnat)
        hinnoitellaan? (and hinnoittelu-id (= hinnoittelu-id (::h/id hintaryhma)))
        hinnat (::h/hinnat hintaryhma)
        hintaryhman-kokonaishinta (hinta/kokonaishinta-yleiskustannuslisineen hinnat)]
    [:div.vv-hintaryhman-hinnoittelu-wrapper
     [:div.vv-hintaryhman-hinnoittelu
      (if hinnoitellaan?
        [:div
         [:div.inline-block {:style {:margin-right "10px"}}
          [tee-kentta {:tyyppi :numero
                       :placeholder "Syötä hinta"
                       :kokonaisosan-maara 7}
           (r/wrap (hinta/hinnan-maara
                     (get-in app* [:hinnoittele-hintaryhma ::h/hintaelementit])
                     tiedot/hintaryhman-hintakentta-otsikko)
                   #(e! (tiedot/->HinnoitteleHintaryhmaKentta
                          {::hinta/otsikko tiedot/hintaryhman-hintakentta-otsikko
                           ::hinta/maara %})))]
          [:span " "]
          [:span "€"]]
         [napit/tallenna
          "Valmis"
          #(e! (tiedot/->HinnoitteleHintaryhma (:hinnoittele-hintaryhma app*)))
          {:disabled (or (:hintaryhman-hinnoittelun-tallennus-kaynnissa? app*)
                         (not (oikeudet/on-muu-oikeus? "hinnoittele-tilaus"
                                                       oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                       (:id @nav/valittu-urakka))))}]
         [napit/peruuta
          "Peruuta"
          #(e! (tiedot/->PeruHintaryhmanHinnoittelu))]]
        (if (empty? hinnat)
          [napit/yleinen-ensisijainen
           "Määrittele yksi hinta koko tilaukselle"
           #(e! (tiedot/->AloitaHintaryhmanHinnoittelu (::h/id hintaryhma)))
           {:disabled (:hintaryhman-hinnoittelun-tallennus-kaynnissa? app*)}]
          [:div
           [:div.inline-block {:style {:margin-right "10px"}}
            (if (zero? hintaryhman-toimenpiteiden-yhteishinta)
              [:span
               [:b "Tilauksen hinta: "] [:span (fmt/euro-opt (hinta/kokonaishinta-yleiskustannuslisineen hinnat))]]
              ;; Yleensä hintaryhmän toimenpiteillä on vain yksi könttähinta.
              ;; On kuitenkin mahdollista määrittää myös toimenpiteille omia hintoja hintaryhmän sisällä
              ;; Näytetään tällöin ryhmän hinta, toimenpiteiden kok. hinta ja yhteissumma
              [yleiset/tietoja {:tietokentan-leveys "180px"}
               "Toimenpiteet:" (fmt/euro-opt hintaryhman-toimenpiteiden-yhteishinta)
               "Tilauksen hinta:" (fmt/euro-opt hintaryhman-kokonaishinta)
               "Yhteensä:" (fmt/euro-opt (+ hintaryhman-toimenpiteiden-yhteishinta hintaryhman-kokonaishinta))])]
           [:div.inline-block {:style {:vertical-align :top}}
            [napit/yleinen-toissijainen
             (ikonit/muokkaa)
             #(e! (tiedot/->AloitaHintaryhmanHinnoittelu (::h/id hintaryhma)))
             {:ikoninappi? true
              :disabled (not (oikeudet/on-muu-oikeus? "hinnoittele-tilaus"
                                                      oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                      (:id @nav/valittu-urakka)))}]]]))]]))

(defn- yksikkohintaiset-toimenpiteet-nakyma [e! app valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do
                         (e! (tiedot/->Nakymassa? true))
                         (e! (tiedot/->PaivitaValinnat {:urakka-id (get-in valinnat [:urakka :id])
                                                        :sopimus-id (first (:sopimus valinnat))
                                                        :aikavali (:aikavali valinnat)}))
                         (e! (tiedot/->HaeHintaryhmat)))
                      #(do
                         (u/valitse-oletussopimus-jos-valittuna-kaikki!)
                         (e! (tiedot/->Nakymassa? false))))
    (fn [e! {:keys [toimenpiteet toimenpiteiden-haku-kaynnissa? hintaryhmat] :as app}]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.

      (let [hintaryhmat (concat
                          [{::h/nimi "Kokonaishintaisista siirretyt, valitse tilaus."}]
                          (h/jarjesta-hintaryhmat hintaryhmat))]
        [:div
         [jaettu/suodattimet e! tiedot/->PaivitaValinnat app (:urakka valinnat) tiedot/vaylahaku
          {:urakkatoiminnot (urakkatoiminnot e! app)}]

         [jaettu/tulokset e! app
          [:div
           (for [hintaryhma hintaryhmat
                 :let [hintaryhma-id (::h/id hintaryhma)
                       hintaryhman-toimenpiteet (to/toimenpiteet-hintaryhmalla toimenpiteet hintaryhma-id)
                       app* (assoc app :toimenpiteet hintaryhman-toimenpiteet)
                       listaus-tunniste (keyword (str "listaus-" hintaryhma-id))
                       hintaryhma-tyhja? (::h/tyhja? hintaryhma) ;; Ei sisällä toimenpiteitä kannassa
                       nayta-hintaryhma?
                       (boolean
                         (or (and (not hintaryhma-id)  ;; Kok. hint. siirretyt -ryhmä, jos ei tyhjä
                                  (not (empty? hintaryhman-toimenpiteet)))
                             hintaryhma-tyhja? ;; Kannassa täysin tyhjä hintaryhmä; piirretään aina, jotta voi poistaa
                             (not (empty? hintaryhman-toimenpiteet)))) ;; Sis. toimenpiteitä käytetyillä suodattimilla
                       nayta-hintaryhman-yhteenveto? (boolean (and hintaryhma-id
                                                                   (not (empty? hintaryhman-toimenpiteet))))]]

             (when nayta-hintaryhma?
               ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-hintaryhma")}
               [:div.vv-toimenpideryhma
                ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-otsikko")}
                [jaettu/hintaryhman-otsikko (h/hintaryhman-nimi hintaryhma)]

                (if hintaryhma-tyhja?
                  ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-top-level")}
                  [:div
                   ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-ohje")}
                   [:p "Ei toimenpiteitä - Lisää tilaukseen toimenpiteitä valitsemalla haluamasi toimenpiteet ja valitsemalla yltä toiminto \"Siirrä valitut tilaukseen\"."]
                   ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-poistonappi")}
                   [napit/poista "Poista tyhjä tilaus" #(e! (tiedot/->PoistaHintaryhmat #{hintaryhma-id}))
                    {:disabled (or (:hintaryhmien-poisto-kaynnissa? app)
                                   (not (oikeudet/on-muu-oikeus? "tilausten-muokkaus"
                                                                 oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                                 (:id @nav/valittu-urakka))))}]]
                  ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id)}
                  [jaettu/listaus e! app*
                   {:sarakkeet
                    [jaettu/sarake-tyoluokka
                     jaettu/sarake-toimenpide
                     jaettu/sarake-pvm
                     jaettu/sarake-turvalaite
                     jaettu/sarake-vikakorjaus
                     (jaettu/sarake-liitteet e! app #(oikeudet/on-muu-oikeus?
                                                       "lisää-liite"
                                                       oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                       (:id @nav/valittu-urakka)))
                     {:otsikko "Hinta" :tyyppi :komponentti :leveys 10
                      :komponentti (fn [rivi]
                                     [hinnoittele-toimenpide e! app* rivi listaus-tunniste])}
                     (jaettu/sarake-checkbox e! app)]
                    :listaus-tunniste listaus-tunniste
                    :footer (when nayta-hintaryhman-yhteenveto?
                              [hintaryhman-hinnoittelu e! app* hintaryhma])
                    :otsikko (h/hintaryhman-nimi hintaryhma)
                    :paneelin-checkbox-sijainti "95.5%"
                    :vaylan-checkbox-sijainti "95.5%"}])]))]]]))))

(defn- yksikkohintaiset-toimenpiteet* [e! app]
  [yksikkohintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                :sopimus @u/valittu-sopimusnumero
                                                :aikavali @u/valittu-aikavali}])

(defn yksikkohintaiset-toimenpiteet []
  [tuck (jaettu-tiedot/yhdista-tilat! tiedot/tila kok-hint/tila) yksikkohintaiset-toimenpiteet*])