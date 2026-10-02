(ns harja.views.urakka.toteumat.velho-varusteet
  "Urakan 'TOTEUMAT' välilehden 'Varusteet' osio

  Näyttää Harjan kautta kirjatut varustetoimenpiteet sekä mahdollistaa haut ja muokkaukset suoraan Tierekisteriin rajapinnan
  kautta.

  Harjaan tallennettu varustetoimenpide sisältää Tievelhosta haetun kopion toimenpiteestä ja sen kohteesta rajatuilla tiedoilla.
  Tarkemmat tiedot löytyvät Tievelhosta."
  (:require [clojure.string :as str]
            [harja.tiedot.urakka :as u]
            [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.asiakas.kommunikaatio :as k]
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
            [harja.ui.napit :as napit]
            [harja.ui.sivupalkki :as sivupalkki]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader]]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]))

(defn kuntoluokka-komponentti [kuntoluokka]
  [yleiset/tila-indikaattori kuntoluokka
   {:class-skeema (zipmap (map :nimi v/kuntoluokat) (map :css-luokka v/kuntoluokat))
    :luokka "body-text"
    :wrapper-luokka "inline-block"
    :fmt-fn str}])

(def kuntoluokat-jarjestys
  {"Erittäin hyvä" 1
   "Hyvä" 2
   "Tyydyttävä" 3
   "Huono" 4
   "Erittäin huono" 5
   "Ei voitu tarkastaa" 6})

(defn kohdeluokka-teksti
  "Kääntää kohdeluokan tekstiksi. Lisätään puuttuvat ääkköset, muotoillaan erikoistapausket tai
  lisätään vain iso alkukirjain."
  [kohdeluokka]
  (case kohdeluokka
    "puomit-sulkulaitteet-pollarit" "Puomit, sulkulaitteet ja pollarit"
    "pylvaat" "Pylväät"
    nil "Kaikki"
    (str/capitalize kohdeluokka)))

(defn- oletus-hoitovuosi [urakka]
  (let [urakan-kesto (- (count (pvm/vuodet-valissa (:alkupvm urakka) (:loppupvm urakka))) 1)
        hoitokausien-alkuvuodet (into []
                                  (range
                                    (pvm/vuosi (:alkupvm urakka))
                                    urakan-kesto))
        kuluva-hoitovuosi urakka-tila/kuluva-alkuvuosi]
    (or (some #(when (= % kuluva-hoitovuosi) %) hoitokausien-alkuvuodet)
      (first hoitokausien-alkuvuodet)
      (pvm/vuosi (:alkupvm urakka)))))

(defn suodatuslomake [_e! _app]
  (fn [e! {:keys [valinnat urakka kuntoluokat-nimikkeisto kohdeluokat-nimikkeisto varustetyyppihaku] :as app}]
    (let [hoitokausien-alkuvuodet (into []
                                    (range
                                      (pvm/vuosi (:alkupvm urakka))
                                      (pvm/vuosi (:loppupvm urakka))))
          oletus-hoitovuosi (oletus-hoitovuosi urakka)
          hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi valinnat)
          valittu-toimenpide (:toimenpide valinnat)
          hoitovuoden-kuukaudet [nil 10 11 12 1 2 3 4 5 6 7 8 9]
          itse-tai-kaikki #(if % % "Kaikki")
          multimap-fn (fn [avain] (fn [{:keys [id nimi] :as t}]
                                    {:id id
                                     :nimi (or nimi t)
                                     :valittu? (if nimi
                                                 (contains? (get valinnat avain) nimi)
                                                 (nil? (get valinnat avain)))}))
          kuntoluokat (map (multimap-fn :kuntoluokat)
                        (conj (into ["Kaikki"] (map-indexed (fn [i v]
                                                              {:id i
                                                               :nimi v})
                                                 (conj (vec (sort-by #(kuntoluokat-jarjestys (:otsikko %))
                                                              kuntoluokat-nimikkeisto))
                                                   {:otsikko "Kuntoluokka puuttuu"
                                                    :nimi :ei-kuntoluokkaa})))))
          kohdeluokat (map (multimap-fn :kohdeluokat) (into ["Kaikki"] (map-indexed (fn [i v]
                                                                                      {:id i
                                                                                       :nimi v})
                                                                         (keys kohdeluokat-nimikkeisto))))
          toimenpiteet (into [nil] v/varuste_toimenpiteet)

          tr-kentan-valitse-fn (fn [avain]
                                 (fn [event]
                                   (e! (v/->ValitseTR-osoite (-> event .-target .-value) avain))))
          tie (:tie valinnat)
          aosa (:aosa valinnat)
          aeta (:aeta valinnat)
          losa (:losa valinnat)
          leta (:leta valinnat)]
      [:div
       ;[debug app {:otsikko "TUCK STATE"}]
       [:div.row.filtterit-container {:style {:height "100px"}}
        [valinnat/urakan-hoitokausi-tuck
         (:hoitokauden-alkuvuosi valinnat)
         hoitokausien-alkuvuodet
         #(e! (v/->ValitseHoitokausi %))
         {:wrapper-luokka "col-md-2 filtteri label-ja-alasveto-grid"
          :kaikki-valinta? true
          :kaikki-teksti "Ei rajausta"}]
        [yleiset/pudotusvalikko "Kuukausi"
         {:wrap-luokka "col-md-1 filtteri varusteet label-ja-alasveto-grid"
          :disabled (nil? hoitokauden-alkuvuosi)
          :valinta (:hoitovuoden-kuukausi valinnat)
          :vayla-tyyli? true
          :valitse-fn #(e! (v/->ValitseHoitovuodenKuukausi %))
          :format-fn #(if %
                        (str (pvm/kuukauden-nimi % true) " "
                          (if (>= % 10)
                            hoitokauden-alkuvuosi
                            (inc hoitokauden-alkuvuosi)))
                        (if hoitokauden-alkuvuosi
                          "Kaikki"
                          "Valitse hoitovuosi"))
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
          :format-fn #(or (:nimi %) "Kaikki")
          :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
         toimenpiteet]
        [valinnat/monivalinta-pudotusvalikko
         "Luokka"
         kohdeluokat
         (fn [kohdetyyppi valittu?]
           (e! (v/->ValitseKohdeluokka (:nimi kohdetyyppi) valittu?)))
         [nil " Kohdeluokkaa valittu"]
         {:wrap-luokka "col-md-1 filtteri label-ja-alasveto-grid"
          :vayla-tyyli? true
          :yksi-valittu-teksti (kohdeluokka-teksti (first (:kohdeluokat valinnat)))
          :fmt kohdeluokka-teksti
          :valintojen-maara (count (:kohdeluokat valinnat))}]

        [:div {:class "col-md-2 filtteri label-ja-alasveto-grid"}
         [:label.alasvedon-otsikko "Varustetyyppi"]

         [kentat/tee-kentta
          {:tyyppi :haku
           :nayta :otsikko :fmt :otsikko
           :hae-kun-yli-n-merkkia 0
           :lomake? true
           :disabled? (empty? (:kohdeluokat valinnat))
           :lahde varustetyyppihaku
           :monivalinta? true
           :tarkkaile-ulkopuolisia-muutoksia? true
           :monivalinta-teksti #(case (count %)
                                  0 "Kaikki valittu"
                                  1 (:otsikko (first %))
                                  (str (count %) " varustetyyppiä valittu"))}
          v/varustetyypit]]

        [valinnat/monivalinta-pudotusvalikko
         "Kuntoluokitus"
         kuntoluokat
         (fn [kuntoluokka valittu?]
           (e! (v/->ValitseKuntoluokka (:nimi kuntoluokka) valittu?)))
         [nil " Kuntoluokkaa valittu"]
         {:wrap-luokka "col-md-2 filtteri label-ja-alasveto-grid"
          :vayla-tyyli? true
          :yksi-valittu-teksti (:otsikko (first (:kuntoluokat valinnat)))
          :fmt (comp itse-tai-kaikki :otsikko)}]]

       [:div.row.haku-ja-tyhjennys
        [napit/yleinen-ensisijainen "Hae varustetoimenpiteitä" #(e! (v/->HaeVarusteet)) {:luokka "nappi-korkeus-32"
                                                                                         :disabled false
                                                                                         :ikoni (ikonit/livicon-search)}]
        [napit/yleinen-toissijainen "Tyhjennä valinnat" #(e! (v/->TyhjennaSuodattimet oletus-hoitovuosi))
         {:luokka "nappi-korkeus-32"
          :disabled (and (every? nil? (vals (dissoc valinnat :hoitokauden-alkuvuosi)))
                      (= oletus-hoitovuosi (:hoitokauden-alkuvuosi valinnat)))}]]])))

(def infoteksti-poistuneista-varusteista
  "Harjassa näytetään vain voimassaolevat varusteet. Jos kaipaat tietoa poistetuista varusteista tietyssä urakassa, käänny joko Velhon tai Harja-palautteen puoleen.")

(defn excel-vienti [app]
  [:form {:target "_blank"
          :method "POST"
          :action (k/excel-url :varusteet-ulkoiset-excel)}
   [:input {:type "hidden"
            :name "parametrit"
            :value (transit/clj->transit (v/hakuparametrit app))}]
   [napit/tallenna "Tallenna Excel" (constantly true)
    {:ikoni (ikonit/harja-icon-action-download)
     :luokka "nappi-toissijainen"
     :type "submit"
     :esta-prevent-default? true}]])

(defn listaus [e! {:keys [varusteet haku-paalla]}]
  (let [lkm (count varusteet)]
    [:span
     [grid/grid
      {:otsikko
       (str "Varustetoimenpiteet "
         (if (>= lkm v/+max-toteumat+)
           (str "(Liikaa osumia. Näytetään vain " v/+max-toteumat+ " ensimmäistä.)")
           (str "(" lkm ")")))
       :tunniste :rivi-id
       :luokat ["varuste-taulukko" "margin-top-32"]
       :tyhja (if haku-paalla
                [ajax-loader "Haetaan varustetapahtumia..."]
                "Suorita haku syöttämällä hakuehdot ja klikkaamalla Hae varustetoimenpiteitä.")
       :rivi-klikattu #(yleiset/fn-viiveella
                         (fn [] (do
                                  (e! (v/->AvaaVarusteLomake %))
                                  (e! (v/->HaeVarusteenHistoria %)))))
       :otsikkorivi-klikattu (fn [opts]
                               (e! (v/->JarjestaVarusteet (:nimi opts))))
       :voi-lisata? false :voi-kumota? false
       :voi-poistaa? (constantly false) :voi-muokata? true}
      [{:otsikko "Ajan\u00ADkoh\u00ADta" :nimi :alkupvm :leveys 5
        :fmt pvm/pvm-opt}
       {:otsikko "Tie\u00ADosoi\u00ADte" :leveys 5
        :hae v/muodosta-tr-osoite}
       {:otsikko "Toi\u00ADmen\u00ADpide" :nimi :toimenpide :leveys 3}
       {:otsikko "Varus\u00ADte\u00ADtyyppi" :nimi :tyyppi :leveys 5
        :hae (fn [rivi] (when-let [varustetyyppi (or (:tyyppi rivi) (:kohdeluokka rivi))]
                          (when varustetyyppi (str/capitalize varustetyyppi))))}
       {:otsikko "Varus\u00ADteen lisä\u00ADtieto" :nimi :lisatieto :leveys 9}
       {:otsikko "Kunto\u00ADluoki\u00ADtus" :nimi :kuntoluokka :tyyppi :komponentti :leveys 4
        :komponentti (fn [rivi]
                       [kuntoluokka-komponentti (:kuntoluokka rivi)])}
       {:otsikko "Teki\u00ADjä" :nimi :muokkaaja :leveys 3}]
      varusteet]
     [yleiset/info-laatikko :neutraali infoteksti-poistuneista-varusteista]]))

(defn listaus-toteumat [_ {:keys [historia-haku-paalla?]} valittu-toteumat]
  (if (and (not historia-haku-paalla?) (nil? valittu-toteumat))
    [:div "Varusteelle ei löytynyt historiaa velhosta"]

    [grid/grid
     {:otsikko "Käyntihistoria"
      :tunniste :ulkoinen-oid
      :luokat ["varuste-taulukko"]
      :voi-lisata? false :voi-kumota? false
      :voi-poistaa? (constantly false) :voi-muokata? true}
     [{:otsikko "Käyty" :nimi :alkupvm :leveys 3
       :fmt pvm/fmt-p-k-v-lyhyt}
      {:otsikko "Toi\u00ADmen\u00ADpide" :nimi :toimenpide :leveys 3}
      {:otsikko "Kunto\u00ADluoki\u00ADtus muu\u00ADtos" :nimi :kuntoluokka :tyyppi :komponentti :leveys 4
       :komponentti (fn [rivi]
                      [kuntoluokka-komponentti (:kuntoluokka rivi)])}
      {:otsikko "Teki\u00ADjä" :nimi :muokkaaja :leveys 3}]
     valittu-toteumat]))

(defn varustelomake-nakyma
  [e! _app]
  (let [saa-sulkea? (atom false)]
    (komp/luo
      (komp/piirretty #(yleiset/fn-viiveella (fn []
                                               (reset! saa-sulkea? true))))
      (komp/klikattu-ulkopuolelle #(when @saa-sulkea?
                                     (e! (v/->SuljeVarusteLomake)))
        {:tarkista-komponentti? true})
      (fn [e! {{:keys [ulkoinen-oid historia] :as varuste} :valittu-varuste :as app}]
        [:div.varustelomake {:on-click #(.stopPropagation %)}
         [sivupalkki/oikea
          {:leveys "600px"}
          [lomake/lomake
           {:luokka "padding-32"
            :otsikko-komp (fn [_]
                            [:span
                             "Velho OID: " ulkoinen-oid])
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
            {:nimi ::spacer :piilota-label? true :tyyppi :komponentti :palstoja 3
             ::lomake/col-luokka "margin-top-32"
             :komponentti (fn [_] [:hr])}
            {:tyyppi :komponentti :palstoja 3
             ::lomake/col-luokka "margin-top-32"
             :piilota-label? true
             :komponentti listaus-toteumat :komponentti-args [app historia]}]
           varuste]]]))))

(defn- varusteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos
      #(do
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M)
         (kartta-tasot/taso-paalle! :varusteet-ulkoiset)
         (e! (v/->ValitseHoitokausi (oletus-hoitovuosi (:urakka app))))
         (e! (v/->HaeNimikkeisto))
         (reset! varusteet-kartalla/varuste-klikattu-fn
           (fn [varuste-kartalla]
             (e! (v/->AvaaVarusteLomake varuste-kartalla))
             (e! (v/->HaeVarusteenHistoria varuste-kartalla)))))
      #(do
         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
         (kartta-tasot/taso-pois! :varusteet-ulkoiset)
         (reset! nav/kartan-edellinen-koko nil)
         (reset! varusteet-kartalla/varuste-klikattu-fn (constantly nil))))
    (komp/watcher nav/valittu-urakka
      (fn [_ _ urakka]
        (when urakka
          (e! (v/->TyhjennaVarusteListaus)))))
    (fn [e! app]
      [:div.varusteet-nakyma
       [:div.flex-row
        [:h1 "Varusteet"]
        [:div {:style {:margin-left "auto"}}
         [excel-vienti app]]]
       (when (:valittu-varuste app)
         [varustelomake-nakyma e! app])
       [suodatuslomake e! app]
       [kartta/kartan-paikka]
       [listaus e! app]])))

(defn velho-varusteet [ur]
  (swap! urakka-tila/velho-varusteet assoc :urakka ur)
  [tuck/tuck urakka-tila/velho-varusteet varusteet*])
