(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.tarjous-nakyma
  "Kustannussuunnitelman etusivu määrittää, että renderöidäänkö tarjous vai kustannussuunnitelma"
  (:require [harja.fmt :as fmt]
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
(defonce grid-tiedot-atom (atom [{}]))

(defn- tallennus-painikkeet [e! {:keys [tallennus-kesken?] :as app}]
  [:div.painikkeet
   [napit/yleinen-ensisijainen "Tallenna muutokset"
    #(do
       (reset! tallenna-painettu false)
       (e! (tarjous-tiedot/->TallennaTarjouksenTiedot @grid-tiedot-atom)))
    {:disabled (or tallennus-kesken? false)}]
   [napit/yleinen-toissijainen "Tyhjennä"
    #(do
       (reset! tallenna-painettu false)
       (e! (tarjous-tiedot/->HaeTarjouksenTiedot)))
    {:disabled (or tallennus-kesken? false)}]])

(defn- yhteenvetorivi [otsikko yhteenveto] (reduce (fn [y rivi]
                                                     (conj y {:teksti (fmt/euro false (:summa rivi)) :tasaa :oikea :luokka "yhteensa lihavoitu"}))
                                             [{:teksti otsikko
                                               :luokka "yhteensa disabled lihavoitu"
                                               :yhteenveto-vayla true
                                               :tyyppi :euro
                                               :fmt #(fmt/euro false %)}
                                              {:teksti ""
                                               :luokka "yhteensa lihavoitu"}]
                                             (:hoitovuosittaiset-arvot yhteenveto)))
;; Lisätään vielä yhteenveto yhteenvetoriviin)

(defn poista-rivi [rivi rivit app e!]
  (e! (tarjous-tiedot/->PoistaRivi rivi)))

(defn laske-rivit-yhteen [rivi]
  (let [kustannukset (vals (filter #(clojure.string/starts-with? (name (key %)) "vuosi-") rivi))]
    (reduce + kustannukset)))

(defn laske-vuosisummat [rivit vuosikentat]
  (let [vuosidata (->> rivit
          (map #(select-keys % vuosikentat))
          (apply merge-with +))]
    (->> vuosidata
      (sort-by key)
      (mapv (fn [[_ arvo]]
              {:teksti arvo
               :luokka "yhteensa lihavoitu"
               :tyyppi :euro
               :tasaa :oikea
               :fmt fmt/euro-opt
               })))))

(defn tee-haerivi [rivi hoitokausien-maara]
  (let [nimi (:nimi rivi)]
    (case nimi
      "Äkilliset hoitotyöt" (:eperhoitovuosi rivi)
      "Vahinkojen korjaukset" (/ (:eperhoitovuosi rivi) hoitokausien-maara)
      nil)))

(defn tarjous-nakyma [e! app]
  (let [tarjouksen-tiedot (:tarjous app)
        hoitokausien-maara (count (:hoitovuosittaiset-arvot (first tarjouksen-tiedot)))
        nimi-leveys 20
        yhteensa-leveys 20
        _ (println "\n vuodet")
        _ (cljs.pprint/pprint tarjouksen-tiedot)
        _ (println "\n")
        vuosi-leveys (/ (- 100 nimi-leveys yhteensa-leveys) hoitokausien-maara)
        ;; Muodostetaan otsikot, jotka voivat olla erilaisia eri mittaisilla urakoilla
        vuositaulukon-otsikot (reduce (fn [rivit vuosi-rivi]
                                        (println "vuosi-rivi: " vuosi-rivi)
                                        (let [index (inc (count rivit))]
                                          (concat rivit [{:otsikko (str index ". Hoitovuosi " (:vuosi vuosi-rivi) " - " (inc (:vuosi vuosi-rivi)) "(€)")
                                                          :nimi (keyword (str "vuosi-" (:vuosi vuosi-rivi)))
                                                          :tyyppi :euro
                                                          :leveys (str vuosi-leveys "%")

                                                          #_#_:hae (fn [rivi] (if (or (= (:nimi rivi) "Äkilliset hoitotyöt")
                                                                                    (= (:nimi rivi) "Vahinkojen korjaukset")) (tee-haerivi rivi hoitokausien-maara) 0.00))
                                                          :muokattava? (fn [rivi] (if (or (= (:nimi rivi) "Äkilliset hoitotyöt")
                                                                                        (= (:nimi rivi) "Hoidonjohtopalkkio")
                                                                                        (= (:nimi rivi) "Vahinkojen korjaukset")
                                                                                        (= (:nimi rivi) "Tarjouksen tavoitehinta")
                                                                                        (= (:nimi rivi) "Tarjouksen kattohinta (1,1 x tarjouksen tavoitehinta)")) false true))
                                                          :tasaa :oikea}])))
                                [] (:hoitovuosittaiset-arvot (first tarjouksen-tiedot)))

        ;; Riittää, että käytetään esimerkkinä ensimmäistä riviä
        ;; Otetaan taulukosta yhteenvetorivi pois ennen käsittelyä
        yhteenveto (last tarjouksen-tiedot)
        yhteenveto-rivit (reduce (fn [y rivi]
                                   (conj y {:teksti (fmt/euro false (:summa rivi)) :tasaa :oikea :luokka "yhteensa lihavoitu"}))
                           [{:teksti "Kaikki hankinnat yhteensä" :luokka "yhteensa lihavoitu" :yhteenveto-vayla true :tyyppi :euro :fmt #(fmt/euro false %)}]
                           (:hoitovuosittaiset-arvot yhteenveto))
        ;; Lisätään vielä yhteenveto yhteenvetoriviin
        yhteenveto-rivit (conj yhteenveto-rivit
                           {:teksti (if (:yhteensa yhteenveto) (fmt/euro false (:yhteensa yhteenveto)) "0,00")
                            :luokka "yhteensa lihavoitu"
                            :tasaa :oikea
                            :tyyppi :euro
                            :fmt #(fmt/euro false %)
                            :muokattava? false
                            :rivi-disabled? true})
        taulukon-tiedot (into [] (reduce (fn [rivit tarjous-rivi]
                                           (let [vuosiarvot (reduce (fn [uusi rivi]
                                                                      (-> uusi
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
     [:h3 "Tarjouksen tiedot"]
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
                  (reset! grid-tiedot-atom (vals (grid/hae-muokkaustila %)))
                  (reset! virheet-atom (grid/hae-virheet %)))
       :rivi-jalkeen-fn (fn [rivit]
                          (let [vuosi-arvot (map :nimi vuositaulukon-otsikot)
                                summat (laske-vuosisummat rivit vuosi-arvot)]
                            (into
                              [{:teksti "Kaikki hankinnat yhteensä", :luokka "yhteensa lihavoitu" :yhteenveto-vayla true :tyyppi :euro }
                               {:teksti "" :luokka "yhteensa lihavoitu"}]
                              summat
                              )))}

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
      (conj hankinnat-tiedot {:vuosi-2024 0.00 :vuosi-2025 0.00 :vuosi-2023 0.00 :vuosi-2022 0.00 :vuosi-2021 0.00 :yhteensa 0 :nimi "Hankinnat yhteensä" :rivi-disabled? true :muokattava? (fn [rivi])})]

     ;;Johto- ja hallintokorvaus
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
                  (reset! grid-tiedot-atom (vals (grid/hae-muokkaustila %)))
                  (reset! virheet-atom (grid/hae-virheet %)))
       :rivi-jalkeen-fn (fn [rivit]
                          ^{:luokka "yhteenveto"}
                          (yhteenvetorivi "Johto- ja hallintokorvaus yhteensä" (:hoitovuosittaiset-arvot yhteenveto)))}

      (concat [{:otsikko "Johto- ja hallintokorvaus"
                :nimi :nimi
                :tyyppi :valinta
                :valinnat (map :nimi joha-tiedot)
                :luokka "yhteensa"
                :leveys (str nimi-leveys "%")
                :muokattava? (constantly true)}]
              [{:otsikko ""
                :tyyppi :komponentti
                :komponentti (fn [rivi rivit]
                                (napit/yleinen "Poista rivi"
                                  :toissijainen
                                   #(poista-rivi rivi rivit app e!)
                                   {:ikoni (ikonit/livicon-trash) :luokka "btn-xs"}))
                :leveys (str vuosi-leveys "%")}]
                (mapv #(assoc % :muokattava false) vuositaulukon-otsikot)
                [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro
                  :muokattava? (constantly false) :luokka "yhteensa"
                  :hae (fn [rivi] (laske-rivit-yhteen rivi))
                  :fmt (fn [arvo] (if arvo (fmt/euro false arvo) 0.00)) :leveys (str yhteensa-leveys "%") :tasaa :oikea}])

      joha-tiedot]

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
                  (reset! grid-tiedot-atom (vals (grid/hae-muokkaustila %)))
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
                  (reset! grid-tiedot-atom (vals (grid/hae-muokkaustila %)))
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
            [:h1 "Hoitovuoden alun tavoitehinta"]
            [:div (-> @tila/yleiset :urakka :nimi)]]]
          [:div.row
           [yleiset/info-laatikko :neutraali "Tarkempi kustannusten suunnittelu tehdään tarjouksen tietojen tallentamisen jälkeen." nil nil {:sulje-nappi-id (gensym)}]]
          [tarjous-nakyma e! app]
          [debug/debug app]])])))

(defn tarjous []
  (tuck/tuck tila/tarjous-kustannussuunnitelma nakyma*))
