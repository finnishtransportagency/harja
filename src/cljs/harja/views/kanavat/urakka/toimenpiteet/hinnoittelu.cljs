(ns harja.views.kanavat.urakka.toimenpiteet.hinnoittelu
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.loki :refer [log]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.leijuke :refer [leijuke]]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.varmista-kayttajalta :refer [varmista-kayttajalta]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as tiedot]

            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.kanavat.tyo :as tyo]
            [harja.domain.toimenpidekoodi :as tpk]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kanavat.kommentti :as kommentti]
            [harja.domain.roolit :as roolit]
            [harja.domain.vesivaylat.materiaali :as materiaali])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]))

(defn- kentta*
  [e! asia arvo-kw kentan-optiot asetus-fn]
  [tee-kentta kentan-optiot
   (r/wrap (arvo-kw asia)
           asetus-fn)])

(defn- kentta-hinnalle
  ([e! hinta arvo-kw kentan-optiot]
   (assert (map? hinta) (pr-str hinta))
   [kentta-hinnalle e! hinta arvo-kw kentan-optiot
    (fn [uusi]
      ;; (log "kentta-hinnalle: uusi" (pr-str uusi) "hinta" (pr-str hinta) "arvo-kw" (pr-str arvo-kw))
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

(defn- hinnoittelu-header
  [{:keys [otsikko yk-lisa? yhteensa-otsikko? hinnoittelu-otsikot?] :as optiot}]
  [:thead
   [:tr
    [:th {:style {:width "40%"}} (when otsikko otsikko)]
    [:th.tasaa-oikealle {:style {:width "15%"}} (when hinnoittelu-otsikot? "Yks. hinta")]
    [:th.tasaa-oikealle {:style {:width "15%"}} (when hinnoittelu-otsikot? "Määrä")]
    [:th {:style {:width "5%"}} (when hinnoittelu-otsikot? "Yks.")]
    [:th.tasaa-oikealle {:style {:width "10%"}} (when yhteensa-otsikko? "Yhteensä")]
    [:th.tasaa-oikealle {:style {:width "10%"}} (when yk-lisa? "YK-lisä")]
    [:th {:style {:width "5%"}} ""]]])

(declare suunnitellut-tyot-paivamaaralle)

(defn- hinnoittelun-yhteenveto [app*]
  (let [suunnitellut-tyot (suunnitellut-tyot-paivamaaralle app* (get-in app* [:hinnoittele-toimenpide ::toimenpide/pvm]))
        tyorivit (remove ::m/poistettu? (get-in app* [:hinnoittele-toimenpide ::tyo/tyot]))
        hinnat (remove ::m/poistettu? (get-in app* [:hinnoittele-toimenpide ::hinta/hinnat]))
        hinnat-yhteensa (hinta/hintojen-summa-ilman-yklisaa hinnat)
        tyot-yhteensa (tyo/toiden-kokonaishinta tyorivit suunnitellut-tyot)
        yleiskustannuslisien-osuus (hinta/yklisien-osuus hinnat)]
    [:div
     [valiotsikko ""]
     [:table
      [hinnoittelu-header]
      [:tbody
       [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
        "Hinnat yhteensä" (fmt/euro-opt (+ hinnat-yhteensa tyot-yhteensa))]
       [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
        "Yleiskustannuslisät (12%) yhteensä" (fmt/euro-opt yleiskustannuslisien-osuus)]
       [toimenpiteen-hinnoittelutaulukko-yhteenvetorivi
        "Kaikki yhteensä" (fmt/euro-opt (+ hinnat-yhteensa tyot-yhteensa
                                           yleiskustannuslisien-osuus))]]]]))

(defn- suunniteltu-tyo-voimassa-paivamaaralle? [tp-pvm tyo]
  ;; (log "valissa " (pr-str  tp-pvm) (:alkupvm tyo) (:loppupvm tyo) "->" (pr-str (pvm/valissa? tp-pvm (:alkupvm tyo) (:loppupvm tyo))))
  (pvm/valissa? tp-pvm (:alkupvm tyo) (:loppupvm tyo)))

(defn- suunnitellut-tyot-paivamaaralle [app* pvm]
  (filter (partial suunniteltu-tyo-voimassa-paivamaaralle? pvm)
          (:suunnitellut-tyot app*)))

(defn- sopimushintaiset-tyot [e! app*]
  (let [tyot (get-in app* [:hinnoittele-toimenpide ::tyo/tyot])
        tp-pvm (get-in app* [:hinnoittele-toimenpide ::toimenpide/pvm])
        ei-poistetut-tyot (remove ::m/poistettu? tyot)
        ]
    ;; (log "sopimushintaiset tyot:" (pr-str ei-poistetut-tyot))

    [:div.hinnoitteluosio.sopimushintaiset-tyot-osio
     [valiotsikko "Sopimushintaiset tyot ja materiaalit"]
     [:table
      [hinnoittelu-header {:otsikko "Työ" :yk-lisa? false :yhteensa-otsikko? true :hinnoittelu-otsikot? true}]
      [:tbody
       (map-indexed
         (fn [index tyorivi]
           (let [tyovalinnat-toimenpiteen-ajalle (sort-by :tehtavan_nimi (suunnitellut-tyot-paivamaaralle app* tp-pvm))
                 toimenpidekoodi (tpk/toimenpidekoodi-tehtavalla tyovalinnat-toimenpiteen-ajalle
                                                                 (::tyo/toimenpidekoodi-id tyorivi))
                 yksikko (:yksikko toimenpidekoodi)
                 yksikkohinta (:yksikkohinta toimenpidekoodi)
                 tyon-hinta-voidaan-laskea? (boolean (and yksikkohinta yksikko))]
             ^{:key index}
             [:tr
              [:td
               [yleiset/livi-pudotusvalikko
                {:valitse-fn #(do
                                (e! (tiedot/->AsetaTyorivilleTiedot {::tyo/id (::tyo/id tyorivi)
                                                                     ::tyo/toimenpidekoodi-id (:tehtava %)})))
                 :format-fn #(if %
                               (:tehtavan_nimi %)
                               "Valitse työ")
                 :class "livi-alasveto-250 inline-block"
                 :valinta (first (filter (fn [suunniteltu-tyo]
                                           (assert (pvm/valissa? tp-pvm (:alkupvm suunniteltu-tyo) (:loppupvm suunniteltu-tyo)))
                                           (= (::tyo/toimenpidekoodi-id tyorivi)
                                              (:tehtava suunniteltu-tyo)))
                                         tyovalinnat-toimenpiteen-ajalle))
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


(defn omakustannushintainen-tyo-hinnoittelurivi [e! hinta]
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

(defn- omakustannushintaiset-tyot [e! app*]
  (let [omakustannushintaiset-tyot (tiedot/omakustannushintaiset-tyot app*)]
    [:div.hinnoitteluosio.sopimushintaiset-tyot-osio
     [valiotsikko "Omakustannushintaiset työt (ei indeksilaskentaa)"]
     [:table
      [hinnoittelu-header {:otsikko "Työ" :yk-lisa? true :yhteensa-otsikko? true :hinnoittelu-otsikot? true}]
      [:tbody
       (for* [okt-tyo omakustannushintaiset-tyot]
             [omakustannushintainen-tyo-hinnoittelurivi e! okt-tyo])]]

     [rivinlisays "Lisää työrivi" #(e! (tiedot/->LisaaOmakustannushintainenTyorivi))]]))


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
      [hinnoittelu-header {:otsikko "Työ" :yk-lisa? true :yhteensa-otsikko? true :hinnoittelu-otsikot? true}]
      [:tbody
       (for* [muu-tyo muut-tyot]
         [muu-tyo-hinnoittelurivi e! muu-tyo])]]

     [rivinlisays "Lisää työrivi" #(e! (tiedot/->LisaaMuuTyorivi))]]))

(defn- materiaali-hinnoittelurivi
  [e! materiaali-hinta materiaalit]
  (let [toimenpiteelle? (tiedot/kaytto-merkattu-toimenpiteelle? materiaali-hinta materiaalit)]
    [:tr
     [:td (if toimenpiteelle?
            (::hinta/otsikko materiaali-hinta)
            [kentta-hinnalle e! materiaali-hinta ::hinta/otsikko {:tyyppi :string}])]
     [:td.tasaa-oikealle [kentta-hinnalle e! materiaali-hinta ::hinta/yksikkohinta
                          {:tyyppi :positiivinen-numero :kokonaisosan-maara 9}]]
     [:td.tasaa-oikealle
      [kentta-hinnalle e! materiaali-hinta ::hinta/maara {:tyyppi :positiivinen-numero}]]
     [:td (if toimenpiteelle?
            (::hinta/yksikko materiaali-hinta)
            [kentta-hinnalle e! materiaali-hinta ::hinta/yksikko
             {:tyyppi :string}])]
     [:td (fmt/euro (hinta/hinnan-kokonaishinta-yleiskustannuslisineen materiaali-hinta))]
     [:td.keskita [yleiskustannuslisakentta e! materiaali-hinta]]
     [:td.keskita
      (if toimenpiteelle?
        ""
        [ikonit/klikattava-roskis #(e! (tiedot/->PoistaHinnoiteltavaHintarivi materiaali-hinta))])]]))

(defn- materiaalit [e! app*]
  (let [materiaali-hinnat (tiedot/materiaalit app*)]
    [:div.hinnoitteluosio
     [valiotsikko "Varaosat ja materiaalit"]
     [:table
      [hinnoittelu-header {:otsikko "Materiaali" :yk-lisa? true :yhteensa-otsikko? true :hinnoittelu-otsikot? true}]
      [:tbody
       (for* [materiaali-hinta materiaali-hinnat]
         [materiaali-hinnoittelurivi e! materiaali-hinta (:urakan-materiaalit app*)])]]
     [rivinlisays "Lisää materiaalirivi" #(e! (tiedot/->LisaaMateriaaliKulurivi))]]))

(defn- muut-hinnat [e! app*]
  (let [hinnat (tiedot/muut-hinnat app*)]
    (assert (every? map? hinnat) (pr-str hinnat))
    (assert (some? hinnat))

    [:div.hinnoitteluosio.muut-osio
     [valiotsikko "Muut"]
     [:table
      [hinnoittelu-header]
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
   [omakustannushintaiset-tyot e! app*]
   [muut-tyot e! app*]
   [materiaalit e! app*]
   [muut-hinnat e! app*]
   [hinnoittelun-yhteenveto app*]])


(defn- nykyisten-arvo [app* tp aikavali]
  (let [nykyiset-hinnat (::toimenpide/hinnat tp)
        nykyiset-tyot (::toimenpide/tyot tp)
        suunnitellut-tyot (tpk/aikavalin-hinnalliset-suunnitellut-tyot (:suunnitellut-tyot app*)
                                                                       aikavali)]
    (+ (hinta/kokonaishinta-yleiskustannuslisineen nykyiset-hinnat)
       (tyo/toiden-kokonaishinta nykyiset-tyot
                                 suunnitellut-tyot))))


(defn hinnoittelunappi [e! app* hinta toimenpide-rivi tila]
  (let [{:keys [teksti
                luokka
                ikoni]} (case tila
                          (nil :poistettu)
                          {:teksti "Hinnoittele"
                           :luokka "nappi-ensisijainen"
                           :ikoni (ikonit/livicon-pen)}

                          (:luotu :muokattu)
                          {:teksti "Muokkaa"
                           :luokka "nappi-toissijainen"
                           :ikoni (ikonit/livicon-pen)}

                          :hyvaksytty
                          {:teksti "Tarkastele"
                           :luokka "nappi-myonteinen"
                           :ikoni (ikonit/livicon-eye)}

                          :hylatty
                          {:teksti "Käsittele"
                           :luokka "nappi-kielteinen"
                           :ikoni (ikonit/livicon-pen)})]
    [:div.arvo-ja-nappi
     (when-not (#{nil :poistettu} tila)
       [:span.arvo-ja-nappi-arvo
        hinta])
     [napit/nappi
      teksti
      #(e! (tiedot/->AloitaToimenpiteenHinnoittelu (::toimenpide/id toimenpide-rivi)))
      {:ikoninappi? false
       :ikoni ikoni
       :luokka (str "btn-xs arvo-ja-nappi-nappi " luokka)
       :disabled (or (:id (:infolaatikko-nakyvissa app*))
                     (not (oikeudet/on-muu-oikeus? "hinnoittele-toimenpide"
                                                   oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                   (:id @nav/valittu-urakka))))}]]))

(defn hyvaksy-tai-hylkaa [e! app* hinta toimenpide-rivi]
  [:div.arvo-ja-nappi
   [:span.arvo-ja-nappi-arvo
    hinta]
   [napit/yleinen-toissijainen
    "Tarkastele"
    #(e! (tiedot/->AloitaToimenpiteenHinnoittelu (::toimenpide/id toimenpide-rivi)))
    {:ikoninappi? false
     :ikoni (ikonit/livicon-eye)
     :luokka (str "btn-xs arvo-ja-nappi-nappi")
     :disabled (or (:id (:infolaatikko-nakyvissa app*))
                   (not (oikeudet/on-muu-oikeus? "hinnoittele-toimenpide"
                                                 oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                 (:id @nav/valittu-urakka))))}]
   [napit/hyvaksy
    "Hyväksy"
    #(e! (tiedot/->KommentoiToimenpiteenHinnoittelua :hyvaksytty "" (::toimenpide/id toimenpide-rivi)))
    {:luokka (str "btn-xs arvo-ja-nappi-nappi")}]
   [napit/peruuta
    "Hylkää"
    #(e! (tiedot/->KommentoiToimenpiteenHinnoittelua :hylatty "" (::toimenpide/id toimenpide-rivi)))
    {:luokka (str "btn-xs arvo-ja-nappi-nappi")}]])

(defn tarkastele [e! app* hinta toimenpide-rivi]
  (grid/arvo-ja-nappi
    {:sisalto :arvo-ja-nappi
     :arvo hinta
     :arvo-ja-nappi-napin-teksti "Tarkastele"
     :arvo-ja-nappi-toiminto-fn #(e! (tiedot/->AloitaToimenpiteenHinnoittelu (::toimenpide/id toimenpide-rivi)))
     :nappi-optiot {:ikoninappi? false
                    :ikoni (ikonit/livicon-eye)
                    :luokka (str "btn-xs arvo-ja-nappi-nappi")
                    :disabled (or (:id (:infolaatikko-nakyvissa app*))
                                  (not (oikeudet/on-muu-oikeus? "hinnoittele-toimenpide"
                                                                oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                                (:id @nav/valittu-urakka))))}}))

(defn urakoitsijan-footer-napit [e! app* tila]
  [:footer.vv-toimenpiteen-hinnoittelu-footer
   [napit/peruuta
    "Peruuta"
    #(e! (tiedot/->PeruToimenpiteenHinnoittelu))
    {:luokka "btn-xs"}]
   (if (= :hyvaksytty tila)
     [napit/yleinen-toissijainen
      "Ylikirjoita"
      #(varmista-kayttajalta
         {:otsikko "Palauta hinnoittelu käsittelyyn"
          :sisalto
          [:div "Toimenpide on jo hinnoiteltu, ja tilaaja on sen hyväksynyt. Jos tallennat hinnoittelun uudelleen,
          tilaajan pitää myös hyväksyä hinnoittelu uudelleen. Ylikirjoitetaanko hinnoittelu?"]
          :toiminto-fn (fn []
                         (e! (tiedot/->TallennaToimenpiteenHinnoittelu (:hinnoittele-toimenpide app*))))
          :hyvaksy "Kyllä, ylikirjoita hinnoittelu"})
      {:luokka "btn-xs"
       :disabled (or
                   (not (tiedot/hinnoittelun-voi-tallentaa? app*))
                   (:toimenpiteen-hinnoittelun-tallennus-kaynnissa? app*)
                   (not (oikeudet/on-muu-oikeus? "hinnoittele-toimenpide"
                                                 oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                 (:id @nav/valittu-urakka))))
       :ikoni (ikonit/livicon-pen)}]

     [napit/tallenna
      "Valmis"
      #(e! (tiedot/->TallennaToimenpiteenHinnoittelu (:hinnoittele-toimenpide app*)))
      {:luokka "btn-xs"
       :disabled (or
                   (not (tiedot/hinnoittelun-voi-tallentaa? app*))
                   (:toimenpiteen-hinnoittelun-tallennus-kaynnissa? app*)
                   (not (oikeudet/on-muu-oikeus? "hinnoittele-toimenpide"
                                                 oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                 (:id @nav/valittu-urakka))))}])])

(defn tilaajan-footer-napit [e! app* tila]
  [:footer.vv-toimenpiteen-hinnoittelu-footer
   [napit/peruuta
    "Hylkää"
    #(e! (tiedot/->KommentoiToimenpiteenHinnoittelua :hylatty "" (get-in app* [:hinnoittele-toimenpide ::toimenpide/id])))
    {:disabled (= :hylatty tila)
     :luokka "btn-xs"}]
   [napit/hyvaksy
    "Hyväksy"
    #(e! (tiedot/->KommentoiToimenpiteenHinnoittelua :hyvaksytty "" (get-in app* [:hinnoittele-toimenpide ::toimenpide/id])))
    {:disabled (= :hyvaksytty tila)
     :luokka "btn-xs"}]])

(defn hinnoittele-toimenpide [e! app* toimenpide-rivi]
  (let [hinnoittele-toimenpide-id (get-in app* [:hinnoittele-toimenpide ::toimenpide/id])
        valittu-aikavali (get-in app* [:valinnat :aikavali])
        tila (kommentti/hinnoittelun-tila (::toimenpide/kommentit toimenpide-rivi))]

    [:div
     (if (and hinnoittele-toimenpide-id
              (= hinnoittele-toimenpide-id (::toimenpide/id toimenpide-rivi)))
       ;; Piirrä leijuke
       [:div
        [leijuke {:otsikko "Hinnoittele toimenpide"
                  :sulje! #(e! (tiedot/->PeruToimenpiteenHinnoittelu))}
         [:div.vv-toimenpiteen-hinnoittelutiedot
          {:on-click #(.stopPropagation %)}
          (if (or (nil? (:suunnitellut-tyot app*)) (true? (:suunniteltujen-toiden-haku-kaynnissa? app*)))
            [ajax-loader "Ladataan..."]
            [toimenpiteen-hinnoittelutaulukko e! app*])
          (cond
            ;;jvh näkee dialogissa tallenna-napin, rivillä hyväksy/hylkää
            (roolit/jvh? @istunto/kayttaja)
            [urakoitsijan-footer-napit e! app* tila]

            (roolit/tilaajan-kayttaja? @istunto/kayttaja)
            [tilaajan-footer-napit e! app* tila]

            :default
            [urakoitsijan-footer-napit e! app* tila])]]]

       ;; Solun sisältö

       [:div.arvo-ja-nappi-container
        (let [hinta (fmt/euro-opt (nykyisten-arvo app* toimenpide-rivi valittu-aikavali))]
          (cond
            (roolit/jvh? @istunto/kayttaja)
            (case tila
              (nil :poistettu)
              [hinnoittelunappi e! app* hinta toimenpide-rivi tila]

              (:luotu :muokattu)
              [hyvaksy-tai-hylkaa e! app* hinta toimenpide-rivi]

              (:hyvaksytty :hylatty)
              [tarkastele e! app* hinta toimenpide-rivi])

            (roolit/tilaajan-kayttaja? @istunto/kayttaja)
            (case tila
              (nil :poistettu)
              nil

              (:luotu :muokattu)
              [hyvaksy-tai-hylkaa e! app* hinta toimenpide-rivi]

              (:hyvaksytty :hylatty)
              [tarkastele e! app* hinta toimenpide-rivi])

            :default
            [hinnoittelunappi e! app* hinta toimenpide-rivi tila]))])]))
