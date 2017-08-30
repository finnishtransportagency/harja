(ns harja.views.vesivaylat.urakka.toimenpiteet.toimenpiteen-hinnoittelu
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.toimenpiteen-hinnoittelu :as ui-tiedot]
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
   (r/wrap (::hinta/maara hinta)
           (fn [uusi]
             (e! (tiedot/->HinnoitteleToimenpideKentta {::hinta/id (::hinta/id hinta)
                                                        ::hinta/maara uusi}))))])

(defn- yleiskustannuslisakentta
  [e! hinta]
  [tee-kentta {:tyyppi :checkbox}
   (r/wrap (if-let [yleiskustannuslisa (::hinta/yleiskustannuslisa hinta)]
             (pos? yleiskustannuslisa)
             false)
           (fn [uusi]
             (e! (tiedot/->HinnoitteleToimenpideKentta
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
                   (e! (tiedot/->OtsikoiToimenpideKentta {::hinta/id (::hinta/id hinta)
                                                          ::hinta/otsikko uusi}))))])]
     [:td] ;; TODO Hintatyyppi
     [:td.tasaa-oikealle [hintakentta e! hinta]]
     [:td.keskita [yleiskustannuslisakentta e! hinta]]
     [:td
      (when-not (tiedot/vakiohintakentta? otsikko)
        [ikonit/klikattava-roskis #(e! (tiedot/->PoistaKulurivi {::hinta/id (::hinta/id hinta)}))])]]))

(defn- toimenpiteen-hinnoittelutaulukko-yhteenvetorivi [otsikko arvo]
  [:tr.hinnoittelun-yhteenveto-rivi
   [:td otsikko]
   [:td]
   [:td.tasaa-oikealle arvo]
   [:td]
   [:td]])

(defn- valiotsikko [otsikko]
  [:h3.valiotsikko otsikko])

(defn- rivinlisays [otsikko toiminto]
  [:div.rivinlisays
   [napit/uusi otsikko toiminto]])

(defn- sopimushintaiset-tyot-header []
  [:thead
   [:tr
    [:th {:style {:width "40%"}} "Työ"]
    [:th.tasaa-oikealle {:style {:width "15%"}} "Yks. hinta"]
    [:th.tasaa-oikealle {:style {:width "15%"}} "Määrä"]
    [:th {:style {:width "10%"}} "Yks."]
    [:th.tasaa-oikealle {:style {:width "15%"}} "Yhteensä"]
    [:th {:style {:width "5%"}} ""]]])

(defn- muu-hinnoittelu-header
  ([] (muu-hinnoittelu-header true))
  ([otsikot?]
   [:thead
    [:tr
     [:th {:style {:width "45%"}} (when otsikot? "Työ")]
     [:th {:style {:width "20%"}} (when otsikot? "Hintatyyppi")]
     [:th {:style {:width "20%"}} (when otsikot? "Hinta yhteensä")]
     [:th {:style {:width "10%"}} (when otsikot? "YK-lisä")]
     [:th {:style {:width "5%"}} ""]]]))

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
    [:div
     [valiotsikko ""]
     [:table
      [muu-hinnoittelu-header false]
      [:tbody
       [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
        "Hinnat yhteensä" (fmt/euro-opt (+ perushinnat-yhteensa tyot-yhteensa))]
       [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
        "Yleiskustannuslisät (12%) yhteensä" (fmt/euro-opt yleiskustannuslisien-osuus)]
       [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
        "Kaikki yhteensä" (fmt/euro-opt (+ hinnat-yleiskustannuslisineen-yhteensa tyot-yhteensa))]]]]))

(defn- sopimushintaiset-tyot [e! app*]
  [:div.hinnoitteluosio.sopimushintaiset-tyot-osio
   [valiotsikko "Sopimushintaiset tyot ja materiaalit"]
   [:table
    ;; TODO Tehdäänkö combobox? --> Ehkä, mutta ei ainakaan ennen Kaukon muutosten valmistumista.
    ;; TODO Kaukon muutosten pohjalta tehdään näin:
    ;; - Päivän hinta ja omakustannushinta pois täältä.
    ;;  Jos niitä on aiemmin kirjattu, niin näytetään muut -otsikon alla (pitäisi toimia autom. näin)
    ;; - namespacetetaan mapit työksi, pidetään id:t tallessa
    ;; - Kannassa tehdään normaali insert / update, ei siis enää poisteta aiempiä töitä kuten tähän asti, koska
    ;; tyyppi on tästä lähtien aina työtä
    ;; - Oma sarake: yksikkö, yksikköhinta, hinta yhteensä. Edelleen vain määrää voi muokata.
    ;; - Oma osio Muut työt, johon voi listata vapaasti hintarivejä omalla tekstillä
    [sopimushintaiset-tyot-header]
    [:tbody
     (map-indexed
       (fn [index tyorivi]
         ^{:key index}
         (let [toimenpidekoodi (tpk/toimenpidekoodi-tehtavalla (:suunnitellut-tyot app*)
                                                               (:toimenpidekoodi-id tyorivi))
               hinta-nimi (:hinta-nimi tyorivi)
               yksikko (:yksikko toimenpidekoodi)
               yksikkohinta (:yksikkohinta toimenpidekoodi)
               tyon-hinta-voidaan-laskea? (boolean (and yksikkohinta yksikko))]
           [:tr
            [:td
             (let [hintavalinnat (map #(ui-tiedot/->HintaValinta %) tyo/tyo-hinnat)
                   tyovalinnat (map ui-tiedot/suunniteltu-tyo->Record (:suunnitellut-tyot app*))
                   kaikki-valinnat (concat hintavalinnat tyovalinnat)]
               [yleiset/livi-pudotusvalikko
                {:valitse-fn #(ui-tiedot/valitse % e! index)
                 :format-fn #(if %
                               (ui-tiedot/formatoi %)
                               "Valitse työ")
                 :nayta-ryhmat [:hinta :tyo]
                 :ryhmittely #(ui-tiedot/ryhmittely %)
                 :ryhman-otsikko #(case %
                                    :hinta "Hinta"
                                    :tyo "Sopimushinnat")
                 :class "livi-alasveto-250 inline-block"
                 :valinta (first (filter #(cond (instance? ui-tiedot/TyoValinta %)
                                                (= (:toimenpidekoodi-id tyorivi) (:toimenpidekoodi-id %))

                                                (instance? ui-tiedot/HintaValinta %)
                                                (= (:hinta-nimi tyorivi) (:nimi %)))
                                         kaikki-valinnat))
                 :disabled false}
                kaikki-valinnat])]
            [:td.tasaa-oikealle (fmt/euro-opt yksikkohinta)]
            [:td.tasaa-oikealle
             [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 5}
              (r/wrap (:maara tyorivi)
                      (fn [uusi]
                        (e! (tiedot/->AsetaTyorivilleTiedot
                              {:index index
                               :maara uusi}))))]]
            [:td yksikko]
            [:td.tasaa-oikealle
             (when tyon-hinta-voidaan-laskea? (fmt/euro (* (:maara tyorivi) yksikkohinta)))]
            [:td.keskita
             [ikonit/klikattava-roskis #(e! (tiedot/->PoistaHinnoiteltavaTyorivi {:index index}))]]]))
       (get-in app* [:hinnoittele-toimenpide ::h/tyot]))]]
   [rivinlisays "Lisää työrivi" #(e! (tiedot/->LisaaHinnoiteltavaTyorivi))]])

(defn- komponentit [e! app*]
  (let [;; TODO Tällä hetkellä hardkoodattu, lista. Pitää tehdä näin:
        ;; - Listataan tässä pudotusvalikossa kaikki toimenpiteeseen kuuluvat komponentit jotka on vaihdettu / lisätty
        ;; - Lisätään nappi jolla voi lisätä oman rivin ja valita siihen komponentin ja antaa sille hinnan
        ;; - Hintatyyppi: Omakustannus / Päivän hinta
        komponentit-testidata [{::tkomp/komponenttityyppi {::tktyyppi/nimi "Lateraalimerkki, vaihdettu"}
                                ::tkomp/sarjanumero "123"
                                ::tkomp/turvalaitenro "8881"}
                               {::tkomp/komponenttityyppi {::tktyyppi/nimi "Lateraalimerkki, lisätty"}
                                ::tkomp/sarjanumero "124"
                                ::tkomp/turvalaitenro "8882"}]]
    [:div.hinnoitteluosio.komponentit-osio
     [valiotsikko "Komponentit"]
     [:table
      [muu-hinnoittelu-header]
      [:tbody
       (map-indexed
         (fn [index komponentti]
           ^{:key index}
           [:tr
            [:td
             (str (get-in komponentti [::tkomp/komponenttityyppi ::tktyyppi/nimi])
                  " (" (::tkomp/sarjanumero komponentti) ")")]
            [:td] ;; TODO Hintatyyppi
            [:td.tasaa-oikealle
             [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 5}
              (r/wrap 0
                      (fn [uusi]
                        (log "TODO")))]]
            [:td] ; TODO YK-lisä
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
         [vapaa-hinnoittelurivi e! hinta])
       (filter
         #(and (= (::hinta/ryhma %) :muu) (not (::m/poistettu? %)))
         (get-in app* [:hinnoittele-toimenpide ::h/hinnat])))]]
   [rivinlisays "Lisää kulurivi" #(e! (tiedot/->LisaaKulurivi))]])

(defn- toimenpiteen-hinnoittelutaulukko [e! app*]
  ;; TODO Korkeus alkaa olla jo aikamoinen haaste
  ;; Voisi piirtää rivin alapuolelle, mutta vasta kun hinnoittelu muuten valmista
  [:div.vv-toimenpiteen-hinnoittelutiedot
   [sopimushintaiset-tyot e! app*]
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
                              (every? #(or (:toimenpidekoodi-id %)
                                           (:hinta-nimi %))
                                      hinnoiteltavat-tyot)
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