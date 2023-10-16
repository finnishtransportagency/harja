(ns harja.views.urakka.toteumat.velho-varusteet
  "Urakan 'TOTEUMAT' välilehden 'Varusteet' osio

  Näyttää Harjan kautta kirjatut varustetoimenpiteet sekä mahdollistaa haut ja muokkaukset suoraan Tierekisteriin rajapinnan
  kautta.

  Harjaan tallennettu varustetoimenpide sisältää Tievelhosta haetun kopion toimenpiteestä ja sen kohteesta rajatuilla tiedoilla.
  Tarkemmat tiedot löytyvät Tievelhosta."
  (:require [clojure.string :as str]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.varuste-ulkoiset :as varuste-ulkoiset]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.toteumat.velho-varusteet-tiedot :as v]
            [harja.tiedot.urakka.urakka :as urakka-tila]
            [harja.tiedot.urakka.varusteet-kartalla :as varusteet-kartalla]
            [harja.transit :as transit]
            [harja.ui.debug :refer [debug]]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake]
            [harja.ui.protokollat :as protokollat]
            [harja.ui.napit :as napit]
            [harja.ui.sivupalkki :as sivupalkki]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader]]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn kuntoluokka-komponentti [kuntoluokka]
  [yleiset/tila-indikaattori kuntoluokka
   {:class-skeema (zipmap (map :nimi v/kuntoluokat) (map :css-luokka v/kuntoluokat))
    :luokka "body-text"
    :wrapper-luokka "inline-block"
    :fmt-fn str}])

(defn suodatuslomake [_e! _app]
  ;; Varustetyypit on muista valinnoista poiketen toteutettu atomilla.
  ;; Voisi olla mahdollista toteuttaa käyttäen r/wrapia, mutta se osoittautui toistaiseksi liian haastavaksi.
  (let [varustetyypit (atom nil)]
    (fn [e! {:keys [valinnat urakka kuntoluokat kohdeluokat toimenpiteet varustetyyppihaku] :as app}]
      (let [alkupvm (:alkupvm urakka)
            vuosi (pvm/vuosi alkupvm)
            hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
            hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi valinnat)
            valittu-toimenpide (:toimenpide valinnat)
            hoitovuoden-kuukaudet [nil 10 11 12 1 2 3 4 5 6 7 8 9]
            itse-tai-kaikki #(if % % "Kaikki")
            multimap-fn (fn [avain] (fn [{:keys [id nimi alasveto-eritin?] :as t}]
                                      {:id id
                                       :nimi (or nimi t)
                                       :valittu? (if nimi
                                                   (contains? (get valinnat avain) nimi)
                                                   (nil? (get valinnat avain)))
                                       :alavetos-eritin? alasveto-eritin?}))
            kuntoluokat (map (multimap-fn :kuntoluokat) (into ["Kaikki"] (map-indexed (fn [i v]
                                                                                        {:id i
                                                                                         :nimi v})
                                                                           kuntoluokat)))
            kohdeluokat (map (multimap-fn :kohdeluokat) (into ["Kaikki"] (map-indexed (fn [i v]
                                                                                        {:id i
                                                                                         :nimi v})
                                                                           (keys kohdeluokat))))
            toimenpiteet (into [nil] toimenpiteet)

            tr-kentan-valitse-fn (fn [avain]
                                   (fn [event]
                                     (e! (v/->ValitseTR-osoite (-> event .-target .-value) avain))))
            tie (:tie valinnat)
            aosa (:aosa valinnat)
            aeta (:aeta valinnat)
            losa (:losa valinnat)
            leta (:leta valinnat)]
        [:div
         [debug app {:otsikko "TUCK STATE"}]
         [:div.row.filtterit-container {:style {:height "100px"}}
          [yleiset/pudotusvalikko "Hoitovuosi"
           {:wrap-luokka "col-md-2 filtteri label-ja-alasveto-grid"
            :valinta hoitokauden-alkuvuosi
            :vayla-tyyli? true
            :data-cy "hoitokausi-valinta"
            :valitse-fn #(e! (v/->ValitseHoitokausi %))
            :format-fn #(str v/fin-hk-alkupvm % " \u2014 " v/fin-hk-loppupvm (inc %))
            :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
           hoitokaudet]
          [yleiset/pudotusvalikko "Kuukausi"
           {:wrap-luokka "col-md-1 filtteri varusteet label-ja-alasveto-grid"
            :valinta (:hoitovuoden-kuukausi valinnat)
            :vayla-tyyli? true
            :valitse-fn #(e! (v/->ValitseHoitovuodenKuukausi %))
            :format-fn #(if %
                          (str (pvm/kuukauden-nimi % true) " "
                            (if (>= % 10)
                              hoitokauden-alkuvuosi
                              (inc hoitokauden-alkuvuosi)))
                          "Kaikki")
            :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
           hoitovuoden-kuukaudet]
          [yleiset/tr-kentat-flex
           {:wrap-luokka "col-md-2 filtteri varusteet tr-osoite-wrap"}
           {:tie [yleiset/tr-kentan-elementti {:otsikko "Tie" :valitse-fn (tr-kentan-valitse-fn :tie) :luokka "tr-numero" :arvo tie}]
            :aosa [yleiset/tr-kentan-elementti {:otsikko "aosa" :valitse-fn (tr-kentan-valitse-fn :aosa) :luokka "tr-alkuosa" :arvo aosa}]
            :aeta [yleiset/tr-kentan-elementti {:otsikko "aet" :valitse-fn (tr-kentan-valitse-fn :aeta) :luokka "tr-alkuetaisyys" :arvo aeta}]
            :losa [yleiset/tr-kentan-elementti {:otsikko "losa" :valitse-fn (tr-kentan-valitse-fn :losa) :luokka "tr-loppuosa" :arvo losa}]
            :leta [yleiset/tr-kentan-elementti {:otsikko "let" :valitse-fn (tr-kentan-valitse-fn :leta) :luokka "tr-loppuetaisyys" :arvo leta}]}]

          [yleiset/pudotusvalikko "Toimenpide"
           {:wrap-luokka "col-md-1 filtteri label-ja-alasveto-grid"
            :valinta valittu-toimenpide
            :vayla-tyyli? true
            :valitse-fn #(e! (v/->ValitseToimenpide %))
            :format-fn #(or (:otsikko %) "Kaikki")
            :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
           toimenpiteet]
          [valinnat/monivalinta-pudotusvalikko
           "Kohdeluokka"
           kohdeluokat
           (fn [kohdetyyppi valittu?]
             (e! (v/->ValitseKohdeluokka (:nimi kohdetyyppi) valittu? varustetyypit)))
           [" Kohdeluokka valittu" " Kohdeluokkaa valittu"]
           {:wrap-luokka "col-md-1 filtteri label-ja-alasveto-grid"
            :vayla-tyyli? true
            :fmt (comp str/capitalize itse-tai-kaikki)
            :valintojen-maara (count (:kohdeluokat valinnat))}]

          [:div {:class "col-md-2 filtteri label-ja-alasveto-grid"}
           [:label.alasvedon-otsikko-vayla "Varustetyyppi"]

           [kentat/tee-kentta
            {:tyyppi :haku
             :nayta :otsikko :fmt :otsikko
             :hae-kun-yli-n-merkkia 0
             :vayla-tyyli? true
             :lahde varustetyyppihaku
             :monivalinta? true
             :tarkkaile-ulkopuolisia-muutoksia? true
             :monivalinta-teksti #(do
                                    (case (count %)
                                      0 "Kaikki valittu"
                                      1 (:otsikko (first %))

                                      (str (count %) " varustetyyppiä valittu")))}
            varustetyypit]]

          [valinnat/monivalinta-pudotusvalikko
           "Kuntoluokitus"
           kuntoluokat
           (fn [kuntoluokka valittu?]
             (e! (v/->ValitseKuntoluokka (:nimi kuntoluokka) valittu?)))
           [" Kuntoluokka valittu" " Kuntoluokkaa valittu"]
           {:wrap-luokka "col-md-2 filtteri label-ja-alasveto-grid"
            :vayla-tyyli? true
            :fmt (comp itse-tai-kaikki :otsikko)}]]
         [:div.row
          ;; TODO: poista
          [napit/yleinen-ensisijainen "Hae varustetoimenpiteitä VANHA" #(do
                                                                          (e! (v/->TaydennaTR-osoite-suodatin tie aosa aeta losa leta))
                                                                          (e! (v/->HaeVarusteet :harja nil))) {:luokka "nappi-korkeus-32"
                                                                                                           :disabled false
                                                                                                           :ikoni (ikonit/livicon-search)}]
          [napit/yleinen-ensisijainen "Hae varustetoimenpiteitä" #(e! (v/->HaeVarusteet :velho @varustetyypit)) {:luokka "nappi-korkeus-32"
                                                                                                                    :disabled false
                                                                                                                    :ikoni (ikonit/livicon-search)}]
          [napit/yleinen-toissijainen "Tyhjennä valinnat" #(e! (v/->TyhjennaSuodattimet (pvm/vuosi (get-in app [:urakka :alkupvm]))))
           {:luokka "nappi-korkeus-32"
            :disabled (and (every? nil? (vals (dissoc valinnat :hoitokauden-alkuvuosi)))
                        (= (pvm/vuosi (get-in app [:urakka :alkupvm])) (:hoitokauden-alkuvuosi valinnat)))}]]]))))


(defn listaus [e! {:keys [varusteet haku-paalla] :as app}]
  (let [lkm (count varusteet)]
    [grid/grid
     {:otsikko (if (>= lkm v/+max-toteumat+)
                 (str "Varustetoimenpiteet (Liikaa osumia. Näytetään vain " v/+max-toteumat+ " ensimmäistä.)")
                 (str "Varustetoimenpiteet (" lkm ")"))
      :tunniste :ulkoinen-oid
      :luokat ["varuste-taulukko" "margin-top-32"]
      :tyhja (if haku-paalla
               [ajax-loader "Haetaan varustetapahtumia..."]
               "Suorita haku syöttämällä hakuehdot ja klikkaamalla Hae varustetoimenpiteitä.")
      :rivi-klikattu #(do
                        (e! (v/->AvaaVarusteLomake %))
                        (e! (v/->HaeToteumat)))
      :otsikkorivi-klikattu (fn [opts]
                              (e! (v/->JarjestaVarusteet (:nimi opts))))
      :paneelikomponentit [(fn [] [:span.inline-block
                                   [:form {:style {:margin-left "auto"}
                                           :target "_blank" :method "POST"
                                           :action (k/excel-url :varusteet-ulkoiset-excel)}
                                    [:input {:type "hidden" :name "parametrit"
                                             :value (transit/clj->transit (v/hakuparametrit app))}]
                                    [:button {:type "submit"
                                              :class #{"nappi-toissijainen nappi-reunaton"}}
                                     [ikonit/ikoni-ja-teksti (ikonit/livicon-download) "Vie exceliin"]]]])]
      :voi-lisata? false :voi-kumota? false
      :voi-poistaa? (constantly false) :voi-muokata? true}
     [{:otsikko "Ajan\u00ADkoh\u00ADta" :nimi :alkupvm :leveys 5
       :fmt pvm/pvm-opt}
      {:otsikko "Tie\u00ADrekis\u00ADteri\u00ADosoi\u00ADte" :leveys 5
       :hae v/muodosta-tr-osoite}
      {:otsikko "Toi\u00ADmen\u00ADpide" :nimi :toimenpide :leveys 3}
      {:otsikko "Varus\u00ADte\u00ADtyyppi" :nimi :tyyppi :leveys 5}
      {:otsikko "Varus\u00ADteen lisä\u00ADtieto" :nimi :lisatieto :leveys 9}
      {:otsikko "Kunto\u00ADluoki\u00ADtus" :nimi :kuntoluokka :tyyppi :komponentti :leveys 4
       :komponentti (fn [rivi]
                      [kuntoluokka-komponentti (:kuntoluokka rivi)])}
      {:otsikko "Teki\u00ADjä" :nimi :muokkaaja :leveys 3}]
     varusteet]))

(defn listaus-toteumat [_ valittu-toteumat]
  [grid/grid
   {:otsikko "Käyntihistoria"
    :tunniste :id
    :luokat ["varuste-taulukko"]
    :voi-lisata? false :voi-kumota? false
    :voi-poistaa? (constantly false) :voi-muokata? true}
   [{:otsikko "Käyty" :nimi :alkupvm :leveys 3
     :fmt pvm/fmt-p-k-v-lyhyt}
    {:otsikko "Toi\u00ADmen\u00ADpide" :nimi :toteuma :leveys 3
     :fmt varuste-ulkoiset/toteuma->toimenpide}
    {:otsikko "Kunto\u00ADluoki\u00ADtus muu\u00ADtos" :nimi :kuntoluokka :tyyppi :komponentti :leveys 4
     :komponentti (fn [rivi]
                    [kuntoluokka-komponentti (:kuntoluokka rivi)])}
    {:otsikko "Teki\u00ADjä" :nimi :muokkaaja :leveys 3}]
   valittu-toteumat])

(defn varustelomake-nakyma
  [e! _app]
  (let [saa-sulkea? (atom false)]
    (komp/luo
      (komp/piirretty #(yleiset/fn-viiveella (fn []
                                               (reset! saa-sulkea? true))))
      (komp/klikattu-ulkopuolelle #(when @saa-sulkea?
                                     (e! (v/->SuljeVarusteLomake)))
        {:tarkista-komponentti? true})
      (fn [e! {varuste :valittu-varuste toteumat :valittu-toteumat}]
        [:div.varustelomake {:on-click #(.stopPropagation %)}
         [sivupalkki/oikea
          {:leveys "600px"}
          [lomake/lomake
           {:luokka "padding-32"
            :otsikko-komp (fn [_]
                            [:span
                             "Velho OID: " (:ulkoinen-oid varuste)])
            :voi-muokata? false
            :sulje-fn #(e! (v/->SuljeVarusteLomake))
            :ei-borderia? true
            :footer-fn (fn [_]
                         [:span
                          [napit/yleinen-toissijainen "Sulje"
                           #(e! (v/->SuljeVarusteLomake))]])
            :footer-luokka ""}
           [{:otsikko "" :muokattava? (constantly false) :nimi :tyyppi
             :palstoja 1
             ::lomake/col-luokka "margin-top-4"
             :piilota-label? true :vayla-tyyli? true :kentan-arvon-luokka "fontti-20"}
            {:nimi :kuntoluokka :tyyppi :komponentti
             :komponentti (fn [data]
                            [:span
                             "Kuntoluokitus: "
                             [kuntoluokka-komponentti (get-in data [:data :kuntoluokka])]])
             ::lomake/col-luokka "margin-top-16"
             :piilota-label? true}
            {:tyyppi :komponentti
             :nimi :ulkoinen-id
             ::lomake/col-luokka ""
             :komponentti (fn [_]
                            [yleiset/tooltip
                             {}
                             [napit/yleinen-toissijainen "Katso tarkemmat varustetiedot"
                              ;; TODO: Linkki velhoon, kunhan velholta saadaan sellainen.
                              (constantly nil)
                              {:ikoni [ikonit/harja-icon-navigation-external-link]
                               :ikoni-oikealle? true
                               :disabled true}]
                             "Linkki velhoon ei ole vielä saatavilla."])}
            {:nimi ::spacer :piilota-label? true :tyyppi :komponentti :palstoja 3
             ::lomake/col-luokka "margin-top-32"
             :komponentti (fn [_] [:hr])}
            {:tyyppi :komponentti :palstoja 3
             ::lomake/col-luokka "margin-top-32"
             :piilota-label? true
             :komponentti listaus-toteumat :komponentti-args [toteumat]}]
           varuste]]]))))

(defn- varusteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos
      #(do
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M)
         (kartta-tasot/taso-paalle! :varusteet-ulkoiset)
         (e! (v/->ValitseHoitokausi (pvm/vuosi (get-in app [:urakka :alkupvm]))))
         (e! (v/->HaeNimikkeisto))
         (reset! varusteet-kartalla/varuste-klikattu-fn
           (fn [varuste-kartalla]
             (e! (v/->AvaaVarusteLomake varuste-kartalla))
             (e! (v/->HaeToteumat)))))
      #(do
         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
         (kartta-tasot/taso-pois! :varusteet-ulkoiset)
         (reset! nav/kartan-edellinen-koko nil)
         (reset! varusteet-kartalla/varuste-klikattu-fn (constantly nil))))
    (fn [e! app]
      [:div
       (when (:valittu-varuste app)
         [varustelomake-nakyma e! app])
       [suodatuslomake e! app]
       [kartta/kartan-paikka]
       [listaus e! app]])))

(defn velho-varusteet [ur]
  (swap! urakka-tila/velho-varusteet assoc :urakka ur)
  [tuck/tuck urakka-tila/velho-varusteet varusteet*])
