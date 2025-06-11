(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.tarjous-nakyma
  "Kustannussuunnitelman etusivu määrittää, että renderöidäänkö tarjous vai kustannussuunnitelma"
  (:require [harja.fmt :as fmt]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.urakka :as tila]
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

(defn tarjous-nakyma [e! app]
  (let [tarjouksen-tiedot (:tarjous app)
        hoitokausien-maara (count (:hoitovuosittaiset-arvot (first tarjouksen-tiedot)))
        nimi-leveys 20
        yhteensa-leveys 20
        vuosi-leveys (/ (- 100 nimi-leveys yhteensa-leveys) hoitokausien-maara)

        ;; Muodostetaan otsikot, jotka voivat olla erilaisia eri mittaisilla urakoilla
        vuositaulukon-otsikot (reduce (fn [rivit vuosi-rivi]
                                        (let [index (inc (count rivit))]
                                          (concat rivit [{:otsikko (str index ". Hoitovuosi (€)")
                                                          :nimi (keyword (str "vuosi-" (:vuosi vuosi-rivi)))
                                                          :tyyppi :euro
                                                          :leveys (str vuosi-leveys "%")
                                                          :muokattava? (constantly true)
                                                          :tasaa :oikea}])))
                                [] (:hoitovuosittaiset-arvot (first tarjouksen-tiedot))) ;; Riittää, että käytetään esimerkkinä ensimmäistä riviä
        ;; Otetaan taulukosta yhteenvetorivi pois ennen käsittelyä
        yhteenveto (last tarjouksen-tiedot)
        yhteenveto-rivit (reduce (fn [y rivi]
                                   (conj y {:teksti (fmt/euro false (:summa rivi)) :tasaa :oikea :luokka "yhteensa lihavoitu"}))
                           [{:teksti "Tarjouksen tavoitehinta" :luokka "yhteensa lihavoitu" :yhteenveto-vayla true :tyyppi :euro :fmt #(fmt/euro false %)}]
                           (:hoitovuosittaiset-arvot yhteenveto))
        ;; Lisätään vielä yhteenveto yhteenvetoriviin
        yhteenveto-rivit (conj yhteenveto-rivit
                           {:teksti (fmt/euro false (:yhteensa yhteenveto))
                            :luokka "yhteensa lihavoitu"
                            :tasaa :oikea
                            :tyyppi :euro
                            :fmt #(fmt/euro false %)
                            :muokattava? false
                            :rivi-disabled? true})
        taulukon-tiedot (reduce (fn [rivit tarjous-rivi]
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
                          [] (drop-last tarjouksen-tiedot)) ;; Jätetään viimeinen rivi pois, koska se on yhteenvetorivi
        ;; Gridin tila säilytetään atomissa, jotta siihen on kahva erillisessä tallennusnapissa
        _ (reset! grid-tiedot-atom taulukon-tiedot)]

    [:div
     [debug/debug app]
     [:hr]
     [:h3 "Tarjouksen tiedot"]
     ;; Custom toteutus - Tallennusnapit on taulukon yläpuolella
     [tallennus-painikkeet e! app]
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
                          ^{:luokka "yhteenveto"}
                          yhteenveto-rivit)}

      (concat [{:otsikko "" :nimi :nimi :tyyppi :string :leveys (str nimi-leveys "%") :muokattava? (constantly false)}]
        vuositaulukon-otsikot
        [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro :fmt #(fmt/euro false %) :leveys (str yhteensa-leveys "%") :tasaa :oikea :muokattava? (constantly false)}])
      taulukon-tiedot]

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
          [tarjous-nakyma e! app]])])))

(defn tarjous []
  (tuck/tuck tila/tarjous-kustannussuunnitelma nakyma*))
