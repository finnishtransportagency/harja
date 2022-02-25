(ns harja.views.urakka.toteumat.velho-varusteet
  "Urakan 'Toteumat' välilehden 'Varusteet' osio

  Näyttä Harjan kautta kirjatut varustetoteumat sekä mahdollistaa haut ja muokkaukset suoraan Tierekisteriin rajapinnan
  kautta.

  Harjaan tallennettu varustetoteuma sisältää tiedot varsinaisesta työstä. Varusteiden tekniset tiedot päivitetään
  aina Tierekisteriin"
  (:require [cljs.core.async :refer [<! >! chan timeout]]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as kommunikaatio]
            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.domain.tierekisteri.varusteet :refer [varusteominaisuus->skeema] :as tierekisteri-varusteet]
            [harja.geo :as geo]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.tierekisteri.varusteet :as tv]
            [harja.tiedot.urakka.toteumat.velho-varusteet-tiedot :as v]
            [harja.tiedot.urakka.urakka :as urakka-tila]
            [harja.ui.debug :refer [debug]]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat :refer [tee-kentta]]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader]]
            [harja.views.kartta :as kartta]
            [harja.views.tierekisteri.varusteet :refer [varustehaku] :as view]
            [harja.views.urakka.toteumat.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [tuck.intercept :refer [intercept send-to]]))

(defn kuntoluokka-komponentti [kuntoluokka]
  [:span [yleiset/tila-indikaattori kuntoluokka
          {:class-skeema (zipmap (map :nimi v/kuntoluokat) (map :css-luokka v/kuntoluokat))
           :luokka "body-text"
           :fmt-fn str}]])

(defn suodatuslomake [e! {:keys [valinnat urakka] :as app}]
  (let [alkupvm (:alkupvm urakka)
        vuosi (pvm/vuosi alkupvm)
        hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
        hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi valinnat)
        valittu-varustetyyppi (:varustetyyppi valinnat)
        valittu-toteuma (:toteuma valinnat)
        hoitokauden-kuukaudet [nil 10 11 12 1 2 3 4 5 6 7 8 9]
        kuntoluokat (map (fn [{:keys [id nimi] :as t}]
                           {:id id
                            :nimi (or nimi t)
                            :valittu? (if nimi
                                        (contains? (:kuntoluokat valinnat) nimi)
                                        (nil? (:kuntoluokat valinnat))) })
                         (into ["Kaikki"] v/kuntoluokat))
        varustetyypit (into [nil] v/varustetyypit)
        toteumat (into [nil] (map :tallennusmuoto v/toteumat))
        tr-kentan-valitse-fn (fn [avain]
                               (fn [event]
                                 (e! (v/->ValitseTR-osoite (-> event .-target .-value) avain))))
        tie  (:tie valinnat)
        aosa (:aosa valinnat)
        aeta (:aeta valinnat)
        losa (:losa valinnat)
        leta (:leta valinnat)]
    [:div
     [:div.row.filtterit-container
      [debug app {:otsikko "TUCK STATE"}]
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
       {:wrap-luokka "col-md-1 filtteri label-ja-alasveto-grid"
        :valinta (:hoitokauden-kuukausi valinnat)
        :vayla-tyyli? true
        :valitse-fn #(e! (v/->ValitseHoitokaudenKuukausi %))
        :format-fn #(if %
                      (str (pvm/kuukauden-nimi % true) " "
                           (if (>= % 10)
                             hoitokauden-alkuvuosi
                             (inc hoitokauden-alkuvuosi)))
                      "Kaikki")
        :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
       hoitokauden-kuukaudet]
      [yleiset/tr-kentat-flex
       {:otsikko "Tierekisteriosoite"
        :wrap-luokka "col-md-3 filtteri tr-osoite"
        :alaotsikot? true}
       {:tie [yleiset/tr-kentan-elementti {:otsikko "Tie" :valitse-fn (tr-kentan-valitse-fn :tie) :luokka "tr-numero" :arvo tie}]
        :aosa [yleiset/tr-kentan-elementti {:otsikko "aosa" :valitse-fn (tr-kentan-valitse-fn :aosa) :luokka "tr-alkuosa" :arvo aosa}]
        :aeta [yleiset/tr-kentan-elementti {:otsikko "aet" :valitse-fn (tr-kentan-valitse-fn :aeta) :luokka "tr-alkuetaisyys" :arvo aeta}]
        :losa [yleiset/tr-kentan-elementti {:otsikko "losa" :valitse-fn (tr-kentan-valitse-fn :losa) :luokka "tr-loppuosa" :arvo losa}]
        :leta [yleiset/tr-kentan-elementti {:otsikko "let" :valitse-fn (tr-kentan-valitse-fn :leta) :luokka "tr-loppuetaisyys" :arvo leta}]}]
      [yleiset/pudotusvalikko "Varustetyyppi"
       {:wrap-luokka "col-md-2 filtteri label-ja-alasveto-grid"
        :valinta valittu-varustetyyppi
        :vayla-tyyli? true
        :valitse-fn #(e! (v/->ValitseVarustetyyppi %))
        :format-fn #(if %
                      %
                      "Kaikki")
        :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
       varustetyypit]
      [:div.col-md-2.filtteri.label-ja-alasveto-grid
       [:label.alasvedon-otsikko-vayla "Kuntoluokitus"]
       [valinnat/checkbox-pudotusvalikko
        kuntoluokat
        (fn [kuntoluokka valittu?]
          (e! (v/->ValitseKuntoluokka (:nimi kuntoluokka) valittu?)))
        [" Kuntoluokka valittu" " Kuntoluokkaa valittu"]
        {:vayla-tyyli? true
         :fmt (fn [x]
                (if x
                  x
                  "Kaikki"))}]]
      #_[yleiset/pudotusvalikko "Kuntoluokitus"
         {:wrap-luokka "col-md-2 filtteri label-ja-alasveto-grid"
          :valinta valittu-kuntoluokka
          :vayla-tyyli? true
          :valitse-fn #(e! (v/->ValitseKuntoluokka % true))
          :format-fn #(if %
                        %
                        "Kaikki")
          :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
         kuntoluokat]
      [yleiset/pudotusvalikko "Toimenpide"
       {:wrap-luokka "col-md-1 filtteri label-ja-alasveto-grid"
        :valinta valittu-toteuma
        :vayla-tyyli? true
        :valitse-fn #(e! (v/->ValitseToteuma %))
        :format-fn #(if %
                      (v/toteuma->toimenpide %)
                      "Kaikki")
        :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
       toteumat]]
     [:div.row
      [napit/yleinen-ensisijainen "Hae varusteita" #(do
                                                      (e! (v/->TaydennaTR-osoite-suodatin tie aosa aeta losa leta))
                                                      (e! (v/->HaeVarusteet))) {:luokka "nappi-korkeus-36"
                                                                                :disabled false
                                                                                :ikoni (ikonit/livicon-search)}]
      [napit/yleinen-toissijainen "Tyhjennä valinnat" #(e! (v/->TyhjennaSuodattimet (pvm/vuosi (get-in app [:urakka :alkupvm]))))
       {:luokka "nappi-korkeus-36"
        :disabled (and (every? nil? (vals (dissoc valinnat :hoitokauden-alkuvuosi)))
                       (= (pvm/vuosi (get-in app [:urakka :alkupvm])) (:hoitokauden-alkuvuosi valinnat)))}]]]))

(defn listaus [e! {:keys [varusteet] :as app}]
  [grid/grid
   {:otsikko (str "Varusteet (" (count varusteet) ")")
    :tunniste :id
    :luokat ["varuste-taulukko"]
    ;:tyhja (if (nil? (:varusteet app))
    ;         [ajax-loader "Haetaan varusteita..."]
    ;         "Specify filter")
    :rivi-klikattu #(do
                      (e! (v/->AvaaVarusteLomake %))
                      (e! (v/->HaeToteumat)))
    :otsikkorivi-klikattu (fn [opts]
                            (println "petar kliknuo " opts)
                            (e! (v/->JarjestaVarusteet (:nimi opts))))
    :voi-lisata? false :voi-kumota? false
    :voi-poistaa? (constantly false) :voi-muokata? true}
   [{:otsikko "Ajan\u00ADkoh\u00ADta" :nimi :alkupvm :leveys 5
     :fmt pvm/fmt-p-k-v-lyhyt}
    {:otsikko "Tie\u00ADrekis\u00ADteri\u00ADosoi\u00ADte" :leveys 5
     :hae v/muodosta-tr-osoite}
    {:otsikko "Varus\u00ADte\u00ADtyyppi" :nimi :tietolaji :leveys 5
     :fmt v/tietolaji->varustetyyppi}
    {:otsikko "Varus\u00ADteen lisä\u00ADtieto" :nimi :lisatieto :leveys 9}
    {:otsikko "Kunto\u00ADluoki\u00ADtus" :nimi :kuntoluokka :tyyppi :komponentti :leveys 4
     :komponentti (fn [rivi]
                    [kuntoluokka-komponentti (:kuntoluokka rivi)])}
    {:otsikko "Toi\u00ADmen\u00ADpide" :nimi :toteuma :leveys 3
     :fmt v/toteuma->toimenpide}
    {:otsikko "Teki\u00ADjä" :nimi :muokkaaja :leveys 3}]
   varusteet])

(defn listaus-toteumat [{:keys [muokkaa-lomaketta data]} e! valittu-toteumat]
  [grid/grid
   {:otsikko "Käyntihistoria"
    :tunniste :id
    :luokat ["varuste-taulukko"]
    :voi-lisata? false :voi-kumota? false
    :voi-poistaa? (constantly false) :voi-muokata? true}
   [{:otsikko "Käyty" :nimi :alkupvm :leveys 3
     :fmt pvm/fmt-p-k-v-lyhyt}
    {:otsikko "Toi\u00ADmen\u00ADpide" :nimi :toteuma :leveys 3
     :fmt v/toteuma->toimenpide}
    {:otsikko "Kunto\u00ADluoki\u00ADtus muu\u00ADtos" :nimi :kuntoluokka :tyyppi :komponentti :leveys 4
     :komponentti (fn [rivi]
                    [kuntoluokka-komponentti (:kuntoluokka rivi)])}
    {:otsikko "Teki\u00ADjä" :nimi :muokkaaja :leveys 3}]
   valittu-toteumat])

(defn varustelomake-nakyma
  [e! varuste toteumat]
  (let [saa-sulkea? (atom false)]
    (komp/luo
      (komp/piirretty #(yleiset/fn-viiveella (fn []
                                               (reset! saa-sulkea? true))))
      (komp/klikattu-ulkopuolelle #(when @saa-sulkea?
                                     (e! (v/->SuljeVarusteLomake)))
                                  {:tarkista-komponentti? true})
      (fn [e! varuste toteumat]
        [:div.varustelomake {:on-click #(.stopPropagation %)}
         [lomake/lomake
          {:luokka " overlay-oikealla"
           :otsikko-komp (fn [_]
                           [:span
                            [:div.lomake-otsikko-pieni (:ulkoinen-oid varuste)]])
           :voi-muokata? false
           :sulje-fn #(e! (v/->SuljeVarusteLomake))
           :ei-borderia? true
           :footer-fn (fn [data]
                        [:span

                         [napit/sulje "Sulje"
                          #(e! (v/->SuljeVarusteLomake))
                          {:luokka "pull-left"}]])}
          [{:otsikko "" :muokattava? (constantly false) :nimi :tietolaji
            :fmt v/tietolaji->varustetyyppi :palstoja 3
            :piilota-label? true :vayla-tyyli? true :kentan-arvon-luokka "fontti-20"}
           {:nimi :kuntoluokka :tyyppi :komponentti
            :komponentti (fn [data]
                           (println "petrisi1523: kuntoluokka: " (get-in data [:data :kuntoluokka]))
                           (kuntoluokka-komponentti (get-in data [:data :kuntoluokka])))
            :otsikko "Kuntoluokitus"}
           {:nimi :tr-alkuosa
            :palstoja 1
            :otsikko "Aosa"
            :pakollinen? true :tyyppi :positiivinen-numero :kokonaisluku? true}
           {:tyyppi :komponentti :palstoja 3
            :komponentti listaus-toteumat :komponentti-args [e! toteumat]}]
          varuste]]))))

(defn- varusteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos
      #(do
         (println "petrisi1045: sisaan")
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M)
         (e! (v/->ValitseHoitokausi (pvm/vuosi (get-in app [:urakka :alkupvm])))))
      #(do
         (println "petrisi1046: ulos")
         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
         (reset! nav/kartan-edellinen-koko nil)))
    (fn [e! {ur :urakka :as app}]
      [:div
       (when (:valittu-varuste app)
         [varustelomake-nakyma e! (:valittu-varuste app) (:valittu-toteumat app)])
       [suodatuslomake e! app]
       [kartta/kartan-paikka]
       [listaus e! app]])))

(defn velho-varusteet [ur]
  (println "petrisi1039: urakka:" ur)
  (swap! urakka-tila/velho-varusteet assoc :urakka ur)
  [tuck/tuck urakka-tila/velho-varusteet varusteet*])
