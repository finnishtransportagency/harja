(ns harja.views.vesivaylat.urakka.toimenpiteet.jaettu
  (:require [reagent.core :as r]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.otsikkopaneeli :refer [otsikkopaneeli]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.organisaatio :as o]
            [harja.domain.vesivaylat.urakoitsija :as urakoitsija]
            [harja.domain.vesivaylat.sopimus :as sop]
            [harja.domain.vesivaylat.turvalaitekomponentti :as tkomp]
            [harja.domain.vesivaylat.komponenttityyppi :as ktyyppi]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as tiedot]
            [harja.fmt :as fmt]
            [harja.ui.liitteet :as liitteet])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]))

;;;;;;;;;;;;;;
;; SUODATTIMET
;;;;;;;;;;;;;;

(defn- toimenpide-infolaatikossa [toimenpide]
  [:div
   [yleiset/tietoja {:otsikot-omalla-rivilla? true
                     :kavenna? true
                     :jata-kaventamatta #{"Työlaji" "Työluokka" "Toimenpide"}
                     :otsikot-samalla-rivilla #{"Työlaji" "Työluokka" "Toimenpide"}
                     :tyhja-rivi-otsikon-jalkeen #{"Vesialue ja väylä" "Toimenpide"}}
    "Urakoitsija" (get-in toimenpide [::to/reimari-urakoitsija ::urakoitsija/nimi])
    "Sopimusnumero" (str (get-in toimenpide [::to/reimari-sopimus ::sop/r-nimi])
                         " ("
                         (get-in toimenpide [::to/reimari-sopimus ::sop/r-nro])
                         ")")
    "Vesialue ja väylä" (get-in toimenpide [::to/vayla ::va/nimi])
    "Työlaji" (to/reimari-tyolaji-fmt (::to/tyolaji toimenpide))
    "Työluokka" (to/reimari-tyoluokka-fmt (::to/tyoluokka toimenpide))
    "Toimenpide" (to/reimari-toimenpidetyyppi-fmt (::to/toimenpide toimenpide))
    "Päivämäärä ja aika" (pvm/pvm-opt (::to/pvm toimenpide))
    "Turvalaite" (get-in toimenpide [::to/turvalaite ::tu/nimi])
    "Henkilömäärä" (::to/reimari-henkilo-lkm toimenpide)
    "Lisätiedot" (::to/lisatieto toimenpide)]
   [:footer.livi-grid-infolaatikko-footer
    [:h5 "Turvalaitteen komponentit"]
    (if (empty? (::to/turvalaitekomponentit toimenpide))
      [:p "Ei komponentteja"]
      [:table
       [:thead
        [:tr
         [:th {:style {:width "25%"}} "Sarja\u00ADnumero"]
         [:th {:style {:width "75%"}} "Kompo\u00ADnent\u00ADti"]]]
       [:tbody
        (for* [turvalaitekomponentti (::to/turvalaitekomponentit toimenpide)]
          [:tr
           [:td (::tkomp/sarjanumero turvalaitekomponentti)]
           [:td (get-in turvalaitekomponentti [::tkomp/komponenttityyppi ::ktyyppi/nimi])]])]])]])

(defn- suodattimet-ja-toiminnot [e! PaivitaValinnatKonstruktori app urakka vaylahaku lisasuodattimet urakkatoiminto-napit]
  [valinnat/urakkavalinnat {}
   ^{:key "valintaryhmat"}
   [valinnat/valintaryhmat-3
    [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali urakka]

    [:div
     [valinnat/vaylatyyppi
      (r/wrap (get-in app [:valinnat :vaylatyyppi])
              (fn [uusi]
                (e! (PaivitaValinnatKonstruktori {:vaylatyyppi uusi}))))
      (sort-by va/tyyppien-jarjestys (into [nil] va/tyypit))
      #(if % (va/tyyppi-fmt %) "Kaikki")]

     [kentat/tee-otsikollinen-kentta {:otsikko "Väylä"
                                      :kentta-params {:tyyppi :haku
                                                      :nayta ::va/nimi
                                                      :lahde vaylahaku}
                                      :arvo-atom (r/wrap (get-in app [:valinnat :vayla])
                                                         (fn [uusi]
                                                           (e! (PaivitaValinnatKonstruktori {:vayla (::va/id uusi)}))))}]]

    (into
      [:div
       [valinnat/tyolaji
        (r/wrap (get-in app [:valinnat :tyolaji])
                (fn [uusi]
                  (e! (PaivitaValinnatKonstruktori {:tyolaji uusi}))))
        (to/jarjesta-reimari-tyolajit (tiedot/arvot-pudotusvalikko-valinnoiksi to/reimari-tyolajit))
        #(if % (to/reimari-tyolaji-fmt %) "Kaikki")]

       [valinnat/tyoluokka
        (r/wrap (get-in app [:valinnat :tyoluokka])
                (fn [uusi]
                  (e! (PaivitaValinnatKonstruktori {:tyoluokka uusi}))))
        (to/jarjesta-reimari-tyoluokat (tiedot/arvot-pudotusvalikko-valinnoiksi to/reimari-tyoluokat))
        #(if % (to/reimari-tyoluokka-fmt %) "Kaikki")]

       [valinnat/toimenpide
        (r/wrap (get-in app [:valinnat :toimenpide])
                (fn [uusi]
                  (e! (PaivitaValinnatKonstruktori {:toimenpide uusi}))))
        (to/jarjesta-reimari-toimenpidetyypit (tiedot/arvot-pudotusvalikko-valinnoiksi to/reimari-toimenpidetyypit))
        #(if % (to/reimari-toimenpidetyyppi-fmt %) "Kaikki")]

       [kentat/tee-kentta {:tyyppi :checkbox
                           :teksti "Näytä vain vikailmoituksista tulleet toimenpiteet"}
        (r/wrap (get-in app [:valinnat :vain-vikailmoitukset?])
                (fn [uusi]
                  (e! (PaivitaValinnatKonstruktori {:vain-vikailmoitukset? uusi}))))]]

      lisasuodattimet)]

   (when-not (empty? urakkatoiminto-napit)
     (into
       ^{:key "urakkatoiminnot"}
       [valinnat/urakkatoiminnot {:sticky? true}]
       urakkatoiminto-napit))])

(defn suodattimet
  [e! PaivitaValinnatKonstruktori app urakka vaylahaku {:keys [lisasuodattimet urakkatoiminnot]}]
  [:div
   [suodattimet-ja-toiminnot e! PaivitaValinnatKonstruktori app urakka vaylahaku
    (or lisasuodattimet [])
    (or urakkatoiminnot [])]])

(defn siirtonappi [e! {:keys [siirto-kaynnissa? toimenpiteet]} otsikko toiminto]
  [:div.inline-block {:style {:margin-right "10px"}}
   [napit/yleinen-ensisijainen (if siirto-kaynnissa?
                                 [ajax-loader-pieni "Siirretään.."]
                                 (str otsikko
                                      (when-not (empty? (tiedot/valitut-toimenpiteet toimenpiteet))
                                        (str " (" (count (tiedot/valitut-toimenpiteet toimenpiteet)) ")"))))
    toiminto
    {:disabled (or (not (tiedot/joku-valittu? toimenpiteet))
                   siirto-kaynnissa?)}]])


;;;;;;;;;;;;;;;;;
;; GRID / LISTAUS
;;;;;;;;;;;;;;;;;

(def sarake-tyoluokka {:otsikko "Työluokka" :nimi ::to/tyoluokka :fmt to/reimari-tyoluokka-fmt :leveys 10})
(def sarake-toimenpide {:otsikko "Toimenpide" :nimi ::to/toimenpide :fmt to/reimari-toimenpidetyyppi-fmt :leveys 10})
(def sarake-pvm {:otsikko "Päivämäärä" :nimi ::to/pvm :fmt pvm/pvm-opt :leveys 5})
(def sarake-turvalaite {:otsikko "Turvalaite" :nimi ::to/turvalaite :leveys 10 :hae #(get-in % [::to/turvalaite ::tu/nimi])})
(def sarake-vikakorjaus {:otsikko "Vikakorjaus" :nimi ::to/vikakorjauksia? :fmt fmt/totuus :leveys 5})
(defn sarake-liitteet [e! app]
  {:otsikko "Liitteet" :nimi :liitteet :tyyppi :komponentti :leveys 10
   :komponentti (fn [rivi]
                  [liitteet/liitteet-ja-lisays
                   (get-in app [:valinnat :urakka-id])
                   (::to/liitteet rivi)
                   {:uusi-liite-atom (r/wrap nil
                                             (fn [uusi-arvo]
                                               (e! (tiedot/->LisaaToimenpiteelleLiite
                                                     {:liite uusi-arvo
                                                      ::to/id (::to/id rivi)}))))
                    :disabled? (:liitteen-lisays-kaynnissa? app)
                    :lisaa-usea-liite? true
                    :salli-poistaa-tallennettu-liite? true
                    :poista-tallennettu-liite-fn #(e! (tiedot/->PoistaToimenpiteenLiite {::to/liite-id %
                                                                                         ::to/id (::to/id rivi)}))
                    :nayta-lisatyt-liitteet? false ; Tässä näkymässä liitteet eivät odota erillistä linkitystä,
                    ; vaan ne linkitetään toimenpiteeseen heti
                    :grid? true}])})
(defn sarake-checkbox [e! {:keys [toimenpiteet] :as app}]
  {:otsikko "Valitse" :nimi :valinta :tyyppi :komponentti :tasaa :keskita
   :komponentti (fn [rivi]
                  ;; TODO Olisi kiva jos otettaisiin click koko solun alueelta
                  ;; Siltatarkastuksissa käytetty radio-elementti expandoi labelin
                  ;; koko soluun. Voisi ehkä käyttää myös checkbox-elementille
                  ;; Täytyy kuitenkin varmistaa, ettei mikään mene rikki.
                  ;; Ja entäs otsikkorivit?
                  [kentat/tee-kentta
                   {:tyyppi :checkbox}
                   (r/wrap (:valittu? rivi)
                           (fn [uusi]
                             (e! (tiedot/->ValitseToimenpide {:id (::to/id rivi)
                                                              :valinta uusi}
                                                             toimenpiteet))))])
   :leveys 5})

(defn vaylaotsikko [e! vaylan-toimenpiteet vayla vaylan-checkbox-sijainti]
  (grid/otsikko
    (grid/otsikkorivin-tiedot
      (::va/nimi vayla)
      (count vaylan-toimenpiteet))
    {:id (::va/id vayla)
     :otsikkokomponentit
     [{:sijainti vaylan-checkbox-sijainti
       :sisalto
       (fn [_]
         [kentat/tee-kentta
          {:tyyppi :checkbox}
          (r/wrap (tiedot/valinnan-tila vaylan-toimenpiteet)
                  (fn [uusi]
                    (e! (tiedot/->ValitseVayla {:vayla-id (::va/id vayla)
                                                :valinta uusi}
                                               vaylan-toimenpiteet))))])}]}))

(defn vaylaotsikko-ja-sisalto [e! toimenpiteet-vaylittain vaylan-checkbox-sijainti]
  (fn [vayla]
    (cons
      ;; Väylän otsikko
      (vaylaotsikko e! (get toimenpiteet-vaylittain vayla) vayla vaylan-checkbox-sijainti)
      ;; Väylän toimenpiderivit
      (get toimenpiteet-vaylittain vayla))))

(defn- ryhmittele-toimenpiteet-vaylalla [e! toimenpiteet vaylan-checkbox-sijainti]
  (let [toimenpiteet-vaylittain (group-by ::to/vayla toimenpiteet)
        vaylat (keys toimenpiteet-vaylittain)]
    (vec (mapcat (vaylaotsikko-ja-sisalto e! toimenpiteet-vaylittain vaylan-checkbox-sijainti) vaylat))))

(defn- suodata-ja-ryhmittele-toimenpiteet-gridiin [e! toimenpiteet tyolaji vaylan-checkbox-sijainti]
  (as-> toimenpiteet $
        (tiedot/toimenpiteet-tyolajilla $ tyolaji)
        (ryhmittele-toimenpiteet-vaylalla e! $ vaylan-checkbox-sijainti)))

(defn- paneelin-sisalto [e! app listaus-tunniste toimenpiteet sarakkeet]
  [grid/grid
   {:tunniste ::to/id
    :infolaatikon-tila-muuttui (fn [nakyvissa?]
                                 (e! (tiedot/->AsetaInfolaatikonTila listaus-tunniste nakyvissa?)))
    :mahdollista-rivin-valinta? (nil? (get-in app [:hinnoittele-toimenpide ::to/id]))
    :rivin-infolaatikko (fn [rivi data]
                          [toimenpide-infolaatikossa rivi])
    :salli-valiotsikoiden-piilotus? true
    :ei-footer-muokkauspaneelia? true
    :valiotsikoiden-alkutila :kaikki-kiinni}
   sarakkeet
   toimenpiteet])

(defn- luo-otsikkorivit
  [{:keys [e! app listaus-tunniste toimenpiteet toimenpiteiden-haku-kaynnissa?
           gridin-sarakkeet vaylan-checkbox-sijainti]}]
  (let [tyolajit (keys (group-by ::to/tyolaji toimenpiteet))]
    (vec (mapcat
           (fn [tyolaji]
             [tyolaji
              [:span
               (grid/otsikkorivin-tiedot (to/reimari-tyolaji-fmt tyolaji)
                                         (count (tiedot/toimenpiteet-tyolajilla
                                                  toimenpiteet
                                                  tyolaji)))
               (when toimenpiteiden-haku-kaynnissa? [:span " " [ajax-loader-pieni]])]
              [paneelin-sisalto
               e!
               app
               listaus-tunniste
               (suodata-ja-ryhmittele-toimenpiteet-gridiin
                 e!
                 toimenpiteet
                 tyolaji
                 vaylan-checkbox-sijainti)
               gridin-sarakkeet]])
           tyolajit))))

(defn hintaryhman-otsikko [otsikko]
  [:h1 otsikko])

(defn- toimenpiteet-listaus [e! {:keys [toimenpiteet infolaatikko-nakyvissa toimenpiteiden-haku-kaynnissa?] :as app}
                             gridin-sarakkeet {:keys [otsikko paneelin-checkbox-sijainti footer
                                                      listaus-tunniste vaylan-checkbox-sijainti]}]
  [:div
   (when otsikko [hintaryhman-otsikko otsikko])
   (into [otsikkopaneeli
          {:otsikkoluokat (when (get infolaatikko-nakyvissa listaus-tunniste) ["livi-grid-infolaatikolla"])
           :paneelikomponentit
           [{:sijainti paneelin-checkbox-sijainti
             :sisalto
             (fn [{:keys [tunniste]}]
               (let [tyolajin-toimenpiteet (tiedot/toimenpiteet-tyolajilla toimenpiteet tunniste)]
                 [kentat/tee-kentta
                  {:tyyppi :checkbox}
                  (r/wrap (tiedot/valinnan-tila tyolajin-toimenpiteet)
                          (fn [uusi]
                            (e! (tiedot/->ValitseTyolaji {:tyolaji tunniste
                                                          :valinta uusi}
                                                         toimenpiteet))))]))}]}]
         (luo-otsikkorivit
           {:e! e!
            :app app
            :listaus-tunniste listaus-tunniste
            :toimenpiteet toimenpiteet
            :toimenpiteiden-haku-kaynnissa? toimenpiteiden-haku-kaynnissa?
            :gridin-sarakkeet gridin-sarakkeet
            :vaylan-checkbox-sijainti vaylan-checkbox-sijainti}))
   (when footer
     [:div.toimenpiteet-listaus-footer footer])])

(defn tulokset [e! {:keys [toimenpiteet toimenpiteiden-haku-kaynnissa?] :as app} sisalto]
  (cond (and toimenpiteiden-haku-kaynnissa? (empty? toimenpiteet)) [ajax-loader "Toimenpiteitä haetaan..."]
        (empty? toimenpiteet) [:div "Ei toimenpiteitä"]
        :default sisalto))

(defn listaus
  ([e! app] (listaus e! app {}))
  ([e! app {:keys [otsikko paneelin-checkbox-sijainti vaylan-checkbox-sijainti
                   footer listaus-tunniste sarakkeet]}]
   (assert (and paneelin-checkbox-sijainti vaylan-checkbox-sijainti) "Anna checkboxin sijainnit")
   [toimenpiteet-listaus e! app
    sarakkeet
    {:otsikko otsikko
     :footer footer
     :listaus-tunniste listaus-tunniste
     :paneelin-checkbox-sijainti paneelin-checkbox-sijainti
     :vaylan-checkbox-sijainti vaylan-checkbox-sijainti}]))