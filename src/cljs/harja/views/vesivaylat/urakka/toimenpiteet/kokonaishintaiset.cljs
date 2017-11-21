(ns harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :refer [tuck]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as tiedot]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as yks-hint]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu-tiedot]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.ui.debug :as debug]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.views.kartta :as kartta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]))

(def sivu "Vesiväylät/Kokonaishintaiset")

(defn- kiintiovaihtoehdot [e! {:keys [valittu-kiintio-id toimenpiteet kiintiot] :as app}]
  [:div.inline-block {:style {:margin-right "10px"}}
   [yleiset/livi-pudotusvalikko
    {:valitse-fn #(e! (tiedot/->ValitseKiintio (::kiintio/id %)))
     :format-fn #(or (::kiintio/nimi %) "Valitse kiintiö")
     :class "livi-alasveto-250"
     :valinta (kiintio/kiintio-idlla kiintiot valittu-kiintio-id)
     :disabled (not (jaettu-tiedot/joku-valittu? toimenpiteet))}
    kiintiot]])

(defn- liita-kiintioon-nappi [e! {:keys [toimenpiteet valittu-kiintio-id kiintioon-liittaminen-kaynnissa?] :as app}]
  [napit/yleinen-ensisijainen
   (if kiintioon-liittaminen-kaynnissa?
     [yleiset/ajax-loader-pieni "Liitetään.."]
     "Liitä")
   #(e! (tiedot/->LiitaToimenpiteetKiintioon))
   {:disabled (or (not (jaettu-tiedot/joku-valittu? toimenpiteet))
                  (not valittu-kiintio-id)
                  kiintioon-liittaminen-kaynnissa?
                  (not (oikeudet/on-muu-oikeus? "liitä-kiintiöön"
                                                oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset
                                                (:id @nav/valittu-urakka))))}])

(defn- liita-kiintioon [e! app]
  [:span
   [:span {:style {:margin-right "10px"}} "Liitä valitut kiintiöön"]
   [kiintiovaihtoehdot e! app]
   [liita-kiintioon-nappi e! app]])

(defn urakkatoiminnot [e! app]
  [^{:key "siirto"}
  [jaettu/siirtonappi e!
   app
   "Siirrä yksikköhintaisiin"
   #(e! (tiedot/->SiirraValitutYksikkohintaisiin))
   #(oikeudet/on-muu-oikeus? "siirrä-yksikköhintaisiin"
                             oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset
                             (:id @nav/valittu-urakka))]
   ^{:key "kiintio"}
   [liita-kiintioon e! app]])

(defn- kokonaishintaiset-toimenpiteet-nakyma [e! app valinnat]
  (komp/luo
    (komp/kirjaa-kaytto! sivu)
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do
                         (e! (tiedot/->Nakymassa? true))
                         (e! (tiedot/->PaivitaValinnat {:urakka-id (get-in valinnat [:urakka :id])
                                                        :sopimus-id (first (:sopimus valinnat))
                                                        :aikavali (:aikavali valinnat)}))
                         (e! (tiedot/->HaeKiintiot)))
                      #(do
                         (u/valitse-oletussopimus-jos-valittuna-kaikki!)
                         (e! (tiedot/->Nakymassa? false))))
    (fn [e! {:keys [toimenpiteet] :as app}]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.

      (let [; Kiintiöttömät toimenpiteet liitetään väliaikaiseen kiintiöön kun ne palautuvat
            ; palvelimelta
            kiintiot (concat
                       [tiedot/valiaikainen-kiintio]
                       (kiintio/jarjesta-kiintiot (into #{} (keep ::to/kiintio toimenpiteet))))]
        [:div
        [kartta/kartan-paikka]
        [jaettu/suodattimet e!
         tiedot/->PaivitaValinnat
         app (:urakka valinnat)
         tiedot/vaylahaku
         tiedot/turvalaitehaku
         {:urakkatoiminnot (urakkatoiminnot e! app)}]
        [jaettu/tulokset e! app
         [:div
          (for* [kiintio kiintiot
                 :let [kiintio-id (::kiintio/id kiintio)
                       kiintion-toimenpiteet (to/toimenpiteet-kiintiolla toimenpiteet kiintio-id)
                       app* (assoc app :toimenpiteet kiintion-toimenpiteet)
                       kiintio-tyhja? (empty? (:toimenpiteet app*))]]
            (when-not kiintio-tyhja?
              [:div.vv-toimenpideryhma
               [:span [napit/nappi
                       (ikonit/map-marker)
                       #(if (tiedot/kiintio-korostettu? kiintio app)
                          (e! (tiedot/->PoistaKiintionKorostus))

                          (e! (tiedot/->KorostaKiintioKartalla kiintio)))
                       {:ikoninappi? true
                        :disabled kiintio-tyhja?
                        :luokka (str "vv-hintaryhma-korostus-nappi "
                                     (if (tiedot/kiintio-korostettu? kiintio app)
                                       "nappi-ensisijainen"
                                       "nappi-toissijainen"))}]
                [jaettu/hintaryhman-otsikko (::kiintio/nimi kiintio)]]

               [jaettu/listaus e! app*
                {:otsikko (or (::kiintio/nimi kiintio) "Kiintiö")
                 :sarakkeet [jaettu/sarake-tyolaji
                             jaettu/sarake-tyoluokka
                             jaettu/sarake-toimenpide
                             {:otsikko "Kiintiö" :tyyppi :string :leveys 10
                              :hae #(get-in % [::to/kiintio ::kiintio/nimi])}
                             jaettu/sarake-pvm
                             jaettu/sarake-vayla
                             jaettu/sarake-turvalaite
                             jaettu/sarake-turvalaitenumero
                             jaettu/sarake-vikakorjaus
                             (jaettu/sarake-liitteet e! app #(oikeudet/on-muu-oikeus?
                                                               "lisää-liite"
                                                               oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset
                                                               (:id @nav/valittu-urakka)))
                             (jaettu/sarake-checkbox e! app*)]
                 :listaus-tunniste :kokonaishintaiset-toimenpiteet
                 :paneelin-checkbox-sijainti "95.5%"
                 :vaylan-checkbox-sijainti "95.5%"}]]))]]]))))

(defn- kokonaishintaiset-toimenpiteet* [e! app]
  [kokonaishintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                 :sopimus @u/valittu-sopimusnumero
                                                 :aikavali @u/valittu-aikavali}])

(defn kokonaishintaiset-toimenpiteet []
  [tuck (jaettu-tiedot/yhdista-tilat! tiedot/tila yks-hint/tila) kokonaishintaiset-toimenpiteet*])
