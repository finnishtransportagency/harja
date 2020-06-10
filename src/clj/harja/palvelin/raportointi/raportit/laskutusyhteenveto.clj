(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto
  "Laskutusyhteenveto"
  (:require [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikko-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.maksuerat :as maksuerat-q]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as lyv-yhteiset]

            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
            [harja.palvelin.palvelut.indeksit :as indeksipalvelu]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [clj-time.local :as l]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.domain.toimenpidekoodi :as toimenpidekoodit]))


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

(defn- korotettuna-jos-indeksi-saatavilla
  [rivi avain]
  (let [avain-ind-korotus (keyword (str avain "_ind_korotus"))
        ind-korotus (avain-ind-korotus rivi)
        ilman-korotusta  ((keyword avain) rivi)]
    (if-not ind-korotus  ;indeksipuutteen aiheuttama nil
      {:tulos            ilman-korotusta
       :indeksi-puuttui? true}
      {:tulos (+ ilman-korotusta ind-korotus)
       :indeksi-puuttui? false})))


(defn taulukko-elementti
  [otsikko taulukon-tiedot kyseessa-kk-vali?
   laskutettu-teksti laskutetaan-teksti
   yhteenveto yhteenveto-teksti summa-fmt
   tyypin-maksuerat]
  [:taulukko {:oikealle-tasattavat-kentat #{1 2 3}
              :viimeinen-rivi-yhteenveto? true}
   (rivi
     {:otsikko otsikko :leveys 36}
     (when kyseessa-kk-vali?
       {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti})
     (when kyseessa-kk-vali?
       {:otsikko laskutetaan-teksti :leveys 24 :tyyppi :varillinen-teksti})
     {:otsikko yhteenveto-teksti :leveys 29 :tyyppi :varillinen-teksti})

   (into []
         (concat
           (map (fn [[nimi
                      {laskutettu :tulos laskutettu-ind-puuttui? :indeksi-puuttui?}
                      {laskutetaan :tulos laskutetaan-ind-puuttui? :indeksi-puuttui?}
                      tpi]]
                  (rivi
                    (str nimi (when (= 1 (count (filter #(= (:toimenpideinstanssi %) tpi) tyypin-maksuerat)))
                                (str " (#" (:numero (first (filter #(= (:toimenpideinstanssi %) tpi) tyypin-maksuerat))) ")")))
                    (when kyseessa-kk-vali?
                      [:varillinen-teksti {:arvo (or laskutettu (summa-fmt nil))
                                           :fmt (when laskutettu :raha)
                                           :tyyli (when laskutettu-ind-puuttui? :virhe)}])
                    (when kyseessa-kk-vali?
                      [:varillinen-teksti {:arvo (or laskutetaan (summa-fmt nil))
                                           :fmt (when laskutetaan :raha)
                                           :tyyli (when laskutetaan-ind-puuttui? :virhe)}])
                    (if (and laskutettu laskutetaan)
                      [:varillinen-teksti {:arvo (+ laskutettu laskutetaan)
                                           :fmt :raha
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
  (let [taulukon-rivit
        (for [rivi tiedot
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
                         (when kyseessa-kk-vali?
                           [:varillinen-teksti {:arvo (:tulos laskutettu-yht)
                                                :fmt :raha
                                                :tyyli (when (:indeksi-puuttui? laskutettu-yht) :virhe)}])
                         (when kyseessa-kk-vali?
                           [:varillinen-teksti {:arvo (:tulos laskutetaan-yht)
                                                :fmt :raha
                                                :tyyli (when (:indeksi-puuttui? laskutetaan-yht) :virhe)}])

                         [:varillinen-teksti {:arvo (:tulos yhteensa)
                                              :fmt :raha
                                              :tyyli (when (:indeksi-puuttui? yhteensa) :virhe)}])]
    (when-not (empty? taulukon-rivit)
      (taulukko-elementti otsikko taulukon-rivit kyseessa-kk-vali?
                          laskutettu-teksti laskutetaan-teksti
                          yhteenveto yhteenveto-teksti summa-fmt
                          nil))))

(defn- taulukko
  ([otsikko otsikko-jos-tyhja
    laskutettu-teksti laskutettu-kentta
    laskutetaan-teksti laskutetaan-kentta
    yhteenveto-teksti kyseessa-kk-vali?
    tiedot summa-fmt kentat-joiden-laskennan-indeksipuute-sotki
    tyypin-maksuerat]
   (let [laskutettu-kentat (map laskutettu-kentta tiedot)
         laskutetaan-kentat (map laskutetaan-kentta tiedot)
         kaikkien-toimenpiteiden-summa (fn [kentat]
                                         (reduce + (keep identity kentat)))
         laskutettu-yht (kaikkien-toimenpiteiden-summa laskutettu-kentat)
         laskutetaan-yht (kaikkien-toimenpiteiden-summa laskutetaan-kentat)
         yhteenveto (rivi "Toimenpiteet yhteensä"
                          (when kyseessa-kk-vali?
                           [:varillinen-teksti {:arvo laskutettu-yht
                                                :fmt :raha
                                                 :tyyli (when (kentat-joiden-laskennan-indeksipuute-sotki laskutettu-kentta) :virhe)}])
                          (when kyseessa-kk-vali?
                           [:varillinen-teksti {:arvo laskutetaan-yht
                                                :fmt :raha
                                                 :tyyli (when (kentat-joiden-laskennan-indeksipuute-sotki laskutetaan-kentta) :virhe)}])
                          (if (and laskutettu-yht laskutetaan-yht)
                           [:varillinen-teksti {:arvo (+ laskutettu-yht laskutetaan-yht)
                                                :fmt :raha
                                                 :tyyli (when (or (kentat-joiden-laskennan-indeksipuute-sotki laskutettu-kentta)
                                                                  (kentat-joiden-laskennan-indeksipuute-sotki laskutetaan-kentta)) :virhe)}]
                            nil))
         taulukon-tiedot (filter (fn [[_ laskutettu laskutetaan]]
                                   (not (and (= 0.0M (:tulos laskutettu))
                                             (= 0.0M (:tulos laskutetaan)))))
                                 (map (juxt :nimi
                                            (fn [rivi]
                                             {:tulos (laskutettu-kentta rivi)
                                               :indeksi-puuttui? (kentat-joiden-laskennan-indeksipuute-sotki laskutettu-kentta)})
                                            (fn [rivi]
                                             {:tulos (laskutetaan-kentta rivi)
                                               :indeksi-puuttui? (kentat-joiden-laskennan-indeksipuute-sotki laskutetaan-kentta)})
                                            :tpi)
                                      tiedot))]
     (when-not (empty? taulukon-tiedot)
       (taulukko-elementti otsikko taulukon-tiedot kyseessa-kk-vali?
                           laskutettu-teksti laskutetaan-teksti
                           yhteenveto yhteenveto-teksti summa-fmt
                           tyypin-maksuerat)))))



(defn- laskettavat-kentat [rivi konteksti]
  (let [kustannusten-kentat (into []
                                  (apply concat [(lyv-yhteiset/kustannuslajin-kaikki-kentat "kht")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "yht")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "sakot")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "muutostyot")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "akilliset_hoitotyot")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "vahinkojen_korjaukset")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "bonukset")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "erilliskustannukset")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "kaikki_paitsi_kht")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "kaikki")
                                                 (when (= :urakka konteksti) [:tpi])]))]
    (if (and (some? (:suolasakot_laskutettu rivi))
             (some?(:suolasakot_laskutetaan rivi)))
      (into []
            (concat kustannusten-kentat
                    (lyv-yhteiset/kustannuslajin-kaikki-kentat "suolasakot")))
      kustannusten-kentat)))

(def kentat-kaikki-paitsi-kht
  #{"yht" "sakot" "suolasakot" "muutostyot" "akilliset_hoitotyot"
    "vahinkojen_korjaukset" "bonukset" "erilliskustannukset"})
(def kentat-kaikki (conj kentat-kaikki-paitsi-kht "kht"))


(defn suorita [db user {:keys [alkupvm loppupvm urakka-id hallintayksikko-id] :as parametrit}]
  (log/debug "LASKUTUSYHTEENVETO PARAMETRIT: " (pr-str parametrit))
  (let [;; Aikavälit ja otsikkotekstit
        kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        kyseessa-hoitokausi-vali? (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
        kyseessa-vuosi-vali? (pvm/kyseessa-vuosi-vali? alkupvm loppupvm)
        laskutettu-teksti (str "Laskutettu hoito\u00ADkaudella ennen " (pvm/kuukausi-ja-vuosi alkupvm))
        laskutetaan-teksti (str "Laskutetaan " (pvm/kuukausi-ja-vuosi alkupvm))
        yhteenveto-teksti (str (if (or kyseessa-kk-vali? kyseessa-hoitokausi-vali?)
                                 (str "Hoitokaudella " (pvm/vuosi (first (pvm/paivamaaran-hoitokausi alkupvm))) "-"
                                      (pvm/vuosi (second (pvm/paivamaaran-hoitokausi alkupvm))) " yhteensä")
                                 (if kyseessa-vuosi-vali?
                                   (str "Vuonna " (pvm/vuosi (l/to-local-date-time alkupvm)) " yhteensä")
                                   (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm) " yhteensä"))))

        ;; Konteksti ja urakkatiedot
        konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :urakka)
        {alueen-nimi :nimi} (first (if (= konteksti :hallintayksikko)
                                     (hallintayksikko-q/hae-organisaatio db hallintayksikko-id)
                                     (urakat-q/hae-urakka db urakka-id)))
        urakat (urakat-q/hae-urakkatiedot-laskutusyhteenvetoon
                 db {:alkupvm alkupvm :loppupvm loppupvm
                     :hallintayksikkoid hallintayksikko-id :urakkaid urakka-id
                     :urakkatyyppi (name (:urakkatyyppi parametrit))})
        urakoiden-parametrit (mapv #(assoc parametrit :urakka-id (:id %)
                                                      :urakka-nimi (:nimi %)
                                                      :indeksi (:indeksi %)
                                                      :urakkatyyppi (:tyyppi %)) urakat)
        ;; Datan nostaminen tietokannasta urakoittain, hyödyntää cachea
        laskutusyhteenvedot (mapv (fn [urakan-parametrit]
                                    (mapv #(assoc % :urakka-id (:urakka-id urakan-parametrit)
                                                    :urakka-nimi (:urakka-nimi urakan-parametrit)
                                                    :indeksi (:indeksi urakan-parametrit)
                                                    :urakkatyyppi (:urakkatyyppi urakan-parametrit))
                                          (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot db user urakan-parametrit)))
                                  urakoiden-parametrit)

        urakoiden-lahtotiedot (lyv-yhteiset/urakoiden-lahtotiedot laskutusyhteenvedot)

        ;; Indeksitiedot
        ainakin-yhdessa-urakassa-indeksit-kaytossa? (some #(:indeksi (val %)) urakoiden-lahtotiedot)
        perusluku (when (= 1 (count urakat)) (:perusluku (val (first urakoiden-lahtotiedot))))
        kkn-indeksiarvo (when kyseessa-kk-vali?
                          (indeksipalvelu/hae-urakan-kuukauden-indeksiarvo db urakka-id (pvm/vuosi alkupvm) (pvm/kuukausi alkupvm)))
        urakat-joissa-indeksilaskennan-perusluku-puuttuu (lyv-yhteiset/urakat-joissa-indeksilaskennan-perusluku-puuttuu urakoiden-lahtotiedot)
        urakoittain-kentat-joiden-laskennan-indeksipuute-sotki (lyv-yhteiset/urakoittain-kentat-joiden-laskennan-indeksipuute-sotki laskutusyhteenvedot)
        urakat-joiden-laskennan-indeksipuute-sotki (into #{} (keep #(when (not-empty (val %))
                                                                      (key %)) urakoittain-kentat-joiden-laskennan-indeksipuute-sotki))
        kentat-joiden-laskennan-indeksipuute-sotki (apply clojure.set/union (map #(val %) urakoittain-kentat-joiden-laskennan-indeksipuute-sotki))

        ;; Suolasakko- ja lämpötilatiedot
        ainakin-yhdessa-urakassa-suolasakko-kaytossa? (some #(:suolasakko_kaytossa (val %)) urakoiden-lahtotiedot)
        urakat-joissa-suolasakon-laskenta-epaonnistui-ja-lampotila-puuttuu (lyv-yhteiset/urakat-joissa-suolasakon-laskenta-epaonnistui-ja-lampotila-puuttuu urakoiden-lahtotiedot)

        ;; Datan tuotteittain ryhmittely
        tiedot-tuotteittain (fmap #(group-by :nimi %) laskutusyhteenvedot)
        kaikki-tuotteittain (apply merge-with concat tiedot-tuotteittain)
        ;; Urakoiden datan yhdistäminen yhteenlaskulla. Datapuutteet (indeksi, lämpötila, suolasakko, jne) voivat aiheuttaa nillejä,
        ;; joiden yli ratsastetaan (fnil + 0 0):lla. Tämä vuoksi pidetään käsin kirjaa mm. indeksipuuteiden sotkemista kentistä
        kaikki-tuotteittain-summattuna (when kaikki-tuotteittain
                                         (fmap #(apply merge-with (fnil + 0 0)
                                                       (map (fn [rivi]
                                                              (select-keys rivi (laskettavat-kentat rivi konteksti)))
                                                            %))
                                               kaikki-tuotteittain))
        tiedot (into []
                     (map #(merge {:nimi (key %)} (val %)) kaikki-tuotteittain-summattuna))

        maksueratiedot (when (= :urakka konteksti)
                         (group-by :tyyppi (maksuerat-q/hae-urakan-maksueratiedot db {:urakka_id urakka-id})))

        ;; Varoitustekstit raportille
        varoitus-indeksilaskennan-perusluku-puuttuu (lyv-yhteiset/varoitus-indeksilaskennan-perusluku-puuttuu urakat-joissa-indeksilaskennan-perusluku-puuttuu)
        varoitus-lampotila-puuttuu (lyv-yhteiset/varoitus-lampotila-puuttuu urakat-joissa-suolasakon-laskenta-epaonnistui-ja-lampotila-puuttuu)
        varoitus-indeksitietojen-puuttumisesta
        (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
          (lyv-yhteiset/varoitus-indeksitietojen-puuttumisesta varoitus-indeksilaskennan-perusluku-puuttuu
                                                               urakat-joiden-laskennan-indeksipuute-sotki))

        taulukot
        (lyv-yhteiset/aseta-sheet-nimi
          (concat
            (keep (fn [[otsikko tyhja laskutettu laskutetaan tiedot summa-fmt tyypin-maksuerat :as taulukko-rivi]]
                    (when taulukko-rivi
                      (taulukko otsikko tyhja
                                laskutettu-teksti laskutettu
                                laskutetaan-teksti laskutetaan
                                yhteenveto-teksti kyseessa-kk-vali?
                                tiedot (or summa-fmt
                                           (if ainakin-yhdessa-urakassa-indeksit-kaytossa?
                                             fmt/luku-indeksikorotus
                                             fmt/euro-opt))
                                kentat-joiden-laskennan-indeksipuute-sotki
                                tyypin-maksuerat)))
                  [[" Kokonaishintaiset työt " " Ei kokonaishintaisia töitä "
                    :kht_laskutettu :kht_laskutetaan tiedot nil
                    (lyv-yhteiset/tyypin-maksuerat "kokonaishintainen" maksueratiedot)]
                   [" Yksikköhintaiset työt " " Ei yksikköhintaisia töitä "
                    :yht_laskutettu :yht_laskutetaan tiedot nil
                    (lyv-yhteiset/tyypin-maksuerat "yksikkohintainen" maksueratiedot)]
                   [" Sanktiot " " Ei sanktioita "
                    :sakot_laskutettu :sakot_laskutetaan tiedot nil
                    (lyv-yhteiset/tyypin-maksuerat "sakko" maksueratiedot)]
                   (when ainakin-yhdessa-urakassa-suolasakko-kaytossa?
                     [" Talvisuolasakko/\u00ADbonus (autom. laskettu) " " Ei talvisuolasakkoa "
                      :suolasakot_laskutettu :suolasakot_laskutetaan tiedot fmt/euro-ei-voitu-laskea])
                   [" Muutos- ja lisätyöt " " Ei muutos- ja lisätöitä "
                    :muutostyot_laskutettu :muutostyot_laskutetaan tiedot nil
                    (lyv-yhteiset/tyypin-maksuerat "lisatyo" maksueratiedot)]
                   [" Äkilliset hoitotyöt " " Ei äkillisiä hoitotöitä "
                    :akilliset_hoitotyot_laskutettu :akilliset_hoitotyot_laskutetaan tiedot nil
                    (lyv-yhteiset/tyypin-maksuerat "akillinen-hoitotyo" maksueratiedot)]
                   [" Vahinkojen korjaukset " " Ei vahinkojen korjauksia "
                    :vahinkojen_korjaukset_laskutettu :vahinkojen_korjaukset_laskutetaan tiedot]
                   [" Bonukset " " Ei bonuksia "
                    :bonukset_laskutettu :bonukset_laskutetaan tiedot nil
                    (lyv-yhteiset/tyypin-maksuerat "bonus" maksueratiedot)]
                   [" Erilliskustannukset (muut kuin bonukset) " " Ei erilliskustannuksia "
                    :erilliskustannukset_laskutettu :erilliskustannukset_laskutetaan tiedot]
                   (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
                     [" Kokonaishintaisten töiden indeksitarkistukset " " Ei indeksitarkistuksia "
                      :kht_laskutettu_ind_korotus :kht_laskutetaan_ind_korotus tiedot])
                   (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
                     [" Yksikköhintaisten töiden indeksitarkistukset " " Ei indeksitarkistuksia "
                      :yht_laskutettu_ind_korotus :yht_laskutetaan_ind_korotus tiedot])
                   (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
                     [" Sanktioiden indeksitarkistukset " " Ei indeksitarkistuksia "
                      :sakot_laskutettu_ind_korotus :sakot_laskutetaan_ind_korotus tiedot])
                   (when (and ainakin-yhdessa-urakassa-indeksit-kaytossa? ainakin-yhdessa-urakassa-suolasakko-kaytossa?)
                     [" Talvisuolasakon/\u00ADbonuksen indeksitarkistus (autom. laskettu) " " Ei indeksitarkistuksia "
                      :suolasakot_laskutettu_ind_korotus :suolasakot_laskutetaan_ind_korotus tiedot fmt/euro-ei-voitu-laskea])
                   (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
                     [" Muutos- ja lisätöiden indeksitarkistukset " " Ei indeksitarkistuksia "
                      :muutostyot_laskutettu_ind_korotus :muutostyot_laskutetaan_ind_korotus tiedot])
                   (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
                     [" Äkillisten hoitotöiden indeksitarkistukset " " Ei indeksitarkistuksia "
                      :akilliset_hoitotyot_laskutettu_ind_korotus :akilliset_hoitotyot_laskutetaan_ind_korotus tiedot])
                   (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
                     [" Vahinkojen korjausten indeksitarkistukset " " Ei indeksitarkistuksia "
                      :vahinkojen_korjaukset_laskutettu_ind_korotus :vahinkojen_korjaukset_laskutetaan_ind_korotus tiedot])
                   (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
                     [" Bonusten indeksitarkistukset " " Ei indeksitarkistuksia "
                      :bonukset_laskutettu_ind_korotus :bonukset_laskutetaan_ind_korotus tiedot])
                   (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
                     [" Erilliskustannusten indeksitarkistukset (muut kuin bonukset) " " Ei indeksitarkistuksia "
                      :erilliskustannukset_laskutettu_ind_korotus :erilliskustannukset_laskutetaan_ind_korotus tiedot])
                   (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
                     [" Muiden kuin kok.hint. töiden indeksitarkistukset yhteensä " " Ei indeksitarkistuksia "
                      :kaikki_paitsi_kht_laskutettu_ind_korotus :kaikki_paitsi_kht_laskutetaan_ind_korotus tiedot])
                   (when ainakin-yhdessa-urakassa-indeksit-kaytossa?
                     [" Kaikki indeksitarkistukset yhteensä " " Ei indeksitarkistuksia "
                      :kaikki_laskutettu_ind_korotus :kaikki_laskutetaan_ind_korotus tiedot nil
                      (lyv-yhteiset/tyypin-maksuerat "indeksi" maksueratiedot)])])

            [(summataulukko " Kaikki paitsi kok.hint. työt yhteensä " kentat-kaikki-paitsi-kht
                            laskutettu-teksti laskutetaan-teksti
                            yhteenveto-teksti kyseessa-kk-vali?
                            tiedot
                            (if ainakin-yhdessa-urakassa-indeksit-kaytossa?
                              fmt/luku-indeksikorotus
                              fmt/euro-opt) true)

             (summataulukko " Kaikki yhteensä " kentat-kaikki
                            laskutettu-teksti laskutetaan-teksti
                            yhteenveto-teksti kyseessa-kk-vali?
                            tiedot
                            (if ainakin-yhdessa-urakassa-indeksit-kaytossa?
                              fmt/luku-indeksikorotus
                              fmt/euro-opt) false)]))]

    (vec (keep identity
               [:raportti {:nimi "Laskutusyhteenveto"}
                [:otsikko (str (or (str alueen-nimi ", ") "") (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))]
                (when perusluku
                  (yleinen/urakan-indlask-perusluku {:perusluku perusluku}))
                (when (and ainakin-yhdessa-urakassa-indeksit-kaytossa? urakka-id)
                  (yleinen/kkn-indeksiarvo {:kyseessa-kk-vali? kyseessa-kk-vali?
                                            :alkupvm alkupvm :kkn-indeksiarvo kkn-indeksiarvo}))

                [:varoitusteksti varoitus-indeksitietojen-puuttumisesta]
                [:varoitusteksti varoitus-lampotila-puuttuu]
                (when (or varoitus-indeksitietojen-puuttumisesta varoitus-lampotila-puuttuu)
                  [:varoitusteksti lyv-yhteiset/varoitus-vain-jvh-voi-muokata-tietoja])

                (if (empty? taulukot)
                  [:teksti " Ei laskutettavaa"]
                  taulukot)

                (when (and hallintayksikko-id (= 0 (count urakat)))
                  [:teksti " Hallintayksikössä ei aktiivisia urakoita valitulla aikavälillä"])

                (when (and hallintayksikko-id (< 0 (count urakat)))
                  [:otsikko "Raportti sisältää seuraavien urakoiden tiedot: "])
                (when (and hallintayksikko-id (< 0 (count urakat)))
                  (for [u (sort-by :nimi urakat)]
                    [:teksti (str (:nimi u))]))]))))
