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

(defn kilpailutettavat-hankinnat [e! {:keys [tallennus-kesken? valittu-hoitokausi tarjous] :as app}]
  (let [urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))

        tarjous-hankintakustannukset (filter #(= (:osio %) "hankintakustannukset") (:tarjous tarjous))
        tarjous-hankintakustannukset (:yhteensa (first tarjous-hankintakustannukset))
        kilpailutettavat-hankinnat (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])
        kilpailutettavat-hankinnat-yhteensa (:yhteensa (last kilpailutettavat-hankinnat))
        kilpailutettavat-hankinnat-yhteensa-indeksikorjattu (:yhteensa-indeksikorjattu (last kilpailutettavat-hankinnat))


        taulukon-tiedot (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])

        yht-alkukausi (apply + (map #(get-in % [:alkukausi]) taulukon-tiedot))
        yht-loppukausi (apply + (map #(get-in % [:loppukausi]) taulukon-tiedot))
        yht (+ yht-alkukausi yht-loppukausi)

        yhteenveto-rivit [{:teksti "Yhteensä" :luokka "yhteensa lihavoitu" :yhteenveto-vayla true}
                          {:teksti "Ei muutoksia" :luokka "yhteensa lihavoitu" :yhteenveto-vayla true}
                          {:teksti (fmt/euro false yht-alkukausi) :luokka "yhteensa lihavoitu" :yhteenveto-vayla true :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                          {:teksti (fmt/euro false yht-loppukausi) :luokka "yhteensa lihavoitu" :yhteenveto-vayla true :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                          {:teksti (fmt/euro false yht) :luokka "yhteensa lihavoitu" :yhteenveto-vayla true :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]]
    [:div.kustannussuunnitelma-osio
     [:div.row
      [:div.col-xs-12
       [:h2 "Kilpailutettavat hankinnat"]
       [:div.body-text (fmt/hoitokauden-jarjestysluku-ja-vuodet (pvm/vuosi (first valittu-hoitokausi)) hoitovuodet "Hoitovuosi")]]]
     [:div.row
      [:div.col-xs-12.col-md-3
       [:div.small-text.bold "Tarjouksen määrä"]
       [:div.body-text tarjous-hankintakustannukset]]

      [:div.col-xs-12.col-md-3
       [:div.small-text.bold "Pysyvät muutokset"]
       [:div.body-text "Ei muutoksia"]
       [:div.body-text "Siirry muutoksiin"]]

      [:div.col-xs-12.col-md-3
       [:div.small-text.bold "Yhteensä"]
       [:div.body-text (if kilpailutettavat-hankinnat-yhteensa (fmt/euro true kilpailutettavat-hankinnat-yhteensa) "0,00 €")]]

      [:div.col-xs-12.col-md-3
       [:div.small-text.bold "Indeksikorjattu"]
       [:div.body-text (if kilpailutettavat-hankinnat-yhteensa-indeksikorjattu (fmt/euro true kilpailutettavat-hankinnat-yhteensa-indeksikorjattu) "0,00 €") ]]]

     [:div.row
      [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]
     [:div.row
      [:div.col-xs-12.body-text "Erittele " [:strong "yhteensä-summa"] " toimenpiteille."]]
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
                   :rivi-jalkeen-fn (fn [rivit]
                                      ^{:luokka "yhteenveto"}
                                      yhteenveto-rivit)}
        [{:otsikko "Toimenpide"
          :nimi :nimi
          :tyyppi :string
          :leveys "30%"
          :muokattava? (constantly false)}
         {:otsikko "Pysyvät muutokset (€)"
          :nimi :pysyvat-muutokset
          :tyyppi :string
          :leveys "20%"
          :muokattava? (constantly false)}
         {:otsikko "Loka-joulukuu 2025 (€)"
          :nimi :alkukausi
          :tyyppi :string
          :leveys "20%"
          :muokattava? (constantly true)
          :tasaa :oikea}
         {:otsikko "Tammi-joulukuu 2025 (€)"
          :nimi :loppukausi
          :tyyppi :string
          :leveys "20%"
          :muokattava? (constantly true)
          :tasaa :oikea}
         {:otsikko "Yhteensä (€)"
          :nimi :yhteensa
          :tyyppi :string
          :leveys "20%"
          :muokattava? (constantly false)
          :tasaa :oikea}]
        taulukon-tiedot]]]
     [:hr]
     [:div.painikkeet
      [napit/yleinen-ensisijainen "Tallenna tiedot"
       #(do
          (reset! tallenna-painettu false)
          (e! (kust-tiedot/->TallennaKilpailutettavatHankinnat @grid-tiedot-atom)))
       {:disabled (or tallennus-kesken? false)}]]

     ]))

(defn rahavaraukset [e! app]
  [:div.row.kustannussuunnitelma-osio
   [:div.col-xs-12
    [:h2 "Rahavaraukset"]]])

(defn kustannussuunnitelma [e! {:keys [tallennus-kesken? valittu-hoitokausi] :as app}]
  (let [urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        urakan-kesto-vuosina (- urakan-loppuvuosi urakan-alkuvuosi)
        hoitokaudet (into [] (range urakan-alkuvuosi (+ urakan-kesto-vuosina urakan-alkuvuosi)))]
    [:div
     [debug/debug app]
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
       [kustannussuunnitelma e! app]])))

(defn kustannussuunitelma []
  (tuck/tuck tila/tarjous-kustannussuunnitelma nakyma*))
