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
            [harja.domain.vesivaylat.tyo :as tyo]
            [harja.domain.toimenpidekoodi :as tpk]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.debug :as debug]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.kartta :as kartta]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]))

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

(defn- varmistusdialogi-sisalto [toimenpiteet hintaryhmat]
  (let [valitut-toimenpiteet (filter :valittu? toimenpiteet)]
    [:div
     (when (to/toimenpiteilla-hintaryhmia? valitut-toimenpiteet)
       (jaettu/varmistusdialog-ohje
         {:varmistusehto ::to/hintaryhma-id
          :valitut-toimenpiteet valitut-toimenpiteet
          :nayta-max 5
          :toimenpide-lisateksti-fn #(str "Tilaus: " (::h/nimi (h/hinnoittelu-idlla hintaryhmat (::to/hintaryhma-id %))) ".")
          :varmistusteksti-header "Seuraavat toimenpiteet kuuluvat tilaukseen:"
          :varmistusteksti-footer "Nämä toimenpiteet irrotetaan tilauksesta siirron aikana."}))
     (when (to/toimenpiteilla-omia-hinnoitteluja? (filter :valittu? toimenpiteet))
       (jaettu/varmistusdialog-ohje
         {:varmistusehto ::to/oma-hinnoittelu
          :valitut-toimenpiteet valitut-toimenpiteet
          :nayta-max 5
          :toimenpide-lisateksti-fn #(str "Hinta: " (fmt/euro-opt (hinta/kokonaishinta-yleiskustannuslisineen
                                                                    (get-in % [::to/oma-hinnoittelu ::h/hinnat])))
                                          ".")
          :varmistusteksti-header "Seuraavat toimenpiteet sisältävät hinnoittelutietoja:"
          :varmistusteksti-footer "Näiden toimenpiteiden hinnoittelutiedot poistetaan siirron aikana."}))
     [:p "Haluatko jatkaa?"]]))

(defn- valmistele-toimenpiteiden-siirto [e! toimenpiteet hintaryhmat]
  (if (or (to/toimenpiteilla-hintaryhmia? (filter :valittu? toimenpiteet))
          (to/toimenpiteilla-omia-hinnoitteluja? (filter :valittu? toimenpiteet)))
    (varmista-kayttajalta/varmista-kayttajalta
      {:otsikko "Siirto kokonaishintaisiin"
       :sisalto (varmistusdialogi-sisalto toimenpiteet hintaryhmat)
       :hyvaksy "Siirrä kokonaishintaisiin"
       :toiminto-fn #(e! (tiedot/->SiirraValitutKokonaishintaisiin))})
    (e! (tiedot/->SiirraValitutKokonaishintaisiin))))

(defn- urakkatoiminnot [e! app]
  [^{:key "siirto"}
  [jaettu/siirtonappi e! app
   "Siirrä kokonaishintaisiin"
   #(valmistele-toimenpiteiden-siirto e! (:toimenpiteet app) (:hintaryhmat app))
   #(oikeudet/on-muu-oikeus? "siirrä-kokonaishintaisiin"
                             oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                             (:id @nav/valittu-urakka))]
   ^{:key "hinnoittelu"}
   [hinnoittelu e! app]])

;;;;;;;;;;;;
;; Hinnan antamisen leijuke

;; HOX Hinnan antamisessa otsikko on sekä UI:lla näkyvä otsikko että hinnan nimi kannassa.
;; Jos otsikkoa muutetaan, joudutaan myös hintojen nimet kannassa migratoimaan.

(defn- muu-tyo-kentta
  [e! app* otsikko]
  [:span
   [:span
    [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 7}
     (r/wrap (hinta/hinnan-maara-otsikolla
               (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit])
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
                                         (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit])
                                         otsikko)]
             (pos? yleiskustannuslisa)
             false)
           (fn [uusi]
             (e! (tiedot/->HinnoitteleToimenpideKentta
                   {::hinta/otsikko otsikko
                    ::hinta/yleiskustannuslisa (if uusi
                                                 hinta/yleinen-yleiskustannuslisa
                                                 0)}))))])

(defn- toimenpiteen-hinnoittelutaulukko [e! app* tyot suunnitellut-tyot]
  ;; Työ hinnoiteltiin aiemmin yhtenä könttäsummana, nyt huomattavasti tarkemmin
  ;; Jos kannassa on hinta nimellä "Työ", niin se näytetään kuten ennenkin, muutoin
  ;; sitä ei tarjota, vaan työt pitää syöttää tarkemmin.
  (let [tyo-hinnoiteltu-konttana? (pos? (hinta/hinnan-maara-otsikolla
                                          (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit])
                                          "Työ"))]
    [:table.vv-toimenpiteen-hinnoittelutiedot-grid
     [:thead
      [:tr
       [:th {:style {:width "48%"}}]
       [:th {:style {:width "28%"}} "Hinta / määrä"]
       [:th {:style {:width "18%"}} "Yleis\u00ADkustan\u00ADnusli\u00ADsä"]
       [:th {:style {:width "6%"}} ""]]]
     [:tbody
      [:tr.otsikkorivi
       [:td.tyot-osio [:b "Sopimushintaiset työt"]]
       [:td.tyot-osio]
       [:td.tyot-osio]
       [:td.tyot-osio]]
      (for* [tyorivi tyot]
        [:tr.tyon-hinnoittelu-rivi
         [:td.tyot-osio
          [yleiset/livi-pudotusvalikko
           {:valitse-fn #(e! (tiedot/->AsetaTyolleTehtava {::tyo/id (::tyo/id tyorivi)
                                                           ::tyo/toimenpidekoodi-id (:tehtava %)}))
            :format-fn #(if (empty? suunnitellut-tyot)
                          "Ei suunniteltuja töitä"
                          (if % (:tehtavan_nimi %) "Valitse työ"))
            :class "livi-alasveto-250"
            :valinta (first (filter #(= (::tyo/toimenpidekoodi-id tyorivi) (:tehtava %))
                                    suunnitellut-tyot))
            :disabled false}
           suunnitellut-tyot]]
         [:td.tyot-osio
          [:span
           [tee-kentta {:tyyppi :positiivinen-numero :kokonaisosan-maara 5}
            (r/wrap (::tyo/maara tyorivi)
                    (fn [uusi]
                      (e! (tiedot/->HinnoitteleTyo
                            {::tyo/id (::tyo/id tyorivi)
                             ::tyo/maara uusi}))))]
           [:span " "]
           [:span "kpl (" (fmt/euro (* (::tyo/maara tyorivi)
                                       (:yksikkohinta (tpk/toimenpidekoodi-tehtavalla
                                                        suunnitellut-tyot
                                                        (::tyo/toimenpidekoodi-id tyorivi)))))
            ")"]]]
         [:td.tyot-osio]
         [:td.tyot-osio
          [:span.klikattava
           {:on-click #(e! (tiedot/->PoistaHinnoiteltavaTyorivi {::tyo/id (::tyo/id tyorivi)}))}
           (ikonit/livicon-trash)]]])
      [:tr.tyon-hinnoittelu-rivi
       [:td.tyot-osio.lisaa-tyorivi-solu
        [napit/uusi "Lisää työrivi" #(e! (tiedot/->LisaaHinnoiteltavaTyorivi))]]
       [:td.tyot-osio]
       [:td.tyot-osio]
       [:td.tyot-osio]]
      [:tr.otsikkorivi
       [:td [:b "Muut"]]
       [:td]
       [:td]
       [:td]]
      (when tyo-hinnoiteltu-konttana?
        [:tr.muu-hinnoittelu-rivi
         [:td.tyon-otsikko "Työ:"]
         [:td [muu-tyo-kentta e! app* "Työ"]]
         [:td [yleiskustannuslisa-kentta e! app* "Työ"]]
         [:td]])
      [:tr.muu-hinnoittelu-rivi
       [:td.tyon-otsikko "Komponentit:"]
       [:td [muu-tyo-kentta e! app* "Komponentit"]]
       [:td [yleiskustannuslisa-kentta e! app* "Komponentit"]]
       [:td]]
      [:tr.muu-hinnoittelu-rivi
       [:td.tyon-otsikko "Yleiset materiaalit:"]
       [:td [muu-tyo-kentta e! app* "Yleiset materiaalit"]]
       [:td [yleiskustannuslisa-kentta e! app* "Yleiset materiaalit"]]
       [:td]]
      [:tr.muu-hinnoittelu-rivi
       [:td.tyon-otsikko "Matkakulut:"]
       [:td [muu-tyo-kentta e! app* "Matkakulut"]]
       [:td [yleiskustannuslisa-kentta e! app* "Matkakulut"]]
       [:td]]
      [:tr.muu-hinnoittelu-rivi
       [:td.tyon-otsikko "Muut kulut:"]
       [:td [muu-tyo-kentta e! app* "Muut kulut"]]
       [:td [yleiskustannuslisa-kentta e! app* "Muut kulut"]]
       [:td]]
      ;; Yhteenveto
      [:tr.hinnoittelun-yhteenveto-rivi
       [:td.tyon-otsikko "Perushinta:"]
       [:td (fmt/euro-opt (+ (hinta/perushinta
                               (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit]))
                             (tyo/toiden-kokonaishinta tyot suunnitellut-tyot)))]
       [:td]
       [:td]]
      [:tr.hinnoittelun-yhteenveto-rivi
       [:td.tyon-otsikko "Yleiskustannuslisät (12%):"]
       [:td (fmt/euro-opt (hinta/yleiskustannuslisien-osuus
                            (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit])))]
       [:td]
       [:td]]
      [:tr.hinnoittelun-yhteenveto-rivi
       [:td.tyon-otsikko "Yhteensä:"]
       [:td (fmt/euro-opt (+ (hinta/kokonaishinta-yleiskustannuslisineen
                               (get-in app* [:hinnoittele-toimenpide ::h/hintaelementit]))
                             (tyo/toiden-kokonaishinta tyot suunnitellut-tyot)))]
       [:td]
       [:td]]]]))

(defn- hinnoittele-toimenpide [e! app* toimenpide-rivi listaus-tunniste]
  (let [hinnoittele-toimenpide-id (get-in app* [:hinnoittele-toimenpide ::to/id])
        toimenpiteen-nykyiset-hinnat (get-in toimenpide-rivi [::to/oma-hinnoittelu ::h/hinnat])
        tyot (get-in app* [:hinnoittele-toimenpide ::h/tyot])
        valittu-aikavali (get-in app* [:valinnat :aikavali])
        aikavalin-hinnalliset-suunnitellut-tyot (filter
                                                  #(and (:yksikkohinta %)
                                                        (pvm/sama-pvm? (:alkupvm %) (first valittu-aikavali))
                                                        (pvm/sama-pvm? (:loppupvm %) (second valittu-aikavali)))
                                                  (:suunnitellut-tyot app*))
        hinnoittelu-validi? (every? ::tyo/toimenpidekoodi-id tyot)]
    [:div
     (if (and hinnoittele-toimenpide-id
              (= hinnoittele-toimenpide-id (::to/id toimenpide-rivi)))
       ;; Piirrä leijuke
       [:div
        [:span "Hinta: 0€"]
        [leijuke {:otsikko "Hinnoittele toimenpide"
                  :sulje! #(e! (tiedot/->PeruToimenpiteenHinnoittelu))}
         [:div.vv-toimenpiteen-hinnoittelutiedot
          {:on-click #(.stopPropagation %)}
          (if (or (nil? (:suunnitellut-tyot app*)) (true? (:suunniteltujen-toiden-haku-kaynnissa? app*)))
            [ajax-loader "Ladataan..."]
            [toimenpiteen-hinnoittelutaulukko e! app* tyot aikavalin-hinnalliset-suunnitellut-tyot])
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
          :arvo (fmt/euro-opt (hinta/kokonaishinta-yleiskustannuslisineen toimenpiteen-nykyiset-hinnat))
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
           (r/wrap (hinta/hinnan-maara-otsikolla
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

(defn- yksikkohintaiset-toimenpiteet-nakyma [e! app ulkoiset-valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do
                         (e! (tiedot/->Nakymassa? true))
                         (e! (tiedot/->PaivitaValinnat {:urakka-id (get-in ulkoiset-valinnat [:urakka :id])
                                                        :sopimus-id (first (:sopimus ulkoiset-valinnat))
                                                        :aikavali (:aikavali ulkoiset-valinnat)}))
                         (e! (tiedot/->HaeHintaryhmat))
                         (e! (tiedot/->HaeSuunnitellutTyot)))
                      #(do
                         (u/valitse-oletussopimus-jos-valittuna-kaikki!)
                         (e! (tiedot/->Nakymassa? false))
                         (e! (tiedot/->TyhjennaSuunnitellutTyot))))
    (fn [e! {:keys [toimenpiteet toimenpiteiden-haku-kaynnissa? hintaryhmat] :as app}]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.

      (let [hintaryhmat (concat
                          (tiedot/kokonaishintaisista-siirretyt-hintaryhma)
                          (h/jarjesta-hintaryhmat hintaryhmat))]
        [:div
         [kartta/kartan-paikka]
         [debug/debug app]
         [jaettu/suodattimet e! tiedot/->PaivitaValinnat app (:urakka ulkoiset-valinnat) tiedot/vaylahaku
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
                         (or
                           ;; Kok. hint. siirretyt -ryhmä, jos ei tyhjä
                           (and (tiedot/kokonaishintaisista-siirretyt-hintaryhma? hintaryhma)
                                (not (empty? hintaryhman-toimenpiteet)))
                           hintaryhma-tyhja? ;; Kannassa täysin tyhjä hintaryhmä; piirretään aina, jotta voi poistaa
                           (not (empty? hintaryhman-toimenpiteet)))) ;; Sis. toimenpiteitä käytetyillä suodattimilla
                       nayta-hintaryhman-yhteenveto? (boolean (and hintaryhma-id
                                                                   (not (empty? hintaryhman-toimenpiteet))))]]

             (when nayta-hintaryhma?
               ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-hintaryhma")}
               [:div.vv-toimenpideryhma
                ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-otsikko")}
                [:span [napit/nappi
                        (ikonit/map-marker)
                        #(if (tiedot/hintaryhma-korostettu? hintaryhma app)
                           (e! (tiedot/->PoistaHintaryhmanKorostus))

                           (e! (tiedot/->KorostaHintaryhmaKartalla hintaryhma)))
                        {:ikoninappi? true
                         :disabled hintaryhma-tyhja?
                         :luokka (str "vv-hintaryhma-korostus-nappi "
                                      (if (tiedot/hintaryhma-korostettu? hintaryhma app)
                                        "nappi-ensisijainen"
                                        "nappi-toissijainen"))}]
                 [jaettu/hintaryhman-otsikko (h/hintaryhman-nimi hintaryhma)]]

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
                    :rivi-klikattu [tiedot/poista-hintaryhmien-korostus]
                    :infolaatikon-tila-muuttui [tiedot/poista-hintaryhmien-korostus]
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