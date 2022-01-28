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
           :fmt-fn str}]
   ])

(defn suodatuslomake [e! app]
  (let [{:keys [alkupvm]} (-> @urakka-tila/tila :yleiset :urakka) ;; Ota urakan alkamis päivä
        vuosi (pvm/vuosi alkupvm)
        hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
        hoitokauden-alkuvuosi (-> app :valinnat :hoitokauden-alkuvuosi)
        nykyinen-kaikki-kuukaudet (v/hoitokausi-rajat hoitokauden-alkuvuosi)
        valittu-kuukausi (-> app :valinnat :hoitokauden-kuukausi)
        valittu-kuukausi (if (nil? valittu-kuukausi)
                           nykyinen-kaikki-kuukaudet
                           valittu-kuukausi)
        valittu-kuntoluokka (-> app :valinnat :kuntoluokka)
        hoitokauden-kuukaudet (into [nykyinen-kaikki-kuukaudet]
                                    (vec (pvm/aikavalin-kuukausivalit nykyinen-kaikki-kuukaudet)))
        kuntoluokat (into [nil] (map :nimi v/kuntoluokat))]
    [:div.row.filtterit-container
     [debug app {:otsikko "TUCK STATE"}]
     [:div.col-md-4.filtteri
      [:span.alasvedon-otsikko-vayla "Hoitovuosi"]
      [yleiset/livi-pudotusvalikko {:valinta hoitokauden-alkuvuosi
                                    :vayla-tyyli? true
                                    :data-cy "hoitokausi-valinta"
                                    :valitse-fn #(do (e! (v/->ValitseHoitokausi (:id @nav/valittu-urakka) %))
                                                     (e! (v/->HaeVarusteet)))
                                    :format-fn #(str v/fin-hk-alkupvm % " \u2014 " v/fin-hk-loppupvm (inc %))
                                    :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
       hoitokaudet]]
     [:div.col-md-6.filtteri.kuukausi
      [:span.alasvedon-otsikko-vayla "Kuukausi"]
      [yleiset/livi-pudotusvalikko {:valinta valittu-kuukausi
                                    :vayla-tyyli? true
                                    :valitse-fn #(e! (v/->ValitseHoitokaudenKuukausi
                                                       (:id @nav/valittu-urakka)
                                                       %))
                                    :format-fn #(if %
                                                  (if (= nykyinen-kaikki-kuukaudet %)
                                                    "Kaikki"
                                                    (let [[alkupvm _] %
                                                          kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                                                      (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm))))
                                                  "Kaikki")
                                    :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
       hoitokauden-kuukaudet]]
     [:div.col-md-1.filtteri.kuukausi
      [:span.alasvedon-otsikko-vayla "Kuntoluokitus"]
      [yleiset/livi-pudotusvalikko {:valinta valittu-kuntoluokka
                                    :vayla-tyyli? true
                                    :valitse-fn #(do (e! (v/->ValitseKuntoluokka (:id @nav/valittu-urakka) %))
                                                     (e! (v/->HaeVarusteet)))
                                    :format-fn #(if %
                                                  %
                                                  "Kaikki")
                                    :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
       kuntoluokat]]
     #_[:div.filtteri {:style {:padding-top "21px"}}
        ^{:key "raporttixls"}
        [:form {:style {:margin-left "auto"}
                :target "_blank" :method "POST"
                :action (k/excel-url :kustannukset)}
         [:input {:type "hidden" :name "parametrit"
                  :value (transit/clj->transit {:urakka-id (:id @nav/valittu-urakka)
                                                :urakka-nimi (:nimi @nav/valittu-urakka)
                                                :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                                :alkupvm haun-alkupvm
                                                :loppupvm haun-loppupvm})}]
         [:button {:type "submit"
                   :class "nappi-toissijainen"}
          [ikonit/ikoni-ja-teksti [ikonit/livicon-download] "Tallenna Excel"]]]]
     ]))

(defn listaus [e! app]
  (println "petar " app)
  [grid/grid
   {:otsikko "Varusteet"
    :tunniste :id
    :luokat ["varuste-taulukko"]
    ;:tyhja (if (nil? (:varusteet app))
    ;         [ajax-loader "Haetaan varusteita..."]
    ;         "Specify filter")
    :rivi-klikattu #(e! (v/->AvaaVarusteLomake %))
    :voi-lisata? false :voi-kumota? false
    :voi-poistaa? (constantly false) :voi-muokata? true}
   [{:otsikko "Ajan\u00ADkoh\u00ADta" :nimi :alkupvm :leveys 5
     :fmt pvm/fmt-p-k-v-lyhyt}
    {:otsikko "Tie\u00ADrekis\u00ADteri\u00ADosoi\u00ADte" :leveys 5
     :hae (fn [rivi]
            (let [tie (:tr-numero rivi)
                  aosa (:tr-alkuosa rivi)
                  aet (:tr-alkuetaisyys rivi)
                  losa (:tr-loppuosa rivi)
                  let (:tr-loppuetaisyys rivi)]
              (if losa
                (str tie "/" aosa "/" aet "/" losa "/" let)
                (str tie "/" aosa "/" aet ))))}
    {:otsikko "Varus\u00ADte\u00ADtyyppi" :nimi :tietolaji :leveys 5
     :fmt v/tietolaji->varustetyyppi}
    {:otsikko "Varus\u00ADteen lisä\u00ADtieto" :nimi :lisatieto :leveys 9}
    {:otsikko "Kunto\u00ADluoki\u00ADtus" :nimi :kuntoluokka :tyyppi :komponentti :leveys 4
     :komponentti (fn [rivi]
                    [kuntoluokka-komponentti (:kuntoluokka rivi)])}
    {:otsikko "Toi\u00ADmen\u00ADpide" :nimi :toteuma :leveys 3}
    {:otsikko "Teki\u00ADjä" :nimi :muokkaaja :leveys 3}]
   (:varusteet app)])

(defn varustelomake-nakyma
  [e! varuste]
  (let [saa-sulkea? (atom false)]
    (komp/luo
      (komp/piirretty #(yleiset/fn-viiveella (fn []
                                               (reset! saa-sulkea? true))))
      (komp/klikattu-ulkopuolelle #(when @saa-sulkea?
                                     (e! (v/->SuljeVarusteLomake)))
                                  {:tarkista-komponentti? true})
      (fn [e! varuste]
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
                           (kuntoluokka-komponentti (get-in data [:data :kuntoluokka]))
                           )
            :otsikko "Kuntoluokitus"}
           {:nimi :tr-alkuosa
            :palstoja 1
            :otsikko "Aosa"
            :pakollinen? true :tyyppi :positiivinen-numero :kokonaisluku? true}]
          varuste]]))))

(defn- varusteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos
      #(do
         (println "petrisi1045: sisaan")
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M)
         (e! (v/->HaeVarusteet)))
      #(do
         (println "petrisi1046: ulos")
         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
         (reset! nav/kartan-edellinen-koko nil)))
    (fn [e! {ur :urakka :as app}]
      [:div
       (when (:valittu-varuste app)
         [varustelomake-nakyma e! (:valittu-varuste app)])
       [suodatuslomake e! app]
       [kartta/kartan-paikka]
       [listaus e! app]])))

(defn velho-varusteet [ur]
  (println "petrisi1039: urakka:" ur)
  (swap! urakka-tila/velho-varusteet assoc :urakka ur)
  [tuck/tuck urakka-tila/velho-varusteet varusteet*])
