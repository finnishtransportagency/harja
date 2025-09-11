(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.tarjous-nakyma
  "Kustannussuunnitelman etusivu määrittää, että renderöidäänkö tarjous vai kustannussuunnitelma"
  (:require [harja.fmt :as fmt]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as tarjous-tiedot]
            [tuck.core :as tuck]))

(defonce tallenna-painettu (atom false))
(defonce virheet-atom (atom {}))
;; Määritellään kaikkien kolumnien leveyksiä
(def nimi-leveys 20)
(def yhteensa-leveys 20)

(defn- tallennus-painikkeet [e! {:keys [tallennus-kesken?] :as app}]
  [:div.painikkeet.text-right
   [napit/yleinen-toissijainen "Tyhjennä"
    #(do
       (reset! tallenna-painettu false)
       (e! (tarjous-tiedot/->HaeTyhjatTarjouksenTiedot)))
    {:disabled (or tallennus-kesken? false)}]
   [napit/yleinen-ensisijainen "Tallenna muutokset"
    #(do
       (reset! tallenna-painettu false)
       (e! (tarjous-tiedot/->TallennaTarjouksenTiedot @tarjous-tiedot/grid-tiedot-atom @tarjous-tiedot/grid-toimenkuvat-atom)))
    {:disabled (or tallennus-kesken? false)}]])

(defn- lopullinen-yhteenvetorivi [otsikko rivi]
  (flatten (conj [{:teksti otsikko
                   :luokka "yhteensa disabled lihavoitu"
                   :yhteenveto-vayla true
                   :tyyppi :string}
                  {:teksti ""
                   :luokka "yhteensa lihavoitu"}]
             rivi)))

(defn laske-rivit-yhteen [rivi]
  (let [kustannukset (vals (filter #(str/starts-with? (name (key %)) "vuosi-") rivi))]
    (reduce + kustannukset)))

(defn laske-vuosisummat [rivit vuosikentat]
  (let [vuosidata (->> rivit
                    (map #(select-keys % vuosikentat))
                    (apply merge-with +))
        v (->> vuosidata
            (sort-by key)
            (mapv (fn [[_ arvo]]
                    {:teksti (fmt/euro-opt false arvo)
                     :luokka "yhteensa lihavoitu"
                     :tyyppi :euro
                     :summa arvo
                     :tasaa :oikea
                     :fmt fmt/euro-opt})))
        yhteensa (apply + (map #(get % :summa 0) v))
        v (conj v {:teksti (fmt/euro-opt false yhteensa)
                   :luokka "yhteensa lihavoitu"
                   :tyyppi :euro
                   :tasaa :oikea
                   :fmt fmt/euro-opt})]
    v))

(defn johto-ja-hallintokorvaukset [e! joha-tiedot kaikki-toimenkuvat vuositaulukon-otsikot vuosi-leveys]
  (let [;; Estetään käyttöliittymässä poistettujen toimenkuvien näkyminen listauksessa, vaikka ei ole vielä tallennettu muutoksia kantaan
        toimenkuvat (remove #(true? (:poistettu %)) joha-tiedot)
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        ;; Rajaa toimenkuvavalinnaksi vain ne, jotka eivät ole vielä käytössä
        muut-toimenkuvat (filter
                           (fn [toimenkuva]
                             (not (some #(= (:nimi toimenkuva) (:nimi %)) toimenkuvat)))
                           kaikki-toimenkuvat)
        vuosiavaimet (flatten (map :nimi vuositaulukon-otsikot))
        vuosi-map (zipmap vuosiavaimet (repeat 0))]
    [grid/grid
     {:otsikko ""
      :muokkaa-aina true
      :voi-muokata? true
      :muokattava? (constantly true)
      :voi-poistaa? (constantly false)
      :voi-lisata? true
      :uusi-rivi (fn [rivi]
                   (merge (assoc rivi :id -1 :nimi "" :yhteensa 0)
                     vuosi-map))
      :voi-kumota? false
      :piilota-toiminnot? false
      :tunniste :nimi
      :jarjesta :toimenkuva-id
      :muutos #(do
                 (let [toimenkuvat (vals (grid/hae-muokkaustila %))
                       ;; Jos muutos on ollut uuden rivin lisäys, niin asetetaan valittu toimenkuva
                       toimenkuvat (map (fn [toimenkuva]
                                          (if (= -1 (:id toimenkuva))
                                            (let [uusi-toimenkuva-kaikista (first (filter (fn [t]
                                                                                            (= (:nimi t) (:nimi toimenkuva)))
                                                                                    kaikki-toimenkuvat))]
                                              (assoc toimenkuva
                                                :osio "johto-ja-hallintokorvaus"
                                                :poistettu nil
                                                :yhteensa 0
                                                :rahavaraus-id nil
                                                :toimenkuva-id (:id uusi-toimenkuva-kaikista)))
                                            toimenkuva))
                                     toimenkuvat)]
                   (reset! tallenna-painettu false)
                   (reset! tarjous-tiedot/grid-toimenkuvat-atom toimenkuvat)
                   (reset! virheet-atom (grid/hae-virheet %))))
      :rivi-jalkeen-fn (fn [rivit]
                         (let [vuosi-arvot (map :nimi vuositaulukon-otsikot)
                               yhteenvetorivi (laske-vuosisummat rivit vuosi-arvot)]
                           ^{:luokka "yhteenveto"}
                           (lopullinen-yhteenvetorivi "Johto- ja hallintokorvaus yhteensä" yhteenvetorivi)))}

     ;; Otsikot
     (concat [;; ennen 2025 alkaneet urakat eivät voi valita toimenkuvia tästä tarjouslomakkeesta
              (if (< urakan-alkuvuosi 2025)
                {:otsikko "Johto- ja hallintokorvaus"
                 :nimi :nimi
                 :tyyppi :valinta
                 :valinnat-fn #(map :nimi muut-toimenkuvat)
                 :aseta (fn [rivi arvo]
                          (assoc rivi :id -1 :nimi arvo :paivtetty? true :uusi-nimi arvo :vanha-id (:toimenkuva-id rivi)))
                 :luokka "yhteensa"
                 :leveys (str nimi-leveys "%")
                 :muokattava? (fn [rivi arvo] (if (= -1 (:id rivi)) true false))}
                {:otsikko "Johto- ja hallintokorvaus"
                 :nimi :nimi
                 :tyyppi :valinta
                 :valinnat-fn #(map :nimi muut-toimenkuvat)
                 :aseta (fn [rivi arvo]
                          (assoc rivi :id -1 :nimi arvo :paivtetty? true :uusi-nimi arvo :vanha-id (:toimenkuva-id rivi)))
                 :luokka "yhteensa"
                 :leveys (str nimi-leveys "%")
                 :muokattava? (constantly true)})]
       [;; Poista nappi vain 2025 tai jälkeen alkaneissa urakoissa
        (if (>= urakan-alkuvuosi 2025)
          {:otsikko ""
           :tyyppi :komponentti
           :komponentti (fn [rivi]
                          (napit/yleinen "Poista rivi"
                            :toissijainen
                            #(e! (tarjous-tiedot/->PoistaToimenkuva rivi))
                            {:ikoni (ikonit/livicon-trash) :luokka "btn-xs"}))
           :leveys (str vuosi-leveys "%")}
          {:otsikko ""
           :tyyppi :komponentti
           :komponentti (fn [rivi]
                          [:span])
           :leveys (str vuosi-leveys "%")})]
       (mapv #(assoc % :muokattava false) vuositaulukon-otsikot)
       [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro
         :muokattava? (constantly false) :luokka "yhteensa"
         :hae (fn [rivi] (laske-rivit-yhteen rivi))
         :fmt (fn [arvo] (if arvo (fmt/euro false arvo) 0.00)) :leveys (str yhteensa-leveys "%") :tasaa :oikea}])
     toimenkuvat]))

(defn tarjous-nakyma [e! app]
  (let [tarjouksen-tiedot (:tarjous app)
        hoitokausien-maara (count (:hoitovuosittaiset-arvot (first tarjouksen-tiedot)))
        vuosi-leveys (/ (- 100 nimi-leveys yhteensa-leveys) hoitokausien-maara)
        ;; Muodostetaan otsikot, jotka voivat olla erilaisia eri mittaisilla urakoilla
        vuositaulukon-otsikot (reduce (fn [rivit vuosi-rivi]
                                        (let [index (inc (count rivit))]
                                          (concat rivit [{:otsikko (str index ". Hoitovuosi " (:vuosi vuosi-rivi) " - " (inc (:vuosi vuosi-rivi)) " (€)")
                                                          :nimi (keyword (str "vuosi-" (:vuosi vuosi-rivi)))
                                                          :tyyppi :euro
                                                          :leveys (str vuosi-leveys "%")
                                                          :muokattava? (fn [rivi] (if (or (= (:nimi rivi) "Äkilliset hoitotyöt")
                                                                                        (= (:nimi rivi) "Hoidonjohtopalkkio")
                                                                                        (= (:nimi rivi) "Erillishankinnat")
                                                                                        (= (:nimi rivi) "Vahinkojen korjaukset")
                                                                                        (= (:nimi rivi) "Tarjouksen tavoitehinta")
                                                                                        (= (:nimi rivi) "Tarjouksen kattohinta (1,1 x tarjouksen tavoitehinta)")) false true))
                                                          :tasaa :oikea}])))
                                [] (:hoitovuosittaiset-arvot (first tarjouksen-tiedot)))

        taulukon-tiedot (into [] (reduce (fn [rivit tarjous-rivi]
                                           (let [vuosiarvot (reduce (fn [uusi rivi]
                                                                      (-> uusi
                                                                        (assoc :poistettu (:poistettu tarjous-rivi))
                                                                        (assoc :rahavaraus-id (:rahavaraus-id tarjous-rivi))
                                                                        (assoc :toimenkuva-id (:toimenkuva-id tarjous-rivi))
                                                                        (assoc :tehtava-id (:tehtava-id tarjous-rivi))
                                                                        (assoc :tehtavaryhma-id (:tehtavaryhma-id tarjous-rivi))
                                                                        (assoc :osio (:osio tarjous-rivi))
                                                                        (assoc (keyword (str "vuosi-" (:vuosi rivi))) (:summa rivi))))
                                                              {} (:hoitovuosittaiset-arvot tarjous-rivi))
                                                 nimiarvot {:nimi (:nimi tarjous-rivi) :yhteensa (:yhteensa tarjous-rivi)}
                                                 lopputulos (merge vuosiarvot nimiarvot)]
                                             (concat rivit [lopputulos])))
                                   [] (drop-last tarjouksen-tiedot))) ;; Jätetään viimeinen rivi pois, koska se on yhteenvetorivi
        hankinnat-tiedot (into [] (filter #(some #{"hankintakustannukset" "tavoitehintaiset-rahavaraukset"}
                                             [(:osio %)]) taulukon-tiedot))
        joha-tiedot (into [] (filter #(some #{"johto-ja-hallintokorvaus"}
                                        [(:osio %)]) taulukon-tiedot))]

    [:div
     [:hr]
     ;; Custom toteutus - Tallennusnapit on taulukon yläpuolella
     [tallennus-painikkeet e! app]

     ;;Hankinnat
     [grid/grid
      {:otsikko ""
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
                  (reset! tarjous-tiedot/grid-tiedot-atom (vals (grid/hae-muokkaustila %)))
                  (reset! virheet-atom (grid/hae-virheet %)))
       :rivi-jalkeen-fn (fn [rivit]
                          (let [vuosi-arvot (map :nimi vuositaulukon-otsikot)
                                summat (laske-vuosisummat rivit vuosi-arvot)]
                            (into
                              [{:teksti "Kaikki hankinnat yhteensä", :luokka "yhteensa lihavoitu" :yhteenveto-vayla true :tyyppi :euro}
                               {:teksti "" :luokka "yhteensa lihavoitu"}]
                              summat)))}

      (concat [{:otsikko "Hankinnat" :nimi :nimi :tyyppi :string :luokka "yhteensa" :leveys (str nimi-leveys "%") :muokattava? (constantly false)}]
        [{:otsikko "€ / hoitovuosi" :nimi :eperhoitovuosi :tyyppi :euro :leveys (str vuosi-leveys "%")
          :muokattava? (fn [rivi] (if (or (= "Äkilliset hoitotyöt" (:nimi rivi)) (= "Vahinkojen korjaukset" (:nimi rivi))) true false))}]
        vuositaulukon-otsikot
        [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro
          :fmt (fn [arvo]
                 (if arvo (fmt/euro false arvo) 0.00))
          :leveys (str yhteensa-leveys "%")
          :hae (fn [rivi] (laske-rivit-yhteen rivi))
          :tasaa :oikea
          :muokattava? (fn [rivi] (if (:yhteensa rivi) false true))}])
      hankinnat-tiedot]

     ;;Erillisihankinnat
     [grid/grid
      {:otsikko ""
       :muokkaa-aina true
       :voi-muokata? true
       :muokattava? (constantly false)
       :voi-poistaa? (constantly false)
       :voi-lisata? false
       :voi-kumota? false
       :piilota-toiminnot? false
       :tunniste :nimi
       :muutos #(do
                  (reset! tallenna-painettu false)
                  (reset! tarjous-tiedot/grid-tiedot-atom (vals (grid/hae-muokkaustila %)))
                  (reset! virheet-atom (grid/hae-virheet %)))}
      (concat [{:otsikko "Erillishankinnat" :nimi :nimi :tyyppi :string :luokka "yhteensa" :leveys (str nimi-leveys "%") :muokattava? (constantly false)}]
        [{:otsikko "€ / hoitovuosi" :nimi :eperhoitovuosi :tyyppi :euro :leveys (str vuosi-leveys "%") :muokattava? (constantly true)}]
        (map #(assoc % :hae (fn [rivi] (:eperhoitovuosi rivi))) vuositaulukon-otsikot)
        [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro
          :fmt (fn [arvo]
                 (if arvo (fmt/euro false arvo) 0.00))
          :leveys (str yhteensa-leveys "%")
          :hae (fn [rivi] (laske-rivit-yhteen rivi))
          :tasaa :oikea
          :muokattava? (constantly false)}])
      [{:nimi "Erillishankinnat" :yhteensa 0 :vuosi-2021 0 :vuosi-2022 0 :vuosi-2023 0 :vuosi-2024 0 :vuosi-2025 0 :eperhoitovuosi 0}]]

     ;;Johto- ja hallintokorvaus
     (johto-ja-hallintokorvaukset e! joha-tiedot (:kaikki-toimenkuvat app) vuositaulukon-otsikot vuosi-leveys)

     ;;Hoidonjohtopalkkio
     [grid/grid
      {:otsikko ""
       :muokkaa-aina true
       :voi-muokata? true
       :muokattava? (constantly true)
       :voi-poistaa? (constantly false)
       :voi-lisata? true
       :voi-kumota? false
       :piilota-toiminnot? false
       :tunniste :nimi
       :muutos #(do
                  (reset! tallenna-painettu false)
                  (reset! tarjous-tiedot/grid-tiedot-atom (vals (grid/hae-muokkaustila %)))
                  (reset! virheet-atom (grid/hae-virheet %)))
       :rivi-jalkeen-fn nil}

      (concat [{:otsikko "Hoidonjohtopalkkio"
                :nimi :nimi
                :tyyppi :string
                :leveys (str nimi-leveys "%")
                :muokattava? (constantly false)}]
        [{:otsikko "€ / hoitovuosi" :nimi :eperhoitovuosi :tyyppi :euro :leveys (str vuosi-leveys "%") :muokattava? (constantly true)}]
        (map #(assoc % :hae (fn [rivi] (:eperhoitovuosi rivi))) vuositaulukon-otsikot)

        [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro :tasaa :oikea
          :muokattava? (constantly false) :luokka "yhteensa"
          :hae (fn [rivi] (laske-rivit-yhteen rivi))
          :fmt (fn [arvo] (if arvo (fmt/euro false arvo) 0.00)) :leveys (str yhteensa-leveys "%")}])

      [{:nimi "Hoidonjohtopalkkio" :yhteensa 0 :vuosi-2021 0 :vuosi-2022 0 :vuosi-2023 0 :vuosi-2024 0 :vuosi-2025 0 :eperhoitovuosi 0}]]

     ;;Tavoite- ja kattohinta
     [grid/grid
      {:otsikko ""
       :muokkaa-aina true
       :voi-muokata? true
       :muokattava? (constantly true)
       :voi-poistaa? (constantly false)
       :voi-lisata? true
       :voi-kumota? false
       :piilota-toiminnot? false
       :tunniste :nimi
       :muutos #(do
                  (reset! tallenna-painettu false)
                  (reset! tarjous-tiedot/grid-tiedot-atom (vals (grid/hae-muokkaustila %)))
                  (reset! virheet-atom (grid/hae-virheet %)))
       :rivi-jalkeen-fn nil}

      (concat [{:otsikko "Tavoite- ja kattohinta"
                :nimi :nimi
                :tyyppi :string
                :leveys (str nimi-leveys "%")
                :muokattava? (constantly false)}]
        vuositaulukon-otsikot
        [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro
          :muokattava? (constantly false) :luokka "yhteensa" :tasaa :oikea
          :fmt (fn [arvo] (if arvo (fmt/euro false arvo) 0.00)) :leveys (str yhteensa-leveys "%")}])
      [{:nimi "Tarjouksen tavoitehinta" :yhteensa 0 :vuosi-2021 0 :vuosi-2022 0 :vuosi-2023 0 :vuosi-2024 0 :vuosi-2025 0 :eperhoitovuosi 0}
       {:nimi "Tarjouksen kattohinta (1,1 x tarjouksen tavoitehinta)" :yhteensa 0 :vuosi-2021 0 :vuosi-2022 0 :vuosi-2023 0 :vuosi-2024 0 :vuosi-2025 0 :eperhoitovuosi 0}]]
     ;; Custom-toteutus. Tallennusnapit on taulukon jälkeen
     [tallennus-painikkeet e! app]]))

(defn nakyma* [e! app]
  (komp/luo
    (komp/sisaan #(e! (tarjous-tiedot/->HaeTarjouksenTiedot)))
    (fn [e! app]
      [:div
       (when (:tarjous app)
         [:div
          [:div.row
           [:div.col-xs-12.col-md-6
            [:h1 "Tarjouksen tiedot"]]]
          [:div.row
           [yleiset/info-laatikko :neutraali "Tarkempi kustannusten suunnittelu tehdään tarjouksen tietojen tallentamisen jälkeen." nil nil {:sulje-nappi-id (gensym)}]]
          [tarjous-nakyma e! app]
          [debug/debug app]])])))

(defn tarjous []
  (tuck/tuck tila/tarjous-kustannussuunnitelma nakyma*))
