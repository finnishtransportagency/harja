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
  (into []
        (laskutus-q/hae-laskutusyhteenvedon-tiedot db
                                                   (konv/sql-date hk-alkupvm)
                                                   (konv/sql-date hk-loppupvm)
                                                   (konv/sql-date aikavali-alkupvm)
                                                   (konv/sql-date aikavali-loppupvm)
                                                   urakka-id)))

(defn laske-asiakastyytyvaisyysbonus
  [db {:keys [urakka-id maksupvm indeksinimi summa] :as tiedot}]
  (log/debug "laske-asiakastyytyvaisyysbonus" tiedot)
  (assert (and maksupvm summa) "Annettava maksupvm ja summa jotta voidaan laskea asiakastyytyväisyysbonuksen arvo.")
  (first
    (into []
          (laskutus-q/laske-asiakastyytyvaisyysbonus db
                                                     urakka-id
                                                     (konv/sql-date maksupvm)
                                                     indeksinimi
                                                     summa))))

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
                    (fmt/euro-indeksikorotus (rivi laskutettu-kentta))
                    (fmt/euro-indeksikorotus (rivi laskutetaan-kentta))
                    (fmt/euro-indeksikorotus (+ (or (rivi laskutettu-kentta) 0)
                                     (or (rivi laskutetaan-kentta) 0)))]) tiedot)
            [yhteenveto]))]))

(defn suorita [db user {:keys [aikavali-alkupvm aikavali-loppupvm] :as parametrit}]
  (log/debug "LASKUTUSYHTEENVETO PARAMETRIT: " (pr-str parametrit))
  (let [joda-aikavali (t/plus (tc/from-date aikavali-alkupvm) (t/hours 2))
        laskutettu-teksti (str "Laskutettu hoitokaudella ennen " (kuukausi aikavali-alkupvm) "ta "
                               (pvm/vuosi joda-aikavali))
        laskutetaan-teksti (str "Laskutetaan " (kuukausi aikavali-alkupvm) "ssa "
                                (pvm/vuosi joda-aikavali))
        tiedot (hae-laskutusyhteenvedon-tiedot db user parametrit)
        talvihoidon-tiedot (filter #(= (:tuotekoodi %) "23100") tiedot)
        avaimet (map name (keys (first tiedot)))
        laskutettu-korotus-kentat (mapv keyword (filter #(re-find #"laskutettu_ind_korotus" %) avaimet))
        laskutetaan-korotus-kentat (mapv keyword (filter #(re-find #"laskutetaan_ind_korotus" %) avaimet))
        indeksiarvo-puuttuu-jo-laskutetulta-ajalta? (some nil? (vals (select-keys (first tiedot) laskutettu-korotus-kentat)))
        indeksiarvo-puuttuu-valitulta-kklta? (some nil? (vals (select-keys (first tiedot) laskutetaan-korotus-kentat)))
        vain-jvh-viesti "Vain järjestelmän vastuuhenkilö voi syöttää indeksiarvoja Harjaan."
        mahdollinen-varoitus-indeksiarvojen-puuttumisesta
        (if (and indeksiarvo-puuttuu-jo-laskutetulta-ajalta? indeksiarvo-puuttuu-valitulta-kklta?)
          [:varoitusteksti (str "Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu sekä valitulta kuukaudelta että ajalta ennen sitä. "
                                vain-jvh-viesti)]
          (if indeksiarvo-puuttuu-jo-laskutetulta-ajalta?
            [:varoitusteksti (str "Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu ajalta ennen valittua kuukautta. "
                                  vain-jvh-viesti)]
            (if indeksiarvo-puuttuu-valitulta-kklta?
              [:varoitusteksti (str "Huom! Laskutusyhteenvedon laskennassa tarvittava valitun kuukauden indeksiarvo puuttuu. "
                                    vain-jvh-viesti)])))]

    [:raportti {:nimi "Laskutusyhteenveto"}
     mahdollinen-varoitus-indeksiarvojen-puuttumisesta
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
           ["Muutos- ja lisätyöt sekä vahinkojen korjaukset" "Ei muutos- ja lisätöitä"
            :muutostyot_laskutettu :muutostyot_laskutetaan tiedot]
           ["Äkilliset hoitotyöt" "Ei äkillisiä hoitotöitä"
            :akilliset_hoitotyot_laskutettu :akilliset_hoitotyot_laskutetaan tiedot]
           ["Bonukset" "Ei bonuksia"
            :bonukset_laskutettu :bonukset_laskutetaan tiedot]
           ["Erilliskustannukset (muut kuin bonukset)" "Ei erilliskustannuksia"
            :erilliskustannukset_laskutettu :erilliskustannukset_laskutetaan tiedot]
           ["Kokonaishintaisten töiden indeksitarkistukset" "Ei indeksitarkistuksia"
            :kht_laskutettu_ind_korotus :kht_laskutetaan_ind_korotus tiedot]
           ["Yksikköhintaisten töiden indeksitarkistukset" "Ei indeksitarkistuksia"
            :yht_laskutettu_ind_korotus :yht_laskutetaan_ind_korotus tiedot]
           ["Sanktioiden indeksitarkistukset" "Ei indeksitarkistuksia"
            :sakot_laskutettu_ind_korotus :sakot_laskutetaan_ind_korotus tiedot]
           ["Talvisuolasakon indeksitarkistus (autom. laskettu)" "Ei indeksitarkistuksia"
            :suolasakot_laskutettu_ind_korotus :suolasakot_laskutetaan_ind_korotus talvihoidon-tiedot]
           ["Muutos- ja lisätöiden sekä vahinkojen korjausten indeksitarkistukset" "Ei indeksitarkistuksia"
            :muutostyot_laskutettu_ind_korotus :muutostyot_laskutetaan_ind_korotus tiedot]
           ["Äkillisten hoitotöiden indeksitarkistukset" "Ei indeksitarkistuksia"
            :akilliset_hoitotyot_laskutettu_ind_korotus :akilliset_hoitotyot_laskutetaan_ind_korotus tiedot]
           ["Bonusten indeksitarkistukset" "Ei indeksitarkistuksia"
            :bonukset_laskutettu_ind_korotus :bonukset_laskutetaan_ind_korotus tiedot]
           ["Erilliskustannusten indeksitarkistukset (muut kuin bonukset)" "Ei indeksitarkistuksia"
            :erilliskustannukset_laskutettu_ind_korotus :erilliskustannukset_laskutetaan_ind_korotus tiedot]
           ["Muiden kuin kok.hint. töiden indeksitarkistukset yhteensä" "Ei indeksitarkistuksia"
            :kaikki_paitsi_kht_laskutettu_ind_korotus :kaikki_paitsi_kht_laskutetaan_ind_korotus tiedot]
           ["Kaikki indeksitarkistukset yhteensä" "Ei indeksitarkistuksia"
            :kaikki_laskutettu_ind_korotus :kaikki_laskutetaan_ind_korotus tiedot]
           ["Kaikki paitsi kok.hint. työt yhteensä" "Ei kustannuksia"
            :kaikki_paitsi_kht_laskutettu :kaikki_paitsi_kht_laskutetaan tiedot]
           ["Kaikki yhteensä" "Ei kustannuksia"
            :kaikki_laskutettu :kaikki_laskutetaan tiedot]])]))
         
                
                
                                                
