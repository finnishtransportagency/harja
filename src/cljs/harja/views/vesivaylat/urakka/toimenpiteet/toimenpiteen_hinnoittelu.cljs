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
            [harja.domain.muokkaustiedot :as m]
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

(defn- hintakentta
  [e! hinta]
  [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 7}
   (r/wrap (::hinta/summa hinta)
           (fn [uusi]
             (e! (tiedot/->AsetaHintakentalleTiedot {::hinta/id (::hinta/id hinta)
                                                     ::hinta/summa uusi}))))])

(defn- yleiskustannuslisakentta
  [e! hinta]
  [tee-kentta {:tyyppi :checkbox}
   (r/wrap (if-let [yleiskustannuslisa (::hinta/yleiskustannuslisa hinta)]
             (pos? yleiskustannuslisa)
             false)
           (fn [uusi]
             (e! (tiedot/->AsetaHintakentalleTiedot
                   {::hinta/id (::hinta/id hinta)
                    ::hinta/yleiskustannuslisa (if uusi
                                                 hinta/yleinen-yleiskustannuslisa
                                                 0)}))))])

(defn vapaa-hinnoittelurivi [e! hinta]
  (let [otsikko (::hinta/otsikko hinta)]
    [:tr
     [:td
      (if (tiedot/vakiohintakentta? otsikko)
        otsikko
        [tee-kentta {:tyyppi :string}
         (r/wrap otsikko
                 (fn [uusi]
                   (e! (tiedot/->AsetaHintakentalleTiedot {::hinta/id (::hinta/id hinta)
                                                           ::hinta/otsikko uusi}))))])]
     [:td]
     [:td]
     [:td]
     [:td.tasaa-oikealle [hintakentta e! hinta]]
     [:td.keskita [yleiskustannuslisakentta e! hinta]]
     [:td
      (when-not (tiedot/vakiohintakentta? otsikko)
        [ikonit/klikattava-roskis #(e! (tiedot/->PoistaMuuKulurivi {::hinta/id (::hinta/id hinta)}))])]]))

(defn- toimenpiteen-hinnoittelutaulukko-yhteenvetorivi [otsikko arvo]
  [:tr.hinnoittelun-yhteenveto-rivi
   [:td otsikko]
   [:td]
   [:td]
   [:td]
   [:td.tasaa-oikealle arvo]
   [:td]
   [:td]])

(defn- valiotsikko [otsikko]
  [:h3.valiotsikko otsikko])

(defn- rivinlisays [otsikko toiminto]
  [:div.rivinlisays
   [napit/uusi otsikko toiminto]])

(defn- sopimushintaiset-tyot-header
  ([] (sopimushintaiset-tyot-header {:yk-lisa? true}))
  ([{:keys [yk-lisa?] :as optiot}]
   [:thead
    [:tr
     [:th {:style {:width "40%"}} "Työ"]
     [:th.tasaa-oikealle {:style {:width "15%"}} "Yks. hinta"]
     [:th.tasaa-oikealle {:style {:width "15%"}} "Määrä"]
     [:th {:style {:width "5%"}} "Yks."]
     [:th.tasaa-oikealle {:style {:width "10%"}} "Yhteensä"]
     [:th.tasaa-oikealle {:style {:width "10%"}} (when yk-lisa? "YK-lisä")]
     [:th {:style {:width "5%"}} ""]]]))

(defn- muu-hinnoittelu-header
  ([] (muu-hinnoittelu-header {:otsikot? false}))
  ([{:keys [otsikot?] :as optiot}]
   [:thead
    [:tr
     [:th {:style {:width "40%"}} (when otsikot? "Työ")]
     [:th.tasaa-oikealle {:style {:width "15%"}} ""]
     [:th.tasaa-oikealle {:style {:width "15%"}} ""]
     [:th {:style {:width "5%"}} ""]
     [:th.tasaa-oikealle {:style {:width "10%"}} (when otsikot? "Yhteensä")]
     [:th.tasaa-oikealle {:style {:width "10%"}} (when otsikot? "YK-lisä")]
     [:th {:style {:width "5%"}} ""]]]))

(defn- hinnoittelun-yhteenveto [app*]
  (let [suunnitellut-tyot (:suunnitellut-tyot app*)
        tyorivit (remove ::m/poistettu? (get-in app* [:hinnoittele-toimenpide ::h/tyot]))
        hinnat (remove ::m/poistettu? (get-in app* [:hinnoittele-toimenpide ::h/hinnat]))
        hinnat-yhteensa (hinta/hintojen-summa-ilman-yklisaa hinnat)
        tyot-yhteensa (tyo/toiden-kokonaishinta tyorivit suunnitellut-tyot)
        yleiskustannuslisien-osuus (hinta/yklisien-osuus hinnat)]
    [:div
     [valiotsikko ""]
     [:table
      [muu-hinnoittelu-header {:otsikot? false}]
      [:tbody
       [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
        "Hinnat yhteensä" (fmt/euro-opt (+ hinnat-yhteensa tyot-yhteensa))]
       [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
        "Yleiskustannuslisät (12%) yhteensä" (fmt/euro-opt yleiskustannuslisien-osuus)]
       [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
        "Kaikki yhteensä" (fmt/euro-opt (+ hinnat-yhteensa tyot-yhteensa
                                           yleiskustannuslisien-osuus))]]]]))

(defn- sopimushintaiset-tyot [e! app*]
  (let [tyot (get-in app* [:hinnoittele-toimenpide ::h/tyot])
        ei-poistetut-tyot (filterv (comp not ::m/poistettu?) tyot)]
    [:div.hinnoitteluosio.sopimushintaiset-tyot-osio
     [valiotsikko "Sopimushintaiset tyot ja materiaalit"]
     [:table
      ;; TODO Tehdäänkö combobox? --> ehkä, mutta ei ainakaan ennen kuin varsinainen hintojen anto toimii
      [sopimushintaiset-tyot-header {:yk-lisa? false}]
      [:tbody
       (map-indexed
         (fn [index tyorivi]
           (let [toimenpidekoodi (tpk/toimenpidekoodi-tehtavalla (:suunnitellut-tyot app*)
                                                                 (::tyo/toimenpidekoodi-id tyorivi))
                 yksikko (:yksikko toimenpidekoodi)
                 yksikkohinta (:yksikkohinta toimenpidekoodi)
                 tyon-hinta-voidaan-laskea? (boolean (and yksikkohinta yksikko))]
             ^{:key index}
             [:tr
              [:td
               (let [tyovalinnat (:suunnitellut-tyot app*)]
                 [yleiset/livi-pudotusvalikko
                  {:valitse-fn #(do
                                  (e! (tiedot/->AsetaTyorivilleTiedot
                                        {::tyo/id (::tyo/id tyorivi)
                                         ::tyo/toimenpidekoodi-id (:tehtava %)})))
                   :format-fn #(if %
                                 (:tehtavan_nimi %)
                                 "Valitse työ")
                   :class "livi-alasveto-250 inline-block"
                   :valinta (first (filter #(= (::tyo/toimenpidekoodi-id tyorivi)
                                               (:tehtava %))
                                           tyovalinnat))
                   :disabled false}
                  tyovalinnat])]
              [:td.tasaa-oikealle (fmt/euro-opt yksikkohinta)]
              [:td.tasaa-oikealle
               [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 5}
                (r/wrap (::tyo/maara tyorivi)
                        (fn [uusi]
                          (e! (tiedot/->AsetaTyorivilleTiedot
                                {::tyo/id (::tyo/id tyorivi)
                                 ::tyo/maara uusi}))))]]
              [:td yksikko]
              [:td.tasaa-oikealle
               (when tyon-hinta-voidaan-laskea? (fmt/euro (* (::tyo/maara tyorivi) yksikkohinta)))]
              [:td]
              [:td.keskita
               [ikonit/klikattava-roskis #(e! (tiedot/->PoistaHinnoiteltavaTyorivi {::tyo/id (::tyo/id tyorivi)}))]]]))
         ei-poistetut-tyot)]]
     [rivinlisays "Lisää työrivi" #(e! (tiedot/->LisaaHinnoiteltavaTyorivi))]]))

(defn- muut-tyot [e! app*]
  [:div.hinnoitteluosio.sopimushintaiset-tyot-osio
   [valiotsikko "Muut työt (ei indeksilaskentaa)"]
   [:table
    ;; TODO Tähän kirjataan vapaasti tehtyjä töitä
    ;; - Tehty työ onkin vapaata tekstiä eikä toimenpidekoodi-id
    ;; - Tallennetaan vv_hinta tauluun: otsikko, määrä, yksikkö, yksikköhinta. Ryhmäksi tallennetaan :tyo.
    ;; - Tässä näytetään vain :työ ryhmään kuuluvat vv_hinnat, ei muuta
    ;; - Kaukon kanssa palaveerattu tämä ja edelleen on tärkeää, että syötetää määrä, yksikköhinta ja yksikkö
    [sopimushintaiset-tyot-header]
    [:tbody
     (map-indexed
       (fn [index tyorivi]

         ^{:key index}
         [:tr
          [:td] ;; TODO Työn otsikko
          [:td.tasaa-oikealle] ;; TODO Yksikköhinta
          [:td.tasaa-oikealle] ;; TODO Määrä
          [:td] ;; TODO Yksikkö
          [:td] ;; TODO Yhteensä
          [:td.keskita [yleiskustannuslisakentta e! nil]] ;; TODO YK-lisä
          [:td.keskita
           [ikonit/klikattava-roskis #(log "TODO")]]])
       [])]]
   [rivinlisays "Lisää työrivi" #(log "TODO")]])

(defn- komponentit [e! app*]
  (let [;; TODO Komponenttien hinnoittelu. Pitää tehdä näin:
        ;; - Listataan tässä pudotusvalikossa kaikki toimenpiteeseen kuuluvat komponentit jotka on vaihdettu / lisätty
        ;; - Lisätään nappi jolla voi lisätä oman rivin ja valita siihen komponentin ja antaa sille hinnan. Pakko ei ole lisätä yhtään riviä jos ei halua
        ;; - Alla hardkoodattu esimerkki, ei välttämättä vastaa läheskään lopullista tietomallia, oli vain #nopee #tosinopee #upee demo
        ;; - Tallennetaan vv_hinta tauluun: määrä, yksikköhinta (ja yksikkö?). Vaatii myös linkin. Otsikkoa ei kai tarvita?
        ;; - Vaatii tietomallipäivityksen, jossa tämä linkittyy komponenttille tehtyyn toimenpiteeseen
        ;; - Tallennetaan vv_hinta.ryhma arvoksi :komponentti
        komponentit-testidata [{::tkomp/komponenttityyppi {::tktyyppi/nimi "Lateraalimerkki, vaihdettu"}
                                ::tkomp/sarjanumero "123"
                                ::tkomp/turvalaitenro "8881"}
                               {::tkomp/komponenttityyppi {::tktyyppi/nimi "Lateraalimerkki, lisätty"}
                                ::tkomp/sarjanumero "124"
                                ::tkomp/turvalaitenro "8882"}]]
    [:div.hinnoitteluosio.komponentit-osio
     [valiotsikko "Komponentit"]
     [:table
      [sopimushintaiset-tyot-header]
      [:tbody
       (map-indexed
         (fn [index komponentti]
           ^{:key index}
           [:tr
            [:td
             (str (get-in komponentti [::tkomp/komponenttityyppi ::tktyyppi/nimi])
                  " (" (::tkomp/sarjanumero komponentti) ")")]
            [:td.tasaa-oikealle
             [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 5}
              (r/wrap 0
                      (fn [uusi]
                        (log "TODO")))]]
            [:td.tasaa-oikealle
             [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 5}
              (r/wrap 0
                      (fn [uusi]
                        (log "TODO")))]]
            [:td]
            [:td] ; TODO Yhteensä
            [:td.keskita [yleiskustannuslisakentta e! nil]] ;; TODO YK-lisä
            [:td.keskita
             [ikonit/klikattava-roskis #(log "TODO POISTA KOMPONENTTI!")]]]) ; TODO
         komponentit-testidata)]]
     [rivinlisays "Lisää komponenttirivi" #(log "TODO LISÄÄ KOMPONENTTIRIVI")]])) ; TODO

(defn- muut-hinnat [e! app*]
  [:div.hinnoitteluosio.muut-osio
   [valiotsikko "Muut"]
   [:table
    [muu-hinnoittelu-header]
    [:tbody
     (map-indexed
       (fn [index hinta]
         ^{:key index}
         ;; TODO Tänne ei saa kirjoittaa to/vakiohinnat sisältöä, muuten tapahtuu ikäviä
         ;; TODO Täällä tulisi listata vain hinnat joissa vv_hinta.ryhma = :muu
         [vapaa-hinnoittelurivi e! hinta])
       (filter
         #(and (= (::hinta/ryhma %) :muu) (not (::m/poistettu? %)))
         (get-in app* [:hinnoittele-toimenpide ::h/hinnat])))]]
   [rivinlisays "Lisää kulurivi" #(e! (tiedot/->LisaaMuuKulurivi))]])

(defn- toimenpiteen-hinnoittelutaulukko [e! app*]
  [:div.vv-toimenpiteen-hinnoittelutiedot
   [sopimushintaiset-tyot e! app*]
   [muut-tyot e! app*]
   [komponentit e! app*]
   [muut-hinnat e! app*]
   [hinnoittelun-yhteenveto app*]])

(defn- hinnoittele-toimenpide [e! app* toimenpide-rivi listaus-tunniste]
  (let [hinnoittele-toimenpide-id (get-in app* [:hinnoittele-toimenpide ::to/id])
        toimenpiteen-nykyiset-hinnat (get-in toimenpide-rivi [::to/oma-hinnoittelu ::h/hinnat])
        toimenpiteen-nykyiset-tyot (get-in toimenpide-rivi [::to/oma-hinnoittelu ::h/tyot])
        hinnoiteltavat-tyot (get-in app* [:hinnoittele-toimenpide ::h/tyot])
        hinnoiteltavat-hinnat (get-in app* [:hinnoittele-toimenpide ::h/hinnat])
        valittu-aikavali (get-in app* [:valinnat :aikavali])
        suunnitellut-tyot (tpk/aikavalin-hinnalliset-suunnitellut-tyot (:suunnitellut-tyot app*)
                                                                       valittu-aikavali)
        hinnoittelu-validi? (and
                              ;; Työrivit täytetty oikein
                              (every? ::tyo/toimenpidekoodi-id hinnoiteltavat-tyot)
                              ;; Hintojen nimien pitää olla uniikkeja
                              ;; TODO Pitää testata toimiiko tämä näin
                              (= hinnoiteltavat-hinnat (distinct hinnoiteltavat-hinnat)))]
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
            #(e! (tiedot/->TallennaToimenpiteenHinnoittelu (:hinnoittele-toimenpide app*)))
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