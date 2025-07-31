(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuunnitelma-nakyma
  "Uusi kustannusten suunnittelu"
  (:require [tuck.core :as tuck]
            [harja.validointi :as v]
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
(defonce grid-hankinnat-atom (atom [{}]))
(defonce grid-erillishankinnat-atom (atom [{}]))
(defonce grid-hoidonjohtopalkkiot-atom (atom [{}]))

(defn- otsikkotiedot [e! {:keys [valittu-hoitokausi tarjous] :as app} otsikko tarjouksen-maara yhteensa yhteensa-indeksikorjattu {:keys [div1 div2 div3 div4] :as opts}]
  (let [urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))
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
         [:div.body-text (if tarjouksen-maara (fmt/euro true tarjouksen-maara) "0,00 €")]])
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
         [:div.body-text (if yhteensa (fmt/euro true yhteensa) "0,00 €")]])

      (when div4
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Indeksikorjattu"]
         [:div.body-text (if yhteensa-indeksikorjattu (fmt/euro true yhteensa-indeksikorjattu) "0,00 €")]
         [:div.body-text (when indeksikerroin
                           (str "(" indeksikerroin " * " (if yhteensa (fmt/euro false yhteensa-indeksikorjattu) "0,00 €") " )"))]])]]))

(defn kilpailutettavat-hankinnat [e! {:keys [tallennus-kesken? valittu-hoitokausi tarjous kustannussuunnitelma] :as app}]

  (if (nil? (get-in kustannussuunnitelma [:kilpailutettavat-hankinnat :toimenpiteet]))
    [yleiset/ajax-loader-pieni "Ladataan..."]

    (let [tarjous-hankintakustannukset (filter #(= (:osio %) "hankintakustannukset") (:tarjous tarjous))
          tarjous-vuosi (pvm/vuosi (first valittu-hoitokausi))
          tarjouksen-maara (:summa (first (filter #(= (:vuosi %) tarjous-vuosi) (:hoitovuosittaiset-arvot (first tarjous-hankintakustannukset)))))
          toimenpiteet (get-in kustannussuunnitelma [:kilpailutettavat-hankinnat :toimenpiteet])
          valhvistettu? (true? (get-in kustannussuunnitelma [:kustannussuunnitelma-vahvistettu?]))
          taulukon-tiedot (butlast toimenpiteet) ;; Jätetään yhteenvetorivi pois tässä kohdassa
          _ (reset! grid-hankinnat-atom taulukon-tiedot)

          yht-alkukausi (:alkukausi (last toimenpiteet))
          yht-loppukausi (:loppukausi (last toimenpiteet))
          yht (:yhteensa (last toimenpiteet))

          kirjaamatta (- tarjouksen-maara yht)
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
                            kirjaamatta-rivi]

          kilpailutettavat-hankinnat (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])
          kilpailutettavat-hankinnat-yhteensa (:yhteensa (last kilpailutettavat-hankinnat))
          kilpailutettavat-hankinnat-yhteensa-indeksikorjattu (:yhteensa-indeksikorjattu (last kilpailutettavat-hankinnat))]

      [:div.kustannussuunnitelma-osio
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
                                (reset! grid-hankinnat-atom (vals (grid/hae-muokkaustila %)))
                                (e! (kust-tiedot/->PaivitaKilpailutettavatHankinnat (vals (grid/hae-muokkaustila %))))
                                (reset! virheet-atom (grid/hae-virheet %)))
                     ;; Lisätään 2 riviä gridin päätteeksi
                     :rivi-jalkeen-fn (fn [rivit]
                                        ^{:luokka "yhteenveto"}
                                        yhteenveto-rivit)}
          [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "30%" :muokattava? (constantly false)}
           {:otsikko "Pysyvät muutokset (€)" :nimi :pysyvat-muutokset :tyyppi :string
            :leveys "20%" :muokattava? (constantly false)}
           {:otsikko (str "Loka-joulukuu " (pvm/vuosi (first valittu-hoitokausi)) " (€)") :nimi :alkukausi :tyyppi :euro
            :leveys "20%" :validoi [[:ei-tyhja "Anna positiivinen summa."]] :muokattava? (constantly true) :tasaa :oikea}
           {:otsikko (str "Tammi-syyskuu " (pvm/vuosi (second valittu-hoitokausi)) " (€)") :nimi :loppukausi :tyyppi :euro
            :leveys "20%" :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 9999999 2)) :muokattava? (constantly true) :tasaa :oikea}
           {:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :string :leveys "20%" :muokattava? (constantly false) :tasaa :oikea}]
          taulukon-tiedot]]]
       [:div.row [:div.col-xs-12] [:hr]]

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
           {:disabled (or tallennus-kesken? false)}]]]]])))

(defn rahavaraukset [e! {:keys [valittu-hoitokausi tarjous kustannussuunnitelma] :as app}]
  
  (if (nil? (:rahavaraukset kustannussuunnitelma))
    [yleiset/ajax-loader-pieni "Ladataan..."]

    (let [rahavaraukset (:rahavaraukset kustannussuunnitelma)
          tarjous-rahavaraukset (filter #(= (:osio %) "tavoitehintaiset-rahavaraukset") (:tarjous tarjous))
          tarjous-vuosi (pvm/vuosi (first valittu-hoitokausi))
          hoitovuosittaiset-arvot (flatten (map :hoitovuosittaiset-arvot tarjous-rahavaraukset))
          valitun-vuoden-arvot (filter #(= (:vuosi %) tarjous-vuosi) hoitovuosittaiset-arvot)
          tarjouksen-maara (apply + (map :summa valitun-vuoden-arvot))
          yht (apply + (map (fn [rivi]
                              (:summa rivi 0)) rahavaraukset))
          yht-indeksikorjattu (apply + (map (fn [rivi]
                                              (:summa-indeksikorjattu rivi 0)) rahavaraukset))
          yhteenveto-rivi [[^{:luokka "kustannukset-yhteenveto"}
                            {:teksti "Yhteensä" :luokka "yhteensa"}
                            {:teksti (fmt/euro false yht) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                            {:teksti (fmt/euro false yht-indeksikorjattu) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]]]

      [:div.row.kustannussuunnitelma-osio
       [otsikkotiedot e! app "Rahavaraukset" tarjouksen-maara yht yht-indeksikorjattu {:div1 true :div2 false :div3 false :div4 true}]
       [:div.row
        [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]
       [:div.row
        [:div.col-xs-12
         [grid/grid {:otsikko ""
                     :tyhja "Ei tietoja."
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
           {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "15%" :tyyppi :euro :tasaa :oikea :fmt #(when % (fmt/euro false %))}
           {:otsikko "Indeksikorjattu (€)" :nimi :summa-indeksikorjattu :leveys "15%" :tyyppi :euro :tasaa :oikea :fmt #(when % (fmt/euro false %))}]
          rahavaraukset]]]])))

(defn erillishankinnat [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous] :as app}]

  (if (nil? (get-in app [:kustannussuunnitelma :erillishankinnat]))
    [yleiset/ajax-loader-pieni "Ladataan..."]

    (let [erillishankinnat (get-in app [:kustannussuunnitelma :erillishankinnat])
          tarjous-erillishankinnat (first (filter #(= (:osio %) "erillishankinnat") (:tarjous tarjous)))
          tarjous-vuosi (pvm/vuosi (first valittu-hoitokausi))
          hoitovuosittaiset-arvot (:hoitovuosittaiset-arvot tarjous-erillishankinnat)
          tarjouksen-maara (:summa (first (filter #(= (:vuosi %) tarjous-vuosi) hoitovuosittaiset-arvot)))
          valhvistettu? (true? (get-in app [:kustannussuunnitelma :kustannussuunnitelma-vahvistettu?]))
          voi-muokata? (not valhvistettu?)
          yht (apply + (map (fn [rivi]
                              (:summa rivi 0)) erillishankinnat))
          yht-indeksikorjattu (apply + (map (fn [rivi]
                                              (:summa-indeksikorjattu rivi 0)) erillishankinnat))
          kirjaamatta (- tarjouksen-maara yht)
          kirjaamatta-luokka (if (= 0 kirjaamatta) "yhteensa" "yhteensa-punainen")
          kirjaamatta-rivi (when-not valhvistettu? [^{:luokka "kustannukset-yhteenveto"}
                                                    {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                                                    {:teksti (fmt/euro false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                                                    {:teksti "" :luokka kirjaamatta-luokka}])

          yhteenveto-rivi [[^{:luokka "kustannukset-yhteenveto"}
                            {:teksti "Yhteensä" :luokka "yhteensa"}
                            {:teksti (fmt/euro false yht) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                            {:teksti (fmt/euro false yht-indeksikorjattu) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]
                           kirjaamatta-rivi]
          _ (reset! grid-erillishankinnat-atom erillishankinnat)]

      [:div.row.kustannussuunnitelma-osio
       [otsikkotiedot e! app "Erillishankinnat" tarjouksen-maara yht yht-indeksikorjattu {:div1 true :div2 false :div3 false :div4 true}]
       [:div.row
        [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]
       [:div#erilliskustannukset-elementti.row
        [:div.col-xs-12
         [grid/grid {:otsikko ""
                     :tyhja "Ei tietoja."
                     :muokkaa-aina true
                     :voi-muokata? true
                     :muokattava? (constantly true)
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
                                        yhteenveto-rivi)}
          [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "70%" :muokattava? (constantly false)}
           {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "15%" :tyyppi :euro :tasaa :oikea :fmt #(when % (fmt/euro false %)) :muokattava? (constantly voi-muokata?)}
           {:otsikko "Indeksikorjattu (€)" :nimi :summa-indeksikorjattu :leveys "15%" :tyyppi :euro :tasaa :oikea :fmt #(when % (fmt/euro false %)) :muokattava? (constantly false)}]
          erillishankinnat]]]

       [:div.row [:div.col-xs-12 [:span.body-text "Harja luo kulut kuukausille, kun tallennat tiedot."]]]
       [:div.row [:div.col-xs-12] [:hr]]
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
              (e! (kust-tiedot/->JaaErillishankinnatTasan tarjouksen-maara "erilliskustannukset-elementti")))
           {:disabled (or tallennus-kesken? false)}]]]]])))

(defn hoidonjohtopalkkiot [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous] :as app}]

  (if (nil? (get-in app [:kustannussuunnitelma :hoidonjohtopalkkiot]))
    [yleiset/ajax-loader-pieni "Ladataan..."]

    (let [hoidonjohtopalkkiot (get-in app [:kustannussuunnitelma :hoidonjohtopalkkiot])
          tarjous-hoidonjohtopalkkio (first (filter #(= (:osio %) "hoidonjohtopalkkio") (:tarjous tarjous)))
          tarjous-vuosi (pvm/vuosi (first valittu-hoitokausi))
          hoitovuosittaiset-arvot (:hoitovuosittaiset-arvot tarjous-hoidonjohtopalkkio)
          tarjouksen-maara (:summa (first (filter #(= (:vuosi %) tarjous-vuosi) hoitovuosittaiset-arvot)))
          valhvistettu? (true? (get-in app [:kustannussuunnitelma :kustannussuunnitelma-vahvistettu?]))
          voi-muokata? (not valhvistettu?)
          yht (apply + (map (fn [rivi]
                              (:summa rivi 0)) hoidonjohtopalkkiot))
          yht-indeksikorjattu (apply + (map (fn [rivi]
                                              (:summa-indeksikorjattu rivi 0)) hoidonjohtopalkkiot))
          kirjaamatta (- tarjouksen-maara yht)
          kirjaamatta-luokka (if (= 0 kirjaamatta) "yhteensa" "yhteensa-punainen")
          kirjaamatta-rivi (when-not valhvistettu? [^{:luokka "kustannukset-yhteenveto"}
                                                    {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                                                    {:teksti (fmt/euro false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                                                    {:teksti "" :luokka kirjaamatta-luokka}])

          yhteenveto-rivi [[^{:luokka "kustannukset-yhteenveto"}
                            {:teksti "Yhteensä" :luokka "yhteensa"}
                            {:teksti (fmt/euro false yht) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                            {:teksti (fmt/euro false yht-indeksikorjattu) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]
                           kirjaamatta-rivi]
          _ (reset! grid-hoidonjohtopalkkiot-atom hoidonjohtopalkkiot)]
      [:div.row.kustannussuunnitelma-osio
       [otsikkotiedot e! app "Hoidonjohtopalkkiot" tarjouksen-maara yht yht-indeksikorjattu {:div1 true :div2 false :div3 false :div4 true}]
       [:div.row
        [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]
       [:div#hoidonjohtopalkkio-elementti.row
        [:div.col-xs-12
         [grid/grid {:otsikko ""
                     :tyhja "Ei tietoja."
                     :muokkaa-aina true
                     :voi-muokata? true
                     :muokattava? (constantly true)
                     :voi-poistaa? (constantly false)
                     :voi-lisata? false
                     :voi-kumota? false
                     :piilota-toiminnot? false
                     :tunniste :kalenterikuukausi
                     :muutos #(do
                                (reset! tallenna-painettu false)
                                (reset! grid-hoidonjohtopalkkiot-atom (vals (grid/hae-muokkaustila %)))
                                (reset! virheet-atom (grid/hae-virheet %)))
                     ;; Lisätään yhteenveto rivi gridin päätteeksi
                     :rivi-jalkeen-fn (fn [rivit]
                                        ^{:luokka "yhteenveto"}
                                        yhteenveto-rivi)}
          [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "70%" :muokattava? (constantly false)}
           {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "15%" :tyyppi :euro :tasaa :oikea :fmt #(when % (fmt/euro false %)) :muokattava? (constantly voi-muokata?)}
           {:otsikko "Indeksikorjattu (€)" :nimi :summa-indeksikorjattu :leveys "15%" :tyyppi :euro :tasaa :oikea :fmt #(when % (fmt/euro false %)) :muokattava? (constantly false)}]
          hoidonjohtopalkkiot]]]

       [:div.row [:div.col-xs-12 [:span.body-text "Harja luo kulut kuukausille, kun tallennat tiedot."]]]
       [:div.row [:div.col-xs-12] [:hr]]
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
           {:disabled (or tallennus-kesken? false)}]]]]])))

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
     [rahavaraukset e! app]
     [erillishankinnat e! app]
     [hoidonjohtopalkkiot e! app]]))

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
