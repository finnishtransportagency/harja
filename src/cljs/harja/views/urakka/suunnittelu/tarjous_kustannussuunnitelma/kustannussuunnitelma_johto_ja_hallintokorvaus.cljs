(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuunnitelma-johto-ja-hallintokorvaus
  "Tämä komponentti näyttää Johto- ja hallintokorvaukset kustannussuunnitelmassa.
  Johto- ja hallintokorvaukset koostuvat toimenkuvista, joiden syöttämisen tarkkuus vaihtelee urakan alkuvuoden perusteella.
  -19 - 21 vuosina alkavat urakat syöttävät tunnit ja tuntihinnat jokaiselle toimenkuvalle.
  -22 - 24 vuosina alkavat urakat syöttävät kuukausisumman toimenkuvalle.
  -25 ja sitä myöhemmät urakat syöttävät vain kuukausisumman koko toimenkuva kokonaisuudelle, kuten se tarjouksessakin on.
  Käyttöliittymä yksinkertaistuu vuosien myötä, koska tarkkuus vähenee."
  (:require [clojure.string :as str]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.yleiset :as tyokalut]
            [harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain :as kust-domain]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as kust-tiedot]
            [harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.yhteiset :as yhteiset]
            [reagent.core :as r])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn- kuukausierat-modaali [valittu-hoitokausi johto-ja-hallintokorvaukset]
  (let [urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))
        hoitovuoden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        kalenterikuukaudet (mapv (fn [kuukausi]
                                   (let [vuosi (if (>= kuukausi 10)
                                                 hoitovuoden-alkuvuosi
                                                 (inc hoitovuoden-alkuvuosi))]
                                     (pvm/koko-kuukausi-ja-vuosi
                                       (pvm/->pvm (str "01." kuukausi "." vuosi)) true)))

                             [10 11 12 1 2 3 4 5 6 7 8 9])
        kuukaudet (if (<= urakan-alkuvuosi 2024)
                    (reduce (fn [lopputulos kalenterikuukausi]
                              (let [kuukaudet (reduce (fn [kuukaudet toimenkuva]
                                                        (let [kk (first (filter
                                                                          #(= (:kalenterikuukausi %) kalenterikuukausi)
                                                                          (:kuukaudet toimenkuva)))]
                                                          (conj kuukaudet kk)))
                                                [] johto-ja-hallintokorvaukset)
                                    summa (apply + (map :yhteensa-kk kuukaudet))
                                    summa-indeksikorjattu (apply + (map :yhteensa-indeksikorjattu-kk kuukaudet))]

                                (conj
                                  lopputulos
                                  {:kalenterikuukausi kalenterikuukausi
                                   :summa summa
                                   :summa-indeksikorjattu summa-indeksikorjattu})))
                      [] kalenterikuukaudet)
                    (map (fn [kuukausi]
                           {:kalenterikuukausi (:kalenterikuukausi kuukausi)
                            :summa (:summa kuukausi)
                            :summa-indeksikorjattu (:summa-indeksikorjattu kuukausi)})
                      johto-ja-hallintokorvaukset))
        yht (apply + (map :summa kuukaudet))
        yht-indeksikorjattu (apply + (map :summa-indeksikorjattu kuukaudet))]

    [:div
     [:div.flex-row
      [:div.body-text {:style {:margin-top "-15px"}} (fmt/hoitokauden-jarjestysluku-ja-vuodet (pvm/vuosi (first valittu-hoitokausi)) hoitovuodet "Hoitovuosi")]]

     [:div.row {:style {:padding-top "1rem"}}]

     (for* [kuukausi kuukaudet]
       [:div.flex-row.kuukausi-rivi
        [:div.col-xs-6 (:kalenterikuukausi kuukausi)]
        [:div.col-xs-3.oikealle (fmt/euro-opt true (:summa kuukausi))]
        [:div.col-xs-3.oikealle (fmt/euro-opt (:summa-indeksikorjattu kuukausi))]])

     [:hr.hr-tiivis]

     [:div.flex-row.laskenta-rivi
      [:div.col-xs-6 [:strong (fmt/hoitokauden-jarjestysluku-ja-vuodet (pvm/vuosi (first valittu-hoitokausi)) hoitovuodet "Hoitovuosi") " yhteensä"]]
      [:div.col-xs-3.oikealle [:strong (fmt/euro-opt true yht)]]
      [:div.col-xs-3.oikealle [:strong (fmt/euro-opt yht-indeksikorjattu)]]]]))

(defn muut-kulut-vetolaatikko
  "Muut kulut ei ole toimenkuva, joten joudumme renderöimään sen vetolaatikon eritavalla"
  [e! vetolaatikon-muokkaus kuukaudet vahvistettu?]
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
       {:id (str "kuukausitaulukko-muut-kulut")
        :voi-poistaa? (constantly false)
        :voi-lisata? false
        :piilota-toiminnot? true
        :muokkauspaneeli? false
        :jarjesta (juxt :vuosi :kuukausi)
        :voi-muokata? (not vahvistettu?)
        :on-rivi-blur (fn [kuukausi-rivi]
                        (let [muut-kulut-toimenkuva (first (filter
                                                             #(= "Muut kulut" (:toimenkuva %))
                                                             @yhteiset/grid-johto-ja-hallintokorvaukset-atom))
                              toimenkuvat-ilman-muutettavaa (remove
                                                              #(= "Muut kulut" (:toimenkuva %))
                                                              @yhteiset/grid-johto-ja-hallintokorvaukset-atom)
                              valittu-kuukausi (first (filter
                                                        (fn [kuukausi]
                                                          (and (= (:kuukausi kuukausi-rivi) (:kuukausi kuukausi))
                                                            (= (:vuosi kuukausi-rivi) (:vuosi kuukausi))))
                                                        (:kuukaudet muut-kulut-toimenkuva)))
                              kuukaudet-ilman-valittua (remove
                                                         (fn [kuukausi]
                                                           (and (= (:kuukausi kuukausi-rivi) (:kuukausi kuukausi))
                                                             (= (:vuosi kuukausi-rivi) (:vuosi kuukausi))))
                                                         (:kuukaudet muut-kulut-toimenkuva))

                              valittu-kuukausi (assoc valittu-kuukausi :yhteensa-kk (:yhteensa-kk kuukausi-rivi))
                              uudet-kuukaudet (sort-by :vuosi :kuukausi (conj kuukaudet-ilman-valittua valittu-kuukausi))


                              ;; Lasketaan suunniteltu määrä uusiksi.
                              kuukaudet (map (fn [rivi]
                                               (assoc rivi :yhteensa-kk (if (:yhteensa-kk rivi) (:yhteensa-kk rivi) 0)))
                                          uudet-kuukaudet)
                              suunniteltu-yht (apply + (map #(if (:yhteensa-kk %) (:yhteensa-kk %) 0) kuukaudet))
                              muut-kulut (assoc muut-kulut-toimenkuva
                                           :tunnit nil
                                           :tuntipalkka nil
                                           :kuukaudet (vec (sort-by (juxt :vuosi :kuukausi) kuukaudet))
                                           :summa suunniteltu-yht
                                           :summa-indeksikorjattu nil) ;; indeksikorjaus lasketaan bäckendissä
                              _ (reset! yhteiset/tallenna-painettu false)
                              uudet-toimenkuvat (sort-by :jarjestys (conj toimenkuvat-ilman-muutettavaa muut-kulut))
                              _ (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom uudet-toimenkuvat)
                              _ (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset uudet-toimenkuvat))]))
        :voi-kumota? false}
       [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "40%"
         :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
        {:otsikko "Yhteensa (€)" :nimi :yhteensa-kk :leveys "20%" :tyyppi :euro :tasaa :oikea
         :fmt #(fmt/euro-opt false (or % 0)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
        {:otsikko "Indeksikorjattu (€)" :nimi :yhteensa-indeksikorjattu-kk :leveys "20%" :tyyppi :euro :tasaa :oikea
         :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
       kuukaudet-atom]]]))

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
        :jarjesta (juxt :vuosi :kuukausi)
        :voi-muokata? (not vahvistettu?)
        :on-rivi-blur (fn [kuukausi-rivi]
                        (let [toimenkuva (first (filter
                                                  #(= toimenkuva-id (:id %))
                                                  @yhteiset/grid-johto-ja-hallintokorvaukset-atom))
                              toimenkuvat-ilman-muutettavaa (remove
                                                              #(= toimenkuva-id (:id %))
                                                              @yhteiset/grid-johto-ja-hallintokorvaukset-atom)
                              valittu-kuukausi (first (filter
                                                        (fn [kuukausi]
                                                          (and (= (:kuukausi kuukausi-rivi) (:kuukausi kuukausi))
                                                            (= (:vuosi kuukausi-rivi) (:vuosi kuukausi))))
                                                        (:kuukaudet toimenkuva)))
                              kuukaudet-ilman-valittua (remove
                                                         (fn [kuukausi]
                                                           (and (= (:kuukausi kuukausi-rivi) (:kuukausi kuukausi))
                                                             (= (:vuosi kuukausi-rivi) (:vuosi kuukausi))))
                                                         (:kuukaudet toimenkuva))

                              valittu-kuukausi (assoc valittu-kuukausi :tunnit (:tunnit kuukausi-rivi))
                              uudet-kuukaudet (sort-by :vuosi :kuukausi (conj kuukaudet-ilman-valittua valittu-kuukausi))


                              ;; Lasketaan suunniteltu määrä uusiksi.
                              kuukaudet (map (fn [rivi]
                                               (assoc rivi :yhteensa-kk (* (if (:tuntipalkka rivi) (:tuntipalkka rivi) 0) (if (:tunnit rivi) (:tunnit rivi) 0))))
                                          uudet-kuukaudet)
                              suunniteltu-yht (apply + (map #(if (and (:tuntipalkka %) (:tunnit %)) (* (:tunnit %) (:tuntipalkka %)) 0) kuukaudet))
                              toimenkuva (assoc toimenkuva
                                           :tunnit (if (kust-domain/onko-tunnit-samat? kuukaudet) (:tunnit (first kuukaudet))
                                                     nil) ;; Aseta arvo nil, jos tunnit eivät ole samat kaikissa kuukausissa
                                           :kuukaudet (vec (sort-by (juxt :vuosi :kuukausi) kuukaudet))
                                           :summa suunniteltu-yht
                                           :summa-indeksikorjattu nil) ;; indeksikorjaus lasketaan bäckendissä
                              _ (reset! yhteiset/tallenna-painettu false)
                              uudet-toimenkuvat (sort-by :jarjestys (conj toimenkuvat-ilman-muutettavaa toimenkuva))
                              _ (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom uudet-toimenkuvat)
                              _ (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset uudet-toimenkuvat))]))
        :voi-kumota? false}
       [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "40%"
         :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
        {:otsikko "Tunnit (h)" :nimi :tunnit :leveys "20%" :tyyppi :euro :tasaa :oikea
         :fmt #(when % (fmt/euro-opt false %)) :voi-muokata? voi-muokata? :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
        {:otsikko "Yhteensa (€)" :nimi :yhteensa-kk :leveys "20%" :tyyppi :euro :tasaa :oikea
         :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
        {:otsikko "Indeksikorjattu (€)" :nimi :yhteensa-indeksikorjattu-kk :leveys "20%" :tyyppi :euro :tasaa :oikea
         :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
       kuukaudet-atom]]]))

(defn taulukko-2021 [e! app voi-muokata? johto-ja-hallintokorvaukset vahvistettu? yhteenveto-rivit]
  (let [muokkaus-toimenkuvat (into {} (mapv (fn [toimenkuva]
                                              {(:nimike toimenkuva) toimenkuva})
                                        johto-ja-hallintokorvaukset))
        toimenkuvat-atom (r/atom muokkaus-toimenkuvat)

        voiko-muokata? (fn [rivi voi-muokata? on-muu-kulu-kolumni?]
                         (let [tulos (cond
                                       (and (= "Muut kulut" (:toimenkuva rivi)) voi-muokata? on-muu-kulu-kolumni?)
                                       true
                                       (and (= "Muut kulut" (:toimenkuva rivi)) (false? voi-muokata?))
                                       false
                                       (and (not= "Muut kulut" (:toimenkuva rivi)) (not on-muu-kulu-kolumni?))
                                       voi-muokata?
                                       :else false)]
                           tulos))]
    [grid/muokkaus-grid
     {:otsikko ""
      :id "toimenkuvat-taulukko"
      :luokat ["poista-bottom-margin"]
      :voi-lisata? false
      :voi-kumota? false
      :on-rivi-blur (fn [rivi]
                      (let [muokattu-toimenkuva (first (filter
                                                         #(= (:nimike rivi) (:nimike %))
                                                         @yhteiset/grid-johto-ja-hallintokorvaukset-atom))
                            muokattu-toimenkuva (assoc muokattu-toimenkuva
                                                  :tunnit (if (and (not= (:toimenkuva rivi) "Muut kulut") (= nil (:tunnit rivi))) 0 (:tunnit rivi)) ;; nil arvoa käytetään, jos tunnit eivät ole samat kaikissa kuukausissa
                                                  :tuntipalkka (if (and (not= (:toimenkuva rivi) "Muut kulut") (= nil (:tuntipalkka rivi))) 0 (:tuntipalkka rivi))
                                                  :summa (:summa rivi))
                            toimenkuvat-ilman-muutettavaa (remove
                                                            #(= (:nimike rivi) (:nimike %))
                                                            @yhteiset/grid-johto-ja-hallintokorvaukset-atom)
                            uudet-toimenkuvat (sort-by :jarjestys (conj toimenkuvat-ilman-muutettavaa muokattu-toimenkuva))
                            toimenkuvat (reduce (fn [uudet-toimenkuvat toimenkuva]
                                                  (let [;; Laske toimenkuvan kokonaissumma kuudaudelle
                                                        summa (cond
                                                                ;; Toimenkuvat
                                                                (and (:tuntipalkka toimenkuva) (:tunnit toimenkuva) (not= (:toimenkuva rivi) "Muut kulut"))
                                                                (* (:tunnit toimenkuva) (:tuntipalkka toimenkuva))
                                                                ;; Muut kulut
                                                                (and (:summa toimenkuva) (= (:toimenkuva rivi) "Muut kulut"))
                                                                (:summa toimenkuva)
                                                                :else 0)

                                                        toimenkuva (assoc toimenkuva :summa summa)

                                                        ;; Koskee vain Muut Kulut riviä.
                                                        kk-summa (tyokalut/round2 2 (/ summa 12))
                                                        viimeneinen-summa (- summa (tyokalut/round2 2 (* 11 kk-summa)))

                                                        ;; Laske toimenkuvan yhteensä summat kuukausista
                                                        kuukaudet (map-indexed (fn [indeksi rivi]
                                                                                 (merge rivi
                                                                                   {:tuntipalkka (:tuntipalkka toimenkuva)
                                                                                    :tuntipalkka-indeksikorjattu nil ;; indeksikorjaus lasketaan bäckendissä
                                                                                    :tunnit (:tunnit toimenkuva)
                                                                                    :yhteensa-kk (if (not= (:toimenkuva rivi) "Muut kulut")
                                                                                                   (* (:tuntipalkka toimenkuva) (:tunnit toimenkuva))
                                                                                                   (if (= indeksi 11) viimeneinen-summa kk-summa))
                                                                                    :yhteensa-indeksikorjattu-kk nil}))
                                                                    (:kuukaudet toimenkuva))
                                                        yht-kk (apply + (map :yhteensa-kk kuukaudet))
                                                        kuukaudet (vec (sort-by (juxt :vuosi :kuukausi) kuukaudet))
                                                        toimenkuva (assoc toimenkuva :kuukaudet kuukaudet
                                                                     :summa yht-kk)]
                                                    (conj uudet-toimenkuvat toimenkuva)))
                                          [] uudet-toimenkuvat)
                            toimenkuvat (sort-by :jarjestys toimenkuvat)]

                        (reset! yhteiset/tallenna-painettu false)
                        (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom toimenkuvat)
                        (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset toimenkuvat))
                        (e! (kust-tiedot/->AsetaJJHMuutos))))
      :jarjesta :jarjestys
      :piilota-toiminnot? true
      :voi-muokata? true
      :voi-poistaa? (constantly false)
      :vetolaatikot (into {}
                      (map (juxt :nimike (fn [rivi]
                                           (if (= "Muut kulut" (:nimike rivi))
                                             [muut-kulut-vetolaatikko e! (:vetolaatikon-muokkaus app) (:kuukaudet rivi) vahvistettu?]
                                             [toimenkuvat-vetolaatikko-2019 e! (:vetolaatikon-muokkaus app) (:id rivi) (:kuukaudet rivi) vahvistettu?])))
                        johto-ja-hallintokorvaukset))

      :vetolaatikko-optiot {:ei-paddingia true}
      ;; Lisätään yhteenveto rivi gridin päätteeksi
      :rivi-jalkeen yhteenveto-rivit}
     [{:otsikko "" :tyyppi :vetolaatikon-tila :leveys "4%" :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
      {:otsikko "Toimenkuva" :nimi :nimike :tyyppi :string :leveys "31%"
       :muokattava? (constantly false) :otsikkorivi-luokka "korkea" :fmt #(when % (str/capitalize %))}
      {:otsikko "Tarjouksen määrä (€ / vuosi)" :nimi :tarjous-summa :leveys "20%" :tyyppi :positiivinen-numero :tasaa :oikea
       :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
      {:otsikko "Tunnit (h/kk)" :nimi :tunnit :leveys "15%" :tyyppi :positiivinen-numero :tasaa :oikea
       :fmt #(when % (fmt/euro-opt false %)) :muokattava? #(voiko-muokata? % voi-muokata? false) :otsikkorivi-luokka "korkea"}
      {:otsikko "Tuntipalkka (€/h)" :nimi :tuntipalkka :leveys "15%" :tyyppi :positiivinen-numero :tasaa :oikea
       :fmt #(fmt/euro-opt false (or % 0)) :muokattava? #(voiko-muokata? % voi-muokata? false) :otsikkorivi-luokka "korkea"}
      {:otsikko "Yhteensä (€/vuosi)" :nimi :summa :leveys "20%" :tyyppi :euro :tasaa :oikea
       :fmt #(fmt/euro-opt false (or % 0))
       :muokattava? #(voiko-muokata? % voi-muokata? true)
       :voi-muokata-rivia? (constantly true)
       :otsikkorivi-luokka "korkea"}
      {:otsikko "Kk/v" :nimi :kkv :leveys "10%" :tyyppi :euro :tasaa :oikea
       :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
     toimenkuvat-atom]))

(defn toimenkuvat-vetolaatikko-2022
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
       {:id (str "kuukausitaulukko-2022-" toimenkuva-id)
        :voi-poistaa? (constantly false)
        :voi-lisata? false
        :piilota-toiminnot? true
        :muokkauspaneeli? false
        :jarjesta (juxt :vuosi :kuukausi)
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

                              valittu-kuukausi (assoc valittu-kuukausi :tuntipalkka (:tuntipalkka rivi)
                                                 :tuntipalkka-indeksikorjattu nil) ;; indeksikorjaus lasketaan bäckendissä
                              uudet-kuukaudet (sort-by :vuosi :kuukausi (conj kuukaudet-ilman-valittua valittu-kuukausi))


                              ;; Lasketaan suunniteltu määrä uusiksi.
                              kuukaudet (map (fn [rivi]
                                               (assoc rivi :yhteensa-kk (* (if (:tuntipalkka rivi) (:tuntipalkka rivi) 0) (if (:tunnit rivi) (:tunnit rivi) 0))))
                                          uudet-kuukaudet)
                              suunniteltu-yht (apply + (map #(if (and (:tuntipalkka %) (:tunnit %)) (* (:tunnit %) (:tuntipalkka %)) 0) kuukaudet))
                              toimenkuva (assoc toimenkuva
                                           :tunnit (apply + (map (fn [rivi] (or (:tunnit rivi) 0)) kuukaudet))
                                           :kuukaudet (vec (sort-by (juxt :vuosi :kuukausi) kuukaudet))
                                           :summa suunniteltu-yht
                                           :summa-indeksikorjattu nil) ;; indeksikorjaus lasketaan bäckendissä
                              _ (reset! yhteiset/tallenna-painettu false)
                              uudet-toimenkuvat (sort-by :jarjestys (conj toimenkuvat-ilman-muutettavaa toimenkuva))
                              _ (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom uudet-toimenkuvat)
                              _ (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset uudet-toimenkuvat))]))
        :voi-kumota? false}
       [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "70%"
         :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
        {:otsikko "Suunniteltu määrä (€)" :nimi :tuntipalkka :leveys "30%" :tyyppi :euro :tasaa :oikea
         :fmt #(fmt/euro-opt false (or % 0)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
        {:otsikko "Indeksikorjattu (€)" :nimi :tuntipalkka-indeksikorjattu :leveys "30%" :tyyppi :euro :tasaa :oikea
         :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}]
       kuukaudet-atom]]]))

(defn taulukko-2022 [e! app voi-muokata? johto-ja-hallintokorvaukset vahvistettu? yhteenveto-rivit]
  (let [muokkaus-toimenkuvat (into {} (mapv (fn [toimenkuva]
                                              {(:toimenkuva toimenkuva) toimenkuva})
                                        johto-ja-hallintokorvaukset))
        toimenkuvat-atom (r/atom muokkaus-toimenkuvat)]
    [:div [grid/muokkaus-grid
           {:otsikko ""
            :id "toimenkuvat-taulukko-2022"
            :jarjesta :jarjestys
            :luokat ["poista-bottom-margin"]
            :voi-lisata? false
            :voi-kumota? false
            :on-rivi-blur (fn [rivi]
                            (let [muokattu-toimenkuva (first (filter
                                                               #(= (:id rivi) (:id %))
                                                               @yhteiset/grid-johto-ja-hallintokorvaukset-atom))
                                  ;; Vuoden 2022 toimenkuvissa ei ole enää tunteja ja tuntihintaa frontissa, vaan vain kuukausisumma.
                                  muokattu-toimenkuva (assoc muokattu-toimenkuva :summa (:summa rivi)
                                                        :summa-indeksikorjattu nil) ;; indeksikorjaus lasketaan bäckendissä
                                  toimenkuvat-ilman-muutettavaa (remove
                                                                  #(= (:id rivi) (:id %))
                                                                  @yhteiset/grid-johto-ja-hallintokorvaukset-atom)
                                  uudet-toimenkuvat (sort-by :jarjestys (conj toimenkuvat-ilman-muutettavaa muokattu-toimenkuva))
                                  toimenkuvat (reduce (fn [uudet-toimenkuvat toimenkuva]
                                                        (let [;; Laske toimenkuvan kokonaissumma kuudaudelle
                                                              summa (:summa toimenkuva)
                                                              ;; Lasketaan kuukausisummat uusiksi toimenkuvan kokonaissummasta.
                                                              kuukausimaara (count (:kuukaudet toimenkuva))
                                                              kk-summa (tyokalut/round2 2 (/ summa kuukausimaara))
                                                              viimeneinen-summa (- summa (tyokalut/round2 2 (* (dec kuukausimaara) kk-summa)))
                                                              kuukaudet (map-indexed (fn [indeksi rivi]
                                                                                       (merge rivi
                                                                                         {:tuntipalkka (if (= indeksi 11) viimeneinen-summa kk-summa)
                                                                                          :tuntipalkka-indeksikorjattu nil}
                                                                                         (when (= "Muut kulut" (:toimenkuva toimenkuva))
                                                                                           {:yhteensa-kk (if (= indeksi 11) viimeneinen-summa kk-summa)})))
                                                                          (:kuukaudet toimenkuva))
                                                              toimenkuva (assoc toimenkuva :kuukaudet (vec (sort-by (juxt :vuosi :kuukausi) kuukaudet)))]
                                                          (conj uudet-toimenkuvat toimenkuva)))
                                                [] uudet-toimenkuvat)
                                  toimenkuvat (sort-by :jarjestys toimenkuvat)]
                              (reset! yhteiset/tallenna-painettu false)
                              (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom toimenkuvat)
                              (e! (kust-tiedot/->PaivitaJohtoJaHallintokorvaukset toimenkuvat))
                              (e! (kust-tiedot/->AsetaJJHMuutos))))
            :piilota-toiminnot? true
            :voi-muokata? voi-muokata?
            :voi-poistaa? (constantly false)
            :vetolaatikot (into {}
                            (map (juxt :toimenkuva (fn [rivi] [toimenkuvat-vetolaatikko-2022 e! (:vetolaatikon-muokkaus app) (:id rivi) (:kuukaudet rivi) vahvistettu?]))
                              johto-ja-hallintokorvaukset))

            :vetolaatikko-optiot {:ei-paddingia true}
            ;; Lisätään yhteenveto rivi gridin päätteeksi
            :rivi-jalkeen yhteenveto-rivit}
           [{:otsikko "" :tyyppi :vetolaatikon-tila :leveys "5%" :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
            {:otsikko "Toimenkuva" :nimi :toimenkuva :tyyppi :string :leveys "50%"
             :muokattava? (constantly false) :otsikkorivi-luokka "korkea" :fmt #(when % (str/capitalize %))}
            {:otsikko "Tarjouksen määrä (€)" :nimi :tarjous-summa :leveys "25%" :tyyppi :euro :tasaa :oikea
             :fmt #(when % (fmt/euro-opt false %)) :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
            {:otsikko "Suunniteltu määrä (€)" :nimi :summa :leveys "25%" :tyyppi :euro :tasaa :oikea
             :fmt #(fmt/euro-opt false (or % 0)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}]
           toimenkuvat-atom]]))

(defn johto-ja-hallintokorvaus [e! {:keys [valittu-hoitokausi tallennus-kesken? tarjous
                                           kustannussuunnitelma urakan-alkuvuosi tulevaisuudessa-arvoja?
                                           onko-jjh-muutoksia? viimeinen-hoitovuosi?] :as app}]
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
        toimenpiteiden-kuukaudet (flatten (map :kuukaudet johto-ja-hallintokorvaukset))

        yht (apply + (if (<= urakan-alkuvuosi 2021)
                       (map #(:yhteensa-kk % 0) toimenpiteiden-kuukaudet)
                       (map #(:summa % 0) johto-ja-hallintokorvaukset)))
        yht-indeksikorjattu (apply + (if (<= urakan-alkuvuosi 2021)
                                       (map #(:yhteensa-indeksikorjattu-kk % 0) toimenpiteiden-kuukaudet)
                                       (map #(:summa_indeksikorjattu % 0) johto-ja-hallintokorvaukset)))
        kirjaamatta (tyokalut/round2 2 (- tarjouksen-maara yht))
        kirjaamatta-luokka (if (= 0 kirjaamatta) "yhteensa" "yhteensa-punainen")
        kirjaamatta-rivi (cond (and (<= urakan-alkuvuosi 2021) (not vahvistettu?))
                           [^{:luokka "kustannukset-yhteenveto"}
                            {:teksti "" :luokka kirjaamatta-luokka}
                            {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                            {:teksti "" :luokka kirjaamatta-luokka}
                            {:teksti "" :luokka kirjaamatta-luokka}
                            {:teksti "" :luokka kirjaamatta-luokka}
                            {:teksti (fmt/euro-opt false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                            {:teksti "" :luokka kirjaamatta-luokka}]
                           (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024) (not vahvistettu?))
                           [^{:luokka "kustannukset-yhteenveto"}
                            {:teksti "" :luokka kirjaamatta-luokka}
                            {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                            {:teksti "" :luokka kirjaamatta-luokka}
                            {:teksti (fmt/euro-opt false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                            {:teksti "" :luokka kirjaamatta-luokka}]
                           (and (>= urakan-alkuvuosi 2025) (not vahvistettu?))
                           [^{:luokka "kustannukset-yhteenveto"}
                            {:teksti "Kirjaamatta" :luokka kirjaamatta-luokka}
                            {:teksti (fmt/euro-opt false kirjaamatta) :luokka kirjaamatta-luokka :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                            {:teksti "" :luokka kirjaamatta-luokka}])
        yhteenveto-rivi (cond
                          (<= urakan-alkuvuosi 2021)
                          [^{:luokka "kustannukset-yhteenveto"}
                           {:teksti "" :luokka "yhteensa"}
                           {:teksti "Yhteensä" :luokka "yhteensa"}
                           {:teksti (fmt/euro-opt false tarjouksen-maara) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                           {:teksti "" :luokka "yhteensa"}
                           {:teksti "" :luokka "yhteensa"}
                           {:teksti (if-not (= 0 yht)
                                      (fmt/euro-opt false yht)
                                      "-")
                            :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                           {:teksti "" :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]
                          (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024))
                          [^{:luokka "kustannukset-yhteenveto"}
                           {:teksti "" :luokka "yhteensa"}
                           {:teksti "Yhteensä" :luokka "yhteensa"}
                           {:teksti (fmt/euro-opt false tarjouksen-maara) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                           {:teksti (if-not (= 0 yht)
                                      (fmt/euro-opt false yht)
                                      "-")
                            :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}]
                          (>= urakan-alkuvuosi 2025)
                          [^{:luokka "kustannukset-yhteenveto"}
                           {:teksti "Yhteensä" :luokka "yhteensa"}
                           {:teksti (fmt/euro-opt false yht) :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}
                           {:teksti (if-not (= 0 yht-indeksikorjattu)
                                      (fmt/euro-opt false yht-indeksikorjattu)
                                      "-")
                            :luokka "yhteensa" :tyyppi :euro :tasaa :oikea :rivi-disabled? true}])
        yhteenveto-rivit [yhteenveto-rivi (when (>= urakan-alkuvuosi yhteiset/rajavuosi) kirjaamatta-rivi)]
        _ (reset! yhteiset/grid-johto-ja-hallintokorvaukset-atom johto-ja-hallintokorvaukset)]
    [:div#johto-ja-hallintokorvaus-elementti.row.kustannussuunnitelma-osio.osio-976
     [yhteiset/otsikkotiedot valittu-hoitokausi kustannussuunnitelma "Johto- ja hallintokorvaus" tarjouksen-maara pysyvamuutos-maara yht yht-indeksikorjattu
      {:div1 true :div2 false :div3 (if (< valittu-vuosi yhteiset/rajavuosi) true false) :div4 true} valittu-vuosi]

     [:div.row [:div.col-xs-12 [:h3 "Kustannusten erittely"]]]

     (when-not vahvistettu?
       [:div
        [:div.row
         [:div.col-xs-12.body-text (str "Harja luo kulut kuukausille, kun tallennat tiedot. ")
          (when (<= urakan-alkuvuosi 2024)
            [yleiset/linkki "Näytä kuukausierät"
             (fn [] (modal/nayta! {:otsikko "Johto- ja hallintokorvauksen kuukausierät"
                                   :otsikko-muotoilut {:font-size "32px"}
                                   :body-tyyli {:margin-bottom "16px"}
                                   :content-tyyli {:padding-top "24px" :padding-bottom "24px"}
                                   :footer [napit/sulje #(modal/piilota!)]
                                   :footer-tyyli {:text-align "left"}}
                      [kuukausierat-modaali valittu-hoitokausi johto-ja-hallintokorvaukset]))
             {:style {:text-decoration :underline}}])]]
        (yhteiset/tallenna-painike-rivi viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
          #(e! (kust-tiedot/->TallennaJohtoJaHallintokorvaukset @yhteiset/grid-johto-ja-hallintokorvaukset-atom urakan-alkuvuosi false))
          (when (>= urakan-alkuvuosi 2025) #(e! (kust-tiedot/->JaaJohtoJaHallintokorvauksetTasan tarjouksen-maara "johto-ja-hallintokorvaus-elementti")))
          (when-not viimeinen-hoitovuosi?
            #(e! (kust-tiedot/->TallennaJohtoJaHallintokorvaukset @yhteiset/grid-johto-ja-hallintokorvaukset-atom urakan-alkuvuosi true)))
          (:johto-ja-hallintokorvaukset tulevaisuudessa-arvoja?)
          onko-jjh-muutoksia?)])

     [:div.row
      [:div.col-xs-12
       ;; 2019 - 2021
       ;;TODO: Laita urakka_parametrit tauluun tieto, että onko tunnit ja tuntipalkat vai kuukausisumma
       (when (<= urakan-alkuvuosi 2021)
         [taulukko-2021 e! app voi-muokata? johto-ja-hallintokorvaukset vahvistettu? yhteenveto-rivit])
       ;; 2022 - 2024 asti
       (when (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024))
         [taulukko-2022 e! app voi-muokata? johto-ja-hallintokorvaukset vahvistettu? yhteenveto-rivit])

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
                                (reset! yhteiset/virheet-atom (grid/hae-virheet %))
                                (e! (kust-tiedot/->AsetaJJHMuutos)))
                     ;; Lisätään yhteenveto rivi gridin päätteeksi
                     :rivi-jalkeen-fn (fn [rivit]
                                        ^{:luokka "yhteenveto"}
                                        yhteenveto-rivit)
                     :rivin-luokka (fn [_] "korkea")}
          [{:otsikko "Kalenterikuukausi" :nimi :kalenterikuukausi :tyyppi :string :leveys "60%"
            :muokattava? (constantly false) :otsikkorivi-luokka "korkea"}
           {:otsikko "Suunniteltu kustannus (€)" :nimi :summa :leveys "20%" :tyyppi :numero :tasaa :oikea
            :fmt #(fmt/euro-opt false (or % 0)) :muokattava? (constantly voi-muokata?) :otsikkorivi-luokka "korkea"}
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
          #(e! (kust-tiedot/->TallennaJohtoJaHallintokorvaukset @yhteiset/grid-johto-ja-hallintokorvaukset-atom urakan-alkuvuosi false))
          (when (>= urakan-alkuvuosi 2025) #(e! (kust-tiedot/->JaaJohtoJaHallintokorvauksetTasan tarjouksen-maara "johto-ja-hallintokorvaus-elementti")))
          (when-not viimeinen-hoitovuosi?
            #(e! (kust-tiedot/->TallennaJohtoJaHallintokorvaukset @yhteiset/grid-johto-ja-hallintokorvaukset-atom urakan-alkuvuosi true)))
          (:johto-ja-hallintokorvaukset tulevaisuudessa-arvoja?)
          onko-jjh-muutoksia?)])]))
