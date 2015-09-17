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

(defn laskutusyhteenveto
  []
  (komp/luo
    (komp/lippu laskutusyhteenveto-nakyvissa?)
    (fn []
      (let [ur @nav/valittu-urakka
            tiedot @laskutusyhteenvedon-tiedot
            talvihoidon-tiedot (filter #(= (:tuotekoodi %) "23100") tiedot)
            valittu-aikavali @u/valittu-hoitokauden-kuukausi
            taulukko (fn [otsikko otsikko-jos-tyhja
                          laskutettu-kentta laskutetaan-kentta
                          tiedot]
                       (let [yhteenveto {:nimi                        "Toimenpiteet yhteensä"
                                         :yhteenveto                  true
                                         laskutettu-kentta
                                                                      (reduce + (map laskutettu-kentta tiedot))
                                         laskutetaan-kentta (reduce + (map laskutetaan-kentta tiedot))}]
                         [grid/grid
                          {:otsikko      otsikko
                           :tyhja        otsikko-jos-tyhja
                           :tunniste     :nimi
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

                          (sort-by :yhteenveto (conj tiedot yhteenveto))]))]
        [:span.laskutusyhteenveto
         [:h3 "Laskutusyhteenveto"]
         [valinnat/urakan-hoitokausi ur]
         [valinnat/hoitokauden-kuukausi]

         (when (and ur @u/valittu-hoitokausi valittu-aikavali)
           [:span.tiedot

            [taulukko "Kokonaishintaiset työt" "Ei kokonaishintaisia töitä"
             :kht_laskutettu :kht_laskutetaan tiedot]

            [taulukko "Yksikköhintaiset työt" "Ei yksikköhintaisia töitä"
             :yht_laskutettu :yht_laskutetaan tiedot]

            [taulukko "Sanktiot" "Ei sanktioita"
             :sakot_laskutettu :sakot_laskutetaan tiedot]

            [taulukko "Talvisuolasakko (autom. laskettu)" "Ei talvisuolasakkoa"
             :suolasakot_laskutettu :suolasakot_laskutetaan talvihoidon-tiedot]

            [taulukko "Muutos- ja lisätyöt" "Ei muutos- ja lisätöitä"
             :muutostyot_laskutettu :muutostyot_laskutetaan tiedot]

            [taulukko "Erilliskustannukset" "Ei erilliskustannuksia"
             :erilliskustannukset_laskutettu :erilliskustannukset_laskutetaan tiedot]

            [taulukko "Kokonaishintaisten töiden indeksitarkistukset" "Ei indeksitarkistuksia"
             :kht_laskutettu_ind_korotus :kht_laskutetaan_ind_korotus tiedot]

            [taulukko "Yksikköhintaisten töiden indeksitarkistukset" "Ei indeksitarkistuksia"
             :yht_laskutettu_ind_korotus :yht_laskutetaan_ind_korotus tiedot]

            [taulukko "Sanktioiden indeksitarkistukset" "Ei indeksitarkistuksia"
             :sakot_laskutettu_ind_korotus :sakot_laskutetaan_ind_korotus tiedot]

            [taulukko "Talvisuolasakon indeksitarkistus (autom. laskettu)" "Ei indeksitarkistuksia"
             :suolasakot_laskutettu_ind_korotus :suolasakot_laskutetaan_ind_korotus talvihoidon-tiedot]

            [taulukko "Muutos- ja lisätöiden indeksitarkistukset" "Ei indeksitarkistuksia"
             :muutostyot_laskutettu_ind_korotus :muutostyot_laskutetaan_ind_korotus tiedot]

            [taulukko "Erilliskustannusten indeksitarkistukset" "Ei indeksitarkistuksia"
             :erilliskustannukset_laskutettu_ind_korotus :erilliskustannukset_laskutetaan_ind_korotus tiedot]

            [taulukko "Muiden kuin kok.hint. töiden indeksitarkistukset yhteensä" "Ei indeksitarkistuksia"
             :kaikki_paitsi_kht_laskutettu_ind_korotus :kaikki_paitsi_kht_laskutetaan_ind_korotus tiedot]

            [taulukko "Kaikki indeksitarkistukset yhteensä" "Ei indeksitarkistuksia"
             :kaikki_laskutettu_ind_korotus :kaikki_laskutetaan_ind_korotus tiedot]

            [taulukko "Kaikki paitsi kok.hint. työt yhteensä" "Ei kustannuksia"
             :kaikki_paitsi_kht_laskutettu :kaikki_paitsi_kht_laskutetaan tiedot]

            [taulukko "Kaikki yhteensä" "Ei kustannuksia"
             :kaikki_laskutettu :kaikki_laskutetaan tiedot]])]))))
