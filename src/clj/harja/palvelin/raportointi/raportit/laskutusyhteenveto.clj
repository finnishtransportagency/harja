(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto
  "Laskutusyhteenveto"
  (:require [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]))

(defn hae-laskutusyhteenvedon-tiedot
  [db user {:keys [urakka-id hk-alkupvm hk-loppupvm aikavali-alkupvm aikavali-loppupvm] :as tiedot}]
  (log/debug "hae-urakan-laskutusyhteenvedon-tiedot" tiedot)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [urakan-indeksi "MAKU 2010"] ;; indeksi jolla kok. ja yks. hint. työt korotetaan. Implementoidaan tässä tuki jos eri urakkatyyppi tarvii eri indeksiä
    (into []
          (comp
           (map #(konv/decimal->double %
                                       :kaikki_paitsi_kht_laskutettu_ind_korotus :kaikki_laskutettu_ind_korotus
                                       :kaikki_paitsi_kht_laskutetaan_ind_korotus :kaikki_laskutetaan_ind_korotus
                                       :kaikki_paitsi_kht_laskutettu :kaikki_laskutettu
                                       :kaikki_paitsi_kht_laskutetaan :kaikki_laskutetaan
                                       
                                       :kht_laskutettu :kht_laskutettu_ind_korotettuna :kht_laskutettu_ind_korotus
                                       :kht_laskutetaan :kht_laskutetaan_ind_korotettuna :kht_laskutetaan_ind_korotus
                                       :yht_laskutettu :yht_laskutettu_ind_korotettuna :yht_laskutettu_ind_korotus
                                       :yht_laskutetaan :yht_laskutetaan_ind_korotettuna :yht_laskutetaan_ind_korotus
                                       :sakot_laskutettu :sakot_laskutettu_ind_korotettuna :sakot_laskutettu_ind_korotus
                                       :sakot_laskutetaan :sakot_laskutetaan_ind_korotettuna :sakot_laskutetaan_ind_korotus
                                       :suolasakot_laskutettu :suolasakot_laskutettu_ind_korotettuna :suolasakot_laskutettu_ind_korotus
                                       :suolasakot_laskutetaan :suolasakot_laskutetaan_ind_korotettuna :suolasakot_laskutetaan_ind_korotus
                                       :muutostyot_laskutettu :muutostyot_laskutettu_ind_korotettuna :muutostyot_laskutettu_ind_korotus
                                       :muutostyot_laskutetaan :muutostyot_laskutetaan_ind_korotettuna :muutostyot_laskutetaan_ind_korotus
                                       :erilliskustannukset_laskutettu :erilliskustannukset_laskutettu_ind_korotettuna :erilliskustannukset_laskutettu_ind_korotus
                                       :erilliskustannukset_laskutetaan :erilliskustannukset_laskutetaan_ind_korotettuna :erilliskustannukset_laskutetaan_ind_korotus))
           )
          (laskutus-q/hae-laskutusyhteenvedon-tiedot db
                                                     (konv/sql-date hk-alkupvm)
                                                     (konv/sql-date hk-loppupvm)
                                                     (konv/sql-date aikavali-alkupvm)
                                                     (konv/sql-date aikavali-loppupvm)
                                                     urakka-id
                                                     urakan-indeksi))))

(defn- taulukko [otsikko otsikko-jos-tyhja
                 laskutettu-teksti laskutettu-kentta
                 laskutetaan-teksti laskutetaan-kentta tiedot]
  (let [laskutettu-yht (reduce + (map laskutettu-kentta tiedot))
        laskutetaan-yht (reduce + (map laskutetaan-kentta tiedot))
        yhteenveto ["Toimenpiteet yhteensä"
                    laskutettu-yht
                    laskutetaan-yht
                    (+ laskutettu-yht laskutetaan-yht)]]
    (list 
     [:otsikko otsikko]
     [:taulukko 
      [{:otsikko "Toimenpide" :leveys "40%"}
       {:otsikko laskutettu-teksti :leveys "20%"} ;; FIXME: format ja tasaus
       {:otsikko  laskutetaan-teksti :leveys "20%"}
       {:otsikko "Hoitokaudella yhteensä" :leveys "20%"}]

      (into []
            (concat
             (map (fn [rivi]
                    [(:nimi rivi)
                     (rivi laskutettu-kentta)
                     (rivi laskutetaan-kentta)
                     (+ (rivi laskutettu-kentta)
                        (rivi laskutetaan-kentta))]) tiedot)
             [yhteenveto]))])))

(defn suorita [db user {:keys [aikavali-alkupvm aikavali-loppupvm]:as parametrit}]
  (log/info "PARAMETRIT: " (pr-str parametrit))
  ;; FIXME: pvm geneeriseksi cljc?
  (let [
        laskutettu-teksti  (str "Laskutettu hoitokaudella ennen "
                                (pvm/kuukauden-nimi (pvm/kuukausi aikavali-alkupvm))
                                "ta "
                                (pvm/vuosi aikavali-alkupvm))
        laskutetaan-teksti  (str "Laskutetaan "
                                 (pvm/kuukauden-nimi (pvm/kuukausi aikavali-alkupvm))
                                 "ssa "
                                 (pvm/vuosi aikavali-alkupvm))
        tiedot (hae-laskutusyhteenvedon-tiedot db user parametrit)
        talvihoidon-tiedot (filter #(= (:tuotekoodi %) "23100") tiedot)]
    [:raportti {:nimi "Laskutusyhteenveto"}
     (mapcat (fn [[otsikko tyhja laskutettu laskutetaan tiedot]]
               (taulukko otsikko tyhja
                         laskutettu-teksti laskutettu
                         laskutetaan-teksti laskutetaan
                         tiedot))
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
               :kaikki_laskutettu :kaikki_laskutetaan tiedot]])]))
         
                
                
                                                
