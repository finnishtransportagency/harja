(ns harja.views.urakka.laskutusyhteenveto
  "Urakan Laskutusyhteenveto välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]

            [harja.tiedot.urakka.laskutusyhteenveto :as laskutus-tiedot]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.maksuerat :as maksuerat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.valinnat :as valinnat-komp]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))





(defonce laskutusyhteenveto-nakyvissa? (atom false))

(defonce aikavali (reaction
                    (when-let [ur @nav/valittu-urakka]
                      (when @laskutusyhteenveto-nakyvissa?
                        (if (pvm/valissa? (pvm/nyt) (:alkupvm ur) (:loppupvm ur))
                          (pvm/ed-kk-aikavalina (pvm/nyt))
                          (last (pvm/hoitokauden-kuukausivalit @u/valittu-hoitokausi)))))))

(defonce laskutusyhteenvedon-tiedot (reaction<! [ur @nav/valittu-urakka
                                                 [hk_alkupvm hk_loppupvm] @u/valittu-hoitokausi
                                                 [aikavali_alkupvm aikavali_loppupvm] @aikavali]
                                                (log "haetaan laskutusyhteenvedon tiedot, front")
                                                ;urakka-id hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm
                                                (when (and ur hk_alkupvm hk_loppupvm
                                                           aikavali_alkupvm aikavali_loppupvm
                                                           @laskutusyhteenveto-nakyvissa?)
                                                  (laskutus-tiedot/hae-laskutusyhteenvedon-tiedot {:hk_alkupvm        hk_alkupvm
                                                                                                   :hk_loppupvm       hk_loppupvm
                                                                                                   :aikavali_alkupvm  aikavali_alkupvm
                                                                                                   :aikavali_loppupvm aikavali_loppupvm
                                                                                                   :urakka-id         (:id ur)}))))

(tarkkaile! "laskutusyhteenvedon-tiedot" laskutusyhteenvedon-tiedot)
(tarkkaile! "lask aikaväli" aikavali)

(defn laskutusyhteenveto
  []
  (komp/luo
    (komp/lippu laskutusyhteenveto-nakyvissa?)
    (fn []
      (let [ur @nav/valittu-urakka
            tiedot @laskutusyhteenvedon-tiedot
            kht-yhteenveto {:nimi                        "Kokonaishintaiset työt yhteensä"
                        :yhteenveto                  true
                        :kht_laskutettu
                                                     (reduce + (map :kht_laskutettu tiedot))
                        :kht_laskutetaan_aikavalilla (reduce + (map :kht_laskutetaan_aikavalilla tiedot))}

            yht-yhteenveto {:nimi                        "Yksikköhintaiset työt yhteensä"
                            :yhteenveto                  true
                            :yht_laskutettu
                                                         (reduce + (map :yht_laskutettu tiedot))
                            :yht_laskutetaan (reduce + (map :yht_laskutetaan tiedot))}
            kht-ind-tar-yhteenveto {:nimi                        "Kokonaishintaisten töiden indeksitarkistukset yhteensä"
                                    :yhteenveto                  true
                                    :kht_laskutettu
                                                                 (reduce + (map :kht_laskutettu tiedot))
                                    :kht_laskutetaan_aikavalilla (reduce + (map :kht_laskutetaan_aikavalilla tiedot))}
            yht-ind-tar-yhteenveto {:nimi                        "Yksikköhintaisten töiden indeksitarkistukset yhteensä"
                                    :yhteenveto                  true
                                    :kht_laskutettu
                                                                 (reduce + (map :yht_laskutettu_ind_korotus tiedot))
                                    :kht_laskutetaan_aikavalilla (reduce + (map :yht_laskutetaan_ind_korotus tiedot))}
            ]
        [:span.laskutusyhteenveto
         [:h3 "Laskutusyhteenveto"]
         [valinnat/urakan-hoitokausi ur]
         [valinnat-komp/aikavali aikavali]
         (when (and ur @u/valittu-hoitokausi @aikavali)
           [:span.tiedot
            [grid/grid
             {:otsikko      "Kokonaishintaiset työt"
              :tyhja        "Ei kokonaishintaisia töitä"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first @aikavali)))
               :nimi    :kht_laskutettu :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first @aikavali)) " - " (pvm/pvm (second @aikavali)))
               :nimi    :kht_laskutetaan_aikavalilla :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:kht_laskutettu rivi)
                                      (:kht_laskutetaan_aikavalilla rivi)))}]

             (sort-by :yhteenveto (conj tiedot kht-yhteenveto))]

            [grid/grid
             {:otsikko      "Yksikköhintaiset työt"
              :tyhja        "Ei yksikköhintaisia töitä"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first @aikavali)))
               :nimi    :yht_laskutettu :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first @aikavali)) " - " (pvm/pvm (second @aikavali)))
               :nimi    :yht_laskutetaan :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:yht_laskutettu rivi)
                                      (:yht_laskutetaan rivi)))}]

             (sort-by :yhteenveto (conj @laskutusyhteenvedon-tiedot yht-yhteenveto))]

             ;; tähän sakkotaulukot

             [grid/grid
              {:otsikko      "Kokonaishintaisten töiden indeksitarkistukset"
               :tyhja        "Ei indeksitarkistuksia"
               :tunniste     :nimi
               :rivin-luokka #(when (:yhteenveto %) " bold")
               :voi-muokata? false}
              [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
               {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first @aikavali)))
                :nimi    :kht_laskutettu_ind_korotus :tyyppi :numero :leveys "20%"
                :fmt     fmt/euro-opt :tasaa :oikea}
               {:otsikko (str "Laskutetaan " (pvm/pvm (first @aikavali)) " - " (pvm/pvm (second @aikavali)))
                :nimi    :kht_laskutetaan_ind_korotus :tyyppi :numero :leveys "20%"
                :fmt     fmt/euro-opt :tasaa :oikea}
               {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
                :hae     (fn [rivi] (+ (:kht_laskutettu_ind_korotus rivi)
                                       (:kht_laskutetaan_ind_korotus rivi)))}]

              (sort-by :yhteenveto (conj @laskutusyhteenvedon-tiedot kht-ind-tar-yhteenveto))]

            [grid/grid
             {:otsikko      "Yksikköhintaisten töiden indeksitarkistukset"
              :tyhja        "Ei indeksitarkistuksia"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first @aikavali)))
               :nimi    :yht_laskutettu_ind_korotus :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first @aikavali)) " - " (pvm/pvm (second @aikavali)))
               :nimi    :yht_laskutetaan_ind_korotus :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:yht_laskutettu_ind_korotus rivi)
                                      (:yht_laskutetaan_ind_korotus rivi)))}]

             (sort-by :yhteenveto (conj @laskutusyhteenvedon-tiedot yht-ind-tar-yhteenveto))]
            ])]))))
