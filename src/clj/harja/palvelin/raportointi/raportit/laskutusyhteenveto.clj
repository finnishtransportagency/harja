(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto
  "Laskutusyhteenveto"
  (:require [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [harja.fmt :as fmt]))

(defn hae-laskutusyhteenvedon-tiedot
  [db user {:keys [urakka-id hk-alkupvm hk-loppupvm aikavali-alkupvm aikavali-loppupvm] :as tiedot}]
  (log/debug "hae-urakan-laskutusyhteenvedon-tiedot" tiedot)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [urakan-indeksi "MAKU 2010"] ;; indeksi jolla kok. ja yks. hint. työt korotetaan. Implementoidaan tässä tuki jos eri urakkatyyppi tarvii eri indeksiä
    (into []
          (laskutus-q/hae-laskutusyhteenvedon-tiedot db
                                                     (konv/sql-date hk-alkupvm)
                                                     (konv/sql-date hk-loppupvm)
                                                     (konv/sql-date aikavali-alkupvm)
                                                     (konv/sql-date aikavali-loppupvm)
                                                     urakka-id
                                                     urakan-indeksi))))

(defn- kuukausi [date]
  (.format (java.text.SimpleDateFormat. "MMMM") date))

(defn- taulukko [otsikko otsikko-jos-tyhja
                 laskutettu-teksti laskutettu-kentta
                 laskutetaan-teksti laskutetaan-kentta tiedot]
  (let [laskutettu-yht (reduce + (keep laskutettu-kentta tiedot))
        laskutetaan-yht (reduce + (keep laskutetaan-kentta tiedot))
        yhteenveto ["Toimenpiteet yhteensä"
                    (fmt/euro laskutettu-yht)
                    (fmt/euro laskutetaan-yht)
                    (fmt/euro (+ laskutettu-yht laskutetaan-yht))]]
    [:taulukko {:otsikko otsikko :viimeinen-rivi-yhteenveto? true}
     [{:otsikko "Toimenpide" :leveys "40%"}
      {:otsikko laskutettu-teksti :leveys "20%"} ;; FIXME: format ja tasaus
      {:otsikko  laskutetaan-teksti :leveys "20%"}
      {:otsikko "Hoitokaudella yhteensä" :leveys "20%"}]

     (into []
           (concat
            (map (fn [rivi]
                   [(:nimi rivi)
                    (fmt/euro-opt (rivi laskutettu-kentta))
                    (fmt/euro-opt (rivi laskutetaan-kentta))
                    (fmt/euro-opt (+ (or (rivi laskutettu-kentta) 0)
                                     (or (rivi laskutetaan-kentta) 0)))]) tiedot)
            [yhteenveto]))]))

(defn suorita [db user {:keys [aikavali-alkupvm aikavali-loppupvm] :as parametrit}]
  (log/debug "LASKUTUSYHTEENVETO PARAMETRIT: " (pr-str parametrit))
  (let [joda-aikavali (t/plus (tc/from-date aikavali-alkupvm) (t/hours 2))
        laskutettu-teksti  (str "Laskutettu hoitokaudella ennen "
                                (kuukausi aikavali-alkupvm)
                                "ta "
                                (pvm/vuosi joda-aikavali))
        laskutetaan-teksti  (str "Laskutetaan "
                                 (kuukausi aikavali-alkupvm)
                                 "ssa "
                                 (pvm/vuosi joda-aikavali))
        tiedot (hae-laskutusyhteenvedon-tiedot db user parametrit)
        talvihoidon-tiedot (filter #(= (:tuotekoodi %) "23100") tiedot)]
    
    [:raportti {:nimi "Laskutusyhteenveto"}
     (map (fn [[otsikko tyhja laskutettu laskutetaan tiedot]]
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
         
                
                
                                                
