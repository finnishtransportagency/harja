(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto
  "Laskutusyhteenveto"
  (:require [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
            [harja.palvelin.palvelut.indeksit :as indeksipalvelu]
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

    (let [tulos (vec
            (sort-by (juxt (comp toimenpidekoodit/tuotteen-jarjestys :tuotekoodi) :nimi)
                     (into []
                           (laskutus-q/hae-laskutusyhteenvedon-tiedot db
                                                                      (konv/sql-date hk-alkupvm)
                                                                      (konv/sql-date hk-loppupvm)
                                                                      (konv/sql-date alkupvm)
                                                                      (konv/sql-date loppupvm)
                                                                      urakka-id))))]
      #_(log/debug (pr-str tulos))
      tulos)))

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


(defn- korotettuna-jos-indeksi-saatavilla
  [rivi avain]

  (let [avain-ind-korototettuna (keyword (str avain "_ind_korotettuna"))]
    (if-let [ind-korotettuna (avain-ind-korototettuna rivi)]
      {:tulos            ind-korotettuna
       :indeksi-puuttui? false}
      {:tulos            ((keyword avain) rivi)
       :indeksi-puuttui? true})))

(defn taulukko-elementti
  [otsikko taulukon-tiedot kyseessa-kk-vali?
   laskutettu-teksti laskutetaan-teksti
   yhteenveto yhteenveto-teksti summa-fmt]
  [:taulukko {:oikealle-tasattavat-kentat #{1 2 3}
             :viimeinen-rivi-yhteenveto? true}
  (rivi
    {:otsikko otsikko :leveys 36}
    (when kyseessa-kk-vali? {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti})
    (when kyseessa-kk-vali? {:otsikko laskutetaan-teksti :leveys 24 :tyyppi :varillinen-teksti})
    {:otsikko yhteenveto-teksti :leveys 29 :tyyppi :varillinen-teksti})

  (into []
        (concat
          (map (fn [[nimi {laskutettu :tulos laskutettu-ind-puuttui? :indeksi-puuttui?}
                     {laskutetaan :tulos laskutetaan-ind-puuttui? :indeksi-puuttui?}]]
                 (rivi
                   nimi
                   (when kyseessa-kk-vali? [:varillinen-teksti {:arvo (summa-fmt laskutettu)
                                                                :tyyli (when laskutettu-ind-puuttui? :virhe)}])
                   (when kyseessa-kk-vali? [:varillinen-teksti {:arvo (summa-fmt laskutetaan)
                                                                :tyyli (when laskutetaan-ind-puuttui? :virhe)}])
                   (if (and laskutettu laskutetaan)
                     [:varillinen-teksti {:arvo (summa-fmt (+ laskutettu laskutetaan))
                                          :tyyli (when (or laskutettu-ind-puuttui? laskutetaan-ind-puuttui?)
                                                  :virhe)}]
                     [:varillinen-teksti {:arvo (summa-fmt nil)
                                          :tyyli :virhe}]))) taulukon-tiedot)
          [yhteenveto]))])

(defn- summaa-korotetut
  [rivit]
  (reduce
    (fn [{summa :tulos
          ind-puuttui? :indeksi-puuttui?}
         {tulos :tulos indeksi-puuttui? :indeksi-puuttui?}]
      {:tulos (+ summa (or tulos 0.0)) :indeksi-puuttui? (or ind-puuttui? indeksi-puuttui?)})
    {:tulos 0 :indeksi-puuttui? false}
    rivit))

(defn- indeksi-puuttuu-jos-nil
  [tulos]
  {:tulos tulos
   :indeksi-puuttui? (nil? tulos)})

(defn- summataulukko
  [otsikko avaimet
   laskutettu-teksti laskutetaan-teksti
   yhteenveto-teksti kyseessa-kk-vali?
   tiedot summa-fmt lisaa-kht-ind-korotus?]
  (let [taulukon-rivit (for [rivi tiedot
                             :let [nimi (:nimi rivi)
                                   laskutetut-arvot (map #(korotettuna-jos-indeksi-saatavilla rivi (str % "_laskutettu"))
                                                         avaimet)

                                   laskutettu (summaa-korotetut (if lisaa-kht-ind-korotus?
                                                                  (conj laskutetut-arvot (indeksi-puuttuu-jos-nil (:kht_laskutettu_ind_korotus rivi)))
                                                                  laskutetut-arvot))
                                   laskutetaan-arvot (map #(korotettuna-jos-indeksi-saatavilla rivi (str % "_laskutetaan"))
                                                          avaimet)
                                   laskutetaan (summaa-korotetut (if lisaa-kht-ind-korotus?
                                                                   (conj laskutetaan-arvot (indeksi-puuttuu-jos-nil (:kht_laskutetaan_ind_korotus rivi)))
                                                                   laskutetaan-arvot))
                                   summa (summaa-korotetut [laskutettu laskutetaan])]]
                         [nimi laskutettu laskutetaan summa])
        laskutettu-yht (summaa-korotetut (map second taulukon-rivit))
        laskutetaan-yht (summaa-korotetut (map #(nth % 2) taulukon-rivit))
        yhteensa (summaa-korotetut [laskutettu-yht laskutetaan-yht])
        yhteenveto (rivi "Toimenpiteet yhteensä"
                        (when kyseessa-kk-vali? [:varillinen-teksti {:arvo (summa-fmt (:tulos laskutettu-yht))
                                                                     :tyyli (when (:indeksi-puuttui? laskutettu-yht) :virhe)}])
                        (when kyseessa-kk-vali? [:varillinen-teksti {:arvo (summa-fmt (:tulos laskutetaan-yht))
                                                                     :tyyli (when (:indeksi-puuttui? laskutetaan-yht) :virhe)}])

                         [:varillinen-teksti {:arvo (summa-fmt (:tulos yhteensa))
                                              :tyyli (when (:indeksi-puuttui? yhteensa) :virhe)}])]
    (when-not (empty? taulukon-rivit)
      (taulukko-elementti otsikko taulukon-rivit kyseessa-kk-vali?
                          laskutettu-teksti laskutetaan-teksti
                          yhteenveto yhteenveto-teksti summa-fmt))))

(defn- taulukko
  ([otsikko otsikko-jos-tyhja
    laskutettu-teksti laskutettu-kentta
    laskutetaan-teksti laskutetaan-kentta
    yhteenveto-teksti kyseessa-kk-vali?
    tiedot summa-fmt]
  (let [laskutettu-kentat (map laskutettu-kentta tiedot)
        laskutetaan-kentat (map laskutetaan-kentta tiedot)
        kaikkien-toimenpiteiden-summa (fn [kentat]
                                        (reduce + (keep identity kentat)))
        laskutettu-yht (kaikkien-toimenpiteiden-summa laskutettu-kentat)
        laskutetaan-yht (kaikkien-toimenpiteiden-summa laskutetaan-kentat)
        laskutettu-summasta-puuttuu-indeksi? (some nil? laskutettu-kentat)
        laskutetaan-summasta-puuttuu-indeksi? (some nil? laskutetaan-kentat)
        yhteenveto (rivi "Toimenpiteet yhteensä"
                         (when kyseessa-kk-vali? [:varillinen-teksti {:arvo (summa-fmt laskutettu-yht)
                                                                      :tyyli (when laskutettu-summasta-puuttuu-indeksi? :virhe)}])
                         (when kyseessa-kk-vali? [:varillinen-teksti {:arvo (summa-fmt laskutetaan-yht)
                                                                      :tyyli (when laskutetaan-summasta-puuttuu-indeksi? :virhe)}])
                         (if (and laskutettu-yht laskutetaan-yht)
                           [:varillinen-teksti {:arvo (summa-fmt (+ laskutettu-yht laskutetaan-yht))
                                                :tyyli (when (or laskutettu-summasta-puuttuu-indeksi?
                                                                laskutetaan-summasta-puuttuu-indeksi?) :virhe)}]
                           nil))
        taulukon-tiedot (filter (fn [[_ laskutettu laskutetaan]]
                                  (not (and (= 0.0M (:tulos laskutettu))
                                            (= 0.0M (:tulos laskutetaan)))))
                                (map (juxt :nimi
                                           (fn [rivi]
                                             {:tulos (laskutettu-kentta rivi)
                                              :indeksi-puuttui? laskutettu-summasta-puuttuu-indeksi?})
                                           (fn [rivi]
                                             {:tulos (laskutetaan-kentta rivi)
                                              :indeksi-puuttui? laskutetaan-summasta-puuttuu-indeksi?}))
                                     tiedot))]
    (when-not (empty? taulukon-tiedot)
      (taulukko-elementti otsikko taulukon-tiedot kyseessa-kk-vali?
                          laskutettu-teksti laskutetaan-teksti
                          yhteenveto yhteenveto-teksti summa-fmt)))))

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
        perusluku (:perusluku (first tiedot))
        kkn-indeksiarvo (when kyseessa-kk-vali?
                          (indeksipalvelu/hae-urakan-kuukauden-indeksiarvo db urakka-id (pvm/vuosi alkupvm) (pvm/kuukausi alkupvm)))
        talvisuolasakko-kaytossa? (some :suolasakko_kaytossa tiedot)
        _ (log/debug "talvisuolasakko käytössä?" talvisuolasakko-kaytossa?)
        suolasakkojen-laskenta-epaonnistui? (some
                                              #(nil? (val %))
                                              (select-keys (first (filter #(= "Talvihoito" (:nimi %)) tiedot))
                                                           [:suolasakot_laskutetaan :suolasakot_laskutettu]))
        _ (log/debug "suolasakkojen-laskenta-epaonnistui?" suolasakkojen-laskenta-epaonnistui?)
        nayta-etta-lampotila-puuttuu? (when (and talvisuolasakko-kaytossa? suolasakkojen-laskenta-epaonnistui?)
                                        (first (keep #(true? (:lampotila_puuttuu %))
                                                     tiedot)))
        varoitus-lampotilojen-puuttumisesta (if nayta-etta-lampotila-puuttuu?
                                              " Lämpötilatietoja puuttuu. "
                                              " ")
        varoitus-indeksitietojen-puuttumisesta
        (when indeksi-kaytossa?
          (if (not perusluku)
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

        kentat-kaikki-paitsi-kht #{"yht" "sakot" "suolasakot" "muutostyot" "akilliset_hoitotyot"
                                   "vahinkojen_korjaukset" "bonukset" "erilliskustannukset"}
        kentat-kaikki (conj kentat-kaikki-paitsi-kht "kht")
        taulukot
        (aseta-sheet-nimi
          (concat
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
                     [" Talvisuolasakko/\u00ADbonus (autom. laskettu) " " Ei talvisuolasakkoa "
                      :suolasakot_laskutettu :suolasakot_laskutetaan tiedot fmt/euro-ei-voitu-laskea])
                   [" Muutos- ja lisätyöt " " Ei muutos- ja lisätöitä "
                    :muutostyot_laskutettu :muutostyot_laskutetaan tiedot]
                   [" Äkilliset hoitotyöt " " Ei äkillisiä hoitotöitä "
                    :akilliset_hoitotyot_laskutettu :akilliset_hoitotyot_laskutetaan tiedot]
                   [" Vahinkojen korjaukset " " Ei vahinkojen korjauksia "
                    :vahinkojen_korjaukset_laskutettu :vahinkojen_korjaukset_laskutetaan tiedot]
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
                     [" Talvisuolasakon/\u00ADbonuksen indeksitarkistus (autom. laskettu) " " Ei indeksitarkistuksia "
                      :suolasakot_laskutettu_ind_korotus :suolasakot_laskutetaan_ind_korotus tiedot fmt/euro-ei-voitu-laskea])
                   (when indeksi-kaytossa?
                     [" Muutos- ja lisätöiden indeksitarkistukset " " Ei indeksitarkistuksia "
                      :muutostyot_laskutettu_ind_korotus :muutostyot_laskutetaan_ind_korotus tiedot])
                   (when indeksi-kaytossa?
                     [" Äkillisten hoitotöiden indeksitarkistukset " " Ei indeksitarkistuksia "
                      :akilliset_hoitotyot_laskutettu_ind_korotus :akilliset_hoitotyot_laskutetaan_ind_korotus tiedot])
                   (when indeksi-kaytossa?
                     [" Vahinkojen korjausten indeksitarkistukset " " Ei indeksitarkistuksia "
                      :vahinkojen_korjaukset_laskutettu_ind_korotus :vahinkojen_korjaukset_laskutetaan_ind_korotus tiedot])
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
                      :kaikki_laskutettu_ind_korotus :kaikki_laskutetaan_ind_korotus tiedot])])

            [(summataulukko " Kaikki paitsi kok.hint. työt yhteensä " kentat-kaikki-paitsi-kht
                            laskutettu-teksti laskutetaan-teksti
                            yhteenveto-teksti kyseessa-kk-vali?
                            tiedot
                            (if indeksi-kaytossa?
                              fmt/luku-indeksikorotus
                              fmt/euro-opt) true)

             (summataulukko " Kaikki yhteensä " kentat-kaikki
                            laskutettu-teksti laskutetaan-teksti
                            yhteenveto-teksti kyseessa-kk-vali?
                            tiedot
                            (if indeksi-kaytossa?
                              fmt/luku-indeksikorotus
                              fmt/euro-opt) false)]))]

    (vec (keep identity
               [:raportti {:nimi "Laskutusyhteenveto"}
                [:otsikko (str (or (str urakan-nimi ", ") "") (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))]
                (when (and indeksi-kaytossa? perusluku)
                  (yleinen/indeksitiedot {:perusluku perusluku :kyseessa-kk-vali? kyseessa-kk-vali?
                                          :alkupvm alkupvm :kkn-indeksiarvo kkn-indeksiarvo}))
                varoitus-tietojen-puuttumisesta
                (if (empty? taulukot)
                  [:teksti " Ei laskutettavaa"]
                  taulukot)]))))
