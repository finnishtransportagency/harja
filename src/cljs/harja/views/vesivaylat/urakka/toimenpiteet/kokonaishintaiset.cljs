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
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]))

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

(defn- valmistele-toimenpiteiden-siirto [e! toimenpiteet]
  (let [valitut-toimenpiteet (filter :valittu? toimenpiteet)]
    (if (to/toimenpiteilla-kiintioita? valitut-toimenpiteet)
      (varmista-kayttajalta/varmista-kayttajalta
        {:otsikko "Siirto yksikköhintaisiin"
         :sisalto
         [jaettu/varmistusdialog-ohje {:varmistusehto ::to/kiintio
                                       :valitut-toimenpiteet valitut-toimenpiteet
                                       :nayta-max 10
                                       :toimenpide-lisateksti-fn #(str "Kiintiö: " (get-in % [::to/kiintio ::kiintio/nimi]) ".")
                                       :varmistusteksti-header "Seuraavat toimenpiteet kuuluvat kiintiöön:"
                                       :varmistusteksti-footer "Nämä toimenpiteet irrotetaan kiintiöstä siirron aikana. Haluatko jatkaa?"}]

         :hyvaksy "Siirrä yksikköhintaisiin"
         :toiminto-fn #(e! (tiedot/->SiirraValitutYksikkohintaisiin))})
      (e! (tiedot/->SiirraValitutYksikkohintaisiin)))))

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
   #(valmistele-toimenpiteiden-siirto e! (:toimenpiteet app))
   #(oikeudet/on-muu-oikeus? "siirrä-yksikköhintaisiin"
                             oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset
                             (:id @nav/valittu-urakka))]
   ^{:key "kiintio"}
   [liita-kiintioon e! app]])

(defn- kokonaishintaiset-toimenpiteet-nakyma [e! app valinnat]
  (komp/luo
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
    (fn [e! app]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.

      [:div
       [kartta/kartan-paikka]
       [jaettu/suodattimet e!
        tiedot/->PaivitaValinnat
        app (:urakka valinnat)
        tiedot/vaylahaku
        {:urakkatoiminnot (urakkatoiminnot e! app)}]
       [jaettu/tulokset e! app
        [jaettu/listaus e! app
         {:otsikko "Kokonaishintaiset toimenpiteet"
          :sarakkeet [jaettu/sarake-tyoluokka
                      jaettu/sarake-toimenpide
                      {:otsikko "Kiintiö" :tyyppi :string :leveys 10
                       :hae #(get-in % [::to/kiintio ::kiintio/nimi])}
                      jaettu/sarake-pvm
                      jaettu/sarake-turvalaite
                      jaettu/sarake-vikakorjaus
                      (jaettu/sarake-liitteet e! app #(oikeudet/on-muu-oikeus?
                                                        "lisää-liite"
                                                        oikeudet/urakat-vesivaylatoimenpiteet-kokonaishintaiset
                                                        (:id @nav/valittu-urakka)))
                      (jaettu/sarake-checkbox e! app)]
          :listaus-tunniste :kokonaishintaiset-toimenpiteet
          :paneelin-checkbox-sijainti "95.5%"
          :vaylan-checkbox-sijainti "95.5%"}]]])))

(defn- kokonaishintaiset-toimenpiteet* [e! app]
  [kokonaishintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                 :sopimus @u/valittu-sopimusnumero
                                                 :aikavali @u/valittu-aikavali}])

(defn kokonaishintaiset-toimenpiteet []
  [tuck (jaettu-tiedot/yhdista-tilat! tiedot/tila yks-hint/tila) kokonaishintaiset-toimenpiteet*])