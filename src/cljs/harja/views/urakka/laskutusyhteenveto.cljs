(ns harja.views.urakka.laskutusyhteenveto
  "Urakan Laskutusyhteenveto välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            
            [harja.tiedot.urakka.laskutusyhteenveto :as laskutus-tiedot]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.yleiset :as yleiset]
            [harja.asiakas.kommunikaatio :as k])

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


(defn laskutusyhteenveto
  []
  (komp/luo
    (komp/lippu laskutusyhteenveto-nakyvissa?)
    (fn []
      (let [ur @nav/valittu-urakka
            tiedot @laskutusyhteenvedon-tiedot
            talvihoidon-tiedot (filter #(= (:tuotekoodi %) "23100") tiedot)
            valittu-aikavali @u/valittu-hoitokauden-kuukausi
            laskutettu-teksti  (str "Laskutettu hoitokaudella ennen "
                                    (pvm/kuukauden-nimi (pvm/kuukausi (first valittu-aikavali)))
                                    "ta "
                                    (pvm/vuosi (first valittu-aikavali)))
            laskutetaan-teksti  (str "Laskutetaan "
                                     (pvm/kuukauden-nimi (pvm/kuukausi (first valittu-aikavali)))
                                     "ssa "
                                     (pvm/vuosi (first valittu-aikavali)))
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
                           {:otsikko laskutettu-teksti
                            :nimi    laskutettu-kentta :tyyppi :numero :leveys "20%"
                            :fmt     fmt/euro-opt :tasaa :oikea}
                           {:otsikko  laskutetaan-teksti
                                          :nimi    laskutetaan-kentta :tyyppi :numero :leveys "20%"
                            :fmt     fmt/euro-opt :tasaa :oikea}
                           {:otsikko "Hoitokaudella yhteensä" :nimi :yhteensa :tyyppi :numero :leveys "20%" :fmt fmt/euro-opt :tasaa :oikea
                            :hae     (fn [rivi] (+ (laskutettu-kentta rivi)
                                                   (laskutetaan-kentta rivi)))}]

                          (sort-by :yhteenveto (conj tiedot yhteenveto))]))]
        [:span.laskutusyhteenveto
         [:h3 "Laskutusyhteenveto"]
         [valinnat/urakan-hoitokausi ur]
         [valinnat/hoitokauden-kuukausi]

         (when-let [kk @u/valittu-hoitokauden-kuukausi]
           [:form {:style {:float "right"} :target "_blank" :method "GET"
                   :action (k/pdf-url :laskutusyhteenveto)}
            [:input {:type "hidden" :name "_" :value "laskutusyhteenveto"}]
            [:input {:type "hidden" :name "u" :value (:id ur)}]
            [:input {:type "hidden" :name "vuosi" :value (pvm/vuosi (first kk))}]
            [:input {:type "hidden" :name "kk" :value (pvm/kuukausi (first kk))}]
            [:button.nappi-ensisijainen {:type "submit"}
             (ikonit/print)
             " Lataa PDF"]])
         
         (when (and ur @u/valittu-hoitokausi valittu-aikavali)
           [:span.tiedot
            (for [[otsikko tyhja laskutettu laskutetaan tiedot]
                  [["Kokonaishintaiset työt" "Ei kokonaishintaisia töitä"
                    :kht_laskutettu :kht_laskutetaan tiedot]
                   ["Yksikköhintaiset työt" "Ei yksikköhintaisia töitä"
                    :yht_laskutettu :yht_laskutetaan tiedot]
                   ["Sanktiot" "Ei sanktioita"
                    :sakot_laskutettu :sakot_laskutetaan tiedot]
                   ["Talvisuolasakko (autom. laskettu)" "Ei talvisuolasakkoa"
                    :suolasakot_laskutettu :suolasakot_laskutetaan talvihoidon-tiedot]
                   ["Muutos- ja lisätyöt" "Ei muutos- ja lisätöitä"
                    :muutostyot_laskutettu :muutostyot_laskutetaan tiedot]
                   ["Erilliskustannukset" "Ei erilliskustannuksia"
                    :erilliskustannukset_laskutettu :erilliskustannukset_laskutetaan tiedot]
                   ["Kokonaishintaisten töiden indeksitarkistukset" "Ei indeksitarkistuksia"
                    :kht_laskutettu_ind_korotus :kht_laskutetaan_ind_korotus tiedot]
                   ["Yksikköhintaisten töiden indeksitarkistukset" "Ei indeksitarkistuksia"
                    :yht_laskutettu_ind_korotus :yht_laskutetaan_ind_korotus tiedot]
                   ["Sanktioiden indeksitarkistukset" "Ei indeksitarkistuksia"
                    :sakot_laskutettu_ind_korotus :sakot_laskutetaan_ind_korotus tiedot]
                   ["Talvisuolasakon indeksitarkistus (autom. laskettu)" "Ei indeksitarkistuksia"
                    :suolasakot_laskutettu_ind_korotus :suolasakot_laskutetaan_ind_korotus talvihoidon-tiedot]
                   ["Muutos- ja lisätöiden indeksitarkistukset" "Ei indeksitarkistuksia"
                    :muutostyot_laskutettu_ind_korotus :muutostyot_laskutetaan_ind_korotus tiedot]
                   ["Erilliskustannusten indeksitarkistukset" "Ei indeksitarkistuksia"
                    :erilliskustannukset_laskutettu_ind_korotus :erilliskustannukset_laskutetaan_ind_korotus tiedot]
                   ["Muiden kuin kok.hint. töiden indeksitarkistukset yhteensä" "Ei indeksitarkistuksia"
                    :kaikki_paitsi_kht_laskutettu_ind_korotus :kaikki_paitsi_kht_laskutetaan_ind_korotus tiedot]
                   ["Kaikki indeksitarkistukset yhteensä" "Ei indeksitarkistuksia"
                    :kaikki_laskutettu_ind_korotus :kaikki_laskutetaan_ind_korotus tiedot]
                   ["Kaikki paitsi kok.hint. työt yhteensä" "Ei kustannuksia"
                    :kaikki_paitsi_kht_laskutettu :kaikki_paitsi_kht_laskutetaan tiedot]
                   ["Kaikki yhteensä" "Ei kustannuksia"
                    :kaikki_laskutettu :kaikki_laskutetaan tiedot]]]
              ^{:key (str otsikko)}
              [taulukko otsikko tyhja laskutettu laskutetaan tiedot])])]))))
