(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuunnitelma-johto-ja-hallintokorvaus
  "Tämä komponentti näyttää Johto- ja hallintokorvaukset kustannussuunnitelmassa.
  Johto- ja hallintokorvaukset koostuvat toimenkuvista, joiden syöttämisen tarkkuus vaihtelee urakan alkuvuoden perusteella.
  -19 - 21 vuosina alkavat urakat syöttävät tunnit ja tuntihinnat jokaiselle toimenkuvalle.
  -22 - 24 vuosina alkavat urakat syöttävät kuukausisumman toimenkuvalle.
  -25 ja sitä myöhemmät urakat syöttävät vain kuukausisumman.
  Käyttöliittymä yksinkertaistuu vuosien myötä, koska tarkkuus vähenee.
  "
  (:require [clojure.string :as str]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tyokalut.yleiset :as tyokalut]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as kust-tiedot]
            [harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.yhteiset :as yhteiset]
            [reagent.core :as r]))

(defn toimenkuvat-vetolaatikko-2022
  "Anna parametrina valittu toimenkuva sekä kaikki mahdolliset kuukaudet, joita voidaan muokata."
  [e! vetolaatikon-muokkaus toimenkuva-id kuukaudet vahvistettu?]
  (let [voi-muokata? (and (not vahvistettu?) vetolaatikon-muokkaus)]
    [:div
     [kentat/tee-kentta {:tyyppi :checkbox
                         :teksti "Suunnittele kuukausittain"
                         :disabled? vahvistettu?
                         :valitse! #(e! (kust-tiedot/->ToggleVetolaatikonMuokkaus (-> % .-target .-checked)))}
      vetolaatikon-muokkaus]
     [:div.vetolaatikko-border {:style {:border-left "4px solid lightblue" :padding-left "18px"}}
      [grid/grid {:otsikko ""
                  :luokat ["matala-panel"]
                  :voi-muokata? voi-muokata?
                  :muokattava? voi-muokata?
                  :muokkaa-aina voi-muokata?
                  :voi-poistaa? (constantly false)
                  :voi-lisata? false
                  :voi-kumota? false
                  :piilota-toiminnot? true
                  :tunniste (fn [rivi]
                              (str (:kalenterikuukausi rivi) "-" toimenkuva-id))
                  :muutos (fn [tila]
                            (let [toimenkuva (first (filter
                                                      #(= toimenkuva-id (:id %))
                                                      @yhteiset/grid-johto-ja-hallintokorvaukset-atom))
                                  toimenkuvat-ilman-muutettavaa (remove
                                                                  #(= toimenkuva-id (:id %))
                                                                  @yhteiset/grid-johto-ja-hallintokorvaukset-atom)
                                  ;; Lasketaan suunniteltu määrä uusiksi.
                                  kuukaudet (vals (grid/hae-muokkaustila tila))
                                  kuukaudet (map (fn [rivi]
                                                   (assoc rivi :yhteensa-kk (* (if (:tuntipalkka rivi) (:tuntipalkka rivi) 0) (if (:tunnit rivi) (:tunnit rivi) 0))))
                                              kuukaudet)
                                  suunniteltu-yht (apply + (map #(if (and (:tuntipalkka %) (:tunnit %)) (* (:tunnit %) (:tuntipalkka %)) 0) kuukaudet))
                                  toimenkuva (assoc toimenkuva
                                               :kuukaudet (sort-by (juxt :vuosi :kuukausi) kuukaudet)
                                               :summa suunniteltu-yht
                                               :summa-indeksikorjattu nil) ;; indeksikorjaus lasketaan bäckendissä
                                  _ (reset! yhteiset/tallenna-painettu false)
                                  uudet-toimenkuvat (sort-by :id (conj toimenkuvat-ilman-muutettavaa toimenkuva))
                                  _ (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom uudet-toimenkuvat)
                                  _ (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset uudet-toimenkuvat))
                                  _ (reset! yhteiset/virheet-atom (grid/hae-virheet tila))]))
                  :rivin-luokka (fn [_] "korkea")}
       [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "70%"
         :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
        {:otsikko "Suunniteltu määrä (€)" :nimi :tuntipalkka :leveys "30%" :tyyppi :euro :tasaa :oikea
         :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}]
       kuukaudet]]]))

(defn toimenkuvat-vetolaatikko-2019
  "Anna parametrina valittu toimenkuva sekä kaikki mahdolliset kuukaudet, joita voidaan muokata."
  [e! vetolaatikon-muokkaus toimenkuva-id kuukaudet vahvistettu?]
  (let [voi-muokata? (and (not vahvistettu?) vetolaatikon-muokkaus)
        muokkaus-kuukaudet (into {} (mapv (fn [kuukausi]
                                            {(:kalenterikuukausi kuukausi) kuukausi})
                                      kuukaudet))
        kuukaudet-atom (r/atom muokkaus-kuukaudet)]
    [:div
     [kentat/tee-kentta {:tyyppi :checkbox
                         :teksti "Suunnittele kuukausittain"
                         :disabled? vahvistettu?
                         :valitse! #(e! (kust-tiedot/->ToggleVetolaatikonMuokkaus (-> % .-target .-checked)))}
      vetolaatikon-muokkaus]
     [:div.vetolaatikko-border {:style {:border-left "4px solid lightblue" :padding-left "18px"}}
      [grid/muokkaus-grid
       {:id (str "kuukausitaulukko-" toimenkuva-id)
        :voi-poistaa? (constantly false)
        :voi-lisata? false
        :piilota-toiminnot? true
        :muokkauspaneeli? false
        :voi-muokata? (not vahvistettu?)
        :on-rivi-blur (fn [rivi]
                        (let [toimenkuva (first (filter
                                                  #(= toimenkuva-id (:id %))
                                                  @yhteiset/grid-johto-ja-hallintokorvaukset-atom))
                              toimenkuvat-ilman-muutettavaa (remove
                                                              #(= toimenkuva-id (:id %))
                                                              @yhteiset/grid-johto-ja-hallintokorvaukset-atom)
                              valittu-kuukausi (first (filter
                                                        (fn [kuukausi]
                                                          (and (= (:kuukausi rivi) (:kuukausi kuukausi))
                                                            (= (:vuosi rivi) (:vuosi kuukausi))))
                                                        (:kuukaudet toimenkuva)))
                              kuukaudet-ilman-valittua (remove
                                                         (fn [kuukausi]
                                                           (and (= (:kuukausi rivi) (:kuukausi kuukausi))
                                                             (= (:vuosi rivi) (:vuosi kuukausi))))
                                                         (:kuukaudet toimenkuva))

                              valittu-kuukausi (assoc valittu-kuukausi :tunnit (:tunnit rivi))
                              uudet-kuukaudet (sort-by :vuosi :kuukausi (conj kuukaudet-ilman-valittua valittu-kuukausi))


                              ;; Lasketaan suunniteltu määrä uusiksi.
                              kuukaudet (map (fn [rivi]
                                               (assoc rivi :yhteensa-kk (* (if (:tuntipalkka rivi) (:tuntipalkka rivi) 0) (if (:tunnit rivi) (:tunnit rivi) 0))))
                                          uudet-kuukaudet)
                              suunniteltu-yht (apply + (map #(if (and (:tuntipalkka %) (:tunnit %)) (* (:tunnit %) (:tuntipalkka %)) 0) kuukaudet))
                              toimenkuva (assoc toimenkuva
                                           :tunnit (apply + (map (fn [rivi] (or (:tunnit rivi) 0)) kuukaudet))
                                           :kuukaudet (sort-by (juxt :vuosi :kuukausi) kuukaudet)
                                           :summa suunniteltu-yht
                                           :summa-indeksikorjattu nil) ;; indeksikorjaus lasketaan bäckendissä
                              _ (reset! yhteiset/tallenna-painettu false)
                              uudet-toimenkuvat (sort-by :id (conj toimenkuvat-ilman-muutettavaa toimenkuva))
                              _ (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom uudet-toimenkuvat)
                              _ (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset uudet-toimenkuvat))]))
        :voi-kumota? false}
       [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "40%"
         :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
        {:otsikko "Tunnit/kk, h" :nimi :tunnit :leveys "20%" :tyyppi :euro :tasaa :oikea
         :fmt #(when % (fmt/euro-opt false %)) :voi-muokata? voi-muokata? :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
        {:otsikko "Yhteensa/kk" :nimi :yhteensa-kk :leveys "20%" :tyyppi :euro :tasaa :oikea
         :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
       kuukaudet-atom]]]))

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
        voi-muokata? (and (not vahvistettu?) (not (:vetolaatikon-muokkaus app)))
        yht (apply + (map (fn [rivi]
                            (:summa rivi 0)) johto-ja-hallintokorvaukset))
        yht-indeksikorjattu (apply + (map (fn [rivi]
                                            (:summa_indeksikorjattu rivi 0)) johto-ja-hallintokorvaukset))
        kirjaamatta (tyokalut/round2 2 (- tarjouksen-maara yht))
        kirjaamatta-luokka (if (= 0 kirjaamatta) "yhteensa" "yhteensa-punainen")
        kirjaamatta-rivi (cond (and (<= urakan-alkuvuosi 2021) (not vahvistettu?))
                           [^{:luokka "kustannukset-yhteenveto"}
                            {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                            {:teksti "" :luokka kirjaamatta-luokka}
                            {:teksti "" :luokka kirjaamatta-luokka}
                            {:teksti "" :luokka kirjaamatta-luokka}
                            {:teksti (fmt/euro-opt false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                            {:teksti "" :luokka kirjaamatta-luokka}]
                           (and (>= urakan-alkuvuosi 2025) (not vahvistettu?))
                           [^{:luokka "kustannukset-yhteenveto"}
                            {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                            {:teksti (fmt/euro-opt false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                            {:teksti "" :luokka kirjaamatta-luokka}])
        yhteenveto-rivi (cond (and (<= urakan-alkuvuosi 2021) (not vahvistettu?))
                          [^{:luokka "kustannukset-yhteenveto"}
                           {:teksti "Yhteensä 2021" :luokka "yhteensa"}
                           {:teksti "" :luokka "yhteensa"}
                           {:teksti "" :luokka "yhteensa"}
                           {:teksti "" :luokka "yhteensa"}
                           #_ {:teksti (fmt/euro-opt false tarjouksen-maara) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                           {:teksti (if-not (= 0 yht)
                                      (fmt/euro-opt false yht)
                                      "-")
                            :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                           {:teksti "" :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]
                          (>= urakan-alkuvuosi 2025)
                          [^{:luokka "kustannukset-yhteenveto"}
                           {:teksti "Yhteensä 2025" :luokka "yhteensa"}
                           {:teksti (fmt/euro-opt false yht) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                           {:teksti (if-not (= 0 yht-indeksikorjattu)
                                      (fmt/euro-opt false yht-indeksikorjattu)
                                      "-")
                            :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}])
        yhteenveto-rivit [yhteenveto-rivi kirjaamatta-rivi]
        _ (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom johto-ja-hallintokorvaukset)
        muokkaus-toimenkuvat (into {} (mapv (fn [toimenkuva]
                                              {(:toimenkuva toimenkuva) toimenkuva})
                                        johto-ja-hallintokorvaukset))
        toimenkuvat-atom (r/atom muokkaus-toimenkuvat)]
    [:div#johto-ja-hallintokorvaus-elementti.row.kustannussuunnitelma-osio.kapea-osio
     [yhteiset/otsikkotiedot e! app "Johto- ja hallintokorvaus" tarjouksen-maara pysyvamuutos-maara yht yht-indeksikorjattu
      {:div1 true :div2 false :div3 (if (< valittu-vuosi yhteiset/rajavuosi) true false) :div4 true} valittu-vuosi]

     [:div.row [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]

     (when-not vahvistettu?
       [:div
        [:div.row
         [:div.col-xs-12.body-text "Harja luo kulut kuukausille, kun tallennat tiedot."]]
        (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
          #(e! (kust-tiedot/->TallennaJohtoJaHallintokorvaukset @yhteiset/grid-johto-ja-hallintokorvaukset-atom urakan-alkuvuosi))
          (when (>= urakan-alkuvuosi 2025) #(e! (kust-tiedot/->JaaJohtoJaHallintokorvauksetTasan tarjouksen-maara "johto-ja-hallintokorvaus-elementti"))))])

     [:div.row
      [:div.col-xs-12
       ;; 2019 - 2021
       ;;TODO: Laita urakka_parametrit tauluun tieto, että onko tunnit ja tuntipalkat vai kuukausisumma
       (when (<= urakan-alkuvuosi 2021)
         [grid/muokkaus-grid
          {:otsikko ""
           :id "toimenkuvat-taulukko"
           :luokat ["poista-bottom-margin"]
           :voi-lisata? false
           :voi-kumota? false
           :on-rivi-blur (fn [rivi]
                           (let [muokattu-toimenkuva (first (filter
                                                              #(= (:id rivi) (:id %))
                                                              @yhteiset/grid-johto-ja-hallintokorvaukset-atom))
                                 muokattu-toimenkuva (assoc muokattu-toimenkuva :tunnit (:tunnit rivi) :tuntipalkka (:tuntipalkka rivi))
                                 toimenkuvat-ilman-muutettavaa (remove
                                                                 #(= (:id rivi) (:id %))
                                                                 @yhteiset/grid-johto-ja-hallintokorvaukset-atom)
                                 uudet-toimenkuvat (sort-by :id (conj toimenkuvat-ilman-muutettavaa muokattu-toimenkuva))
                                 toimenkuvat (reduce (fn [uudet-toimenkuvat toimenkuva]
                                                       (let [;; Laske toimenkuvan kokonaissumma kuudaudelle
                                                             summa (if (and (:tuntipalkka toimenkuva) (:tunnit toimenkuva)) (* (:tunnit toimenkuva) (:tuntipalkka toimenkuva)) 0)
                                                             toimenkuva (assoc toimenkuva :summa summa)
                                                             vuoden-tunnit (if (:tunnit toimenkuva) (:tunnit toimenkuva) 0)
                                                             ;; Laske toimenkuvan kuukausille tunnit.
                                                             vuoden-tunnit (tyokalut/round2 2 vuoden-tunnit)
                                                             kuukausimaara (count (:kuukaudet toimenkuva))
                                                             kk-tunnit (/ vuoden-tunnit kuukausimaara)
                                                             viimeneinen-tunnit (- vuoden-tunnit (tyokalut/round2 2 (* (dec kuukausimaara) kk-tunnit)))
                                                             kuukaudet (map-indexed (fn [indeksi rivi]
                                                                                      (merge rivi
                                                                                        {:tunnit (if (= indeksi (dec kuukausimaara)) viimeneinen-tunnit kk-tunnit)
                                                                                         :yhteensa-kk (* (if (= indeksi (dec kuukausimaara)) viimeneinen-tunnit kk-tunnit) (:tuntipalkka rivi))}))
                                                                         (:kuukaudet toimenkuva))
                                                             kuukaudet (sort-by (juxt :vuosi :kuukausi) kuukaudet)
                                                             toimenkuva (assoc toimenkuva :kuukaudet kuukaudet)]
                                                         (conj uudet-toimenkuvat toimenkuva)))
                                               [] uudet-toimenkuvat)
                                 toimenkuvat (sort-by :id toimenkuvat)]

                             (reset! yhteiset/tallenna-painettu false)
                             (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom toimenkuvat)
                             (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset toimenkuvat))))
           :jarjesta :id
           :piilota-toiminnot? true
           :voi-muokata? voi-muokata?
           :voi-poistaa? (constantly false)
           :vetolaatikot (into {}
                           (map (juxt :toimenkuva (fn [rivi] [toimenkuvat-vetolaatikko-2019 e! (:vetolaatikon-muokkaus app) (:id rivi) (:kuukaudet rivi) vahvistettu?]))
                             johto-ja-hallintokorvaukset))

           :vetolaatikko-optiot {:ei-paddingia true}
           ;; Lisätään yhteenveto rivi gridin päätteeksi
           :rivi-jalkeen yhteenveto-rivit #_  kirjaamatta-rivi #_ yhteenveto-rivit}
          [{:otsikko "Toimenkuva" :nimi :toimenkuva :tyyppi :string :leveys "35%"
            :muokattava? (constantly false) :otsikkorivi-luokka "korkea" :fmt #(when % (str/capitalize %))}
           {:otsikko "" :tyyppi :vetolaatikon-tila :leveys "5%" :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
           {:otsikko "Tunnit/kk, h" :nimi :tunnit :leveys "15%" :tyyppi :positiivinen-numero :tasaa :oikea
            :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
           {:otsikko "Tuntipalkka, €" :nimi :tuntipalkka :leveys "15%" :tyyppi :positiivinen-numero :tasaa :oikea
            :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
           {:otsikko "Vuosipalkka, €" :nimi :summa :leveys "20%" :tyyppi :euro :tasaa :oikea
            :fmt #(when % (fmt/euro-opt false %))
            :muokattava? (constantly false)
            :voi-muokata-rivia? (constantly true)
            :otsikkorivi-luokka "korkea"}
           {:otsikko "kk/v" :nimi :kkv :leveys "10%" :tyyppi :euro :tasaa :oikea
            :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
          toimenkuvat-atom])
       ;; 2022 - 2024 asti
       (when (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024))
         [grid/grid {:otsikko ""
                     :luokat ["matala-panel"]
                     ;:muokkaa-aina voi-muokata?
                     :voi-muokata? voi-muokata?
                     :muokattava? (constantly voi-muokata?)
                     :voi-poistaa? (constantly false)
                     :voi-lisata? false
                     :voi-kumota? false
                     :piilota-toiminnot? false
                     :tunniste :id
                     :muutos #(do
                                (reset! yhteiset/tallenna-painettu false)
                                (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom (vals (grid/hae-muokkaustila %)))
                                (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset (vals (grid/hae-muokkaustila %))))
                                (reset! yhteiset/virheet-atom (grid/hae-virheet %)))
                     ;; Lisätään yhteenveto rivi gridin päätteeksi
                     :rivi-jalkeen-fn (fn [rivit]
                                        ^{:luokka "yhteenveto"}
                                        yhteenveto-rivit)
                     :rivin-luokka (fn [_] "korkea")
                     :vetolaatikot (into {}
                                     (map (juxt :id (fn [rivi] [toimenkuvat-vetolaatikko-2022 e! (:vetolaatikon-muokkaus app) (:id rivi) (:kuukaudet rivi) vahvistettu?]))
                                       johto-ja-hallintokorvaukset))}
          [{:otsikko "" :tyyppi :vetolaatikon-tila :leveys "5%" :muokattava? (constantly false)}
           {:otsikko "Toimenkuva" :nimi :toimenkuva :tyyppi :string :leveys "50%"
            :muokattava? (constantly false) :otsikkorivi-luokka "korkea" :fmt #(when % (str/capitalize %))}
           {:otsikko "Tarjouksen määrä (€)" :nimi :tarjous-summa :leveys "25%" :tyyppi :euro :tasaa :oikea
            :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
           {:otsikko "Suunniteltu määrä (€)" :nimi :summa :leveys "25%" :tyyppi :euro :tasaa :oikea
            :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
          johto-ja-hallintokorvaukset])

       ;-25 eteenäin
       (when (>= urakan-alkuvuosi 2025)
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
                                        yhteenveto-rivit)
                     :rivin-luokka (fn [_] "korkea")}
          [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "60%"
            :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
           {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "20%" :tyyppi :euro :tasaa :oikea
            :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
           {:otsikko "Indeksikorjattu (€)" :nimi :summa_indeksikorjattu :leveys "20%" :tyyppi :euro :tasaa :oikea
            :fmt #(if-not (= 0 yht-indeksikorjattu) (fmt/euro-opt false %) "-") :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
          johto-ja-hallintokorvaukset])]]

     (when-not vahvistettu?
       [:div

        (when (:johto-ja-hallintokorvaukset-virheet kustannussuunnitelma)
          [:div.row {:style {:margin-bottom "1rem"}}
           [:div.col-xs-12
            [yleiset/info-laatikko :varoitus (:johto-ja-hallintokorvaukset-virheet kustannussuunnitelma) nil nil {:sulje-nappi-id (gensym)}]]])

        (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
          #(e! (kust-tiedot/->TallennaJohtoJaHallintokorvaukset @yhteiset/grid-johto-ja-hallintokorvaukset-atom urakan-alkuvuosi))
          (when (>= urakan-alkuvuosi 2025) #(e! (kust-tiedot/->JaaJohtoJaHallintokorvauksetTasan tarjouksen-maara "johto-ja-hallintokorvaus-elementti"))))])]))
