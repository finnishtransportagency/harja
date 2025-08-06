(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuunnitelma-nakyma
  "Uusi kustannusten suunnittelu"
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
            [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as kust-tiedot]))

(defonce tallenna-painettu (atom false))
(defonce virheet-atom (atom {}))
(defonce grid-hankinnat-atom (atom [{}]))
(defonce grid-erillishankinnat-atom (atom [{}]))
(defonce grid-hoidonjohtopalkkiot-atom (atom [{}]))
(defonce grid-johto-ja-hallintokorvaukset-atom (atom [{}]))

;; Rajavuotta aiemmilla ei ole pysyviä muutoksia
(def rajavuosi 2024)

(defn- otsikkotiedot [e! {:keys [valittu-hoitokausi kustannussuunnitelma] :as app} otsikko tarjouksen-maara
                      pysyvamuutos-maara suunniteltu-yhteensa suunniteltu-yhteensa-indeksikorjattu
                      {:keys [div1 div2 div3 div4] :as opts} valittu-vuosi]
  (let [vahvistettu? (:vahvistettu? kustannussuunnitelma)
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))
        indeksikerroin (:indeksikerroin kustannussuunnitelma)
        tarjous-pysyvat-yhteensa (+ tarjouksen-maara pysyvamuutos-maara)
        tarjous-pysyvat-yhteensa-indeksikorjattu (* tarjous-pysyvat-yhteensa indeksikerroin)]
    [:div
     [:div.row
      [:div.col-xs-12
       [:h2 otsikko]
       [:div.body-text {:style {:margin-top "-15px"}} (fmt/hoitokauden-jarjestysluku-ja-vuodet (pvm/vuosi (first valittu-hoitokausi)) hoitovuodet "Hoitovuosi")]]]

     [:div.row {:style {:padding-top "1rem"}}
      (when div1
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Tarjouksen määrä"]
         [:div.body-text (if tarjouksen-maara (fmt/euro-opt true tarjouksen-maara) "0,00 €")]])

      ;; -24 vuodesta eteenpäin näytetään pysyvät muutokset, jos tämä osio aiotaan näyttää
      (when (and div2 (>= valittu-vuosi rajavuosi))
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Pysyvät muutokset"]
         [:div.body-text "Ei muutoksia"]
         [:div.body-text [yleiset/linkki "Siirry muutoksiin"
                          #(siirtymat/siirry-annettuun-valilehteen @nav/valittu-hallintayksikko-id (-> @tila/yleiset :urakka :id)
                             {:taso1 :urakat :taso2 :mhu-muutokset :taso3 nil})]]])

      ;; -24 vuodesta eteenpäin näytetään tarjous + pysyvät muutokset, jos tämä osio aiotaan näyttää
      (when (and div3 (>= valittu-vuosi rajavuosi))
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Yhteensä"]
         [:div.body-text (if tarjous-pysyvat-yhteensa (fmt/euro-opt true tarjous-pysyvat-yhteensa) "0,00 €")]])

      ;; -23 vuoteen asti näytetään yhteensä suunniteltu määrä
      (when (and div3 (< valittu-vuosi rajavuosi))
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Suunniteltu määrä"]
         [:div.body-text (if suunniteltu-yhteensa (fmt/euro-opt true suunniteltu-yhteensa) "0,00 €")]])

      ;; -24 vuodesta eteenpäin näytetään indeksikorjattu määrä tarjouksen hinnalle, jos tämä osio aiotaan näyttää
      (when (and div4 (>= valittu-vuosi rajavuosi))
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Indeksikorjattu"]
         [:div.body-text (if vahvistettu? (fmt/euro-opt true tarjous-pysyvat-yhteensa-indeksikorjattu) "Indeksilukua ei ole saatavilla")]
         (when vahvistettu?
           [:div.body-text
            (str "(" indeksikerroin " * " (if tarjous-pysyvat-yhteensa (fmt/euro-opt false tarjous-pysyvat-yhteensa) "0,00 €") " )")])])

      ;; -23 vuoteen asti näytetään indeksikorjattu määrä suunnitellulle summalle, koska tarjousihintoja ja pysyviä muutoksia ei ole ollut
      (when (and div4 (< valittu-vuosi rajavuosi))
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Indeksikorjattu"]
         [:div.body-text (if vahvistettu? (fmt/euro-opt true suunniteltu-yhteensa-indeksikorjattu) "Indeksilukua ei ole saatavilla")]
         (when vahvistettu?
           [:div.body-text
            (str "(" indeksikerroin " * " (if suunniteltu-yhteensa (fmt/euro-opt false suunniteltu-yhteensa) "0,00 €") " )")])])]]))

(defn kilpailutettavat-hankinnat [e! {:keys [tallennus-kesken? valittu-hoitokausi tarjous kustannussuunnitelma] :as app}]
  (if (nil? (get-in kustannussuunnitelma [:kilpailutettavat-hankinnat :toimenpiteet]))
    [yleiset/ajax-loader-pieni "Ladataan..."]
  (let [tarjous-hankintakustannukset (filter #(= (:osio %) "hankintakustannukset") (:tarjous tarjous))
        tarjous-vuosi (pvm/vuosi (first valittu-hoitokausi))
        tarjouksen-maara (:summa (first (filter #(= (:vuosi %) tarjous-vuosi)
                                          (:hoitovuosittaiset-arvot (first tarjous-hankintakustannukset)))))
        toimenpiteet (get-in kustannussuunnitelma [:kilpailutettavat-hankinnat :toimenpiteet])
        vahvistettu? (:vahvistettu? kustannussuunnitelma)
        taulukon-tiedot (butlast toimenpiteet) ;; Jätetään yhteenvetorivi pois tässä kohdassa
        _ (reset! grid-hankinnat-atom taulukon-tiedot)

          yht-alkukausi (:alkukausi (last toimenpiteet))
          yht-loppukausi (:loppukausi (last toimenpiteet))
          yht (:yhteensa (last toimenpiteet))

        kirjaamatta (tyokalut/round2 2 (- tarjouksen-maara yht))
        kirjaamatta-luokka (if (= 0 kirjaamatta) "yhteensa" "yhteensa-punainen")
        kirjaamatta-rivi (when-not vahvistettu? [^{:luokka "kustannukset-yhteenveto"}
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

          kilpailutettavat-hankinnat (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])
          kilpailutettavat-hankinnat-yhteensa (:yhteensa (last kilpailutettavat-hankinnat))
          kilpailutettavat-hankinnat-yhteensa-indeksikorjattu (:yhteensa-indeksikorjattu (last kilpailutettavat-hankinnat))]

    [:div#kilpailutettavat-hankinnat-elementti.kustannussuunnitelma-osio.osio-976
     [otsikkotiedot e! app "Kilpailutettavat hankinnat" tarjouksen-maara
      kilpailutettavat-hankinnat-yhteensa kilpailutettavat-hankinnat-yhteensa-indeksikorjattu
      {:div1 true :div2 true :div3 true :div4 true}]

     [:div.row
      [:div.row
       [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]
      [:div.row
       [:div.col-xs-12.body-text "Erittele " [:strong "yhteensä-summa"] " toimenpiteille."]]]
     [:div.row
      [:div.col-xs-12

       [grid/grid {:otsikko ""
                   :tyhja "Ei tietoja."
                   :luokat ["matala-panel"]
                   :muokkaa-aina (if vahvistettu? false true)
                   :voi-muokata? (if vahvistettu? false true)
                   :muokattava? (constantly (if vahvistettu? false true))
                   :voi-poistaa? (constantly false)
                   :voi-lisata? false
                   :voi-kumota? false
                   :piilota-toiminnot? false
                   :tunniste :nimi
                   :muutos #(do
                              (reset! tallenna-painettu false)
                              (reset! grid-hankinnat-atom (vals (grid/hae-muokkaustila %)))
                              (e! (kust-tiedot/->PaivitaKilpailutettavatHankinnat (vals (grid/hae-muokkaustila %))))
                              (reset! virheet-atom (grid/hae-virheet %)))
                   ;; Lisätään 2 riviä gridin päätteeksi
                   :rivi-jalkeen-fn (fn [rivit]
                                      ^{:luokka "yhteenveto"}
                                      yhteenveto-rivit)
                   :rivin-luokka (fn [_] "korkea")}
        [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "30%" :muokattava? (constantly false)
          :otsikkorivi-luokka "korkea"}
         {:otsikko "Pysyvät muutokset (€)" :nimi :pysyvat-muutokset :tyyppi :string
          :leveys "20%" :muokattava? (constantly false)}
         {:otsikko (str "Loka-joulukuu " (pvm/vuosi (first valittu-hoitokausi)) " (€)") :nimi :alkukausi :tyyppi :euro
          :leveys "20%" :validoi [[:ei-tyhja "Anna positiivinen summa."]]
          :muokattava? (constantly (if vahvistettu? false true)) :tasaa :oikea :otsikkorivi-luokka "korkea"}
         {:otsikko (str "Tammi-syyskuu " (pvm/vuosi (second valittu-hoitokausi)) " (€)")
          :nimi :loppukausi :tyyppi :euro
          :leveys "20%" :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 9999999 2))
          :muokattava? (constantly (if vahvistettu? false true)) :tasaa :oikea :otsikkorivi-luokka "korkea"}
         {:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :string :fmt #(fmt/euro-opt false %) :leveys "20%" :muokattava? (constantly false)
          :tasaa :oikea :otsikkorivi-luokka "korkea"}]
        taulukon-tiedot]]]

     (when-not vahvistettu?
       [:div
        [:div.row [:div.col-xs-12 [:hr]]]

        (when (:kilpailutettavat-hankinnat-virheet kustannussuunnitelma)
          [:div.row {:style {:margin-bottom "1rem"}}
           [:div.col-xs-12
            [yleiset/info-laatikko :varoitus (:kilpailutettavat-hankinnat-virheet kustannussuunnitelma) nil nil {:sulje-nappi-id (gensym)}]]])

        [:div.row
         [:div.col-xs-12
          [:div.painikkeet
           [napit/yleinen-ensisijainen "Tallenna tiedot"
            #(do
               (reset! tallenna-painettu false)
               (e! (kust-tiedot/->TallennaKilpailutettavatHankinnat @grid-hankinnat-atom)))
            {:disabled tallennus-kesken?}]]]]])])))

(defn rahavaraukset [e! {:keys [valittu-hoitokausi tarjous kustannussuunnitelma] :as app}]
  (if (nil? (:rahavaraukset kustannussuunnitelma))
    [yleiset/ajax-loader-pieni "Ladataan..."]
  (let [rahavaraukset (:rahavaraukset kustannussuunnitelma)
        tarjous-rahavaraukset (filter #(= (:osio %) "tavoitehintaiset-rahavaraukset") (:tarjous tarjous))
        tarjous-vuosi (pvm/vuosi (first valittu-hoitokausi))
        hoitovuosittaiset-arvot (flatten (map :hoitovuosittaiset-arvot tarjous-rahavaraukset))
        valitun-vuoden-arvot (filter #(= (:vuosi %) tarjous-vuosi) hoitovuosittaiset-arvot)
        tarjouksen-maara (apply + (map :summa valitun-vuoden-arvot))
        yht (apply + (map (fn [rivi] (:summa rivi 0)) rahavaraukset))
        yht-indeksikorjattu (apply + (map (fn [rivi] (:summa-indeksikorjattu rivi 0)) rahavaraukset))
        yhteenveto-rivi [[^{:luokka "kustannukset-yhteenveto"}
                          {:teksti "Yhteensä" :luokka "yhteensa korkea"}
                          {:teksti (fmt/euro-opt false yht) :luokka "yhteensa korkea" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                          {:teksti (if-not (= 0 yht-indeksikorjattu)
                                     (fmt/euro-opt false yht-indeksikorjattu)
                                     "-")
                           :luokka "yhteensa korkea" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]]]

    [:div#rahavaraukset-elementti.row.kustannussuunnitelma-osio.kapea-osio
     [otsikkotiedot e! app "Rahavaraukset" tarjouksen-maara yht yht-indeksikorjattu {:div1 true :div2 false :div3 false :div4 true}]
     [:div.row
      [:div.col-xs-12
       [grid/grid {:otsikko "Kustannusten erittely"
                   :tyhja "Ei tietoja."
                   :luokat ["matala-panel"]
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
                                      yhteenveto-rivi)
                   :rivin-luokka (fn [_] "korkea")}
        [{:otsikko "Rahavaraus" :nimi :nimi :tyyppi :string :leveys "60%"
          :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
         {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "20%" :tyyppi :euro :tasaa :oikea
          :fmt #(when % (fmt/euro-opt false %)) :otsikkorivi-luokka "korkea"}
         {:otsikko "Indeksikorjattu (€)" :nimi :summa-indeksikorjattu :leveys "20%" :tyyppi :euro :tasaa :oikea
          :fmt #(if-not (= 0 yht-indeksikorjattu) (fmt/euro-opt false %) "-") :otsikkorivi-luokka "korkea"}]
        rahavaraukset]]]])))

(defn erillishankinnat [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous kustannussuunnitelma] :as app}]
  (if (nil? (get-in app [:kustannussuunnitelma :erillishankinnat]))
    [yleiset/ajax-loader-pieni "Ladataan..."]
  (let [erillishankinnat (:erillishankinnat kustannussuunnitelma)
        tarjous-erillishankinnat (first (filter #(= (:osio %) "erillishankinnat") (:tarjous tarjous)))
        tarjous-vuosi (pvm/vuosi (first valittu-hoitokausi))
        hoitovuosittaiset-arvot (:hoitovuosittaiset-arvot tarjous-erillishankinnat)
        tarjouksen-maara (:summa (first (filter #(= (:vuosi %) tarjous-vuosi) hoitovuosittaiset-arvot)))
        vahvistettu? (:vahvistettu? kustannussuunnitelma)
        voi-muokata? (not vahvistettu?)
        yht (apply + (map (fn [rivi] (:summa rivi 0)) erillishankinnat))
        yht-indeksikorjattu (apply + (map (fn [rivi] (:summa_indeksikorjattu rivi 0)) erillishankinnat))
        kirjaamatta (tyokalut/round2 2 (- tarjouksen-maara yht))
        kirjaamatta-luokka (if (= 0.00 (tyokalut/round2 2 kirjaamatta)) "yhteensa" "yhteensa-punainen")
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
        _ (reset! grid-erillishankinnat-atom erillishankinnat)]

    [:div#erillishankinnat-elementti.row.kustannussuunnitelma-osio.kapea-osio
     [otsikkotiedot e! app "Erillishankinnat" tarjouksen-maara yht yht-indeksikorjattu {:div1 true :div2 false :div3 false :div4 true}]
     [:div.row
      [:div.col-xs-12
       [grid/grid {:otsikko "Kustannusten erittely"
                   :tyhja "Ei tietoja."
                   :luokat ["matala-panel"]
                   :muokkaa-aina voi-muokata?
                   :voi-muokata? voi-muokata?
                   :muokattava? voi-muokata?
                   :voi-poistaa? (constantly false)
                   :voi-lisata? false
                   :voi-kumota? false
                   :piilota-toiminnot? false
                   :tunniste :kalenterikuukausi
                   :muutos #(do
                              (reset! tallenna-painettu false)
                              (reset! grid-erillishankinnat-atom (vals (grid/hae-muokkaustila %)))
                              (e! (kust-tiedot/->PaivitaErillishankinnat (vals (grid/hae-muokkaustila %))))
                              (reset! virheet-atom (grid/hae-virheet %)))
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
        erillishankinnat]]]

     (when-not vahvistettu?
       [:div
        [:div.row [:div.col-xs-12 [:span.body-text "Harja luo kulut kuukausille, kun tallennat tiedot."]]]
        [:div.row [:div.col-xs-12 [:hr]]]

        [:div.row
         [:div.col-xs-12
          [:div.painikkeet
           [napit/yleinen-ensisijainen "Tallenna tiedot"
            #(do
               (reset! tallenna-painettu false)
               (e! (kust-tiedot/->TallennaErillishankinnat @grid-erillishankinnat-atom)))
            {:disabled (or tallennus-kesken? false)}]
           [napit/yleinen-toissijainen "Jaa tasan joka kuukaudelle"
            #(do
               (reset! tallenna-painettu false)
               (e! (kust-tiedot/->JaaErillishankinnatTasan tarjouksen-maara "erillishankinnat-elementti")))
            {:disabled (or tallennus-kesken? false)}]]]]])])))

(defn johto-ja-hallintokorvaus [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous kustannussuunnitelma] :as app}]
  (let [johto-ja-hallintokorvaukset (:johto-ja-hallintokorvaukset kustannussuunnitelma)
        tarjous-johto-ja-hallintokorvaukset (filter #(= (:osio %) "johto-ja-hallintokorvaus") (:tarjous tarjous))
        tarjous-vuosi (pvm/vuosi (first valittu-hoitokausi))
        hoitovuosittaiset-arvot (flatten (map :hoitovuosittaiset-arvot tarjous-johto-ja-hallintokorvaukset))
        valitun-vuoden-arvot (filter #(= (:vuosi %) tarjous-vuosi) hoitovuosittaiset-arvot)
        tarjouksen-maara (apply + (map :summa valitun-vuoden-arvot))
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
        _ (reset! grid-johto-ja-hallintokorvaukset-atom johto-ja-hallintokorvaukset)]
    [:div#johto-ja-hallintokorvaus-elementti.row.kustannussuunnitelma-osio.kapea-osio
     [otsikkotiedot e! app "Johto- ja hallintokorvaus" tarjouksen-maara yht yht-indeksikorjattu {:div1 true :div2 false :div3 false :div4 true}]
     [:div.row
      [:div.col-xs-12
       [grid/grid {:otsikko "Kustannusten erittely"
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
                              (reset! tallenna-painettu false)
                              (reset! grid-johto-ja-hallintokorvaukset-atom (vals (grid/hae-muokkaustila %)))
                              (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset (vals (grid/hae-muokkaustila %))))
                              (reset! virheet-atom (grid/hae-virheet %)))
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
        [:div.row [:div.col-xs-12 [:span.body-text "Harja luo kulut kuukausille, kun tallennat tiedot."]]]
        [:div.row [:div.col-xs-12 [:hr]]]

        (when (:johto-ja-hallintokorvaukset-virheet kustannussuunnitelma)
          [:div.row {:style {:margin-bottom "1rem"}}
           [:div.col-xs-12
            [yleiset/info-laatikko :varoitus (:johto-ja-hallintokorvaukset-virheet kustannussuunnitelma) nil nil {:sulje-nappi-id (gensym)}]]])

        [:div.row
         [:div.col-xs-12
          [:div.painikkeet
           [napit/yleinen-ensisijainen "Tallenna tiedot"
            #(do
               (reset! tallenna-painettu false)
               (e! (kust-tiedot/->TallennaJohtoJaHallintokorvaukset @grid-johto-ja-hallintokorvaukset-atom)))
            {:disabled (or tallennus-kesken? false)}]
           [napit/yleinen-toissijainen "Jaa tasan joka kuukaudelle"
            #(do
               (reset! tallenna-painettu false)
               (e! (kust-tiedot/->JaaJohtoJaHallintokorvauksetTasan tarjouksen-maara "johto-ja-hallintokorvaus-elementti")))
            {:disabled (or tallennus-kesken? false)}]]]]])]))

(defn hoidonjohtopalkkiot [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous kustannussuunnitelma] :as app}]
  (if (nil? (get-in app [:kustannussuunnitelma :hoidonjohtopalkkiot]))
    [yleiset/ajax-loader-pieni "Ladataan..."]
  (let [hoidonjohtopalkkiot (:hoidonjohtopalkkiot kustannussuunnitelma)
        tarjous-hoidonjohtopalkkio (first (filter #(= (:osio %) "hoidonjohtopalkkio") (:tarjous tarjous)))
        tarjous-vuosi (pvm/vuosi (first valittu-hoitokausi))
        hoitovuosittaiset-arvot (:hoitovuosittaiset-arvot tarjous-hoidonjohtopalkkio)
        tarjouksen-maara (:summa (first (filter #(= (:vuosi %) tarjous-vuosi) hoitovuosittaiset-arvot)))
        vahvistettu? (:vahvistettu? kustannussuunnitelma)
        voi-muokata? (not vahvistettu?)
        yht (apply + (map (fn [rivi]
                            (:summa rivi 0)) hoidonjohtopalkkiot))
        yht-indeksikorjattu (apply + (map (fn [rivi]
                                            (:summa_indeksikorjattu rivi 0)) hoidonjohtopalkkiot))
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
        _ (reset! grid-hoidonjohtopalkkiot-atom hoidonjohtopalkkiot)]
    [:div#hoidonjohtopalkkio-elementti.row.kustannussuunnitelma-osio.kapea-osio
     [otsikkotiedot e! app "Hoidonjohtopalkkiot" tarjouksen-maara yht yht-indeksikorjattu {:div1 true :div2 false :div3 false :div4 true}]
     [:div.row
      [:div.col-xs-12
       [grid/grid {:otsikko "Kustannusten erittely"
                   :tyhja "Ei tietoja."
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
                              (reset! tallenna-painettu false)
                              (reset! grid-hoidonjohtopalkkiot-atom (vals (grid/hae-muokkaustila %)))
                              (e! (kust-tiedot/->PaivitaHoidonjohtopalkkiot (vals (grid/hae-muokkaustila %))))
                              (reset! virheet-atom (grid/hae-virheet %)))
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
        hoidonjohtopalkkiot]]]

     (when-not vahvistettu?
       [:div
        [:div.row [:div.col-xs-12 [:span.body-text "Harja luo kulut kuukausille, kun tallennat tiedot."]]]
        [:div.row [:div.col-xs-12 [:hr]]]

        [:div.row
         [:div.col-xs-12
          [:div.painikkeet
           [napit/yleinen-ensisijainen "Tallenna tiedot"
            #(do
               (reset! tallenna-painettu false)
               (e! (kust-tiedot/->TallennaHoidonjohtopalkkiot @grid-hoidonjohtopalkkiot-atom)))
            {:disabled (or tallennus-kesken? false)}]
           [napit/yleinen-toissijainen "Jaa tasan joka kuukaudelle"
            #(do
               (reset! tallenna-painettu false)
               (e! (kust-tiedot/->JaaHoidonjohtopalkkiotTasan tarjouksen-maara "hoidonjohtopalkkio-elementti")))
            {:disabled (or tallennus-kesken? false)}]]]]])])))

(defn tavoite-ja-kattohinta [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous kustannussuunnitelma] :as app}]
  (let [tarjous-vuosi (pvm/vuosi (first valittu-hoitokausi))
        tarjous-yht-rivi (filter #(= tarjous-vuosi (:vuosi %)) (:hoitovuosittaiset-arvot (first (filter #(= "yhteensa" (:osio %)) (:tarjous tarjous)))))
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))
        tarjouksen-maara (or (:summa (first tarjous-yht-rivi)) 0)
        pysyvat-muutokset-maara (or (:pysyvat-muutokset-maara kustannussuunnitelma) 0)
        hoitovuoden-alun-tavoitehinta (or (:hoitovuoden-alun-tavoitehinta kustannussuunnitelma) 0)
        hoitovuoden-alun-indeksikorjattu-tavoitehinta (or (:hoitovuoden-alun-indeksikorjattu-tavoitehinta kustannussuunnitelma) 0)
        indeksikerroin (:indeksikerroin kustannussuunnitelma)
        kattohintakerroin (:kattohintakerroin kustannussuunnitelma)
        hoitovuoden-alun-kattohinta (or (:hoitovuoden-alun-kattohinta kustannussuunnitelma) 0)
        hoitovuoden-alun-indeksikorjattu-kattohinta (or (:hoitovuoden-alun-indeksikorjattu-kattohinta kustannussuunnitelma) 0)
        vahvistettu? (true? (:vahvistettu? kustannussuunnitelma))]
    [:div#tavoite-ja-kattohinta-elementti.row.kustannussuunnitelma-osio.kapea-osio
     [:div.row
      [:div.col-xs-12
       [:h2 "Hoitovuoden alun tavoite- ja kattohinta"]
       [:div.body-text {:style {:margin-top "-15px"}} (fmt/hoitokauden-jarjestysluku-ja-vuodet (pvm/vuosi (first valittu-hoitokausi)) hoitovuodet "Hoitovuosi")]]]

     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.pull-righ.text-right.kohdista-teksti "Tarjouksen tavoitehinta"]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true tarjouksen-maara)]]]
     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.pull-righ.text-right.kohdista-teksti "Pysyvät muutokset"]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true pysyvat-muutokset-maara)]]]
     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.pull-righ.text-right.kohdista-teksti "Hoitovuoden alun tavoitehinta"]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true hoitovuoden-alun-tavoitehinta)]]]
     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.pull-righ.text-right.kohdista-teksti (str "Indeksikorjattu hoitovuoden alun tavoitehinta (" indeksikerroin " * " (fmt/euro-opt false hoitovuoden-alun-tavoitehinta) ")")]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true hoitovuoden-alun-indeksikorjattu-tavoitehinta)]]]
     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.pull-righ.text-right.kohdista-teksti (str "Hoitovuoden alun kattohinta (" kattohintakerroin " * " (fmt/euro-opt false hoitovuoden-alun-tavoitehinta) ")")]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true hoitovuoden-alun-kattohinta)]]]
     [:div.row
      [:div.col-xs-12.korkea-rivi.bottom-border-text
       [:div.col-xs-9.body-text.pull-righ.text-right.kohdista-teksti (str "Indeksikorjattu hoitovuoden alun kattohinta (" indeksikerroin " * " (fmt/euro-opt false hoitovuoden-alun-kattohinta) ")")]
       [:div.col-xs-3.body-text.strong.kohdista-teksti.text-right (fmt/euro-opt true hoitovuoden-alun-indeksikorjattu-kattohinta)]]]

     [:div.row {:style {:margin-top "2rem"}}
      [:div.col-xs-12
       [:div.painikkeet
        (if-not vahvistettu?
          [napit/yleinen-ensisijainen "Vahvista tavoite- ja kattohinta"
           #(do
              (reset! tallenna-painettu false)
              (e! (kust-tiedot/->VahvistaTaiPeruutaTavoiteJaKattohinta true)))
           {:disabled (or tallennus-kesken? false)}]

          [napit/kumoa "Peruuta vahvistus"
           #(do
              (reset! tallenna-painettu false)
              (e! (kust-tiedot/->VahvistaTaiPeruutaTavoiteJaKattohinta false)))
           {:disabled (or tallennus-kesken? false)}])]]]

     (when (:vahvistus-virhe kustannussuunnitelma)
       [:div.row {:style {:margin-bottom "1rem"}}
        [:div.col-xs-12
         [yleiset/info-laatikko :varoitus (:vahvistus-virhe kustannussuunnitelma) nil nil {:sulje-nappi-id (gensym)}]]])]))

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
                                     :valitse-fn #(e! (kust-tiedot/->ValitseHoitokausiKustannussuunnitelmaan %))
                                     :format-fn #(fmt/hoitokauden-jarjestysluku-ja-vuodet % hoitokaudet "Hoitovuosi")
                                     :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
        hoitokaudet]]]

     [kilpailutettavat-hankinnat e! app]
     [rahavaraukset e! app]
     [erillishankinnat e! app]
     [johto-ja-hallintokorvaus e! app]
     [hoidonjohtopalkkiot e! app]
     [tavoite-ja-kattohinta e! app]]))

(defn nakyma* [e! _app]
  (komp/luo
    (komp/lippu kust-tiedot/nakymassa?)
    (komp/sisaan #(e! (kust-tiedot/->HaeKustannussuunnitelmanTiedot)))
    (fn [e! app]
      (if (:haku-kaynnissa? app)
        [yleiset/ajax-loader-pieni "Haku käynnissä..."]
        [kustannussuunnitelma e! app]))))

(defn kustannussuunitelma []
  (tuck/tuck tila/tarjous-kustannussuunnitelma nakyma*))
