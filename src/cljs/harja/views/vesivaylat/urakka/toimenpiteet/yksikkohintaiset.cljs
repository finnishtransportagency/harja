(ns harja.views.vesivaylat.urakka.toimenpiteet.yksikkohintaiset
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as kok-hint]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu-tiedot]
            [harja.views.vesivaylat.urakka.toimenpiteet.toimenpiteen-hinnoittelu :as hinnoittelu-ui]
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

(defn- varmistusdialogi-sisalto [app]
  (let [valitut-toimenpiteet (filter :valittu? (:toimenpiteet app))]
    [:div
     (when (to/toimenpiteilla-hintaryhmia? valitut-toimenpiteet)
       (jaettu/varmistusdialog-ohje
         {:varmistusehto ::to/hintaryhma-id
          :valitut-toimenpiteet valitut-toimenpiteet
          :nayta-max 5
          :toimenpide-lisateksti-fn #(str "Tilaus: " (::h/nimi (h/hinnoittelu-idlla (:hintaryhmat app)
                                                                                    (::to/hintaryhma-id %))) ".")
          :varmistusteksti-header "Seuraavat toimenpiteet kuuluvat tilaukseen:"
          :varmistusteksti-footer "Nämä toimenpiteet irrotetaan tilauksesta siirron aikana."}))
     (when (to/toimenpiteilla-omia-hinnoitteluja? (filter :valittu? (:toimenpiteet app)))
       (jaettu/varmistusdialog-ohje
         {:varmistusehto ::to/oma-hinnoittelu
          :valitut-toimenpiteet valitut-toimenpiteet
          :nayta-max 5
          :toimenpide-lisateksti-fn #(str "Hinta: " (fmt/euro-opt (+ (hinta/kokonaishinta-yleiskustannuslisineen
                                                                       (get-in % [::to/oma-hinnoittelu ::h/hinnat]))
                                                                     (tyo/toiden-kokonaishinta
                                                                       (get-in % [::to/oma-hinnoittelu ::h/tyot])
                                                                       (tpk/aikavalin-hinnalliset-suunnitellut-tyot
                                                                         (:suunnitellut-tyot app)
                                                                         (get-in app [:valinnat :aikavali])))))
                                          ".")
          :varmistusteksti-header "Seuraavat toimenpiteet sisältävät hinnoittelutietoja:"
          :varmistusteksti-footer "Näiden toimenpiteiden hinnoittelutiedot poistetaan siirron aikana."}))
     [:p "Haluatko jatkaa?"]]))

(defn- valmistele-toimenpiteiden-siirto [app e!]
  (if (or (to/toimenpiteilla-hintaryhmia? (filter :valittu? (:toimenpiteet app)))
          (to/toimenpiteilla-omia-hinnoitteluja? (filter :valittu? (:toimenpiteet app))))
    (varmista-kayttajalta/varmista-kayttajalta
      {:otsikko "Siirto kokonaishintaisiin"
       :sisalto (varmistusdialogi-sisalto app)
       :hyvaksy "Siirrä kokonaishintaisiin"
       :toiminto-fn #(e! (tiedot/->SiirraValitutKokonaishintaisiin))})
    (e! (tiedot/->SiirraValitutKokonaishintaisiin))))

(defn- urakkatoiminnot [e! app]
  [^{:key "siirto"}
  [jaettu/siirtonappi e! app
   "Siirrä kokonaishintaisiin"
   #(valmistele-toimenpiteiden-siirto app e!)
   #(oikeudet/on-muu-oikeus? "siirrä-kokonaishintaisiin"
                             oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                             (:id @nav/valittu-urakka))]
   ^{:key "hinnoittelu"}
   [hinnoittelu e! app]])

(defn- hintaryhman-hinnoittelu [e! app* hintaryhma]
  (let [hinnoittelu-id (get-in app* [:hinnoittele-hintaryhma ::h/id])
        hintaryhman-toimenpiteet (:toimenpiteet app*)
        valittu-aikavali (get-in app* [:valinnat :aikavali])
        suunnitellut-tyot (tpk/aikavalin-hinnalliset-suunnitellut-tyot (:suunnitellut-tyot app*)
                                                                       valittu-aikavali)
        hintaryhman-toimenpiteiden-omat-hinnat (remove nil? (mapcat #(get-in % [::to/oma-hinnoittelu ::h/hinnat])
                                                                    hintaryhman-toimenpiteet))
        hintaryhman-toimenpiteiden-omat-tyot (remove nil? (mapcat #(get-in % [::to/oma-hinnoittelu ::h/tyot])
                                                                  hintaryhman-toimenpiteet))
        hintaryhman-toimenpiteiden-yhteishinta (+ (hinta/kokonaishinta-yleiskustannuslisineen
                                                    hintaryhman-toimenpiteiden-omat-hinnat)
                                                  (tyo/toiden-kokonaishinta hintaryhman-toimenpiteiden-omat-tyot
                                                                            suunnitellut-tyot))
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
           (r/wrap (hinta/hinnan-summa-otsikolla
                     (get-in app* [:hinnoittele-hintaryhma ::h/hinnat])
                     tiedot/hintaryhman-hintakentta-otsikko)
                   #(e! (tiedot/->AsetaHintaryhmakentalleTiedot
                          {::hinta/otsikko tiedot/hintaryhman-hintakentta-otsikko
                           ::hinta/summa %})))]
          [:span " "]
          [:span "€"]]
         [napit/tallenna
          "Valmis"
          #(e! (tiedot/->TallennaHintaryhmanHinnoittelu (:hinnoittele-hintaryhma app*)))
          {:disabled (or (:hintaryhman-hinnoittelun-tallennus-kaynnissa? app*)
                         (not (oikeudet/on-muu-oikeus? "hinnoittele-tilaus"
                                                       oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                       (:id @nav/valittu-urakka))))}]
         [napit/peruuta
          "Peruuta"
          #(e! (tiedot/->PeruHintaryhmanHinnoittelu))]]
        (when-not (tiedot/valiaikainen-hintaryhma? hintaryhma)
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
                                                        (:id @nav/valittu-urakka)))}]]])))]]))

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
                          [tiedot/kokonaishintaisista-siirretyt-hintaryhma]
                          [tiedot/reimarin-lisatyot-hintaryhma]
                          (h/jarjesta-hintaryhmat hintaryhmat))]
        [:div
         [kartta/kartan-paikka]
         [debug/debug app]
         [jaettu/suodattimet e! tiedot/->PaivitaValinnat app (:urakka ulkoiset-valinnat)
          tiedot/vaylahaku tiedot/turvalaitehaku
          {:urakkatoiminnot (urakkatoiminnot e! app)}]

         [jaettu/tulokset e! app
          [:div
           (for* [hintaryhma hintaryhmat
                  :let [hintaryhma-id (::h/id hintaryhma)
                        hintaryhman-toimenpiteet (to/toimenpiteet-hintaryhmalla toimenpiteet hintaryhma-id)
                        app* (assoc app :toimenpiteet hintaryhman-toimenpiteet)
                        listaus-tunniste (keyword (str "listaus-" hintaryhma-id))
                        hintaryhma-tyhja? (::h/tyhja? hintaryhma) ;; Ei sisällä toimenpiteitä kannassa
                        nayta-hintaryhma?
                        (boolean
                          (or
                            ;; Kok. hint. siirretyt tai reimarin lisätyöt -ryhmä, jos ei tyhjä
                            (and (tiedot/valiaikainen-hintaryhma? hintaryhma)
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
                        [jaettu/sarake-tyolaji
                         jaettu/sarake-tyoluokka
                         jaettu/sarake-toimenpide
                         jaettu/sarake-pvm
                         jaettu/sarake-vayla
                         jaettu/sarake-turvalaite
                         jaettu/sarake-turvalaitenumero
                         jaettu/sarake-vikakorjaus
                         (jaettu/sarake-liitteet e! app #(oikeudet/on-muu-oikeus?
                                                           "lisää-liite"
                                                           oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                           (:id @nav/valittu-urakka)))
                         {:otsikko "Hinta" :tyyppi :komponentti :leveys 10
                          :komponentti (fn [rivi]
                                         [hinnoittelu-ui/hinnoittele-toimenpide e! app* rivi listaus-tunniste])}
                         (jaettu/sarake-checkbox e! app*)]
                        :listaus-tunniste listaus-tunniste
                        :rivi-klikattu [tiedot/poista-hintaryhmien-korostus]
                        :infolaatikon-tila-muuttui [tiedot/poista-hintaryhmien-korostus]
                        :footer (when nayta-hintaryhman-yhteenveto?
                                  [hintaryhman-hinnoittelu e! app* hintaryhma])
                        :paneelin-checkbox-sijainti "95.5%"
                        :vaylan-checkbox-sijainti "95.5%"}])]))]]]))))

(defn- yksikkohintaiset-toimenpiteet* [e! app]
  [yksikkohintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                :sopimus @u/valittu-sopimusnumero
                                                :aikavali @u/valittu-aikavali}])

(defn yksikkohintaiset-toimenpiteet []
  [tuck (jaettu-tiedot/yhdista-tilat! tiedot/tila kok-hint/tila) yksikkohintaiset-toimenpiteet*])
