(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuunnitelma-johto-ja-hallintokorvaus
  "Tämä komponentti näyttää Johto- ja hallintokorvaukset kustannussuunnitelmassa.
  Johto- ja hallintokorvaukset koostuvat toimenkuvista, joiden syöttämisen tarkkuus vaihtelee urakan alkuvuoden perusteella.
  -19 - 22 vuosina alkavat urakat syöttävät tunnit ja tuntihinnat jokaiselle toimenkuvalle.
  -23 - 24 vuosina alkavat urakat syöttävät kuukausisumman toimenkuvalle.
  -25 ja sitä myöhemmät urakat syöttävät vain kuukausisumman.
  Käyttöliittymä yksinkertaistuu vuosien myötä, koska tarkkuus vähenee.
  "
  (:require [tuck.core :as tuck]
            [harja.validointi :as v]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tyokalut.yleiset :as tyokalut]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as kust-tiedot]
            [harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.yhteiset :as yhteiset]))

(defonce vetolaatikko-auki (atom false))

(defn johto-ja-hallintokorvaus [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous
                                           kustannussuunnitelma urakan-alkuvuosi] :as app}]
  (let [johto-ja-hallintokorvaukset (:johto-ja-hallintokorvaukset kustannussuunnitelma)
        viimeisin-muokkaus (:viimeisin-muokkaus (first johto-ja-hallintokorvaukset))
        viimeisin-muokkaaja (:viimeisin-muokkaaja (first johto-ja-hallintokorvaukset))
        tarjous-johto-ja-hallintokorvaukset (filter #(= (:osio %) "johto-ja-hallintokorvaus") (:tarjous tarjous))
        valittu-vuosi (pvm/vuosi (first valittu-hoitokausi))
        hoitovuosittaiset-arvot (flatten (map :hoitovuosittaiset-arvot tarjous-johto-ja-hallintokorvaukset))
        valitun-vuoden-arvot (filter #(= (:vuosi %) valittu-vuosi) hoitovuosittaiset-arvot)
        tarjouksen-maara (apply + (map :summa valitun-vuoden-arvot))
        pysyvamuutos-maara 0 ;; Toteutus kesken
        vahvistettu? (:vahvistettu? kustannussuunnitelma)
        voi-muokata? (not vahvistettu?)
        yht (apply + (map (fn [rivi]
                            (:summa rivi 0)) johto-ja-hallintokorvaukset))
        yht-indeksikorjattu (apply + (map (fn [rivi]
                                            (:summa_indeksikorjattu rivi 0)) johto-ja-hallintokorvaukset))
        kirjaamatta (tyokalut/round2 2 (- tarjouksen-maara yht))
        kirjaamatta-luokka (if (= 0 kirjaamatta) "yhteensa" "yhteensa-punainen")
        kirjaamatta-rivi (when-not vahvistettu? [^{:luokka "kustannukset-yhteenveto"}
                                                 {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                                                 {:teksti (fmt/euro-opt false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                                                 {:teksti "" :luokka kirjaamatta-luokka}])

        yhteenveto-rivi [[^{:luokka "kustannukset-yhteenveto"}
                          {:teksti "Yhteensä" :luokka "yhteensa"}
                          {:teksti (fmt/euro-opt false yht) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                          {:teksti (if-not (= 0 yht-indeksikorjattu)
                                     (fmt/euro-opt false yht-indeksikorjattu)
                                     "-")
                           :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]
                         kirjaamatta-rivi]
        _ (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom johto-ja-hallintokorvaukset)


        ;; Kokeillaan koostaa vetolaatikot
        vetolaatikot-2024
        [:div
         [kentat/tee-kentta {:tyyppi :checkbox
                             :teksti "Suunnittele kuukausittain"
                             :disabled? vahvistettu?
                             :valitse! #(do (js/console.log "Checkbox painettu")
                                          (reset! vetolaatikko-auki (not @vetolaatikko-auki)))}
          @vetolaatikko-auki]
         [:div.vetolaatikko-border {:style {:border-left "4px solid lightblue" :margin-top "16px" :padding-left "18px"}}
          [grid/grid {:otsikko ""
                      :luokat ["matala-panel"]
                      :muokkaa-aina voi-muokata?
                      :voi-muokata? voi-muokata?
                      :muokattava? (constantly voi-muokata?)
                      :voi-poistaa? (constantly false)
                      :voi-lisata? false
                      :voi-kumota? false
                      :piilota-toiminnot? false
                      :tunniste :kalenterikuukausi
                      :muutos #(do
                                 (reset! yhteiset/tallenna-painettu false)
                                 (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom (vals (grid/hae-muokkaustila %)))
                                 (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset (vals (grid/hae-muokkaustila %))))
                                 (reset! yhteiset/virheet-atom (grid/hae-virheet %)))
                      :rivin-luokka (fn [_] "korkea")}
           [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "60%"
             :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
            {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "20%" :tyyppi :euro :tasaa :oikea
             :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
            {:otsikko "Indeksikorjattu (€)" :nimi :summa_indeksikorjattu :leveys "20%" :tyyppi :euro :tasaa :oikea
             :fmt #(if-not (= 0 yht-indeksikorjattu) (fmt/euro-opt false %) "-") :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
           johto-ja-hallintokorvaukset]]]

        ]
    [:div#johto-ja-hallintokorvaus-elementti.row.kustannussuunnitelma-osio.kapea-osio
     [yhteiset/otsikkotiedot e! app "Johto- ja hallintokorvaus" tarjouksen-maara pysyvamuutos-maara yht yht-indeksikorjattu
      {:div1 true :div2 false :div3 (if (< valittu-vuosi yhteiset/rajavuosi) true false) :div4 true} valittu-vuosi]

     [:div.row [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]

     (when-not vahvistettu?
       [:div
        [:div.row
         [:div.col-xs-12.body-text "Harja luo kulut kuukausille, kun tallennat tiedot."]]
        (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
          #(e! (kust-tiedot/->TallennaJohtoJaHallintokorvaukset @yhteiset/grid-johto-ja-hallintokorvaukset-atom))
          #(e! (kust-tiedot/->JaaJohtoJaHallintokorvauksetTasan tarjouksen-maara "johto-ja-hallintokorvaus-elementti")))])

     [:div.row
      [:div.col-xs-12
       (when (<= urakan-alkuvuosi 2022)
         [:div (str "2022 vs:" urakan-alkuvuosi)])
       ;; -24 asti
       (when (<= urakan-alkuvuosi 2024)
         [grid/grid {:otsikko ""
                     :luokat ["matala-panel"]
                     :muokkaa-aina voi-muokata?
                     :voi-muokata? voi-muokata?
                     :muokattava? (constantly voi-muokata?)
                     :voi-poistaa? (constantly false)
                     :voi-lisata? false
                     :voi-kumota? false
                     :piilota-toiminnot? false
                     :tunniste :kalenterikuukausi
                     :muutos #(do
                                (reset! yhteiset/tallenna-painettu false)
                                (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom (vals (grid/hae-muokkaustila %)))
                                (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset (vals (grid/hae-muokkaustila %))))
                                (reset! yhteiset/virheet-atom (grid/hae-virheet %)))
                     ;; Lisätään yhteenveto rivi gridin päätteeksi
                     :rivi-jalkeen-fn (fn [rivit]
                                        ^{:luokka "yhteenveto"}
                                        yhteenveto-rivi)
                     :rivin-luokka (fn [_] "korkea")
                     :vetolaatikot vetolaatikot-2024}
          [{:otsikko "" :tyyppi :vetolaatikon-tila :leveys "5%" :muokattava? (constantly false)}
           {:otsikko "Toimenkuva" :nimi :toimenkuva :tyyppi :string :leveys "60%"
            :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
           {:otsikko "Tarjouksen määrä (€)" :nimi :tarjous-summa :leveys "20%" :tyyppi :euro :tasaa :oikea
            :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
           {:otsikko "Suunniteltu määrä (€)" :nimi :suunniteltu-summa :leveys "20%" :tyyppi :euro :tasaa :oikea
            :fmt #(if-not (= 0 yht-indeksikorjattu) (fmt/euro-opt false %) "-") :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
          johto-ja-hallintokorvaukset])

       ;-25 eteenäin
       [grid/grid {:otsikko ""
                   :luokat ["matala-panel"]
                   :muokkaa-aina voi-muokata?
                   :voi-muokata? voi-muokata?
                   :muokattava? (constantly voi-muokata?)
                   :voi-poistaa? (constantly false)
                   :voi-lisata? false
                   :voi-kumota? false
                   :piilota-toiminnot? false
                   :tunniste :kalenterikuukausi
                   :muutos #(do
                              (reset! yhteiset/tallenna-painettu false)
                              (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom (vals (grid/hae-muokkaustila %)))
                              (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset (vals (grid/hae-muokkaustila %))))
                              (reset! yhteiset/virheet-atom (grid/hae-virheet %)))
                   ;; Lisätään yhteenveto rivi gridin päätteeksi
                   :rivi-jalkeen-fn (fn [rivit]
                                      ^{:luokka "yhteenveto"}
                                      yhteenveto-rivi)
                   :rivin-luokka (fn [_] "korkea")}
        [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "60%"
          :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
         {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "20%" :tyyppi :euro :tasaa :oikea
          :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
         {:otsikko "Indeksikorjattu (€)" :nimi :summa_indeksikorjattu :leveys "20%" :tyyppi :euro :tasaa :oikea
          :fmt #(if-not (= 0 yht-indeksikorjattu) (fmt/euro-opt false %) "-") :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
        johto-ja-hallintokorvaukset]]]

     (when-not vahvistettu?
       [:div

        (when (:johto-ja-hallintokorvaukset-virheet kustannussuunnitelma)
          [:div.row {:style {:margin-bottom "1rem"}}
           [:div.col-xs-12
            [yleiset/info-laatikko :varoitus (:johto-ja-hallintokorvaukset-virheet kustannussuunnitelma) nil nil {:sulje-nappi-id (gensym)}]]])

        (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
          #(e! (kust-tiedot/->TallennaJohtoJaHallintokorvaukset @yhteiset/grid-johto-ja-hallintokorvaukset-atom))
          #(e! (kust-tiedot/->JaaJohtoJaHallintokorvauksetTasan tarjouksen-maara "johto-ja-hallintokorvaus-elementti")))])]))
