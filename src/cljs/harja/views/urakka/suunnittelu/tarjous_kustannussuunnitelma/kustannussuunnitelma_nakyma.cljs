(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuunnitelma-nakyma
  "Uusi kustannusten suunnittelu"
  (:require
            [tuck.core :as tuck]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tyokalut.yleiset :as tyokalut]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as kust-tiedot]
            [harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuunnitelma-johto-ja-hallintokorvaus :as jjh]
            [harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as muutokset-tiedot]))

(defn kilpailutettavat-hankinnat [e! {:keys [tallennus-kesken? valittu-hoitokausi tarjous kustannussuunnitelma
                                             tulevaisuudessa-arvoja? viimeinen-hoitovuosi? onko-hankinnat-muutoksia?] :as app}]
  (if (nil? (get-in kustannussuunnitelma [:kilpailutettavat-hankinnat :toimenpiteet]))
    [yleiset/ajax-loader-pieni "Ladataan..."]
    (let [{:keys [kilpailutettavat-hankinnat vahvistettu?
                  kilpailutettavat-hankinnat-virheet
                  pysyvat-muutokset-maara]} kustannussuunnitelma
          tarjous-hankintakustannukset (filter #(= (:osio %) "hankintakustannukset") (:tarjous tarjous))
          valittu-vuosi (pvm/vuosi (first valittu-hoitokausi))
          tarjouksen-maara (:summa (first (filter #(= (:vuosi %) valittu-vuosi)
                                            (:hoitovuosittaiset-arvot (first tarjous-hankintakustannukset)))))
          toimenpiteet (:toimenpiteet kilpailutettavat-hankinnat)
          taulukon-tiedot (butlast toimenpiteet) ;; Jätetään yhteenvetorivi pois tässä kohdassa
          _ (reset! yhteiset/grid-hankinnat-atom taulukon-tiedot)

          yht-alkukausi (:alkukausi (last toimenpiteet))
          yht-loppukausi (:loppukausi (last toimenpiteet))
          yht (:yhteensa (last toimenpiteet))

          viimeisin-muokkaus (:viimeisin-muokkaus (last toimenpiteet))
          viimeisin-muokkaaja (:viimeisin-muokkaaja (last toimenpiteet))

          kirjaamatta (tyokalut/round2 2 (- tarjouksen-maara yht))
          kirjaamatta-luokka (if (= 0 kirjaamatta) "yhteensa" "yhteensa-punainen")
          kirjaamatta-rivi (when (and (not vahvistettu?) (>= valittu-vuosi yhteiset/rajavuosi))
                             [^{:luokka "kustannukset-yhteenveto"}
                              {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                              {:teksti "" :luokka kirjaamatta-luokka}
                              {:teksti "" :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                              {:teksti "" :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                              {:teksti (fmt/euro-opt false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}])

          yhteenveto-rivit [[^{:luokka "kustannukset-yhteenveto"}
                             {:teksti "Yhteensä" :luokka "yhteensa korkea"}
                             {:teksti "Ei muutoksia" :luokka "yhteensa-ei-korostusta korkea"}
                             {:teksti (fmt/euro-opt false yht-alkukausi) :luokka "yhteensa korkea" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                             {:teksti (fmt/euro-opt false yht-loppukausi) :luokka "yhteensa korkea" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                             {:teksti (fmt/euro-opt false yht) :luokka "yhteensa korkea" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]
                            kirjaamatta-rivi]

          kilpailutettavat-hankinnat (:toimenpiteet kilpailutettavat-hankinnat)
          kilpailutettavat-hankinnat-yhteensa (:yhteensa (last kilpailutettavat-hankinnat))
          kilpailutettavat-hankinnat-yhteensa-indeksikorjattu (:yhteensa-indeksikorjattu (last kilpailutettavat-hankinnat))]

      [:div#kilpailutettavat-hankinnat-elementti.kustannussuunnitelma-osio.osio-976
       [yhteiset/otsikkotiedot valittu-hoitokausi kustannussuunnitelma "Kilpailutettavat hankinnat" tarjouksen-maara pysyvat-muutokset-maara
        kilpailutettavat-hankinnat-yhteensa kilpailutettavat-hankinnat-yhteensa-indeksikorjattu
        {:div1 true :div2 true :div3 true :div4 true} valittu-vuosi]

       [:div.row
        [:div.row
         [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]
        (when-not vahvistettu?
          [:div.row
           [:div.col-xs-12.body-text "Erittele " [:strong "yhteensä-summa"] " toimenpiteille."]])]

       (when-not vahvistettu?
         (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
           #(e! (kust-tiedot/->TallennaKilpailutettavatHankinnat @yhteiset/grid-hankinnat-atom false))
           nil
           (when-not viimeinen-hoitovuosi?
             #(e! (kust-tiedot/->TallennaKilpailutettavatHankinnat @yhteiset/grid-hankinnat-atom true)))
           tulevaisuudessa-arvoja?
           onko-hankinnat-muutoksia?))

       [:div.row
        [:div.col-xs-12
         [grid/grid (merge (yhteiset/grid-perusasetukset (if vahvistettu? false true)  :nimi)
                      {:otsikko ""
                       :jarjestys :jarjestys
                       :muutos #(do
                                  (reset! yhteiset/tallenna-painettu false)
                                  (reset! yhteiset/grid-hankinnat-atom (vals (grid/hae-muokkaustila %)))
                                  (e! (kust-tiedot/->PaivitaKilpailutettavatHankinnat (vals (grid/hae-muokkaustila %))))
                                  (reset! yhteiset/virheet-atom (grid/hae-virheet %))
                                  (e! (kust-tiedot/->AsetaHankinnatMuutos)))
                       ;; Lisätään 2 riviä gridin päätteeksi
                       :rivi-jalkeen-fn (fn [rivit]
                                          ^{:luokka "yhteenveto"}
                                          yhteenveto-rivit)})
          [{:otsikko "Toimenpide" :nimi :toimenpide-nimi :tyyppi :string :leveys "30%" :muokattava? (constantly false)
            :otsikkorivi-luokka "korkea"}
           {:otsikko "Pysyvät muutokset (€)" :nimi :pysyvat-muutokset :tyyppi :string
            :leveys "20%" :muokattava? (constantly false)}
           {:otsikko (str "Loka-joulukuu " (pvm/vuosi (first valittu-hoitokausi)) " (€)") :nimi :alkukausi :tyyppi :euro
            :leveys "20%" :validoi [[:ei-tyhja "Anna positiivinen summa."]] :vaadi-ei-negatiivinen? true
            :muokattava? (constantly (if vahvistettu? false true)) :tasaa :oikea :otsikkorivi-luokka "korkea"}
           {:otsikko (str "Tammi-syyskuu " (pvm/vuosi (second valittu-hoitokausi)) " (€)")
            :nimi :loppukausi :tyyppi :euro :vaadi-ei-negatiivinen? true
            :leveys "20%" :muokattava? (constantly (if vahvistettu? false true)) :tasaa :oikea :otsikkorivi-luokka "korkea"}
           {:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :string :fmt #(fmt/euro-opt false %) :leveys "20%" :muokattava? (constantly false)
            :tasaa :oikea :otsikkorivi-luokka "korkea"}]
          taulukon-tiedot]]]

       (when-not vahvistettu?
         [:div
          (when kilpailutettavat-hankinnat-virheet
            [:div.row {:style {:margin-bottom "1rem"}}
             [:div.col-xs-12
              [yleiset/info-laatikko :varoitus kilpailutettavat-hankinnat-virheet nil nil {:sulje-nappi-id (gensym)}]]])
          (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
            #(e! (kust-tiedot/->TallennaKilpailutettavatHankinnat @yhteiset/grid-hankinnat-atom false))
            nil
            (when-not viimeinen-hoitovuosi?
              #(e! (kust-tiedot/->TallennaKilpailutettavatHankinnat @yhteiset/grid-hankinnat-atom true)))
            tulevaisuudessa-arvoja?
            onko-hankinnat-muutoksia?)])])))

(defn rahavaraukset [e! {:keys [valittu-hoitokausi tarjous kustannussuunnitelma] :as app}]
  (if (nil? (:rahavaraukset kustannussuunnitelma))
    [yleiset/ajax-loader-pieni "Ladataan..."]
    (let [rahavaraukset (:rahavaraukset kustannussuunnitelma)
          tarjous-rahavaraukset (filter #(= (:osio %) "tavoitehintaiset-rahavaraukset") (:tarjous tarjous))
          valittu-vuosi (pvm/vuosi (first valittu-hoitokausi))
          hoitovuosittaiset-arvot (flatten (map :hoitovuosittaiset-arvot tarjous-rahavaraukset))
          valitun-vuoden-arvot (filter #(= (:vuosi %) valittu-vuosi) hoitovuosittaiset-arvot)
          tarjouksen-maara (apply + (map :summa valitun-vuoden-arvot))
          pysyvamuutos-maara 0 ;; Toteutus kesken
          suunniteltu-yht (apply + (map (fn [rivi] (:suunniteltu-summa rivi 0)) rahavaraukset))
          suunniteltu-yht-indeksikorjattu (apply + (map (fn [rivi] (:suunniteltu-summa-indeksikorjattu rivi 0)) rahavaraukset))
          ;; Yhteenvetorivillä on eri määrä kolumneja, jos rajavuosi ei täyty
          yhteenveto-rivi (if (< valittu-vuosi yhteiset/rajavuosi)
                            [[^{:luokka "kustannukset-yhteenveto"}
                              {:teksti "Yhteensä" :luokka "yhteensa korkea"}
                              {:teksti (fmt/euro-opt false tarjouksen-maara) :luokka "yhteensa korkea" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                              {:teksti (fmt/euro-opt false suunniteltu-yht) :luokka "yhteensa korkea" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                              {:teksti (if-not (= 0 suunniteltu-yht-indeksikorjattu)
                                         (fmt/euro-opt false suunniteltu-yht-indeksikorjattu)
                                         "-")
                               :luokka "yhteensa korkea" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]]
                            [[^{:luokka "kustannukset-yhteenveto"}
                              {:teksti "Yhteensä" :luokka "yhteensa korkea"}
                              {:teksti (fmt/euro-opt false suunniteltu-yht) :luokka "yhteensa korkea" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                              {:teksti (if-not (= 0 suunniteltu-yht-indeksikorjattu)
                                         (fmt/euro-opt false suunniteltu-yht-indeksikorjattu)
                                         "-")
                               :luokka "yhteensa korkea" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]])]

      [:div#rahavaraukset-elementti.row.kustannussuunnitelma-osio.kapea-osio
       [yhteiset/otsikkotiedot valittu-hoitokausi kustannussuunnitelma "Rahavaraukset" tarjouksen-maara pysyvamuutos-maara suunniteltu-yht suunniteltu-yht-indeksikorjattu
        {:div1 true :div2 false :div3 (if (< valittu-vuosi yhteiset/rajavuosi) true false) :div4 true} valittu-vuosi]
       [:div.row
        [:div.col-xs-12
         [grid/grid (merge (yhteiset/grid-perusasetukset false :nimi)
                      {:otsikko "Kustannusten erittely"
                       ;; Lisätään yhteenveto rivi gridin päätteeksi
                       :rivi-jalkeen-fn (fn [rivit]
                                          ^{:luokka "yhteenveto"}
                                          yhteenveto-rivi)})
          ;; Rajavuotta aiemmilla vuosilla näytetään erilainen taulukko
          (if (< valittu-vuosi yhteiset/rajavuosi)
            [{:otsikko "Rahavaraus" :nimi :nimi :tyyppi :string :leveys "60%"
              :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
             {:otsikko "Tarjouksen määrä (€)" :nimi :tarjous-summa :leveys "20%" :tyyppi :euro :tasaa :oikea
              :fmt #(when % (fmt/euro-opt false %)) :otsikkorivi-luokka "korkea"}
             {:otsikko "Suunniteltu kustannus (€)" :nimi :suunniteltu-summa :leveys "20%" :tyyppi :euro :tasaa :oikea
              :fmt #(when % (fmt/euro-opt false %)) :otsikkorivi-luokka "korkea" :solun-luokka (fn [arvo rivi]
                                                                                                 (when (> arvo (:tarjous-summa rivi))
                                                                                                   "rajoitus-ylitetty"))}
             {:otsikko "Indeksikorjattu (€)" :nimi :suunniteltu-summa-indeksikorjattu :leveys "20%" :tyyppi :euro :tasaa :oikea
              :fmt #(if-not (= 0 suunniteltu-yht-indeksikorjattu) (fmt/euro-opt false %) "-") :otsikkorivi-luokka "korkea"}]

            [{:otsikko "Rahavaraus" :nimi :nimi :tyyppi :string :leveys "60%"
              :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
             {:otsikko "Suunniteltu kustannus (€)" :nimi :suunniteltu-summa :leveys "20%" :tyyppi :euro :tasaa :oikea
              :fmt #(when % (fmt/euro-opt false %)) :otsikkorivi-luokka "korkea"}
             {:otsikko "Indeksikorjattu (€)" :nimi :suunniteltu-summa-indeksikorjattu :leveys "20%" :tyyppi :euro :tasaa :oikea
              :fmt #(if-not (= 0 suunniteltu-yht-indeksikorjattu) (fmt/euro-opt false %) "-") :otsikkorivi-luokka "korkea"}])
          rahavaraukset]]]])))

(defn erillishankinnat [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous kustannussuunnitelma
                                   tulevaisuudessa-arvoja? viimeinen-hoitovuosi? onko-erillishankinnat-muutoksia?] :as app}]
  (if (nil? (get-in app [:kustannussuunnitelma :erillishankinnat]))
    [yleiset/ajax-loader-pieni "Ladataan..."]
    (let [{:keys [erillishankinnat vahvistettu?]} kustannussuunnitelma
          viimeisin-muokkaus (:viimeisin-muokkaus (first erillishankinnat))
          viimeisin-muokkaaja (:viimeisin-muokkaaja (first erillishankinnat))
          tarjous-erillishankinnat (first (filter #(= (:osio %) "erillishankinnat") (:tarjous tarjous)))
          valittu-vuosi (pvm/vuosi (first valittu-hoitokausi))
          hoitovuosittaiset-arvot (:hoitovuosittaiset-arvot tarjous-erillishankinnat)
          tarjouksen-maara (:summa (first (filter #(= (:vuosi %) valittu-vuosi) hoitovuosittaiset-arvot)))
          pysyvamuutos-maara 0 ;; Toteutus kesken
          voi-muokata? (not vahvistettu?)
          suunniteltu-yht (apply + (map (fn [rivi] (:summa rivi 0)) erillishankinnat))
          yht-indeksikorjattu (apply + (map (fn [rivi] (:summa_indeksikorjattu rivi 0)) erillishankinnat))
          kirjaamatta (tyokalut/round2 2 (- tarjouksen-maara suunniteltu-yht))
          kirjaamatta-luokka (if (= 0.00 (tyokalut/round2 2 kirjaamatta)) "yhteensa" "yhteensa-punainen")
          kirjaamatta-rivi (when (and (not vahvistettu?) (>= valittu-vuosi yhteiset/rajavuosi))
                             [^{:luokka "kustannukset-yhteenveto"}
                              {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                              {:teksti (fmt/euro-opt false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                              {:teksti "" :luokka kirjaamatta-luokka}])

          yhteenveto-rivi [[^{:luokka "kustannukset-yhteenveto"}
                            {:teksti "Yhteensä" :luokka "yhteensa"}
                            {:teksti (fmt/euro-opt false suunniteltu-yht) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                            {:teksti (if-not (= 0 yht-indeksikorjattu)
                                       (fmt/euro-opt false yht-indeksikorjattu)
                                       "-")
                             :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]
                           kirjaamatta-rivi]
          _ (reset! yhteiset/grid-erillishankinnat-atom erillishankinnat)]

      [:div#erillishankinnat-elementti.row.kustannussuunnitelma-osio.kapea-osio
       [yhteiset/otsikkotiedot valittu-hoitokausi kustannussuunnitelma "Erillishankinnat" tarjouksen-maara pysyvamuutos-maara suunniteltu-yht yht-indeksikorjattu
        {:div1 true :div2 false :div3 (if (< valittu-vuosi yhteiset/rajavuosi) true false) :div4 true} valittu-vuosi]


       [:div.row [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]

       (when-not vahvistettu?
         [:div
          [:div.row
           [:div.col-xs-12.body-text "Harja luo kulut kuukausille, kun tallennat tiedot."]]
          (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
            #(e! (kust-tiedot/->TallennaErillishankinnat @yhteiset/grid-erillishankinnat-atom false))
            (when (and tarjouksen-maara (> tarjouksen-maara 0))
              #(e! (kust-tiedot/->JaaErillishankinnatTasan tarjouksen-maara "erillishankinnat-elementti")))
            (when-not viimeinen-hoitovuosi?
              #(e! (kust-tiedot/->TallennaErillishankinnat @yhteiset/grid-erillishankinnat-atom true)))
            tulevaisuudessa-arvoja?
            onko-erillishankinnat-muutoksia?)])

       [:div.row
        [:div.col-xs-12
         [grid/grid (merge (yhteiset/grid-perusasetukset voi-muokata? :kalenterikuukausi)
                     {:otsikko ""
                      :muutos #(do
                                 (reset! yhteiset/tallenna-painettu false)
                                 (reset! yhteiset/grid-erillishankinnat-atom (vals (grid/hae-muokkaustila %)))
                                 (e! (kust-tiedot/->PaivitaErillishankinnat (vals (grid/hae-muokkaustila %))))
                                 (reset! yhteiset/virheet-atom (grid/hae-virheet %))
                                 (e! (kust-tiedot/->AsetaErillishankinnatMuutos)))
                      ;; Lisätään yhteenveto rivi gridin päätteeksi
                      :rivi-jalkeen-fn (fn [rivit]
                                         ^{:luokka "yhteenveto"}
                                         yhteenveto-rivi)})
          [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "60%"
            :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
           {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "20%" :tyyppi :euro :tasaa :oikea
            :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly voi-muokata?)
            :otsikkorivi-luokka "korkea" :vaadi-ei-negatiivinen? true}
           {:otsikko "Indeksikorjattu (€)" :nimi :summa_indeksikorjattu :leveys "20%" :tyyppi :euro :tasaa :oikea
            :fmt #(if-not (= 0 yht-indeksikorjattu) (fmt/euro-opt false %) "-") :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
          erillishankinnat]]]

       (when-not vahvistettu?
         (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
           #(e! (kust-tiedot/->TallennaErillishankinnat @yhteiset/grid-erillishankinnat-atom false))
           (when (and tarjouksen-maara (> tarjouksen-maara 0))
             #(e! (kust-tiedot/->JaaErillishankinnatTasan tarjouksen-maara "erillishankinnat-elementti")))
           (when-not viimeinen-hoitovuosi?
             #(e! (kust-tiedot/->TallennaErillishankinnat @yhteiset/grid-erillishankinnat-atom true)))
           tulevaisuudessa-arvoja?
           onko-erillishankinnat-muutoksia?))])))

(defn hoidonjohtopalkkiot [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous kustannussuunnitelma
                                      tulevaisuudessa-arvoja? viimeinen-hoitovuosi? onko-hoidonjohtopalkkio-muutoksia?] :as app}]
  (if (nil? (get-in app [:kustannussuunnitelma :hoidonjohtopalkkiot]))
    [yleiset/ajax-loader-pieni "Ladataan..."]
    (let [{:keys [hoidonjohtopalkkiot vahvistettu?]} kustannussuunnitelma
          viimeisin-muokkaus (:viimeisin-muokkaus (first hoidonjohtopalkkiot))
          viimeisin-muokkaaja (:viimeisin-muokkaaja (first hoidonjohtopalkkiot))
          tarjous-hoidonjohtopalkkio (first (filter #(= (:osio %) "hoidonjohtopalkkio") (:tarjous tarjous)))
          valittu-vuosi (pvm/vuosi (first valittu-hoitokausi))
          hoitovuosittaiset-arvot (:hoitovuosittaiset-arvot tarjous-hoidonjohtopalkkio)
          tarjouksen-maara (:summa (first (filter #(= (:vuosi %) valittu-vuosi) hoitovuosittaiset-arvot)))
          pysyvamuutos-maara 0 ;; Toteutus kesken
          voi-muokata? (not vahvistettu?)
          suunniteltu-yht (apply + (map (fn [rivi]
                                          (:summa rivi 0)) hoidonjohtopalkkiot))
          suunniteltu-yht-indeksikorjattu (apply + (map (fn [rivi]
                                                          (:summa_indeksikorjattu rivi 0)) hoidonjohtopalkkiot))
          kirjaamatta (tyokalut/round2 2 (- tarjouksen-maara suunniteltu-yht))
          kirjaamatta-luokka (if (= 0 kirjaamatta) "yhteensa" "yhteensa-punainen")
          kirjaamatta-rivi (when (and (not vahvistettu?) (>= valittu-vuosi yhteiset/rajavuosi))
                             [^{:luokka "kustannukset-yhteenveto"}
                              {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                              {:teksti (fmt/euro-opt false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                              {:teksti "" :luokka kirjaamatta-luokka}])

          yhteenveto-rivi [[^{:luokka "kustannukset-yhteenveto"}
                            {:teksti "Yhteensä" :luokka "yhteensa"}
                            {:teksti (fmt/euro-opt false suunniteltu-yht) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                            {:teksti (if-not (= 0 suunniteltu-yht-indeksikorjattu)
                                       (fmt/euro-opt false suunniteltu-yht-indeksikorjattu)
                                       "-")
                             :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]
                           kirjaamatta-rivi]
          _ (reset! yhteiset/grid-hoidonjohtopalkkiot-atom hoidonjohtopalkkiot)]
      [:div#hoidonjohtopalkkio-elementti.row.kustannussuunnitelma-osio.kapea-osio
       [yhteiset/otsikkotiedot valittu-hoitokausi kustannussuunnitelma "Hoidonjohtopalkkiot" tarjouksen-maara pysyvamuutos-maara suunniteltu-yht
        suunniteltu-yht-indeksikorjattu {:div1 true :div2 false :div3 (if (< valittu-vuosi yhteiset/rajavuosi) true false) :div4 true} valittu-vuosi]

       [:div.row [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]

       (when-not vahvistettu?
         [:div
          [:div.row
           [:div.col-xs-12.body-text "Harja luo kulut kuukausille, kun tallennat tiedot."]]
          (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
            #(e! (kust-tiedot/->TallennaHoidonjohtopalkkiot @yhteiset/grid-hoidonjohtopalkkiot-atom false))
            (when (and tarjouksen-maara (> tarjouksen-maara 0))
              #(e! (kust-tiedot/->JaaHoidonjohtopalkkiotTasan tarjouksen-maara "hoidonjohtopalkkio-elementti")))
            (when-not viimeinen-hoitovuosi?
              #(e! (kust-tiedot/->TallennaHoidonjohtopalkkiot @yhteiset/grid-hoidonjohtopalkkiot-atom true)))
            tulevaisuudessa-arvoja?
            onko-hoidonjohtopalkkio-muutoksia?)])

       [:div.row
        [:div.col-xs-12
         [grid/grid (merge (yhteiset/grid-perusasetukset voi-muokata? :kalenterikuukausi)
                     {:otsikko ""
                      :muutos #(do
                                 (reset! yhteiset/tallenna-painettu false)
                                 (reset! yhteiset/grid-hoidonjohtopalkkiot-atom (vals (grid/hae-muokkaustila %)))
                                 (e! (kust-tiedot/->PaivitaHoidonjohtopalkkiot (vals (grid/hae-muokkaustila %))))
                                 (reset! yhteiset/virheet-atom (grid/hae-virheet %))
                                 (e! (kust-tiedot/->AsetaHoidonjohtopalkkioMuutos)))
                      ;; Lisätään yhteenveto rivi gridin päätteeksi
                      :rivi-jalkeen-fn (fn [rivit]
                                         ^{:luokka "yhteenveto"}
                                         yhteenveto-rivi)})
          [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "60%"
            :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
           {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "20%" :tyyppi :euro
            :tasaa :oikea :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly voi-muokata?)
            :otsikkorivi-luokka "korkea" :vaadi-ei-negatiivinen? true}
           {:otsikko "Indeksikorjattu (€)" :nimi :summa_indeksikorjattu :leveys "20%" :tyyppi :euro :tasaa :oikea
            :fmt #(if-not (= 0 suunniteltu-yht-indeksikorjattu) (fmt/euro-opt false %) "-") :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
          hoidonjohtopalkkiot]]]

       (when-not vahvistettu?
         (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
           #(e! (kust-tiedot/->TallennaHoidonjohtopalkkiot @yhteiset/grid-hoidonjohtopalkkiot-atom false))
           (when (and tarjouksen-maara (> tarjouksen-maara 0))
            #(e! (kust-tiedot/->JaaHoidonjohtopalkkiotTasan tarjouksen-maara "hoidonjohtopalkkio-elementti")))
           (when-not viimeinen-hoitovuosi?
             #(e! (kust-tiedot/->TallennaHoidonjohtopalkkiot @yhteiset/grid-hoidonjohtopalkkiot-atom true)))
           tulevaisuudessa-arvoja?
           onko-hoidonjohtopalkkio-muutoksia?))])))

(defn- pysyvat-muutokset-grid* [e! muutokset]
  [grid/grid
   {:tunniste :id
    :luokat ["kirjatut-muutokset-grid"]
    :tyhja "Ei muutoksia aiemmilta hoitovuosilta."
    :voi-lisata? false
    :voi-kumota? false
    :voi-poistaa? (constantly false)
    :voi-muokata? false
    :rivi-jalkeen-fn (fn [rivit]
                       (let [tavoitehinnan-muutokset-yhteensa (reduce + (map :tavoitehinnan-muutos rivit))
                             tavoitehinnan-muutokset-indeksikorjattu-yht (reduce + (map :tavoitehinnan-muutos-indeksikorjattu rivit))]
                         [{:teksti "Hoitovuoden alun tavoitehinnan muutokset yhteensä" :luokka "yhteensa" :sarakkeita 2}
                          {:teksti (fmt/euro-opt false true tavoitehinnan-muutokset-yhteensa) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti (fmt/euro-opt false true tavoitehinnan-muutokset-indeksikorjattu-yht) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                          {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}]))}

   ;; Taulukon kentät
   [{:otsikko "Muutoksen syy"
     :nimi :syy
     :tyyppi :string
     :leveys 40}

    {:otsikko "Voimassa alkaen"
     :nimi :voimassa_alkaen
     :tyyppi :pvm
     :leveys 15}

    {:otsikko "Tavoitehinnan muutos (€)"
     :nimi :tavoitehinnan-muutos
     :tyyppi :numero
     :fmt (partial fmt/euro-opt false true)
     :tasaa :oikea
     :leveys 15}

    {:otsikko "Indeksikorjattu"
     :nimi :tavoitehinnan-muutos-indeksikorjattu
     :tyyppi :numero
     :fmt (partial fmt/euro-opt false true)
     :tasaa :oikea
     :leveys 15}

    {:otsikko ""
     :nimi :toiminnot
     :tyyppi :komponentti
     :leveys 10
     :tasaa :oikea
     :komponentti (fn [rivi]
                    [napit/muokkaa "Muokkaa"
                     (fn []
                       (let [muutokset-e! (tuck/control tila/muutokset)]
                         ;; Siirry muutoslomakkeelle, käyttäen muutosten omaa tilakontrolleria
                         (muutokset-e! (muutokset-tiedot/->SiirryPysyvanMuutoksenMuokkauslomakkeelle rivi))))])}]
   muutokset])

(defn pysyvat-muutokset [e! {:keys [valittu-hoitokausi kustannussuunnitelma] :as app}]
  (let [muutokset (:pysyvat-muutokset kustannussuunnitelma)
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))]
    [:div#pysyvat-muutokset-elementti.row.kustannussuunnitelma-osio
     [:div.row
      [:div.col-xs-12
       [:h2 "Pysyvät muutokset"]
       [:div.flex-row {:style {:margin-top "-15px" :margin-bottom "12px"}}
        [:div.body-text (fmt/hoitokauden-jarjestysluku-ja-vuodet (pvm/vuosi (first valittu-hoitokausi)) hoitovuodet "Hoitovuosi")]
        (when (istunto/ominaisuus-kaytossa? :mhu-muutokset)
          [yleiset/linkki "Siirry muutokset-sivulle"
           #(e! (muutokset-tiedot/->SiirryMuutosNakymaan))])]
       [:div.row
        [:div "Hoitovuoden alun tavoitehintaan sisällytetään ennen indeksitarkistuksen tekemistä aikaisempina hoitovuosina tehtyjen pysyvien muutosten tavoitehintavaikutus."]]

       (if (istunto/ominaisuus-kaytossa? :mhu-muutokset)
         [pysyvat-muutokset-grid* e! muutokset]

         ;; Mhu-muutokset ei käytössä, näytetään placeholder
         [:div.row
          [:div {:style {:background "#f0f0f0" :padding "20px" :margin "12px 0"}}
           [:div {:style {:text-align "center" :font-size "40px" :color "#0066CC"}} [ikonit/ikoni-ja-teksti (ikonit/harja-icon-misc-maintenance) ""]]
           [:div {:style {:text-align "center" :font-size "15px"}} "Muutokset ovat vielä työn alla. Pahoittelemme aiheutuvaa haittaa."]]])]]]))

(defn tavoite-ja-kattohinta [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous kustannussuunnitelma] :as app}]
  (let [{:keys [pysyvat-muutokset-maara hoitovuoden-alun-tavoitehinta
               hoitovuoden-alun-indeksikorjattu-tavoitehinta indeksikerroin
               kattohintakerroin hoitovuoden-alun-kattohinta
               hoitovuoden-alun-indeksikorjattu-kattohinta vahvistettu?
               vahvistus-virhe]} kustannussuunnitelma
        valittu-vuosi (pvm/vuosi (first valittu-hoitokausi))
        tarjous-yht-rivi (filter #(= valittu-vuosi (:vuosi %)) (:hoitovuosittaiset-arvot (first (filter #(= "yhteensa" (:osio %)) (:tarjous tarjous)))))
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))
        tarjouksen-maara (or (:summa (first tarjous-yht-rivi)) 0)
        pysyvat-muutokset-maara (or pysyvat-muutokset-maara 0)
        hoitovuoden-alun-tavoitehinta (or hoitovuoden-alun-tavoitehinta 0)
        hoitovuoden-alun-indeksikorjattu-tavoitehinta (or hoitovuoden-alun-indeksikorjattu-tavoitehinta 0)
        ero-tarjoukseen (- hoitovuoden-alun-tavoitehinta tarjouksen-maara)

        hoitovuoden-alun-kattohinta (or hoitovuoden-alun-kattohinta 0)
        hoitovuoden-alun-indeksikorjattu-kattohinta (or hoitovuoden-alun-indeksikorjattu-kattohinta 0)]
    [:div#tavoite-ja-kattohinta-elementti.row.kustannussuunnitelma-osio.kapea-osio
     [:div.row
      [:div.col-xs-12
       [:h2 "Hoitovuoden alun tavoite- ja kattohinta"]
       [:div.body-text {:style {:margin-top "-15px"}} (fmt/hoitokauden-jarjestysluku-ja-vuodet (pvm/vuosi (first valittu-hoitokausi)) hoitovuodet "Hoitovuosi")]]]

     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.text-right.kohdista-teksti "Tarjouksen tavoitehinta"]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true tarjouksen-maara)]]]
     (when (>= valittu-vuosi yhteiset/rajavuosi)
       [:div.row
        [:div.col-xs-12.korkea-rivi.bottom-border-text
         [:div.col-xs-9.body-text.text-right.kohdista-teksti "Pysyvät muutokset"]
         [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true pysyvat-muutokset-maara)]]])
     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.text-right.kohdista-teksti "Hoitovuoden alun tavoitehinta"]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true hoitovuoden-alun-tavoitehinta)]]]
     (when (< valittu-vuosi yhteiset/rajavuosi)
       [:div.row
        [:div.col-xs-12.korkea-rivi.bottom-border-text
         [:div.col-xs-9.body-text.text-right.kohdista-teksti "Ero tarjoukseen"]
         [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true ero-tarjoukseen)]]])
     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.text-right.kohdista-teksti (str "Indeksikorjattu hoitovuoden alun tavoitehinta (" (fmt/desimaaliluku indeksikerroin nil nil false ) " * " (fmt/euro-opt false hoitovuoden-alun-tavoitehinta) ")")]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true hoitovuoden-alun-indeksikorjattu-tavoitehinta)]]]
     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.text-right.kohdista-teksti (str "Hoitovuoden alun kattohinta (" (fmt/desimaaliluku kattohintakerroin nil nil false ) " * " (fmt/euro-opt false hoitovuoden-alun-tavoitehinta) ")")]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true hoitovuoden-alun-kattohinta)]]]
     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.text-right.kohdista-teksti (str "Indeksikorjattu hoitovuoden alun kattohinta (" (fmt/desimaaliluku indeksikerroin nil nil false ) " * " (fmt/euro-opt false hoitovuoden-alun-kattohinta) ")")]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true hoitovuoden-alun-indeksikorjattu-kattohinta)]]]

     [:div.row {:style {:margin-top "2rem"}}
      [:div.col-xs-12
       [:div.painikkeet
        (if-not vahvistettu?
          [napit/yleinen-ensisijainen "Vahvista tavoite- ja kattohinta"
           #(do
              (reset! yhteiset/tallenna-painettu false)
              (e! (kust-tiedot/->VahvistaTaiPeruutaTavoiteJaKattohinta true)))
           {:disabled (or tallennus-kesken? false)}]

          [napit/kumoa "Peruuta vahvistus"
           #(do
              (reset! yhteiset/tallenna-painettu false)
              (e! (kust-tiedot/->VahvistaTaiPeruutaTavoiteJaKattohinta false)))
           {:disabled (or tallennus-kesken? false)}])]]]

     (when vahvistus-virhe
       [:div.row {:style {:margin-bottom "1rem"}}
        [:div.col-xs-12
         [yleiset/info-laatikko :varoitus vahvistus-virhe nil nil {:sulje-nappi-id (gensym)}]]])]))

(defn kustannussuunnitelma [e! {:keys [tallennus-kesken? valittu-hoitokausi] :as app}]
  (let [urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        urakan-kesto-vuosina (- urakan-loppuvuosi urakan-alkuvuosi)
        hoitokaudet (into [] (range urakan-alkuvuosi (+ urakan-kesto-vuosina urakan-alkuvuosi)))]
    [:div
     [:div.row
      [:div.col-xs-12.col-md-6 {:style {:padding-left "0"}}
       [:h1 "Hoitovuoden alun tavoitehinta"]
       [:div (-> @tila/yleiset :urakka :nimi)]]
      [:div.col-xs-12.col-md-6
       [yleiset/linkki "Muokkaa tarjouksen tietoja"
        #(siirtymat/siirry-annettuun-valilehteen @nav/valittu-hallintayksikko-id (-> @tila/yleiset :urakka :id)
           {:taso1 :urakat :taso2 :suunnittelu :taso3 :tarjous})
        {:style {:float "right"
                 :line-height "2rem"}}]]]
     [:div.row {:style {:margin-top "1rem"}}
      [:div.col-xs-12.col-md-3 {:style {:padding-left "0"}}
       [:span.caption-small-strong.alasveto-label "Hoitovuosi"]
       [yleiset/livi-pudotusvalikko {:valinta (pvm/vuosi (first valittu-hoitokausi))
                                     :vayla-tyyli? true
                                     :disabled tallennus-kesken?
                                     :data-cy "hoitokausi-valinta"
                                     :valitse-fn #(when (nav/varmista-navigointi-fn :hoitovuoden-vaihto)
                                                    (e! (kust-tiedot/->ValitseHoitokausiKustannussuunnitelmaan %)))
                                     :format-fn #(fmt/hoitokauden-jarjestysluku-ja-vuodet % hoitokaudet "Hoitovuosi")
                                     :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
        hoitokaudet]]]

     [kilpailutettavat-hankinnat e! app]
     [rahavaraukset e! app]
     [erillishankinnat e! app]
     [jjh/johto-ja-hallintokorvaus e! app]
     [hoidonjohtopalkkiot e! app]
     [pysyvat-muutokset e! app]
     [tavoite-ja-kattohinta e! app]
     [debug/debug app]]))

(defn nakyma* [e! _app]
  (let [{:keys [sisaan ulos]}
        (nav/luo-muutosten-hallinta
          :uusi-kustannusuunnitelma-nakyma/muutokset
          #(get @tila/suunnittelu-kustannussuunnitelma :tallentamattomia-muutoksia?)
          :beforeunload-viesti "Tarjouslomakkeella on tallentamattomia muutoksia! Jos poistut, menetät tekemäsi muutokset.")]
    (komp/luo
      (komp/lippu kust-tiedot/nakymassa?)
      (komp/sisaan #(do
                      (e! (kust-tiedot/->HaeKustannussuunnitelmanTiedot))
                      (sisaan)))
      (komp/ulos
        #(do
           (e! (kust-tiedot/->NollaKustannussuunnitelmanMuutokset))
           (ulos)))
      (fn [e! app]
        (if (:haku-kaynnissa? app)
          [yleiset/ajax-loader-pieni "Haku käynnissä..."]
          [kustannussuunnitelma e! app])))))

(defn kustannussuunitelma []
  (tuck/tuck tila/tarjous-kustannussuunnitelma nakyma*))
