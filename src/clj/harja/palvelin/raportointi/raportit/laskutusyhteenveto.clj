(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto
  "Laskutusyhteenveto"
  (:require [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.pvm :as pvm]
            [clj-time.local :as l]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot :as yks-hint-tyot]))

(defn hae-laskutusyhteenvedon-tiedot
  [db user {:keys [urakka-id alkupvm loppupvm] :as tiedot}]
  (let [[hk-alkupvm hk-loppupvm] (if (or (pvm/kyseessa-kk-vali? alkupvm loppupvm)
                                         (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm))
                                   ;; jos kyseessä vapaa aikaväli, lasketaan vain yksi sarake joten
                                   ;; hk-pvm:illä ei ole merkitystä, kunhan eivät konfliktoi alkupvm ja loppupvm kanssa
                                   (pvm/paivamaaran-hoitokausi alkupvm)
                                   [alkupvm loppupvm])]
    (log/debug "hae-urakan-laskutusyhteenvedon-tiedot" tiedot)
    (into []
          (laskutus-q/hae-laskutusyhteenvedon-tiedot db
                                                     (konv/sql-date hk-alkupvm)
                                                     (konv/sql-date hk-loppupvm)
                                                     (konv/sql-date alkupvm)
                                                     (konv/sql-date loppupvm)
                                                     urakka-id))))

(defn hae-toteutuneet-tehtavat [])

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

(defn- taulukko
  ([otsikko otsikko-jos-tyhja
    laskutettu-teksti laskutettu-kentta
    laskutetaan-teksti laskutetaan-kentta
    yhteenveto-teksti kyseessa-kk-vali?
    tiedot summa-fmt]
  (let [laskutettu-kentat (map laskutettu-kentta tiedot)
        laskutetaan-kentat (map laskutetaan-kentta tiedot)
        kaikkien-toimenpiteiden-summa (fn [kentat]
                                              (if (some nil? kentat) nil (reduce + kentat)))
        laskutettu-yht (kaikkien-toimenpiteiden-summa laskutettu-kentat)
        laskutetaan-yht (kaikkien-toimenpiteiden-summa laskutetaan-kentat)
        yhteenveto (rivi "Toimenpiteet yhteensä"
                         (when kyseessa-kk-vali? (summa-fmt laskutettu-yht))
                         (when kyseessa-kk-vali? (summa-fmt laskutetaan-yht))
                         (summa-fmt (if (and laskutettu-yht laskutetaan-yht)
                                                    (+ laskutettu-yht laskutetaan-yht)
                                                    nil)))
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
                        (when kyseessa-kk-vali? (summa-fmt laskutettu))
                        (when kyseessa-kk-vali? (summa-fmt laskutetaan))
                        (summa-fmt (if (and laskutettu laskutetaan)
                                                   (+ laskutettu laskutetaan)
                                                   nil)))) taulukon-tiedot)
               [yhteenveto]))]))))

(defn- aseta-sheet-nimi [[ensimmainen & muut]]
  (when ensimmainen
    (concat [(assoc-in ensimmainen [1 :sheet-nimi] "Laskutusyhteenveto")]
            muut)))

(defn suorita [db user {:keys [alkupvm loppupvm urakka-id] :as parametrit}]
  (log/debug "LASKUTUSYHTEENVETO PARAMETRIT: " (pr-str parametrit))
  (let [urakan-nimi (when urakka-id (:nimi (first (urakat-q/hae-urakka db urakka-id))))
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
        laskutustiedot (hae-laskutusyhteenvedon-tiedot db user parametrit)
        toteutuneet-maarat-tahan-asti (as-> (laskutus-q/hae-yks-hint-tehtavien-maarat-aikaan-asti db urakka-id loppupvm)
                                            toteumat
                                            (yks-hint-tyot/liita-toteumiin-suunnittelutiedot
                                              alkupvm
                                              loppupvm
                                              toteumat
                                              (yks-hint-tyot/hae-urakan-yks-hint-suunnittelutiedot db urakka-id)))
        suunnitellut-kustannukset-yhteensa (reduce (fn [tulos {:keys [suunniteltu_maara yksikkohinta]}]
                                                     (+ tulos (if (and suunniteltu_maara yksikkohinta)
                                                                (* suunniteltu_maara yksikkohinta)
                                                                0)))
                                                   0
                                                   toteutuneet-maarat-tahan-asti)
        toteutuneet-kustannukset-yhteensa (reduce (fn [tulos {:keys [maara yksikkohinta]}]
                                                    (+ tulos (if (and maara yksikkohinta)
                                                               (* maara yksikkohinta)
                                                               0)))
                                                  0
                                                  toteutuneet-maarat-tahan-asti)
        toteumat-ilman-suunnitelmaa (filter
                                       #(nil? (:suunniteltu_maara %))
                                       toteutuneet-maarat-tahan-asti)
        avaimet (map name (keys (first laskutustiedot)))
        ;; poistetaan suolasakot-kentät, koska sen nil voi aiheutua myös lämpötilojen puuttumisesta.
        ;; Halutaan selvittää mahd. tarkasti erikseen puuttuuko indeksiarvoja, lämpötiloja vai molempia
        laskutettu-korotus-kentat (mapv keyword (filter #(and
                                                          (re-find #"laskutettu_ind_korotus" %)
                                                          (not (re-find #"suolasakot_laskutettu_ind_korotus" %))) avaimet))
        laskutetaan-korotus-kentat (mapv keyword (filter #(and
                                                           (re-find #"laskutetaan_ind_korotus" %)
                                                           (not (re-find #"suolasakot_laskutetaan_ind_korotus" %))) avaimet))
        indeksiarvo-puuttuu-jo-laskutetulta-ajalta? (first (keep #(some nil? (vals (select-keys % laskutettu-korotus-kentat))) laskutustiedot))
        indeksiarvo-puuttuu-valitulta-kklta? (first (keep #(some nil? (vals (select-keys % laskutetaan-korotus-kentat))) laskutustiedot))

        perusluku-puuttuu? (not (:perusluku (first laskutustiedot)))
        talvisuolasakko-kaytossa? (some :suolasakko_kaytossa laskutustiedot)
        suolasakkojen-laskenta-epaonnistui? (some
                                              #(nil? (val %))
                                              (select-keys (first (filter #(= "Talvihoito" (:nimi %)) laskutustiedot))
                                                           [:suolasakot_laskutetaan :suolasakot_laskutettu]))
        nayta-etta-lampotila-puuttuu? (when (and talvisuolasakko-kaytossa? suolasakkojen-laskenta-epaonnistui?)
                                        (first (keep #(true? (:lampotila_puuttuu %))
                                                     laskutustiedot)))
        varoitus-lampotilojen-puuttumisesta (if nayta-etta-lampotila-puuttuu?
                                              " Lämpötilatietoja puuttuu. "
                                              " ")
        varoitus-indeksitietojen-puuttumisesta
        (if perusluku-puuttuu?
          " Huom! Laskutusyhteenvedon laskennassa tarvittava urakan indeksiarvojen perusluku puuttuu tältä urakalta puutteellisten indeksitietojen vuoksi. "
          (if (and indeksiarvo-puuttuu-jo-laskutetulta-ajalta? indeksiarvo-puuttuu-valitulta-kklta?)
            " Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu sekä valitulta kuukaudelta että ajalta ennen sitä. "
            (if indeksiarvo-puuttuu-jo-laskutetulta-ajalta?
              " Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu ajalta ennen valittua kuukautta. "
              (if indeksiarvo-puuttuu-valitulta-kklta?
                " Huom! Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu. "
                " "))))
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

        taulukot (aseta-sheet-nimi
                  (keep (fn [[otsikko tyhja laskutettu laskutetaan tiedot summa-fmt]]
                          (taulukko otsikko tyhja
                                    laskutettu-teksti laskutettu
                                    laskutetaan-teksti laskutetaan
                                    yhteenveto-teksti kyseessa-kk-vali?
                                    tiedot (or summa-fmt fmt/luku-indeksikorotus)))
                        [[" Kokonaishintaiset työt " " Ei kokonaishintaisia töitä "
                          :kht_laskutettu :kht_laskutetaan laskutustiedot]
                         [" Yksikköhintaiset työt " " Ei yksikköhintaisia töitä "
                          :yht_laskutettu :yht_laskutetaan laskutustiedot]
                         [" Sanktiot " " Ei sanktioita "
                          :sakot_laskutettu :sakot_laskutetaan laskutustiedot]
                         (when talvisuolasakko-kaytossa?
                           [" Talvisuolasakko (autom. laskettu) " " Ei talvisuolasakkoa "
                            :suolasakot_laskutettu :suolasakot_laskutetaan laskutustiedot fmt/euro-ei-voitu-laskea])
                         [" Muutos- ja lisätyöt sekä vahinkojen korjaukset " " Ei muutos- ja lisätöitä "
                          :muutostyot_laskutettu :muutostyot_laskutetaan laskutustiedot]
                         [" Äkilliset hoitotyöt " " Ei äkillisiä hoitotöitä "
                          :akilliset_hoitotyot_laskutettu :akilliset_hoitotyot_laskutetaan laskutustiedot]
                         [" Bonukset " " Ei bonuksia "
                          :bonukset_laskutettu :bonukset_laskutetaan laskutustiedot]
                         [" Erilliskustannukset (muut kuin bonukset) " " Ei erilliskustannuksia "
                          :erilliskustannukset_laskutettu :erilliskustannukset_laskutetaan laskutustiedot]
                         [" Kokonaishintaisten töiden indeksitarkistukset " " Ei indeksitarkistuksia "
                          :kht_laskutettu_ind_korotus :kht_laskutetaan_ind_korotus laskutustiedot]
                         [" Yksikköhintaisten töiden indeksitarkistukset " " Ei indeksitarkistuksia "
                          :yht_laskutettu_ind_korotus :yht_laskutetaan_ind_korotus laskutustiedot]
                         [" Sanktioiden indeksitarkistukset " " Ei indeksitarkistuksia "
                          :sakot_laskutettu_ind_korotus :sakot_laskutetaan_ind_korotus laskutustiedot]
                         (when talvisuolasakko-kaytossa?
                           [" Talvisuolasakon indeksitarkistus (autom. laskettu) " " Ei indeksitarkistuksia "
                            :suolasakot_laskutettu_ind_korotus :suolasakot_laskutetaan_ind_korotus laskutustiedot fmt/euro-ei-voitu-laskea])
                         [" Muutos- ja lisätöiden sekä vahinkojen korjausten indeksitarkistukset " " Ei indeksitarkistuksia "
                          :muutostyot_laskutettu_ind_korotus :muutostyot_laskutetaan_ind_korotus laskutustiedot]
                         [" Äkillisten hoitotöiden indeksitarkistukset " " Ei indeksitarkistuksia "
                          :akilliset_hoitotyot_laskutettu_ind_korotus :akilliset_hoitotyot_laskutetaan_ind_korotus laskutustiedot]
                         [" Bonusten indeksitarkistukset " " Ei indeksitarkistuksia "
                          :bonukset_laskutettu_ind_korotus :bonukset_laskutetaan_ind_korotus laskutustiedot]
                         [" Erilliskustannusten indeksitarkistukset (muut kuin bonukset) " " Ei indeksitarkistuksia "
                          :erilliskustannukset_laskutettu_ind_korotus :erilliskustannukset_laskutetaan_ind_korotus laskutustiedot]
                         [" Muiden kuin kok.hint. töiden indeksitarkistukset yhteensä " " Ei indeksitarkistuksia "
                          :kaikki_paitsi_kht_laskutettu_ind_korotus :kaikki_paitsi_kht_laskutetaan_ind_korotus laskutustiedot]
                         [" Kaikki indeksitarkistukset yhteensä " " Ei indeksitarkistuksia "
                          :kaikki_laskutettu_ind_korotus :kaikki_laskutetaan_ind_korotus laskutustiedot]
                         [" Kaikki paitsi kok.hint. työt yhteensä " " Ei kustannuksia "
                          :kaikki_paitsi_kht_laskutettu :kaikki_paitsi_kht_laskutetaan laskutustiedot]
                         [" Kaikki yhteensä " " Ei kustannuksia "
                          :kaikki_laskutettu :kaikki_laskutetaan laskutustiedot]]))]

    (vec (keep identity
               [:raportti {:nimi "Laskutusyhteenveto"}
                [:otsikko (str (or (str urakan-nimi ", ") "") (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))]
                varoitus-tietojen-puuttumisesta
                (if (empty? taulukot)
                  [:teksti " Ei laskutettavaa"]
                  taulukot)
                (when-not (empty? taulukot)
                  [:yhteenveto
                  [[(str "Suunnitellut yksikköhintaiset työt " (fmt/pvm loppupvm) " asti")
                    (fmt/euro suunnitellut-kustannukset-yhteensa)]
                   [(str "Toteutuneet yksikköhintaiset työt " (fmt/pvm loppupvm) " asti")
                    (str (fmt/euro toteutuneet-kustannukset-yhteensa)
                         (when-not (empty? toteumat-ilman-suunnitelmaa)
                           (str " (ei sisällä toteumia, joille ei löytynyt suunnittelutietoja: "
                           (count toteumat-ilman-suunnitelmaa)
                           "kpl)")))]]])]))))
