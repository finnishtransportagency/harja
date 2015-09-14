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

(defonce laskutusyhteenvedon-tiedot (reaction<! [ur @nav/valittu-urakka
                                                 [hk-alkupvm hk-loppupvm] @u/valittu-hoitokausi
                                                 [aikavali-alkupvm aikavali-loppupvm] @u/valittu-hoitokauden-kuukausi
                                                 nakymassa? @laskutusyhteenveto-nakyvissa?]
                                                ;urakka-id hk_alkupvm hk_loppupvm aikavali-alkupvm aikavali-loppupvm
                                                (when (and ur hk-alkupvm hk-loppupvm
                                                           aikavali-alkupvm aikavali-loppupvm
                                                           nakymassa?)
                                                  (laskutus-tiedot/hae-laskutusyhteenvedon-tiedot {:hk-alkupvm        hk-alkupvm
                                                                                                   :hk-loppupvm       hk-loppupvm
                                                                                                   :aikavali-alkupvm  aikavali-alkupvm
                                                                                                   :aikavali-loppupvm aikavali-loppupvm
                                                                                                   :urakka-id         (:id ur)}))))

(tarkkaile! "laskutusyhteenvedon-tiedot" laskutusyhteenvedon-tiedot)

(defn lista-kaikki-yhteensa [tiedot valittu-aikavali]
  (let [kaikki-yhteenveto {:nimi                        "Kaikki yhteensä"
                           :yhteenveto                  true
                           :kaikki_laskutettu  (reduce + (map :kaikki_laskutettu tiedot))
                           :kaikki_laskutetaan (reduce + (map :kaikki_laskutetaan tiedot))}]
    [grid/grid
     {:otsikko      "KAIKKI YHTEENSÄ"
      :tyhja        "Ei kustannuksia"
      :tunniste     :nimi
      :rivin-luokka #(when (:yhteenveto %) " bold")
      :voi-muokata? false}
     [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
      {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
       :nimi    :kaikki_laskutettu :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-opt :tasaa :oikea}
      {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
       :nimi    :kaikki_laskutetaan :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-opt :tasaa :oikea}
      {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
       :hae     (fn [rivi] (+ (:kaikki_laskutettu rivi)
                              (:kaikki_laskutetaan rivi)))}]

     (sort-by :yhteenveto (conj tiedot kaikki-yhteenveto))]))

(defn lista-kaikki-paitsi-kok-hint-yhteensa [tiedot valittu-aikavali]
  (let [kaikki-paitsi-kht-yhteenveto {:nimi                        "Kaikki yhteensä"
                                      :yhteenveto                  true
                                      :kaikki_paitsi_kht_laskutettu  (reduce + (map :kaikki_paitsi_kht_laskutettu tiedot))
                                      :kaikki_paitsi_kht_laskutetaan (reduce + (map :kaikki_paitsi_kht_laskutetaan tiedot))}]
    [grid/grid
     {:otsikko      "Kaikki paitsi kok.hint. työt yhteensä"
      :tyhja        "Ei kustannuksia"
      :tunniste     :nimi
      :rivin-luokka #(when (:yhteenveto %) " bold")
      :voi-muokata? false}
     [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
      {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
       :nimi    :kaikki_paitsi_kht_laskutettu :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-opt :tasaa :oikea}
      {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
       :nimi    :kaikki_paitsi_kht_laskutetaan :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-opt :tasaa :oikea}
      {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
       :hae     (fn [rivi] (+ (:kaikki_paitsi_kht_laskutettu rivi)
                              (:kaikki_paitsi_kht_laskutetaan rivi)))}]

     (sort-by :yhteenveto (conj tiedot kaikki-paitsi-kht-yhteenveto))]))

(defn lista-kaikki-indeksitarkastukset-yhteensa [tiedot valittu-aikavali]
  (let [kaikki-ind-tar-yhteenveto {:nimi                        "Kaikki indeksitarkistukset yhteensä"
                                   :yhteenveto                  true
                                   :kaikki_laskutettu_ind_korotus  (reduce + (map :kaikki_laskutettu_ind_korotus tiedot))
                                   :kaikki_laskutetaan_ind_korotus (reduce + (map :kaikki_laskutetaan_ind_korotus tiedot))}]
    [grid/grid
     {:otsikko      "Kaikki indeksitarkistukset yhteensä"
      :tyhja        "Ei indeksitarkistuksia"
      :tunniste     :nimi
      :rivin-luokka #(when (:yhteenveto %) " bold")
      :voi-muokata? false}
     [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
      {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
       :nimi    :kaikki_laskutettu_ind_korotus :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-opt :tasaa :oikea}
      {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
       :nimi    :kaikki_laskutetaan_ind_korotus :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-opt :tasaa :oikea}
      {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
       :hae     (fn [rivi] (+ (:kaikki_laskutettu_ind_korotus rivi)
                              (:kaikki_laskutetaan_ind_korotus rivi)))}]

     (sort-by :yhteenveto (conj tiedot kaikki-ind-tar-yhteenveto))]))

(defn lista-kaikki-paitsi-kht-ind-tar-yhteenveto [tiedot valittu-aikavali]
  (let [kaikki-paitsi-kht-ind-tar-yhteenveto {:nimi                        "Muut kuin kok.hint. töiden indeksitarkistukset yhteensä"
                                              :yhteenveto                  true
                                              :kaikki_paitsi_kht_laskutettu_ind_korotus  (reduce + (map :kaikki_paitsi_kht_laskutettu_ind_korotus tiedot))
                                              :kaikki_paitsi_kht_laskutetaan_ind_korotus (reduce + (map :kaikki_paitsi_kht_laskutetaan_ind_korotus tiedot))}]
    [grid/grid
     {:otsikko      "Muiden kuin kok.hint. töiden indeksitarkistukset yhteensä"
      :tyhja        "Ei indeksitarkistuksia"
      :tunniste     :nimi
      :rivin-luokka #(when (:yhteenveto %) " bold")
      :voi-muokata? false}
     [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
      {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
       :nimi    :kaikki_paitsi_kht_laskutettu_ind_korotus :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-opt :tasaa :oikea}
      {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
       :nimi    :kaikki_paitsi_kht_laskutetaan_ind_korotus :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-opt :tasaa :oikea}
      {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
       :hae     (fn [rivi] (+ (:kaikki_paitsi_kht_laskutettu_ind_korotus rivi)
                              (:kaikki_paitsi_kht_laskutetaan_ind_korotus rivi)))}]

     (sort-by :yhteenveto (conj tiedot kaikki-paitsi-kht-ind-tar-yhteenveto))]))

(defn lista-erilliskustannusten-indeksitarkastukset [tiedot valittu-aikavali]
  (let [erilliskustannukset-ind-tar-yhteenveto {:nimi                        "Erilliskustannusten indeksitarkistukset yhteensä"
                                                :yhteenveto                  true
                                                :erilliskustannukset_laskutettu_ind_korotus  (reduce + (map :erilliskustannukset_laskutettu_ind_korotus tiedot))
                                                :erilliskustannukset_laskutetaan_ind_korotus (reduce + (map :erilliskustannukset_laskutetaan_ind_korotus tiedot))}]
    [grid/grid
     {:otsikko      "Erilliskustannusten indeksitarkistukset"
      :tyhja        "Ei erilliskustannusten indeksitarkistuksia"
      :tunniste     :nimi
      :rivin-luokka #(when (:yhteenveto %) " bold")
      :voi-muokata? false}
     [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
      {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
       :nimi    :erilliskustannukset_laskutettu_ind_korotus :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-indeksikorotus :tasaa :oikea}
      {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
       :nimi    :erilliskustannukset_laskutetaan_ind_korotus :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-indeksikorotus :tasaa :oikea}
      {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
       :hae     (fn [rivi] (+ (:erilliskustannukset_laskutettu_ind_korotus rivi)
                              (:erilliskustannukset_laskutetaan_ind_korotus rivi)))}]

     (sort-by :yhteenveto (conj tiedot erilliskustannukset-ind-tar-yhteenveto))]))

(defn lista-muutos-ja-lisatyot-indeksitarkastukset [tiedot valittu-aikavali]
  (let [muutostyot-ind-tar-yhteenveto {:nimi                        "Muutos- ja lisätöiden indeksitarkistukset yhteensä"
                                                :yhteenveto                  true
                                                :muutostyot_laskutettu_ind_korotus  (reduce + (map :muutostyot_laskutettu_ind_korotus tiedot))
                                       :muutostyot_laskutetaan_ind_korotus (reduce + (map :muutostyot_laskutetaan_ind_korotus tiedot))}]
    [grid/grid
     {:otsikko      "Muutos- ja lisätöiden indeksitarkistukset"
      :tyhja        "Ei muutos- ja lisätöiden indeksitarkistuksia"
      :tunniste     :nimi
      :rivin-luokka #(when (:yhteenveto %) " bold")
      :voi-muokata? false}
     [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
      {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
       :nimi    :muutostyot_laskutettu_ind_korotus :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-indeksikorotus :tasaa :oikea}
      {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
       :nimi    :muutostyot_laskutetaan_ind_korotus :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-indeksikorotus :tasaa :oikea}
      {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
       :hae     (fn [rivi] (+ (:muutostyot_laskutettu_ind_korotus rivi)
                              (:muutostyot_laskutetaan_ind_korotus rivi)))}]

     (sort-by :yhteenveto (conj tiedot muutostyot-ind-tar-yhteenveto))]))

(defn lista-erilliskustannukset [tiedot valittu-aikavali]
  (let [erilliskustannukset-yhteenveto {:nimi                        "Erilliskustannukset yhteensä"
                                        :yhteenveto                  true
                                        :erilliskustannukset_laskutettu  (reduce + (map :erilliskustannukset_laskutettu tiedot))
                                        :erilliskustannukset_laskutetaan (reduce + (map :erilliskustannukset_laskutetaan tiedot))}]
    [grid/grid
     {:otsikko      "Erilliskustannukset"
      :tyhja        "Ei erilliskustannuksia"
      :tunniste     :nimi
      :rivin-luokka #(when (:yhteenveto %) " bold")
      :voi-muokata? false}
     [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
      {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
       :nimi    :erilliskustannukset_laskutettu :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-opt :tasaa :oikea}
      {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
       :nimi    :erilliskustannukset_laskutetaan :tyyppi :numero :leveys "20%"
       :fmt     fmt/euro-opt :tasaa :oikea}
      {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
       :hae     (fn [rivi] (+ (:erilliskustannukset_laskutettu rivi)
                              (:erilliskustannukset_laskutetaan rivi)))}]

     (sort-by :yhteenveto (conj tiedot erilliskustannukset-yhteenveto))]))

(defn laskutusyhteenveto
  []
  (komp/luo
    (komp/lippu laskutusyhteenveto-nakyvissa?)
    (fn []
      (let [ur @nav/valittu-urakka
            tiedot @laskutusyhteenvedon-tiedot
            talvihoidon-tuotekoodi "23100"
            talvihoidon-tiedot (filter #(= (:tuotekoodi %) talvihoidon-tuotekoodi) tiedot)
            kht-yhteenveto {:nimi                        "Kokonaishintaiset työt yhteensä"
                            :yhteenveto                  true
                            :kht_laskutettu
                                                         (reduce + (map :kht_laskutettu tiedot))
                            :kht_laskutetaan (reduce + (map :kht_laskutetaan tiedot))}

            yht-yhteenveto {:nimi            "Yksikköhintaiset työt yhteensä"
                            :yhteenveto      true
                            :yht_laskutettu
                                             (reduce + (map :yht_laskutettu tiedot))
                            :yht_laskutetaan (reduce + (map :yht_laskutetaan tiedot))}
            kht-ind-tar-yhteenveto {:nimi                        "Kokonaishintaisten töiden indeksitarkistukset yhteensä"
                                    :yhteenveto                  true
                                    :kht_laskutettu_ind_korotus  (reduce + (map :kht_laskutettu_ind_korotus tiedot))
                                    :kht_laskutetaan_ind_korotus (reduce + (map :kht_laskutetaan_ind_korotus tiedot))}
            yht-ind-tar-yhteenveto {:nimi                        "Yksikköhintaisten töiden indeksitarkistukset yhteensä"
                                    :yhteenveto                  true
                                    :yht_laskutettu_ind_korotus  (reduce + (map :yht_laskutettu_ind_korotus tiedot))
                                    :yht_laskutetaan_ind_korotus (reduce + (map :yht_laskutetaan_ind_korotus tiedot))}
            sakot-yhteenveto  {:nimi                        "Sanktiot yhteensä"
                               :yhteenveto                  true
                               :sakot_laskutettu (reduce + (map :sakot_laskutettu tiedot))
                               :sakot_laskutetaan (reduce + (map :sakot_laskutetaan tiedot))}
            sakot-ind-tar-yhteenveto {:nimi                        "Sanktioiden indeksitarkistukset yhteensä"
                                     :yhteenveto                  true
                                     :sakot_laskutettu_ind_korotus  (reduce + (map :sakot_laskutettu_ind_korotus tiedot))
                                     :sakot_laskutetaan_ind_korotus (reduce + (map :sakot_laskutetaan_ind_korotus tiedot))}
            talvisuolasakot-yhteenveto {:nimi                        "Talvisuolasakko yhteensä"
                                        :yhteenveto                  true
                                        :suolasakot_laskutettu  (reduce + (map :suolasakot_laskutettu tiedot))
                                        :suolasakot_laskutetaan (reduce + (map :suolasakot_laskutetaan tiedot))}

            talvisuolasakot-ind-tar-yhteenveto {:nimi                        "Talvisuolasakon indeksitarkistukset yhteensä"
                                                :yhteenveto                  true
                                                :suolasakot_laskutettu_ind_korotus  (reduce + (map :suolasakot_laskutettu_ind_korotus tiedot))
                                                :suolasakot_laskutetaan_ind_korotus (reduce + (map :suolasakot_laskutetaan_ind_korotus tiedot))}
            muutostyot-yhteenveto {:nimi                        "Muutos- ja lisätyöt yhteensä"
                                        :yhteenveto                  true
                                        :muutostyot_laskutettu  (reduce + (map :muutostyot_laskutettu tiedot))
                                        :muutostyot_laskutetaan (reduce + (map :muutostyot_laskutetaan tiedot))}

                 valittu-aikavali @u/valittu-hoitokauden-kuukausi]
        [:span.laskutusyhteenveto
         [:h3 "Laskutusyhteenveto"]
         [valinnat/urakan-hoitokausi ur]
         [valinnat/hoitokauden-kuukausi]

         (when (and ur @u/valittu-hoitokausi valittu-aikavali)
           [:span.tiedot
            [grid/grid
             {:otsikko      "Kokonaishintaiset työt"
              :tyhja        "Ei kokonaishintaisia töitä"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
               :nimi    :kht_laskutettu :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
               :nimi    :kht_laskutetaan :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:kht_laskutettu rivi)
                                      (:kht_laskutetaan rivi)))}]

             (sort-by :yhteenveto (conj tiedot kht-yhteenveto))]

            [grid/grid
             {:otsikko      "Yksikköhintaiset työt"
              :tyhja        "Ei yksikköhintaisia töitä"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
               :nimi    :yht_laskutettu :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
               :nimi    :yht_laskutetaan :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:yht_laskutettu rivi)
                                      (:yht_laskutetaan rivi)))}]

             (sort-by :yhteenveto (conj tiedot yht-yhteenveto))]

            [grid/grid
             {:otsikko      "Sanktiot"
              :tyhja        "Ei sanktioita"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
               :nimi    :sakot_laskutettu :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
               :nimi    :sakot_laskutetaan :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:sakot_laskutettu rivi)
                                      (:sakot_laskutetaan rivi)))}]

             (sort-by :yhteenveto (conj tiedot sakot-yhteenveto))]

            [grid/grid
             {:otsikko      "Talvisuolasakko (autom. laskettu)"
              :tyhja        "Ei talvisuolasakkoa"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
               :nimi    :suolasakot_laskutettu :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-indeksikorotus :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
               :nimi    :suolasakot_laskutetaan :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-indeksikorotus :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:suolasakot_laskutettu rivi)
                                      (:suolasakot_laskutetaan rivi)))}]

             (sort-by :yhteenveto (conj talvihoidon-tiedot talvisuolasakot-yhteenveto))]

            [grid/grid
             {:otsikko      "Muutos- ja lisätyöt"
              :tyhja        "Ei muutos- ja lisätöitä"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
               :nimi    :muutostyot_laskutettu :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
               :nimi    :muutostyot_laskutetaan :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:muutostyot_laskutettu rivi)
                                      (:muutostyot_laskutetaan rivi)))}]

             (sort-by :yhteenveto (conj tiedot muutostyot-yhteenveto))]

            [lista-erilliskustannukset tiedot valittu-aikavali]
            

            [grid/grid
             {:otsikko      "Kokonaishintaisten töiden indeksitarkistukset"
              :tyhja        "Ei indeksitarkistuksia"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
               :nimi    :kht_laskutettu_ind_korotus :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
               :nimi    :kht_laskutetaan_ind_korotus :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:kht_laskutettu_ind_korotus rivi)
                                      (:kht_laskutetaan_ind_korotus rivi)))}]

             (sort-by :yhteenveto (conj tiedot kht-ind-tar-yhteenveto))]

            [grid/grid
             {:otsikko      "Yksikköhintaisten töiden indeksitarkistukset"
              :tyhja        "Ei indeksitarkistuksia"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
               :nimi    :yht_laskutettu_ind_korotus :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
               :nimi    :yht_laskutetaan_ind_korotus :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-opt :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:yht_laskutettu_ind_korotus rivi)
                                      (:yht_laskutetaan_ind_korotus rivi)))}]

             (sort-by :yhteenveto (conj tiedot yht-ind-tar-yhteenveto))]

            [grid/grid
             {:otsikko      "Sanktioiden indeksitarkistukset"
              :tyhja        "Ei sanktioiden indeksitarkistuksia"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
               :nimi    :sakot_laskutettu_ind_korotus :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-indeksikorotus :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
               :nimi    :sakot_laskutetaan_ind_korotus :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-indeksikorotus :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:sakot_laskutettu_ind_korotus rivi)
                                      (:sakot_laskutetaan_ind_korotus rivi)))}]

             (sort-by :yhteenveto (conj tiedot sakot-ind-tar-yhteenveto))]

            [grid/grid
             {:otsikko      "Talvisuolasakon indeksitarkistus (autom. laskettu)"
              :tyhja        "Ei talvisuolasakon indeksitarkistuksia"
              :tunniste     :nimi
              :rivin-luokka #(when (:yhteenveto %) " bold")
              :voi-muokata? false}
             [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
              {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
               :nimi    :suolasakot_laskutettu_ind_korotus :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-indeksikorotus :tasaa :oikea}
              {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
               :nimi    :suolasakot_laskutetaan_ind_korotus :tyyppi :numero :leveys "20%"
               :fmt     fmt/euro-indeksikorotus :tasaa :oikea}
              {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
               :hae     (fn [rivi] (+ (:suolasakot_laskutettu_ind_korotus rivi)
                                      (:suolasakot_laskutetaan_ind_korotus rivi)))}]

             (sort-by :yhteenveto (conj talvihoidon-tiedot talvisuolasakot-ind-tar-yhteenveto))]

            [lista-muutos-ja-lisatyot-indeksitarkastukset tiedot valittu-aikavali]
            

            [lista-erilliskustannusten-indeksitarkastukset tiedot valittu-aikavali]
            

            [lista-kaikki-paitsi-kht-ind-tar-yhteenveto tiedot valittu-aikavali]
         
            [lista-kaikki-indeksitarkastukset-yhteensa tiedot valittu-aikavali]
            

            [lista-kaikki-paitsi-kok-hint-yhteensa tiedot valittu-aikavali]

            [lista-kaikki-yhteensa tiedot valittu-aikavali]

            ])]))))
