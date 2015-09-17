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
            suolasakot-yhteenveto {:nimi                        "Talvisuolasakko yhteensä"
                                        :yhteenveto                  true
                                        :suolasakot_laskutettu  (reduce + (map :suolasakot_laskutettu tiedot))
                                        :suolasakot_laskutetaan (reduce + (map :suolasakot_laskutetaan tiedot))}

            suolasakot-ind-tar-yhteenveto {:nimi                        "Talvisuolasakon indeksitarkistukset yhteensä"
                                                :yhteenveto                  true
                                                :suolasakot_laskutettu_ind_korotus  (reduce + (map :suolasakot_laskutettu_ind_korotus tiedot))
                                                :suolasakot_laskutetaan_ind_korotus (reduce + (map :suolasakot_laskutetaan_ind_korotus tiedot))}
            muutostyot-yhteenveto {:nimi                        "Muutos- ja lisätyöt yhteensä"
                                        :yhteenveto                  true
                                        :muutostyot_laskutettu  (reduce + (map :muutostyot_laskutettu tiedot))
                                        :muutostyot_laskutetaan (reduce + (map :muutostyot_laskutetaan tiedot))}
            muutostyot-ind-tar-yhteenveto {:nimi                        "Muutos- ja lisätöiden indeksitarkistukset yhteensä"
                                           :yhteenveto                  true
                                           :muutostyot_laskutettu_ind_korotus  (reduce + (map :muutostyot_laskutettu_ind_korotus tiedot))
                                           :muutostyot_laskutetaan_ind_korotus (reduce + (map :muutostyot_laskutetaan_ind_korotus tiedot))}
            erilliskustannukset-yhteenveto {:nimi                        "Erilliskustannukset yhteensä"
                                            :yhteenveto                  true
                                            :erilliskustannukset_laskutettu  (reduce + (map :erilliskustannukset_laskutettu tiedot))
                                            :erilliskustannukset_laskutetaan (reduce + (map :erilliskustannukset_laskutetaan tiedot))}
            erilliskustannukset-ind-tar-yhteenveto {:nimi                        "Erilliskustannusten indeksitarkistukset yhteensä"
                                                    :yhteenveto                  true
                                                    :erilliskustannukset_laskutettu_ind_korotus  (reduce + (map :erilliskustannukset_laskutettu_ind_korotus tiedot))
                                                    :erilliskustannukset_laskutetaan_ind_korotus (reduce + (map :erilliskustannukset_laskutetaan_ind_korotus tiedot))}
            kaikki-paitsi-kht-ind-tar-yhteenveto {:nimi                        "Muut kuin kok.hint. töiden indeksitarkistukset yhteensä"
                                                  :yhteenveto                  true
                                                  :kaikki_paitsi_kht_laskutettu_ind_korotus  (reduce + (map :kaikki_paitsi_kht_laskutettu_ind_korotus tiedot))
                                                  :kaikki_paitsi_kht_laskutetaan_ind_korotus (reduce + (map :kaikki_paitsi_kht_laskutetaan_ind_korotus tiedot))}
            kaikki-ind-tar-yhteenveto {:nimi                        "Kaikki indeksitarkistukset yhteensä"
                                       :yhteenveto                  true
                                       :kaikki_laskutettu_ind_korotus  (reduce + (map :kaikki_laskutettu_ind_korotus tiedot))
                                       :kaikki_laskutetaan_ind_korotus (reduce + (map :kaikki_laskutetaan_ind_korotus tiedot))}
            kaikki-paitsi-kht-yhteenveto {:nimi                        "Kaikki yhteensä"
                                          :yhteenveto                  true
                                          :kaikki_paitsi_kht_laskutettu  (reduce + (map :kaikki_paitsi_kht_laskutettu tiedot))
                                          :kaikki_paitsi_kht_laskutetaan (reduce + (map :kaikki_paitsi_kht_laskutetaan tiedot))}
            kaikki-yhteenveto {:nimi                        "Kaikki yhteensä"
                               :yhteenveto                  true
                               :kaikki_laskutettu  (reduce + (map :kaikki_laskutettu tiedot))
                               :kaikki_laskutetaan (reduce + (map :kaikki_laskutetaan tiedot))}

            valittu-aikavali @u/valittu-hoitokauden-kuukausi
            taulukko (fn [otsikko otsikko-jos-tyhja
                          laskutettu-kentta laskutetaan-kentta
                          yhteenveto-rivi tiedot]
                       [grid/grid
                        {:otsikko      otsikko
                         :tyhja        otsikko-jos-tyhja
                         :tunniste     :nimi
                         :rivin-luokka #(when (:yhteenveto %) " bold")
                         :voi-muokata? false}
                        [{:otsikko "Toimenpide" :nimi :nimi :tyyppi :string :leveys "40%"}
                         {:otsikko (str "Laskutettu hoitokaudella ennen " (pvm/pvm (first valittu-aikavali)))
                          :nimi    laskutettu-kentta :tyyppi :numero :leveys "20%"
                          :fmt     fmt/euro-opt :tasaa :oikea}
                         {:otsikko (str "Laskutetaan " (pvm/pvm (first valittu-aikavali)) " - " (pvm/pvm (second valittu-aikavali)))
                          :nimi    laskutetaan-kentta :tyyppi :numero :leveys "20%"
                          :fmt     fmt/euro-opt :tasaa :oikea}
                         {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
                          :hae     (fn [rivi] (+ (laskutettu-kentta rivi)
                                                 (laskutetaan-kentta rivi)))}]

                        (sort-by :yhteenveto (conj tiedot yhteenveto-rivi ))])
            ]
        [:span.laskutusyhteenveto
         [:h3 "Laskutusyhteenveto"]
         [valinnat/urakan-hoitokausi ur]
         [valinnat/hoitokauden-kuukausi]

         (when (and ur @u/valittu-hoitokausi valittu-aikavali)
           [:span.tiedot

            [taulukko "Kokonaishintaiset työt" "Ei kokonaishintaisia töitä"
             :kht_laskutettu :kht_laskutetaan kht-yhteenveto tiedot]

            [taulukko "Yksikköhintaiset työt" "Ei yksikköhintaisia töitä"
             :yht_laskutettu :yht_laskutetaan yht-yhteenveto tiedot]

            [taulukko "Sanktiot" "Ei sanktioita"
             :sakot_laskutettu :sakot_laskutetaan sakot-yhteenveto tiedot]

            [taulukko "Talvisuolasakko (autom. laskettu)" "Ei talvisuolasakkoa"
             :suolasakot_laskutettu :suolasakot_laskutetaan suolasakot-yhteenveto talvihoidon-tiedot]

            [taulukko "Muutos- ja lisätyöt" "Ei muutos- ja lisätöitä"
             :muutostyot_laskutettu :muutostyot_laskutetaan muutostyot-yhteenveto tiedot]

            [taulukko "Erilliskustannukset" "Ei erilliskustannuksia"
             :erilliskustannukset_laskutettu :erilliskustannukset_laskutetaan erilliskustannukset-yhteenveto tiedot]

            [taulukko "Kokonaishintaisten töiden indeksitarkistukset" "Ei indeksitarkistuksia"
             :kht_laskutettu_ind_korotus :kht_laskutetaan_ind_korotus kht-ind-tar-yhteenveto tiedot]

            [taulukko "Yksikköhintaisten töiden indeksitarkistukset" "Ei indeksitarkistuksia"
             :yht_laskutettu_ind_korotus :yht_laskutetaan_ind_korotus yht-ind-tar-yhteenveto tiedot]

            [taulukko "Sanktioiden indeksitarkistukset" "Ei indeksitarkistuksia"
             :sakot_laskutettu_ind_korotus :sakot_laskutetaan_ind_korotus sakot-ind-tar-yhteenveto tiedot]

            [taulukko "Talvisuolasakon indeksitarkistus (autom. laskettu)" "Ei indeksitarkistuksia"
             :suolasakot_laskutettu_ind_korotus :suolasakot_laskutetaan_ind_korotus suolasakot-ind-tar-yhteenveto talvihoidon-tiedot]

            [taulukko "Muutos- ja lisätöiden indeksitarkistukset" "Ei indeksitarkistuksia"
             :muutostyot_laskutettu_ind_korotus :muutostyot_laskutetaan_ind_korotus muutostyot-ind-tar-yhteenveto tiedot]


            [taulukko "Erilliskustannusten indeksitarkistukset" "Ei indeksitarkistuksia"
             :erilliskustannukset_laskutettu_ind_korotus :erilliskustannukset_laskutetaan_ind_korotus erilliskustannukset-ind-tar-yhteenveto tiedot]

            [taulukko "Muiden kuin kok.hint. töiden indeksitarkistukset yhteensä" "Ei indeksitarkistuksia"
             :kaikki_paitsi_kht_laskutettu_ind_korotus :kaikki_paitsi_kht_laskutetaan_ind_korotus kaikki-paitsi-kht-ind-tar-yhteenveto tiedot]

            [taulukko "Kaikki indeksitarkistukset yhteensä" "Ei indeksitarkistuksia"
             :kaikki_laskutettu_ind_korotus :kaikki_laskutetaan_ind_korotus kaikki-ind-tar-yhteenveto tiedot]

            [taulukko "Kaikki paitsi kok.hint. työt yhteensä" "Ei kustannuksia"
             :kaikki_paitsi_kht_laskutettu :kaikki_paitsi_kht_laskutetaan kaikki-paitsi-kht-yhteenveto tiedot]

            [taulukko "Kaikki yhteensä" "Ei kustannuksia"
             :kaikki_laskutettu :kaikki_laskutetaan kaikki-yhteenveto tiedot]

            ])]))))
