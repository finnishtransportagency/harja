(ns harja.views.vesivaylat.urakka.toimenpiteet.toimenpiteen-hinnoittelu
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
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
            [harja.domain.vesivaylat.tyo :as tyo]
            [harja.domain.vesivaylat.turvalaitekomponentti :as tkomp]
            [harja.domain.vesivaylat.komponenttityyppi :as tktyyppi]
            [harja.domain.toimenpidekoodi :as tpk]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.debug :as debug]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.kartta :as kartta]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]))

(defn- muu-tyo-kentta
  [e! app* otsikko]
  [:span
   [:span
    [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 7}
     (r/wrap (hinta/hinnan-maara-otsikolla
               (get-in app* [:hinnoittele-toimenpide ::h/hinnat])
               otsikko)
             (fn [uusi]
               (e! (tiedot/->HinnoitteleToimenpideKentta {::hinta/otsikko otsikko
                                                          ::hinta/maara uusi}))))]]
   [:span " "]
   [:span "€"]])

(defn- yleiskustannuslisa-kentta
  [e! app* otsikko]
  [tee-kentta {:tyyppi :checkbox}
   (r/wrap (if-let [yleiskustannuslisa (hinta/hinnan-yleiskustannuslisa
                                         (get-in app* [:hinnoittele-toimenpide ::h/hinnat])
                                         otsikko)]
             (pos? yleiskustannuslisa)
             false)
           (fn [uusi]
             (e! (tiedot/->HinnoitteleToimenpideKentta
                   {::hinta/otsikko otsikko
                    ::hinta/yleiskustannuslisa (if uusi
                                                 hinta/yleinen-yleiskustannuslisa
                                                 0)}))))])

(defn toimenpiteen-hinnoittelutaulukko-hinnoittelurivi [e! app* otsikko]
  [:tr.muu-hinnoittelu-rivi
   [:td.hinnoittelun-otsikko.muu-hinnoittelu-osio (str otsikko ":")]
   [:td.muu-hinnoittelu-osio [muu-tyo-kentta e! app* otsikko]]
   [:td.muu-hinnoittelu-osio [yleiskustannuslisa-kentta e! app* otsikko]]
   [:td.muu-hinnoittelu-osio]])

(defn toimenpiteen-hinnoittelutaulukko-yhteenvetorivi [otsikko arvo]
  [:tr.hinnoittelun-yhteenveto-rivi
   [:td.hinnoittelun-otsikko (str otsikko ":")]
   [:td arvo]
   [:td]
   [:td]])

;; Toimenpiteen hinnoittelun pudotusvalikkoon:
(defrecord HintaValinta [nimi])
(defrecord TyoValinta [nimi yksikko toimenpidekoodi-id yksikkohinta])

(defn- suunniteltu-tyo->Record [suunniteltu-tyo]
  (->TyoValinta (:tehtavan_nimi suunniteltu-tyo)
                (:yksikko suunniteltu-tyo)
                (:tehtava suunniteltu-tyo)
                (:yksikkohinta suunniteltu-tyo)))

(defn- valiotsikkorivi [otsikko osio-luokka]
  [:tr.otsikkorivi
   [:td {:class osio-luokka}
    [:b otsikko]]
   [:td {:class osio-luokka}]
   [:td {:class osio-luokka}]
   [:td {:class osio-luokka}]])

(defn- hinnoittelun-yhteenveto [app*]
  (let [suunnitellut-tyot (:suunnitellut-tyot app*)
        tyorivit (tiedot/tyorivit-taulukosta->tallennusmuotoon
                   (get-in app* [:hinnoittele-toimenpide ::h/tyot]))
        hinnat (get-in app* [:hinnoittele-toimenpide ::h/hinnat])
        perushinnat-yhteensa (hinta/perushinta
                               (concat hinnat tyorivit))
        hinnat-yleiskustannuslisineen-yhteensa (hinta/kokonaishinta-yleiskustannuslisineen
                                                 (concat hinnat tyorivit))
        tyot-yhteensa (tyo/toiden-kokonaishinta tyorivit suunnitellut-tyot)
        yleiskustannuslisien-osuus (hinta/yleiskustannuslisien-osuus
                                     (get-in app* [:hinnoittele-toimenpide ::h/hinnat]))]
    [:tbody
     [valiotsikkorivi ""]
     [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
      "Perushinta" (fmt/euro-opt (+ perushinnat-yhteensa tyot-yhteensa))]
     [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
      "Yleiskustannuslisät (12%)" (fmt/euro-opt yleiskustannuslisien-osuus)]
     [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
      "Yhteensä" (fmt/euro-opt (+ hinnat-yleiskustannuslisineen-yhteensa tyot-yhteensa))]]))

(defn- toimenpiteen-hinnoittelutaulukko [e! app*]
  (let [valittu-aikavali (get-in app* [:valinnat :aikavali])
        suunnitellut-tyot (tpk/aikavalin-hinnalliset-suunnitellut-tyot (:suunnitellut-tyot app*)
                                                                       valittu-aikavali)
        tyot (get-in app* [:hinnoittele-toimenpide ::h/tyot])
        komponentit [{::tkomp/komponenttityyppi {::tktyyppi/nimi "Lateraalimerkki"}
                      ::tkomp/sarjanumero "123"
                      ::tkomp/turvalaitenro "8881"}
                     {::tkomp/komponenttityyppi {::tktyyppi/nimi "Lateraalimerkki"}
                      ::tkomp/sarjanumero "124"
                      ::tkomp/turvalaitenro "8882"}
                     {::tkomp/komponenttityyppi {::tktyyppi/nimi "Lateraalimerkki"}
                      ::tkomp/sarjanumero "125"
                      ::tkomp/turvalaitenro "8883"}]]
    ;; TODO Korkeus alkaa olla jo aikamoinen haaste, piirrä rivin alapuolelle document flowiin?
    [:table.vv-toimenpiteen-hinnoittelutiedot-grid
     [:thead
      [:tr
       [:th {:style {:width "55%"}}]
       [:th {:style {:width "30%"}} "Hinta / määrä"]
       [:th {:style {:width "10%"}} "YK-lisä"]
       [:th {:style {:width "5%"}} ""]]]
     [:tbody
      [valiotsikkorivi "Sopimushintaiset työt" :tyot-osio]
      (map-indexed
        (fn [index tyorivi]
          ^{:key index}
          [:tr.tyon-hinnoittelu-rivi
           [:td.tyot-osio.hinnoittelun-otsikko
            ;; TODO Tehdäänkö combobox?
            (let [hintavalinnat (map #(->HintaValinta %) tyo/tyo-hinnat)
                  tyovalinnat (map suunniteltu-tyo->Record suunnitellut-tyot)
                  kaikki-valinnat (concat hintavalinnat tyovalinnat)]
              [yleiset/livi-pudotusvalikko
               {:valitse-fn #(cond (instance? HintaValinta %)
                                   (e! (tiedot/->AsetaTyorivilleTiedot
                                         {:index index
                                          :toimenpidekoodi-id nil
                                          :hinta-nimi (:nimi %)}))

                                   (instance? TyoValinta %)
                                   (e! (tiedot/->AsetaTyorivilleTiedot
                                         {:index index
                                          :toimenpidekoodi-id (:toimenpidekoodi-id %)
                                          :hinta-nimi nil})))
                :format-fn #(cond (instance? HintaValinta %) (:nimi %)
                                  (instance? TyoValinta %) (str (:nimi %) " (" (fmt/euro (:yksikkohinta %))
                                                                " / " (:yksikko %) ")")
                                  :default "Valitse työ")
                :nayta-ryhmat [:hinta :tyo]
                :ryhmittely #(cond (instance? HintaValinta %) :hinta
                                   (instance? TyoValinta %) :tyo)
                :ryhman-otsikko #(case % :hinta "Hinta" :tyo "Sopimushinnat")
                :class "livi-alasveto-250 inline-block"
                :valinta (first (filter #(cond (instance? TyoValinta %)
                                               (= (:toimenpidekoodi-id tyorivi) (:toimenpidekoodi-id %))

                                               (instance? HintaValinta %)
                                               (= (:hinta-nimi tyorivi) (:nimi %)))
                                        kaikki-valinnat))
                :disabled false}
               kaikki-valinnat])]
           [:td.tyot-osio
            [:span
             [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 5}
              (r/wrap (:maara tyorivi)
                      (fn [uusi]
                        (e! (tiedot/->AsetaTyorivilleTiedot
                              {:index index
                               :maara uusi}))))]
             [:span " "]
             (let [toimenpidekoodi (tpk/toimenpidekoodi-tehtavalla suunnitellut-tyot
                                                                   (:toimenpidekoodi-id tyorivi))
                   hinta-nimi (:hinta-nimi tyorivi)
                   yksikko (:yksikko toimenpidekoodi)
                   yksikkohinta (:yksikkohinta toimenpidekoodi)
                   tyon-hinta-voidaan-laskea? (and yksikko yksikkohinta)]
               (cond tyon-hinta-voidaan-laskea?
                     [:span
                      yksikko
                      " (" (fmt/euro (* (:maara tyorivi) yksikkohinta)) ")"]

                     hinta-nimi "€"
                     :default nil))]]
           [:td.tyot-osio]
           [:td.tyot-osio
            [:span.klikattava
             {:on-click #(e! (tiedot/->PoistaHinnoiteltavaTyorivi {:index index}))}
             (ikonit/livicon-trash)]]])
        tyot)
      [:tr.tyon-hinnoittelu-rivi
       [:td.tyot-osio.hinnoittelun-otsikko.lisaa-rivi-solu
        [napit/uusi "Lisää työrivi" #(e! (tiedot/->LisaaHinnoiteltavaTyorivi))]]
       [:td.tyot-osio]
       [:td.tyot-osio]
       [:td.tyot-osio]]

      [valiotsikkorivi "Komponentit" :komponentit-osio]
      (map-indexed
        (fn [index komponentti]
          ^{:key index}
          [:tr.komponentin-hinnoittelu-rivi
           [:td.hinnoittelun-otsikko.komponentit-osio
            (str (get-in komponentti [::tkomp/komponenttityyppi ::tktyyppi/nimi])
                 " (" (::tkomp/sarjanumero komponentti) "):")]
           [:td.komponentit-osio
            [:span
             [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 5}
              (r/wrap 0
                      (fn [uusi]
                        (log "TODO")))]]
            [:span " "]
            [:span "€"]]
           [:td.komponentit-osio
            [yleiskustannuslisa-kentta e! app* ""]] ;; TODO Otsikko-hommeli ei nyt oikein toimi tässä
           [:td.komponentit-osio]])
        komponentit)

      [valiotsikkorivi "Muut" :muu-hinnoittelu-osio]
      (map-indexed
        (fn [index hinta]
          ^{:key index}
          [toimenpiteen-hinnoittelutaulukko-hinnoittelurivi
           e! app* (::hinta/otsikko hinta)])
        ;; TODO Käy läpi vain ryhmä "muut"
        (get-in app* [:hinnoittele-toimenpide ::h/hinnat]))
      [:tr.muu-hinnoittelu-rivi
       [:td.muu-hinnoittelu-osio.hinnoittelun-otsikko.lisaa-rivi-solu
        ;; TODO Tärkeää varmistaa ettei voi olla kahta hinnoittelua samalla nimellä per toimenpide
        [napit/uusi "Lisää kulurivi" #(log "TODO")]]
       [:td.muu-hinnoittelu-osio]
       [:td.muu-hinnoittelu-osio]
       [:td.muu-hinnoittelu-osio]]]
     [hinnoittelun-yhteenveto app*]]))

(defn- hinnoittele-toimenpide [e! app* toimenpide-rivi listaus-tunniste]
  (let [hinnoittele-toimenpide-id (get-in app* [:hinnoittele-toimenpide ::to/id])
        toimenpiteen-nykyiset-hinnat (get-in toimenpide-rivi [::to/oma-hinnoittelu ::h/hinnat])
        toimenpiteen-nykyiset-tyot (get-in toimenpide-rivi [::to/oma-hinnoittelu ::h/tyot])
        tyot (get-in app* [:hinnoittele-toimenpide ::h/tyot])
        valittu-aikavali (get-in app* [:valinnat :aikavali])
        suunnitellut-tyot (tpk/aikavalin-hinnalliset-suunnitellut-tyot (:suunnitellut-tyot app*)
                                                                       valittu-aikavali)
        hinnoittelu-validi? (every? #(or (:toimenpidekoodi-id %)
                                         (:hinta-nimi %))
                                    tyot)]
    [:div
     (if (and hinnoittele-toimenpide-id
              (= hinnoittele-toimenpide-id (::to/id toimenpide-rivi)))
       ;; Piirrä leijuke
       [:div
        [leijuke {:otsikko "Hinnoittele toimenpide"
                  :sulje! #(e! (tiedot/->PeruToimenpiteenHinnoittelu))}
         [:div.vv-toimenpiteen-hinnoittelutiedot
          {:on-click #(.stopPropagation %)}
          (if (or (nil? (:suunnitellut-tyot app*)) (true? (:suunniteltujen-toiden-haku-kaynnissa? app*)))
            [ajax-loader "Ladataan..."]
            [toimenpiteen-hinnoittelutaulukko e! app*])
          [:footer.vv-toimenpiteen-hinnoittelu-footer
           [napit/peruuta
            "Peruuta"
            #(e! (tiedot/->PeruToimenpiteenHinnoittelu))]
           [napit/tallenna
            "Valmis"
            #(e! (tiedot/->HinnoitteleToimenpide (:hinnoittele-toimenpide app*)))
            {:disabled (or
                         (not hinnoittelu-validi?)
                         (:toimenpiteen-hinnoittelun-tallennus-kaynnissa? app*)
                         (not (oikeudet/on-muu-oikeus? "hinnoittele-toimenpide"
                                                       oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                       (:id @nav/valittu-urakka))))}]]]]]

       ;; Solun sisältö
       (grid/arvo-ja-nappi
         {:sisalto (cond (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                        (get-in app* [:valinnat :urakka-id])))
                         :pelkka-arvo

                         (not (to/toimenpiteella-oma-hinnoittelu? toimenpide-rivi))
                         :pelkka-nappi

                         :default
                         :arvo-ja-nappi)
          :pelkka-nappi-teksti "Hinnoittele"
          :pelkka-nappi-toiminto-fn #(e! (tiedot/->AloitaToimenpiteenHinnoittelu (::to/id toimenpide-rivi)))
          :arvo-ja-nappi-toiminto-fn #(e! (tiedot/->AloitaToimenpiteenHinnoittelu (::to/id toimenpide-rivi)))
          :nappi-optiot {:disabled (or (listaus-tunniste (:infolaatikko-nakyvissa app*))
                                       (not (oikeudet/on-muu-oikeus? "hinnoittele-toimenpide"
                                                                     oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                                     (:id @nav/valittu-urakka))))}
          :arvo (fmt/euro-opt (+ (hinta/kokonaishinta-yleiskustannuslisineen toimenpiteen-nykyiset-hinnat)
                                 (tyo/toiden-kokonaishinta toimenpiteen-nykyiset-tyot
                                                           suunnitellut-tyot)))
          :ikoninappi? true}))]))