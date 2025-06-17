(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuunnitelma-nakyma
  "Uusi kustannusten suunnittelu"
  (:require [tuck.core :as tuck]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as kust-tiedot]))

(defonce tallenna-painettu (atom false))
(defonce virheet-atom (atom {}))
(defonce grid-tiedot-atom (atom [{}]))

(defn- otsikkotiedot [e! {:keys [valittu-hoitokausi tarjous] :as app} otsikko {:keys [div1 div2 div3 div4] :as opts}]
  (let [urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))
        tarjous-hankintakustannukset (filter #(= (:osio %) "hankintakustannukset") (:tarjous tarjous))
        tarjous-hankintakustannukset-yhteensa (:yhteensa (first tarjous-hankintakustannukset) 0)
        kilpailutettavat-hankinnat (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])
        kilpailutettavat-hankinnat-yhteensa (:yhteensa (last kilpailutettavat-hankinnat))
        kilpailutettavat-hankinnat-yhteensa-indeksikorjattu (:yhteensa-indeksikorjattu (last kilpailutettavat-hankinnat))
        indeksikerroin (get-in app [:kustannussuunnitelma :indeksikerroin])]
    [:div
     [:div.row
      [:div.col-xs-12
       [:h2 otsikko]
       [:div.body-text {:style {:margin-top "-15px"}} (fmt/hoitokauden-jarjestysluku-ja-vuodet (pvm/vuosi (first valittu-hoitokausi)) hoitovuodet "Hoitovuosi")]]]

     [:div.row {:style {:padding-top "1rem"}}
      (when div1
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Tarjouksen määrä"]
         [:div.body-text (fmt/euro true tarjous-hankintakustannukset-yhteensa)]])
      (when div2
       [:div.col-xs-12.col-md-3
        [:div.small-text.bold "Pysyvät muutokset"]
        [:div.body-text "Ei muutoksia"]
        [:div.body-text [yleiset/linkki "Siirry muutoksiin"
                         #(siirtymat/siirry-annettuun-valilehteen @nav/valittu-hallintayksikko-id (-> @tila/yleiset :urakka :id)
                            {:taso1 :urakat :taso2 :mhu-muutokset :taso3 nil})]]])
      (when div3
       [:div.col-xs-12.col-md-3
        [:div.small-text.bold "Yhteensä"]
        [:div.body-text (if kilpailutettavat-hankinnat-yhteensa (fmt/euro true kilpailutettavat-hankinnat-yhteensa) "0,00 €")]])

      (when div4
       [:div.col-xs-12.col-md-3
        [:div.small-text.bold "Indeksikorjattu"]
        [:div.body-text (if kilpailutettavat-hankinnat-yhteensa-indeksikorjattu (fmt/euro true kilpailutettavat-hankinnat-yhteensa-indeksikorjattu) "0,00 €")]
        [:div.body-text (when indeksikerroin
                          (str "(" indeksikerroin " * " (if kilpailutettavat-hankinnat-yhteensa (fmt/euro false kilpailutettavat-hankinnat-yhteensa-indeksikorjattu) "0,00 €") " )"))]])]]))

(defn kilpailutettavat-hankinnat [e! {:keys [tallennus-kesken? valittu-hoitokausi tarjous] :as app}]
  (let [tarjous-hankintakustannukset (filter #(= (:osio %) "hankintakustannukset") (:tarjous tarjous))
        tarjous-hankintakustannukset-yhteensa (:yhteensa (first tarjous-hankintakustannukset) 0)
        toimenpiteet (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])
        valhvistettu? (true? (get-in app [:kustannussuunnitelma :kustannussuunnitelma-vahvistettu?]))
        taulukon-tiedot (butlast toimenpiteet) ;; Jätetään yhteenvetorivi pois tässä kohdassa
        _ (reset! grid-tiedot-atom taulukon-tiedot)

        yht-alkukausi (:alkukausi (last toimenpiteet))
        yht-loppukausi (:loppukausi (last toimenpiteet))
        yht (:yhteensa (last toimenpiteet))

        kirjaamatta (- tarjous-hankintakustannukset-yhteensa yht)
        kirjaamatta-luokka (if (= 0 kirjaamatta) "yhteensa" "yhteensa-punainen")
        kirjaamatta-rivi (when-not valhvistettu? [^{:luokka "kustannukset-yhteenveto"}
                                                  {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                                                  {:teksti "" :luokka kirjaamatta-luokka}
                                                  {:teksti "" :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                                                  {:teksti "" :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                                                  {:teksti (fmt/euro false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}])

        yhteenveto-rivit [[^{:luokka "kustannukset-yhteenveto"}
                           {:teksti "Yhteensä" :luokka "yhteensa"}
                           {:teksti "Ei muutoksia" :luokka "yhteensa-ei-korostusta"}
                           {:teksti (fmt/euro false yht-alkukausi) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                           {:teksti (fmt/euro false yht-loppukausi) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                           {:teksti (fmt/euro false yht) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]
                          kirjaamatta-rivi]]
    [:div.kustannussuunnitelma-osio
     [otsikkotiedot e! app "Kilpailutettavat hankinnat" {:div1 true :div2 true :div3 true :div4 true}]

     [:div.row
      [:div.row
       [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]
      [:div.row
       [:div.col-xs-12.body-text "Erittele " [:strong "yhteensä-summa"] " toimenpiteille."]]]
     [:div.row
      [:div.col-xs-12
       [grid/grid {:otsikko ""
                   :muokkaa-aina true
                   :voi-muokata? true
                   :muokattava? (constantly true)
                   :voi-poistaa? (constantly false)
                   :voi-lisata? false
                   :voi-kumota? false
                   :piilota-toiminnot? false
                   :tunniste :nimi
                   :muutos #(do
                              (reset! tallenna-painettu false)
                              (reset! grid-tiedot-atom (vals (grid/hae-muokkaustila %)))
                              (reset! virheet-atom (grid/hae-virheet %)))
                   ;; Lisätään 2 riviä gridin päätteeksi
                   :rivi-jalkeen-fn (fn [rivit]
                                      ^{:luokka "yhteenveto"}
                                      yhteenveto-rivit)}
        [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "30%" :muokattava? (constantly false)}
         {:otsikko "Pysyvät muutokset (€)" :nimi :pysyvat-muutokset :tyyppi :string :leveys "20%" :muokattava? (constantly false)}
         {:otsikko (str "Loka-joulukuu " (pvm/vuosi (first valittu-hoitokausi)) " (€)") :nimi :alkukausi :tyyppi :euro :leveys "20%" :muokattava? (constantly true) :tasaa :oikea}
         {:otsikko (str "Tammi-syyskuu " (pvm/vuosi (second valittu-hoitokausi)) " (€)") :nimi :loppukausi :tyyppi :euro :leveys "20%" :muokattava? (constantly true) :tasaa :oikea}
         {:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :string :leveys "20%" :muokattava? (constantly false) :tasaa :oikea}]
        taulukon-tiedot]]]

     [:div.row [:div.col-xs-12] [:hr]]

     [:div.row
      [:div.col-xs-12
       [:div.painikkeet
        [napit/yleinen-ensisijainen "Tallenna tiedot"
         #(do
            (reset! tallenna-painettu false)
            (e! (kust-tiedot/->TallennaKilpailutettavatHankinnat @grid-tiedot-atom)))
         {:disabled (or tallennus-kesken? false)}]]]]]))

(defn rahavaraukset [e! app]
  (let [rahavaraukset (get-in app [:kustannussuunnitelma :rahavaraukset])
        yht (apply + (map (fn [rivi]
                            (:summa rivi 0)) rahavaraukset))
        yht-indeksikorjattu (apply + (map (fn [rivi]
                                            (:summa-indeksikorjattu rivi 0)) rahavaraukset))
        yhteenveto-rivi [[^{:luokka "kustannukset-yhteenveto"}
                           {:teksti "Yhteensä" :luokka "yhteensa"}
                           {:teksti (fmt/euro false yht) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                           {:teksti (fmt/euro false yht-indeksikorjattu) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]]]
   [:div.row.kustannussuunnitelma-osio
    [otsikkotiedot e! app "Rahavaraukset" {:div1 true :div2 false :div3 false :div4 true}]
    [:div.row
     [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]
    [:div.row
     [:div.col-xs-12
      [grid/grid {:otsikko ""
                  :muokkaa-aina false
                  :voi-muokata? false
                  :muokattava? (constantly false)
                  :voi-poistaa? (constantly false)
                  :voi-lisata? false
                  :voi-kumota? false
                  :piilota-toiminnot? false
                  :tunniste :nimi

                  ;; Lisätään yhteenveto rivi gridin päätteeksi
                  :rivi-jalkeen-fn (fn [rivit]
                                     ^{:luokka "yhteenveto"}
                                     yhteenveto-rivi)}
       [{:otsikko "Rahavaraus" :nimi :nimi :tyyppi :string :leveys "70%" :muokattava? (constantly false)}
        {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "15%" :tyyppi :euro :tasaa :oikea :fmt #(fmt/euro false %)}
        {:otsikko "Indeksikorjattu (€)" :nimi :summa-indeksikorjattu :leveys "15%" :tyyppi :euro :tasaa :oikea :fmt #(when % (fmt/euro false %))}]
       rahavaraukset]]]]))

(defn kustannussuunnitelma [e! {:keys [tallennus-kesken? valittu-hoitokausi] :as app}]
  (let [urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        urakan-kesto-vuosina (- urakan-loppuvuosi urakan-alkuvuosi)
        hoitokaudet (into [] (range urakan-alkuvuosi (+ urakan-kesto-vuosina urakan-alkuvuosi)))]
    [:div
     [:div.row
      [:div.col-xs-12.col-md-6
       [:h1 "Hoitovuoden alun tavoitehinta"]
       [:div (-> @tila/yleiset :urakka :nimi)]]
      [:div.col-xs-12.col-md-6
       [yleiset/linkki "Muokkaa tarjouksen tietoja"
        #(siirtymat/siirry-annettuun-valilehteen @nav/valittu-hallintayksikko-id (-> @tila/yleiset :urakka :id)
           {:taso1 :urakat :taso2 :suunnittelu :taso3 :tarjous})
        {:style {:float "right"
                 :line-height "2rem"}}]]]
     [:div.row {:style {:margin-top "1rem"}}
      [:div.col-xs-12.col-md-3
       [:span.caption-small-strong.alasveto-label "Hoitovuosi"]
       [yleiset/livi-pudotusvalikko {:valinta (pvm/vuosi (first valittu-hoitokausi))
                                     :vayla-tyyli? true
                                     :disabled tallennus-kesken?
                                     :data-cy "hoitokausi-valinta"
                                     :valitse-fn #(e! (kust-tiedot/->ValitseHoitokausiKustannussuunnitelmaan %))
                                     :format-fn #(fmt/hoitokauden-jarjestysluku-ja-vuodet % hoitokaudet "Hoitovuosi")
                                     :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
        hoitokaudet]]]
     [kilpailutettavat-hankinnat e! app]
     [rahavaraukset e! app]]))

(defn nakyma* [e! {:keys [tallennus-kesken? valittu-hoitokausi] :as app}]
  (komp/luo
    (komp/sisaan #(e! (kust-tiedot/->HaeKustannussuunnitelmanTiedot)))
    (fn [e! app]
      [:div
       (if (:haku-kaynnissa? app)
         [yleiset/ajax-loader {:style {:margin-top "1rem"}}]
         [kustannussuunnitelma e! app])
       [debug/debug app]])))

(defn kustannussuunitelma []
  (tuck/tuck tila/tarjous-kustannussuunnitelma nakyma*))
