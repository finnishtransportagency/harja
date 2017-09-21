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
            [harja.domain.vesivaylat.komponentin-tilamuutos :as komp-tila]
            [harja.domain.vesivaylat.komponenttityyppi :as ktyyppi]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as tiedot]
            [harja.fmt :as fmt]
            [harja.ui.liitteet :as liitteet]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]))

(defn varmistusdialog-ohje [{:keys [varmistusehto valitut-toimenpiteet nayta-max
                                    varmistusteksti-header varmistusteksti-footer toimenpide-lisateksti-fn]}]
  (let [varmistettavat-toimenpiteet (filter varmistusehto valitut-toimenpiteet)
        naytettavat-toimenpiteet (take nayta-max varmistettavat-toimenpiteet)]
    [:div
     [:p varmistusteksti-header]
     [:ul
      (for* [toimenpide naytettavat-toimenpiteet]
        [:li (str (pvm/pvm-opt (::to/pvm toimenpide)) " "
                  (to/reimari-toimenpidetyyppi-fmt (::to/toimenpide toimenpide)) ". "
                  (when toimenpide-lisateksti-fn
                    (toimenpide-lisateksti-fn toimenpide)))])
      (when (> (count varmistettavat-toimenpiteet) nayta-max)
        [:li (str "...sekä " (- (count varmistettavat-toimenpiteet) nayta-max) " muuta toimenpidettä.")])]
     [:p varmistusteksti-footer]]))

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
    "Lisätyö?" (to/reimari-lisatyo-fmt (::to/reimari-lisatyo? toimenpide))
    "Päivämäärä ja aika" (pvm/pvm-opt (::to/pvm toimenpide))
    "Turvalaite" (get-in toimenpide [::to/turvalaite ::tu/nimi])
    "Henkilömäärä" (::to/reimari-henkilo-lkm toimenpide)
    "Lisätiedot" (::to/lisatieto toimenpide)]
   [:footer.livi-grid-infolaatikko-footer
    [:h5 "Turvalaitteen komponentit"]
    (if (empty? (::to/komponentit toimenpide))
      [:p "Ei komponentteja"]
      [:table
       [:thead
        [:tr
         [:th {:style {:width "75%"}} "Kompo\u00ADnent\u00ADti"]
         [:th {:style {:width "25%"}} "Tila"]]]
       [:tbody
        (for* [turvalaitekomponentti (::to/komponentit toimenpide)]
          [:tr
           [:td (get-in turvalaitekomponentti [::tkomp/komponenttityyppi ::ktyyppi/nimi])]
           [:td (komp-tila/komponentin-tilakoodi->str (::komp-tila/tilakoodi turvalaitekomponentti))]])]])]])

(defn- suodattimet-ja-toiminnot [e! PaivitaValinnatKonstruktori app urakka vaylahaku turvalaitehaku lisasuodattimet urakkatoiminto-napit]
  [valinnat/urakkavalinnat {}
   ^{:key "valintaryhmat"}
   [valinnat/valintaryhmat-4
    [urakka-valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali
     urakka {:sopimus {:optiot {:kaikki-valinta? true}}}]

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
                                                           (e! (PaivitaValinnatKonstruktori {:vayla-id (::va/id uusi)}))))}]]

    [:div
     [kentat/tee-otsikollinen-kentta {:otsikko "Turvalaite"
                                      :kentta-params {:tyyppi :haku
                                                      :nayta ::tu/nimi
                                                      :lahde turvalaitehaku}
                                      :arvo-atom (r/wrap (get-in app [:valinnat :turvalaite])
                                                         (fn [uusi]
                                                           (e! (PaivitaValinnatKonstruktori {:turvalaite-id (::tu/id uusi)}))))}]]

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
                           :teksti "Näytä vain vikoihin liittyvät toimenpiteet"}
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
  [e! PaivitaValinnatKonstruktori app urakka vaylahaku turvalaitehaku {:keys [lisasuodattimet urakkatoiminnot]}]
  [:div
   [suodattimet-ja-toiminnot e! PaivitaValinnatKonstruktori app urakka vaylahaku turvalaitehaku
    (or lisasuodattimet [])
    (or urakkatoiminnot [])]])

(defn siirtonappi [e! {:keys [siirto-kaynnissa? toimenpiteet]} otsikko toiminto oikeus-fn]
  [:div.inline-block {:style {:margin-right "10px"}}
   [napit/yleinen-ensisijainen (if siirto-kaynnissa?
                                 [ajax-loader-pieni "Siirretään.."]
                                 (str otsikko
                                      (when-not (empty? (tiedot/valitut-toimenpiteet toimenpiteet))
                                        (str " (" (count (tiedot/valitut-toimenpiteet toimenpiteet)) ")"))))
    toiminto
    {:disabled (or (not (tiedot/joku-valittu? toimenpiteet))
                   siirto-kaynnissa?
                   (not (oikeus-fn)))}]])


;;;;;;;;;;;;;;;;;
;; GRID / LISTAUS
;;;;;;;;;;;;;;;;;

(def sarake-tyolaji {:otsikko "Työ\u00ADlaji" :nimi ::to/tyolaji :fmt to/reimari-tyolaji-fmt :leveys 5})
(def sarake-tyoluokka {:otsikko "Työ\u00ADluokka" :nimi ::to/tyoluokka :fmt to/reimari-tyoluokka-fmt :leveys 10})
(def sarake-toimenpide {:otsikko "Toimen\u00ADpide" :nimi ::to/toimenpide :fmt to/reimari-toimenpidetyyppi-fmt :leveys 10})
(def sarake-pvm {:otsikko "Päivä\u00ADmäärä" :nimi ::to/pvm :fmt pvm/pvm-aika-opt :leveys 6})
(def sarake-turvalaite {:otsikko "Turva\u00ADlaite" :nimi ::to/turvalaite :leveys 10 :hae #(get-in % [::to/turvalaite ::tu/nimi])})
(def sarake-turvalaitenumero {:otsikko "Turva\u00ADlaite\u00ADnumero" :nimi :turvalaitenumero :leveys 5 :hae #(get-in % [::to/turvalaite ::tu/turvalaitenro])})
(def sarake-vikakorjaus {:otsikko "Vika\u00ADkorjaus" :nimi ::to/vikakorjauksia? :fmt fmt/totuus :leveys 4})
(def sarake-vayla {:otsikko "Väylä" :nimi :vayla :hae (comp ::va/nimi ::to/vayla) :leveys 10})
(defn sarake-liitteet [e! app oikeus-fn]
  {:otsikko "Liit\u00ADteet" :nimi :liitteet :tyyppi :komponentti :leveys 6
   :komponentti (fn [rivi]
                  [liitteet/liitteet-ja-lisays
                   (get-in app [:valinnat :urakka-id])
                   (::to/liitteet rivi)
                   {:uusi-liite-atom (r/wrap nil
                                             (fn [uusi-arvo]
                                               (e! (tiedot/->LisaaToimenpiteelleLiite
                                                     {:liite uusi-arvo
                                                      ::to/id (::to/id rivi)}))))
                    :disabled? (or (:liitteen-lisays-kaynnissa? app)
                                   (not (oikeus-fn)))
                    :lisaa-usea-liite? true
                    :salli-poistaa-tallennettu-liite? true
                    :poista-tallennettu-liite-fn #(e! (tiedot/->PoistaToimenpiteenLiite {::to/liite-id %
                                                                                         ::to/id (::to/id rivi)}))
                    :nayta-lisatyt-liitteet? false ; Tässä näkymässä liitteet eivät odota erillistä linkitystä,
                    ; vaan ne linkitetään toimenpiteeseen heti
                    :grid? true}])})
(defn sarake-checkbox [e! {:keys [toimenpiteet] :as app}]
  {:otsikko "Valitse" :nimi :valinta :tyyppi :komponentti :tasaa :keskita
   :solu-klikattu (fn [rivi]
                    (e! (tiedot/->ValitseToimenpide {:id (::to/id rivi)
                                                     :valinta (not (:valittu? rivi))}
                                                    toimenpiteet)))
   :komponentti (fn [rivi]
                  [kentat/tee-kentta
                   {:tyyppi :checkbox}
                   (r/wrap (:valittu? rivi)
                           (fn [uusi]
                             (e! (tiedot/->ValitseToimenpide {:id (::to/id rivi)
                                                              :valinta uusi}
                                                             toimenpiteet))))])
   :leveys 3})

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

(defn- paneelin-sisalto [e! app listaus-tunniste toimenpiteet sarakkeet
                         {:keys [infolaatikon-tila-muuttui
                                 rivi-klikattu]}]
  [grid/grid
   {:tunniste ::to/id
    :infolaatikon-tila-muuttui (fn [nakyvissa?]
                                 (e! (tiedot/->AsetaInfolaatikonTila
                                       listaus-tunniste
                                       nakyvissa?
                                       infolaatikon-tila-muuttui)))
    :mahdollista-rivin-valinta? (nil? (get-in app [:hinnoittele-toimenpide ::to/id]))
    :rivin-infolaatikko (fn [rivi data]
                          [toimenpide-infolaatikossa rivi])
    :salli-valiotsikoiden-piilotus? true
    :ei-footer-muokkauspaneelia? true
    :rivi-klikattu (fn [rivi] (e! (tiedot/->KorostaToimenpideKartalla rivi rivi-klikattu)))
    :valiotsikoiden-alkutila :kaikki-kiinni}
   sarakkeet
   toimenpiteet])

(defn- luo-otsikkorivit
  [{:keys [e! app listaus-tunniste toimenpiteet toimenpiteiden-haku-kaynnissa?
           gridin-sarakkeet vaylan-checkbox-sijainti infolaatikon-tila-muuttui rivi-klikattu]}]
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
               gridin-sarakkeet
               {:infolaatikon-tila-muuttui infolaatikon-tila-muuttui
                :rivi-klikattu rivi-klikattu}]])
           tyolajit))))

(defn hintaryhman-otsikko [otsikko]
  [:h1.vv-hintaryhman-otsikko otsikko])

(defn- toimenpiteet-listaus [e! {:keys [toimenpiteet infolaatikko-nakyvissa toimenpiteiden-haku-kaynnissa?] :as app}
                             gridin-sarakkeet {:keys [otsikko paneelin-checkbox-sijainti footer
                                                      listaus-tunniste vaylan-checkbox-sijainti
                                                      rivi-klikattu infolaatikon-tila-muuttui]}]
  [grid/grid
   {:tunniste ::to/id
    :infolaatikon-tila-muuttui (fn [nakyvissa?]
                                 (e! (tiedot/->AsetaInfolaatikonTila
                                       listaus-tunniste
                                       nakyvissa?
                                       infolaatikon-tila-muuttui)))
    :mahdollista-rivin-valinta? (nil? (get-in app [:hinnoittele-toimenpide ::to/id]))
    :rivin-infolaatikko (fn [rivi data]
                          [toimenpide-infolaatikossa rivi])
    :salli-valiotsikoiden-piilotus? true
    :ei-footer-muokkauspaneelia? true
    :rivi-klikattu (fn [rivi] (e! (tiedot/->KorostaToimenpideKartalla rivi rivi-klikattu)))
    :valiotsikoiden-alkutila :kaikki-kiinni}
   gridin-sarakkeet
   (to/jarjesta-toimenpiteet-pvm-mukaan toimenpiteet)])

(defn tulokset [e! {:keys [toimenpiteet toimenpiteiden-haku-kaynnissa?] :as app} sisalto]
  (cond (and toimenpiteiden-haku-kaynnissa? (empty? toimenpiteet)) [ajax-loader "Toimenpiteitä haetaan..."]
        (empty? toimenpiteet) [:div "Ei toimenpiteitä"]
        :default sisalto))

(defn listaus
  ([e! app] (listaus e! app {}))
  ([e! app {:keys [otsikko paneelin-checkbox-sijainti vaylan-checkbox-sijainti
                   footer listaus-tunniste sarakkeet rivi-klikattu infolaatikon-tila-muuttui]}]
   (assert (and paneelin-checkbox-sijainti vaylan-checkbox-sijainti) "Anna checkboxin sijainnit")
   [toimenpiteet-listaus e! app
    sarakkeet
    {:otsikko otsikko
     :footer footer
     :listaus-tunniste listaus-tunniste
     :paneelin-checkbox-sijainti paneelin-checkbox-sijainti
     :vaylan-checkbox-sijainti vaylan-checkbox-sijainti
     :rivi-klikattu rivi-klikattu
     :infolaatikon-tila-muuttui infolaatikon-tila-muuttui}]))
