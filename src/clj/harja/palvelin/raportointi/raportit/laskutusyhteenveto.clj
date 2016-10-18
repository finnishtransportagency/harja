(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto
  "Laskutusyhteenveto"
  (:require [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi +erheen-vari+]]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.pvm :as pvm]
            [clj-time.local :as l]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.domain.toimenpidekoodit :as toimenpidekoodit]))


(defn hae-laskutusyhteenvedon-tiedot
  [db user {:keys [urakka-id alkupvm loppupvm] :as tiedot}]
  (let [[hk-alkupvm hk-loppupvm] (if (or (pvm/kyseessa-kk-vali? alkupvm loppupvm)
                                         (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm))
                                   ;; jos kyseessä vapaa aikaväli, lasketaan vain yksi sarake joten
                                   ;; hk-pvm:illä ei ole merkitystä, kunhan eivät konfliktoi alkupvm ja loppupvm kanssa
                                   (pvm/paivamaaran-hoitokausi alkupvm)
                                   [alkupvm loppupvm])]
    (log/debug "hae-urakan-laskutusyhteenvedon-tiedot" tiedot)

    (vec
     (sort-by (juxt (comp toimenpidekoodit/tuotteen-jarjestys :tuotekoodi) :nimi)
              (into []
                    (laskutus-q/hae-laskutusyhteenvedon-tiedot db
                                                               (konv/sql-date hk-alkupvm)
                                                               (konv/sql-date hk-loppupvm)
                                                               (konv/sql-date alkupvm)
                                                               (konv/sql-date loppupvm)
                                                               urakka-id))))))

(defn laske-asiakastyytyvaisyysbonus
  [db {:keys [urakka-id maksupvm indeksinimi summa] :as tiedot}]
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

(defn- naytetty-indeksiton-summa?
  [kentta]
  (#{:kaikki_paitsi_kht_laskutettu_ilman_korotuksia :kaikki_laskutettu_ilman_korotuksia
     :kaikki_paitsi_kht_laskutetaan_ilman_korotuksia :kaikki_laskutetaan_ilman_korotuksia} kentta))

(defn- taulukko
  ([otsikko otsikko-jos-tyhja
    laskutettu-teksti laskutettu-kentta
    laskutetaan-teksti laskutetaan-kentta
    yhteenveto-teksti kyseessa-kk-vali?
    tiedot summa-fmt]
  (let [laskutettu-kentat (map laskutettu-kentta tiedot)
        laskutetaan-kentat (map laskutetaan-kentta tiedot)
        kaikkien-toimenpiteiden-summa (fn [kentat]
                                        (if (some nil? kentat)
                                          (reduce + (keep identity kentat))
                                          (reduce + kentat)))
        laskutettu-yht (kaikkien-toimenpiteiden-summa laskutettu-kentat)
        laskutetaan-yht (kaikkien-toimenpiteiden-summa laskutetaan-kentat)
        laskutettu-summasta-puuttuu-indeksi? (naytetty-indeksiton-summa? laskutettu-kentta)
        laskutetaan-summasta-puuttuu-indeksi? (naytetty-indeksiton-summa? laskutetaan-kentta)
        yhteenveto (rivi "Toimenpiteet yhteensä"
                         (when kyseessa-kk-vali? [:varillinen-teksti {:arvo (summa-fmt laskutettu-yht)
                                                                      :vari (when laskutettu-summasta-puuttuu-indeksi? +erheen-vari+)}])
                         (when kyseessa-kk-vali? [:varillinen-teksti {:arvo (summa-fmt laskutetaan-yht)
                                                                      :vari (when laskutetaan-summasta-puuttuu-indeksi? +erheen-vari+)}])
                         (if (and laskutettu-yht laskutetaan-yht)
                           [:varillinen-teksti {:arvo (summa-fmt (+ laskutettu-yht laskutetaan-yht))
                                                :vari (when (or laskutettu-summasta-puuttuu-indeksi?
                                                                laskutetaan-summasta-puuttuu-indeksi?) +erheen-vari+)}]
                           nil))
        taulukon-tiedot (filter (fn [[_ laskutettu laskutetaan]]
                                  (not (and (= 0.0M laskutettu)
                                            (= 0.0M laskutetaan))))
                                (map (juxt :nimi laskutettu-kentta laskutetaan-kentta)
                                     tiedot))]
    (when-not (empty? taulukon-tiedot)
      [:taulukko {:oikealle-tasattavat-kentat #{1 2 3}
                  :viimeinen-rivi-yhteenveto? true}
       (rivi
         {:otsikko otsikko :leveys 36}
         (when kyseessa-kk-vali? {:otsikko laskutettu-teksti :leveys 29})
         (when kyseessa-kk-vali? {:otsikko laskutetaan-teksti :leveys 24})
         {:otsikko yhteenveto-teksti :leveys 29})

       (into []
             (concat
               (map (fn [[nimi laskutettu laskutetaan]]
                      (rivi
                        nimi
                        (when kyseessa-kk-vali? [:varillinen-teksti {:arvo (summa-fmt laskutettu)
                                                                     :vari (when laskutettu-summasta-puuttuu-indeksi? +erheen-vari+)}])
                        (when kyseessa-kk-vali? [:varillinen-teksti {:arvo (summa-fmt laskutetaan)
                                                                     :vari (when laskutetaan-summasta-puuttuu-indeksi? +erheen-vari+)}])
                        (if (and laskutettu laskutetaan)
                          [:varillinen-teksti {:arvo (summa-fmt (+ laskutettu laskutetaan))
                                               :vari (when (or laskutettu-summasta-puuttuu-indeksi?
                                                               laskutetaan-summasta-puuttuu-indeksi?) +erheen-vari+)}]
                          (summa-fmt nil)))) taulukon-tiedot)
               [yhteenveto]))]))))

(defn- aseta-sheet-nimi [[ensimmainen & muut]]
  (when ensimmainen
    (concat [(assoc-in ensimmainen [1 :sheet-nimi] "Laskutusyhteenveto")]
            muut)))

(defn suorita [db user {:keys [alkupvm loppupvm urakka-id] :as parametrit}]
  (log/debug "LASKUTUSYHTEENVETO PARAMETRIT: " (pr-str parametrit))
  (let [{urakan-nimi :nimi indeksi :indeksi} (first (urakat-q/hae-urakka db urakka-id))
        indeksi-kaytossa? (some? indeksi)
        kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        kyseessa-hoitokausi-vali? (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
        kyseessa-vuosi-vali? (pvm/kyseessa-vuosi-vali? alkupvm loppupvm)
        laskutettu-teksti (str "Laskutettu hoito\u00ADkaudella ennen " (pvm/kuukausi-ja-vuosi alkupvm) " \u20AC")
        laskutetaan-teksti (str "Laskutetaan " (pvm/kuukausi-ja-vuosi alkupvm) " \u20AC")
        yhteenveto-teksti (str (if (or kyseessa-kk-vali? kyseessa-hoitokausi-vali?)
                                 (str "Hoitokaudella " (pvm/vuosi (first (pvm/paivamaaran-hoitokausi alkupvm))) "-"
                                      (pvm/vuosi (second (pvm/paivamaaran-hoitokausi alkupvm))) " yhteensä" " \u20AC")
                                 (if kyseessa-vuosi-vali?
                                   (str "Vuonna " (pvm/vuosi (l/to-local-date-time alkupvm)) " yhteensä" " \u20AC")
                                   (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm) " yhteensä"))))
        tiedot (hae-laskutusyhteenvedon-tiedot db user parametrit)
        avaimet (map name (keys (first tiedot)))
        ;; poistetaan suolasakot-kentät, koska sen nil voi aiheutua myös lämpötilojen puuttumisesta.
        ;; Halutaan selvittää mahd. tarkasti erikseen puuttuuko indeksiarvoja, lämpötiloja vai molempia
        laskutettu-korotus-kentat (mapv keyword (filter #(and
                                                          (re-find #"laskutettu_ind_korotus" %)
                                                          (not (re-find #"suolasakot_laskutettu_ind_korotus" %))) avaimet))
        laskutetaan-korotus-kentat (mapv keyword (filter #(and
                                                           (re-find #"laskutetaan_ind_korotus" %)
                                                           (not (re-find #"suolasakot_laskutetaan_ind_korotus" %))) avaimet))
        indeksiarvo-puuttuu-jo-laskutetulta-ajalta? (first (keep #(some nil? (vals (select-keys % laskutettu-korotus-kentat))) tiedot))
        indeksiarvo-puuttuu-valitulta-kklta? (first (keep #(some nil? (vals (select-keys % laskutetaan-korotus-kentat))) tiedot))

        perusluku-puuttuu? (not (:perusluku (first tiedot)))
        talvisuolasakko-kaytossa? (some :suolasakko_kaytossa tiedot)
        suolasakkojen-laskenta-epaonnistui? (some
                                              #(nil? (val %))
                                              (select-keys (first (filter #(= "Talvihoito" (:nimi %)) tiedot))
                                                           [:suolasakot_laskutetaan :suolasakot_laskutettu]))
        nayta-etta-lampotila-puuttuu? (when (and talvisuolasakko-kaytossa? suolasakkojen-laskenta-epaonnistui?)
                                        (first (keep #(true? (:lampotila_puuttuu %))
                                                     tiedot)))
        varoitus-lampotilojen-puuttumisesta (if nayta-etta-lampotila-puuttuu?
                                              " Lämpötilatietoja puuttuu. "
                                              " ")
        varoitus-indeksitietojen-puuttumisesta
        (when indeksi-kaytossa?
          (if perusluku-puuttuu?
            " Huom! Laskutusyhteenvedon laskennassa tarvittava urakan indeksiarvojen perusluku puuttuu tältä urakalta puutteellisten indeksitietojen vuoksi. "
            (if (and indeksiarvo-puuttuu-jo-laskutetulta-ajalta? indeksiarvo-puuttuu-valitulta-kklta?)
              " Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu sekä valitulta kuukaudelta että ajalta ennen sitä. "
              (if indeksiarvo-puuttuu-jo-laskutetulta-ajalta?
                " Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu ajalta ennen valittua kuukautta. "
                (if indeksiarvo-puuttuu-valitulta-kklta?
                  " Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu. "
                  " ")))))
        vain-jvh-voi-muokata-tietoja-viesti (str "Vain järjestelmän vastuuhenkilö voi syöttää "
                                                 (if (str/blank? varoitus-indeksitietojen-puuttumisesta)
                                                   ""
                                                   " indeksiarvoja ")
                                                 (if (and (not (str/blank? varoitus-indeksitietojen-puuttumisesta))
                                                          nayta-etta-lampotila-puuttuu?)
                                                   " ja "
                                                   "")
                                                 (if nayta-etta-lampotila-puuttuu? " lämpötiloja " " ") " Harjaan. ")

        varoitus-tietojen-puuttumisesta
        (when (or (not (str/blank? varoitus-indeksitietojen-puuttumisesta))
                (not (str/blank? varoitus-lampotilojen-puuttumisesta)))
          [:varoitusteksti (str varoitus-indeksitietojen-puuttumisesta
                                varoitus-lampotilojen-puuttumisesta
                                vain-jvh-voi-muokata-tietoja-viesti)])

        taulukot
        (aseta-sheet-nimi
         (keep (fn [[otsikko tyhja laskutettu laskutetaan tiedot summa-fmt :as taulukko-rivi]]
                 (when taulukko-rivi
                   (taulukko otsikko tyhja
                             laskutettu-teksti laskutettu
                             laskutetaan-teksti laskutetaan
                             yhteenveto-teksti kyseessa-kk-vali?
                             tiedot (or summa-fmt
                                        (if indeksi-kaytossa?
                                          fmt/luku-indeksikorotus
                                          fmt/euro-opt)))))
               [[" Kokonaishintaiset työt " " Ei kokonaishintaisia töitä "
                 :kht_laskutettu :kht_laskutetaan tiedot]
                [" Yksikköhintaiset työt " " Ei yksikköhintaisia töitä "
                 :yht_laskutettu :yht_laskutetaan tiedot]
                [" Sanktiot " " Ei sanktioita "
                 :sakot_laskutettu :sakot_laskutetaan tiedot]
                (when talvisuolasakko-kaytossa?
                  [" Talvisuolasakko (autom. laskettu) " " Ei talvisuolasakkoa "
                   :suolasakot_laskutettu :suolasakot_laskutetaan tiedot fmt/euro-ei-voitu-laskea])
                [" Muutos- ja lisätyöt sekä vahinkojen korjaukset " " Ei muutos- ja lisätöitä "
                 :muutostyot_laskutettu :muutostyot_laskutetaan tiedot]
                [" Äkilliset hoitotyöt " " Ei äkillisiä hoitotöitä "
                 :akilliset_hoitotyot_laskutettu :akilliset_hoitotyot_laskutetaan tiedot]
                [" Bonukset " " Ei bonuksia "
                 :bonukset_laskutettu :bonukset_laskutetaan tiedot]
                [" Erilliskustannukset (muut kuin bonukset) " " Ei erilliskustannuksia "
                 :erilliskustannukset_laskutettu :erilliskustannukset_laskutetaan tiedot]
                (when indeksi-kaytossa?
                  [" Kokonaishintaisten töiden indeksitarkistukset " " Ei indeksitarkistuksia "
                   :kht_laskutettu_ind_korotus :kht_laskutetaan_ind_korotus tiedot])
                (when indeksi-kaytossa?
                  [" Yksikköhintaisten töiden indeksitarkistukset " " Ei indeksitarkistuksia "
                   :yht_laskutettu_ind_korotus :yht_laskutetaan_ind_korotus tiedot])
                (when indeksi-kaytossa?
                  [" Sanktioiden indeksitarkistukset " " Ei indeksitarkistuksia "
                   :sakot_laskutettu_ind_korotus :sakot_laskutetaan_ind_korotus tiedot])
                (when (and indeksi-kaytossa? talvisuolasakko-kaytossa?)
                  [" Talvisuolasakon indeksitarkistus (autom. laskettu) " " Ei indeksitarkistuksia "
                   :suolasakot_laskutettu_ind_korotus :suolasakot_laskutetaan_ind_korotus tiedot fmt/euro-ei-voitu-laskea])
                (when indeksi-kaytossa?
                  [" Muutos- ja lisätöiden sekä vahinkojen korjausten indeksitarkistukset " " Ei indeksitarkistuksia "
                   :muutostyot_laskutettu_ind_korotus :muutostyot_laskutetaan_ind_korotus tiedot])
                (when indeksi-kaytossa?
                  [" Äkillisten hoitotöiden indeksitarkistukset " " Ei indeksitarkistuksia "
                   :akilliset_hoitotyot_laskutettu_ind_korotus :akilliset_hoitotyot_laskutetaan_ind_korotus tiedot])
                (when indeksi-kaytossa?
                  [" Bonusten indeksitarkistukset " " Ei indeksitarkistuksia "
                   :bonukset_laskutettu_ind_korotus :bonukset_laskutetaan_ind_korotus tiedot])
                (when indeksi-kaytossa?
                  [" Erilliskustannusten indeksitarkistukset (muut kuin bonukset) " " Ei indeksitarkistuksia "
                   :erilliskustannukset_laskutettu_ind_korotus :erilliskustannukset_laskutetaan_ind_korotus tiedot])
                (when indeksi-kaytossa?
                  [" Muiden kuin kok.hint. töiden indeksitarkistukset yhteensä " " Ei indeksitarkistuksia "
                   :kaikki_paitsi_kht_laskutettu_ind_korotus :kaikki_paitsi_kht_laskutetaan_ind_korotus tiedot])
                (when indeksi-kaytossa?
                  [" Kaikki indeksitarkistukset yhteensä " " Ei indeksitarkistuksia "
                   :kaikki_laskutettu_ind_korotus :kaikki_laskutetaan_ind_korotus tiedot])
                [" Kaikki paitsi kok.hint. työt yhteensä " " Ei kustannuksia "
                 (if (some nil? (map :kaikki_paitsi_kht_laskutettu tiedot))
                   :kaikki_paitsi_kht_laskutettu_ilman_korotuksia
                   :kaikki_paitsi_kht_laskutettu)
                 (if (some nil? (map :kaikki_paitsi_kht_laskutetaan tiedot))
                   :kaikki_paitsi_kht_laskutetaan_ilman_korotuksia
                   :kaikki_paitsi_kht_laskutetaan)
                 tiedot]
                [" Kaikki yhteensä " " Ei kustannuksia "
                 (if (some nil? (map :kaikki_laskutettu tiedot))
                   :kaikki_laskutettu_ilman_korotuksia
                   :kaikki_laskutettu)
                 (if (some nil? (map :kaikki_laskutetaan tiedot))
                   :kaikki_laskutetaan_ilman_korotuksia
                   :kaikki_laskutetaan) tiedot]]))]

    (vec (keep identity
               [:raportti {:nimi "Laskutusyhteenveto"}
                [:otsikko (str (or (str urakan-nimi ", ") "") (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))]
                varoitus-tietojen-puuttumisesta
                (if (empty? taulukot)
                  [:teksti " Ei laskutettavaa"]
                  taulukot)]))))
