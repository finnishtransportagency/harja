(ns harja.views.vesivaylat.urakka.toimenpiteet.toimenpiteen-hinnoittelu
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [harja.ui.komponentti :as komp]
            [clojure.string :as string]
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
            [harja.domain.vesivaylat.komponentin-tilamuutos :as komp-tila]
            [harja.domain.toimenpidekoodi :as tpk]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.debug :as debug]
            [harja.views.kartta :as kartta]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]))

(defn- kentta*
  [e! asia arvo-kw kentan-optiot asetus-fn]
  [tee-kentta kentan-optiot
   (r/wrap (arvo-kw asia)
           asetus-fn)])

(defn- kentta-hinnalle
  ([e! hinta arvo-kw kentan-optiot]
   [kentta-hinnalle e! hinta arvo-kw kentan-optiot
    (fn [uusi]
      (e! (tiedot/->AsetaHintakentalleTiedot {::hinta/id (::hinta/id hinta)
                                              arvo-kw uusi})))])
  ([e! hinta arvo-kw kentan-optiot asetus-fn]
   [kentta* e! hinta arvo-kw kentan-optiot asetus-fn]))

(defn- kentta-tyolle
  ([e! tyo arvo-kw kentan-optiot]
   [kentta-tyolle e! tyo arvo-kw kentan-optiot
    (fn [uusi]
      (e! (tiedot/->AsetaTyorivilleTiedot
            {::tyo/id (::tyo/id tyo)
             arvo-kw uusi})))])
  ([e! tyo arvo-kw kentan-optiot asetus-fn]
   [kentta* e! tyo arvo-kw kentan-optiot asetus-fn]))

(defn- hintakentta
  [e! hinta]
  [kentta-hinnalle e! hinta ::hinta/summa {:tyyppi :positiivinen-numero :kokonaisosan-maara 7}])

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

(defn vapaa-hinnoittelurivi [e! hinta ainoa-vakiokentta?]
  [:tr
   [:td
    (if ainoa-vakiokentta?
      (::hinta/otsikko hinta)
      [kentta-hinnalle e! hinta ::hinta/otsikko {:tyyppi :string}])]
   [:td]
   [:td]
   [:td]
   [:td.tasaa-oikealle [hintakentta e! hinta]]
   [:td.keskita [yleiskustannuslisakentta e! hinta]]
   [:td
    (when-not ainoa-vakiokentta?
      [ikonit/klikattava-roskis #(e! (tiedot/->PoistaHinnoiteltavaHintarivi {::hinta/id (::hinta/id hinta)}))])]])

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

(defn- rivinlisays
  ([otsikko toiminto]
    [rivinlisays otsikko toiminto {}])
  ([otsikko toiminto optiot]
   [:div.rivinlisays
    [napit/uusi otsikko toiminto optiot]]))

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

(defn- komponentit-header
  ([] (komponentit-header {:yk-lisa? true}))
  ([{:keys [yk-lisa?] :as optiot}]
   [:thead
    [:tr
     [:th {:style {:width "40%"}} "Työt ja materiaalikulut"]
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

(defn- suunniteltu-tyo-voimassa-paivamaaralle? [tp-pvm tyo]
  (assert some? tp-pvm)
  (pvm/valissa? tp-pvm (:alkupvm tyo) (:loppupvm tyo)))

(defn- suunnitellut-tyot-paivamaaralle [app* pvm]
  (filter (partial suunniteltu-tyo-voimassa-paivamaaralle? pvm)
          (:suunnitellut-tyot app*)))


(defn- suunniteltu-tyo-toimenpidekoodille [tpk suunnitellut-tyot]
  (first (filter (fn [suunniteltu-tyo]
                   (and (= (:tehtavan_id tpk)
                           (:tehtava suunniteltu-tyo))))
                 suunnitellut-tyot)))

(defn- sopimushintaiset-tyot [e! app*]
  (let [tyot (get-in app* [:hinnoittele-toimenpide ::h/tyot])
        tp-pvm (get-in app* [:hinnoittele-toimenpide ::to/pvm])
        ei-poistetut-tyot (remove ::m/poistettu? tyot)]
    [:div.hinnoitteluosio.sopimushintaiset-tyot-osio
     [valiotsikko "Sopimushintaiset tyot ja materiaalit"]
     [:table
      [sopimushintaiset-tyot-header {:yk-lisa? false}]
      [:tbody
       (map-indexed
         (fn [index tyorivi]
           (let [toimenpidekoodi (tpk/toimenpidekoodi-tehtavalla (:suunnitellut-tyot app*)
                                                                 (::tyo/toimenpidekoodi-id tyorivi))
                 yksikko (:yksikko toimenpidekoodi)
                 tyovalinnat-toimenpiteen-ajalle (sort-by :tehtavan_nimi (suunnitellut-tyot-paivamaaralle app* tp-pvm))
                 suunniteltu-tyo (suunniteltu-tyo-toimenpidekoodille toimenpidekoodi tyovalinnat-toimenpiteen-ajalle)
                 yksikkohinta (:yksikkohinta suunniteltu-tyo)
                 tyon-hinta-voidaan-laskea? (boolean (and yksikkohinta yksikko))]
             ^{:key index}
             [:tr
              [:td
               [yleiset/livi-pudotusvalikko
                {:valitse-fn #(do
                                (e! (tiedot/->AsetaTyorivilleTiedot
                                      {::tyo/id (::tyo/id tyorivi)
                                       ::tyo/toimenpidekoodi-id (:tehtava %)})))
                 :format-fn #(if %
                               (:tehtavan_nimi %)
                               "Valitse työ")
                 :class "livi-alasveto-250 inline-block"
                 :valinta suunniteltu-tyo
                 :disabled false}
                tyovalinnat-toimenpiteen-ajalle]]
              [:td.tasaa-oikealle (fmt/euro-opt yksikkohinta)]
              [:td.tasaa-oikealle
               [kentta-tyolle e! tyorivi ::tyo/maara {:tyyppi :positiivinen-numero :kokonaisosan-maara 5}]]
              [:td yksikko]
              [:td.tasaa-oikealle
               (when tyon-hinta-voidaan-laskea? (fmt/euro (* (::tyo/maara tyorivi) yksikkohinta)))]
              [:td]
              [:td.keskita
               [ikonit/klikattava-roskis #(e! (tiedot/->PoistaHinnoiteltavaTyorivi {::tyo/id (::tyo/id tyorivi)}))]]]))
         ei-poistetut-tyot)]]
     [rivinlisays "Lisää työrivi" #(e! (tiedot/->LisaaHinnoiteltavaTyorivi))]]))


(defn muu-tyo-hinnoittelurivi [e! hinta]
  [:tr
   [:td [kentta-hinnalle e! hinta ::hinta/otsikko {:tyyppi :string}]]
   [:td.tasaa-oikealle [kentta-hinnalle e! hinta ::hinta/yksikkohinta
                        {:tyyppi :positiivinen-numero :kokonaisosan-maara 9}]]
   [:td.tasaa-oikealle [kentta-hinnalle e! hinta ::hinta/maara
                        {:tyyppi :positiivinen-numero :kokonaisosan-maara 7}]]
   [:td
    [kentta-hinnalle e! hinta ::hinta/yksikko {:tyyppi :string :pituus-min 1}]]
   [:td (fmt/euro (hinta/hinnan-kokonaishinta-yleiskustannuslisineen hinta))]
   [:td.keskita [yleiskustannuslisakentta e! hinta]]
   [:td.keskita
    [ikonit/klikattava-roskis #(e! (tiedot/->PoistaHinnoiteltavaHintarivi hinta))]]])

(defn- muut-tyot [e! app*]
  (let [muut-tyot (tiedot/muut-tyot app*)]
    [:div.hinnoitteluosio.sopimushintaiset-tyot-osio
    [valiotsikko "Muut työt (ei indeksilaskentaa)"]
    [:table
     [sopimushintaiset-tyot-header]
     [:tbody
      (for* [muu-tyo muut-tyot]
            [muu-tyo-hinnoittelurivi e! muu-tyo])]]
    [rivinlisays "Lisää työrivi" #(e! (tiedot/->LisaaMuuTyorivi))]]))

(defn- komponentit [e! app*]
  (let [komponenttien-hinnat (tiedot/komponenttien-hinnat app*)]
    [:div.hinnoitteluosio.komponentit-osio
     [valiotsikko "Komponentit"]
     [:table
      [komponentit-header]
      [:tbody
       (for* [komponentin-hinta komponenttien-hinnat]
         (let [otsikko (fn [k]
                         (str (get-in k [::tkomp/komponenttityyppi ::tktyyppi/nimi])
                              (when-let [tila (komp-tila/komponentin-tilakoodi->str (::komp-tila/tilakoodi k))] (str ", " tila))
                              " (" (::tkomp/sarjanumero k) ")"))]
           [:tr
            [:td
             [yleiset/livi-pudotusvalikko
              {:valitse-fn #(do
                              (e! (tiedot/->AsetaHintakentalleTiedot
                                    {::hinta/id (::hinta/id komponentin-hinta)
                                     ::hinta/otsikko (otsikko %)
                                     ::hinta/komponentti-tilamuutos (::komp-tila/tilakoodi %)
                                     ::hinta/komponentti-id (::tkomp/id %)})))
               :format-fn #(if %
                             (otsikko %)
                             "Valitse komponentti")
               :class "livi-alasveto-250 inline-block"
               :valinta (some
                          #(when (and (= (::tkomp/id %) (::hinta/komponentti-id komponentin-hinta))
                                      (= (::komp-tila/tilakoodi %) (::hinta/komponentti-tilamuutos komponentin-hinta)))
                             %)
                          (::to/komponentit (tiedot/hinnoiteltava-toimenpide app*)))}
              (::to/komponentit (tiedot/hinnoiteltava-toimenpide app*))]]
            [:td.tasaa-oikealle
             [kentta-hinnalle e! komponentin-hinta ::hinta/yksikkohinta {:tyyppi :positiivinen-numero :kokonaisosan-maara 9}]]
            [:td.tasaa-oikealle
             [kentta-hinnalle e! komponentin-hinta ::hinta/maara {:tyyppi :positiivinen-numero :kokonaisosan-maara 7}]]
            [:td
             [kentta-hinnalle e! komponentin-hinta ::hinta/yksikko {:tyyppi :string :pituus-min 1}]]
            [:td (fmt/euro (hinta/hinnan-kokonaishinta-yleiskustannuslisineen komponentin-hinta))]
            [:td.keskita [yleiskustannuslisakentta e! komponentin-hinta]]
            [:td.keskita
             [ikonit/klikattava-roskis #(e! (tiedot/->PoistaHinnoiteltavaHintarivi komponentin-hinta))]]]))]]
     [rivinlisays "Lisää komponenttirivi"
      #(e! (tiedot/->LisaaHinnoiteltavaKomponenttirivi))
      {:disabled (= 0 (count (::to/komponentit (tiedot/hinnoiteltava-toimenpide app*))))}]]))

(defn- muut-hinnat [e! app*]
  (let [hinnat (tiedot/muut-hinnat app*)]
    [:div.hinnoitteluosio.muut-osio
     [valiotsikko "Muut"]
     [:table
      [muu-hinnoittelu-header]
      [:tbody
       (map-indexed
         (fn [index hinta]
           ^{:key index}
           [vapaa-hinnoittelurivi e! hinta (tiedot/ainoa-otsikon-vakiokentta? hinnat (::hinta/otsikko hinta))])
         hinnat)]]
     [rivinlisays "Lisää kulurivi" #(e! (tiedot/->LisaaMuuKulurivi))]]))

(defn- toimenpiteen-hinnoittelutaulukko [e! app*]
  [:div.vv-toimenpiteen-hinnoittelutiedot
   [sopimushintaiset-tyot e! app*]
   [muut-tyot e! app*]
   [komponentit e! app*]
   [muut-hinnat e! app*]
   [hinnoittelun-yhteenveto app*]])

(defn hinnoittele-toimenpide [e! app* toimenpide-rivi listaus-tunniste]
  ;; Tätä kutsutaan hinta-gridistä solun komponenttina, mutta muuttuu tilanteen mukaan hinnoittelu-leijukkeeksi
  ;; tai tavalliseksi solun arovksi
  (let [hinnoittele-toimenpide-id (get-in app* [:hinnoittele-toimenpide ::to/id])
        toimenpiteen-nykyiset-hinnat (get-in toimenpide-rivi [::to/oma-hinnoittelu ::h/hinnat])
        toimenpiteen-nykyiset-tyot (get-in toimenpide-rivi [::to/oma-hinnoittelu ::h/tyot])
        valittu-aikavali (get-in app* [:valinnat :aikavali])
        suunnitellut-tyot (tpk/aikavalin-hinnalliset-suunnitellut-tyot (:suunnitellut-tyot app*)
                                                                       valittu-aikavali)]
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
                         (not (tiedot/hinnoittelun-voi-tallentaa? app*))
                         (:toimenpiteen-hinnoittelun-tallennus-kaynnissa? app*)
                         (not (oikeudet/on-muu-oikeus? "hinnoittele-toimenpide"
                                                       oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                       (:id @nav/valittu-urakka))))}]]]]]
       ;; Piirrä solun sisältö
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
          ;; XXX K: meneekö oikein vs urakkavuodet? v: pitäisi mennä koska käytetään yllä aikavalin-hinnalliset-suunnitellut-tyot
          :arvo (fmt/euro-opt (+ (hinta/kokonaishinta-yleiskustannuslisineen toimenpiteen-nykyiset-hinnat)
                                 (tyo/toiden-kokonaishinta toimenpiteen-nykyiset-tyot
                                                           suunnitellut-tyot)))
          :ikoninappi? true}))]))
