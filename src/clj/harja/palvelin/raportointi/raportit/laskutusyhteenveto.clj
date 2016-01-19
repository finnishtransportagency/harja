(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto
  "Laskutusyhteenveto"
  (:require [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clj-time.local :as l]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]))

(defn hae-laskutusyhteenvedon-tiedot
  [db user {:keys [urakka-id alkupvm loppupvm] :as tiedot}]
  (let [[hk-alkupvm hk-loppupvm] (if (or (pvm/kyseessa-kk-vali? alkupvm loppupvm)
                                         (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm))
                                   ;; jos kyseessä vapaa aikaväli, lasketaan vain yksi sarake joten
                                   ;; hk-pvm:illä ei ole merkitystä, kunhan eivät konfliktoi alkupvm ja loppupvm kanssa
                                   (pvm/paivamaaran-hoitokausi alkupvm)
                                   [alkupvm loppupvm])]
    (log/debug "hae-urakan-laskutusyhteenvedon-tiedot" tiedot)
    (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
    (into []
          (laskutus-q/hae-laskutusyhteenvedon-tiedot db
                                                     (konv/sql-date hk-alkupvm)
                                                     (konv/sql-date hk-loppupvm)
                                                     (konv/sql-date alkupvm)
                                                     (konv/sql-date loppupvm)
                                                     urakka-id))))

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
                 laskutetaan-teksti laskutetaan-kentta
                 yhteenveto-teksti kyseessa-kk-vali?
                 tiedot]
  (let [laskutettu-yht (reduce + (keep laskutettu-kentta tiedot))
        laskutetaan-yht (reduce + (keep laskutetaan-kentta tiedot))
        yhteenveto (rivi "Toimenpiteet yhteensä"
                          (when kyseessa-kk-vali? (fmt/euro laskutettu-yht))
                          (when kyseessa-kk-vali? (fmt/euro laskutetaan-yht))
                          (or (fmt/euro (+ laskutettu-yht laskutetaan-yht)) 0))]
    [:taulukko {:viimeinen-rivi-yhteenveto? true}
     (rivi
        {:otsikko otsikko :leveys 40}
        (when kyseessa-kk-vali? {:otsikko laskutettu-teksti :leveys 20})
        (when kyseessa-kk-vali? {:otsikko laskutetaan-teksti :leveys 20})
        {:otsikko yhteenveto-teksti :leveys 20})

     (into []
           (concat
             (map (fn [tietorivi]
                    (rivi
                       (:nimi tietorivi)
                       (when kyseessa-kk-vali? (fmt/euro-indeksikorotus (tietorivi laskutettu-kentta)))
                       (when kyseessa-kk-vali? (fmt/euro-indeksikorotus (tietorivi laskutetaan-kentta)))
                       (fmt/euro-indeksikorotus (+ (or (tietorivi laskutettu-kentta) 0)
                                                   (or (tietorivi laskutetaan-kentta) 0))))) tiedot)
             [yhteenveto]))]))

(defn suorita [db user {:keys [alkupvm loppupvm] :as parametrit}]
  (log/debug "LASKUTUSYHTEENVETO PARAMETRIT: " (pr-str parametrit))
  (let [joda-aikavali (t/plus (tc/from-date alkupvm) (t/hours 2))
        kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        kyseessa-hoitokausi-vali? (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
        kyseessa-vuosi-vali? (pvm/kyseessa-vuosi-vali? alkupvm loppupvm)
        laskutettu-teksti (str "Laskutettu hoitokaudella ennen " (kuukausi alkupvm) "ta "
                               (pvm/vuosi joda-aikavali))
        laskutetaan-teksti (str "Laskutetaan " (kuukausi alkupvm) "ssa "
                                (pvm/vuosi joda-aikavali))
        yhteenveto-teksti (str (if (or kyseessa-kk-vali? kyseessa-hoitokausi-vali?)
                                 (str "Hoitokaudella " (pvm/vuosi (first (pvm/paivamaaran-hoitokausi alkupvm))) " - "
                                      (pvm/vuosi (second (pvm/paivamaaran-hoitokausi alkupvm))) " yhteensä")
                                 (if kyseessa-vuosi-vali?
                                   (str "Vuonna " (pvm/vuosi (l/to-local-date-time alkupvm)) " yhteensä")
                                   (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm) " yhteensä"))))
        tiedot (hae-laskutusyhteenvedon-tiedot db user parametrit)
        talvihoidon-tiedot (filter #(= (:tuotekoodi %) "23100") tiedot)
        avaimet (map name (keys (first tiedot)))
        laskutettu-korotus-kentat (mapv keyword (filter #(re-find #"laskutettu_ind_korotus" %) avaimet))
        laskutetaan-korotus-kentat (mapv keyword (filter #(re-find #"laskutetaan_ind_korotus" %) avaimet))
        indeksiarvo-puuttuu-jo-laskutetulta-ajalta? (some nil? (vals (select-keys (first tiedot) laskutettu-korotus-kentat)))
        indeksiarvo-puuttuu-valitulta-kklta? (some nil? (vals (select-keys (first tiedot) laskutetaan-korotus-kentat)))
        vain-jvh-viesti "Vain järjestelmän vastuuhenkilö voi syöttää indeksiarvoja Harjaan."
        perusluku-puuttuu? (not (:perusluku (first tiedot)))
        mahdollinen-varoitus-indeksiarvojen-puuttumisesta
        (if perusluku-puuttuu?
          [:varoitusteksti (str "Huom! Laskutusyhteenvedon laskennassa tarvittava urakan indeksiarvojen perusluku puuttuu tältä urakalta puutteellisten indeksitietojen vuoksi. "
                                vain-jvh-viesti)]
          (if (and indeksiarvo-puuttuu-jo-laskutetulta-ajalta? indeksiarvo-puuttuu-valitulta-kklta?)
            [:varoitusteksti (str "Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu sekä valitulta kuukaudelta että ajalta ennen sitä. "
                                  vain-jvh-viesti)]
            (if indeksiarvo-puuttuu-jo-laskutetulta-ajalta?
              [:varoitusteksti (str "Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu ajalta ennen valittua kuukautta. "
                                    vain-jvh-viesti)]
              (if indeksiarvo-puuttuu-valitulta-kklta?
                [:varoitusteksti (str "Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu. "
                                      vain-jvh-viesti)]))))]

    [:raportti {:nimi "Laskutusyhteenveto"}
     mahdollinen-varoitus-indeksiarvojen-puuttumisesta
     (map (fn [[otsikko tyhja laskutettu laskutetaan tiedot]]
            (taulukko otsikko tyhja
                      laskutettu-teksti laskutettu
                      laskutetaan-teksti laskutetaan
                      yhteenveto-teksti kyseessa-kk-vali?
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
         
                
                
                                                
