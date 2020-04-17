(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-mhu
  "Laskutusyhteenveto MHU-urakoissa"
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



;; kannassa testailua varten:
;; select * from laskutusyhteenveto_teiden_hoito('2020-10-01', '2021-09-30', '2020-03-01', '2020-03-31',36);
(defn hae-laskutusyhteenvedon-tiedot
  [db user {:keys [urakka-id alkupvm loppupvm] :as tiedot}]
  (let [[hk-alkupvm hk-loppupvm] (if (or (pvm/kyseessa-kk-vali? alkupvm loppupvm)
                                         (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm))
                                   ;; jos kyseessä vapaa aikaväli, lasketaan vain yksi sarake joten
                                   ;; hk-pvm:illä ei ole merkitystä, kunhan eivät konfliktoi alkupvm ja loppupvm kanssa
                                   (pvm/paivamaaran-hoitokausi alkupvm)
                                   [alkupvm loppupvm])]
    (log/debug "hae-mhu-urakan-laskutusyhteenvedon-tiedot" tiedot)

    (let [tulos (vec
                  (sort-by (juxt (comp toimenpidekoodit/tuotteen-jarjestys :tuotekoodi) :nimi)
                           (into []
                                 (laskutus-q/hae-laskutusyhteenvedon-tiedot-teiden-hoito db
                                                                            (konv/sql-date hk-alkupvm)
                                                                            (konv/sql-date hk-loppupvm)
                                                                            (konv/sql-date alkupvm)
                                                                            (konv/sql-date loppupvm)
                                                                            urakka-id))))]
      (log/debug (pr-str tulos))
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
     {:otsikko yhteenveto-teksti :leveys 29 :tyyppi :varillinen-teksti})])


(defn- laskettavat-kentat [rivi konteksti]
  (let [kustannusten-kentat (into []
                                  (apply concat [(lyv-yhteiset/kustannuslajin-kaikki-kentat "kit")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "kat")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "kht")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "mt")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "aht")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "sakot")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "bonukset")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "kaikki")
                                                 (when (= :urakka konteksti) [:tpi])]))]
    (if (and (some? (:suolasakot_laskutettu rivi))
             (some?(:suolasakot_laskutetaan rivi)))
      (into []
            (concat kustannusten-kentat
                    (lyv-yhteiset/kustannuslajin-kaikki-kentat "suolasakot")))
      kustannusten-kentat)))


(defn- taulukko
  [otsikko otsikko-jos-tyhja
   laskutettu-teksti laskutettu-kentta
   laskutetaan-teksti laskutetaan-kentta
   yhteenveto-teksti kyseessa-kk-vali?
   tiedot summa-fmt kentat-joiden-laskennan-indeksipuute-sotki
   tyypin-maksuerat]
  [:taulukko {:oikealle-tasattavat-kentat #{1 2 3}
              :viimeinen-rivi-yhteenveto? true}
   (rivi
     {:otsikko otsikko :leveys 36}
     (when kyseessa-kk-vali?
       {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti})
     (when kyseessa-kk-vali?
       {:otsikko laskutetaan-teksti :leveys 24 :tyyppi :varillinen-teksti}))])

(def summa-fmt fmt/euro-opt)


;; TODO: laskentalogiikat täysin tekemättä
(defn- hankinnat
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    "Hankinnat"
    [:varillinen-teksti {:arvo (or 100 (summa-fmt nil))
                         :fmt (when 100 :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or 100 (summa-fmt nil))

                           :fmt (when 100 :raha)}])))

;; TODO: laskentalogiikat täysin tekemättä
(defn- lisatyot
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    "Lisätyöt"
    [:varillinen-teksti {:arvo (or 200 (summa-fmt nil))
                         :fmt (when 200 :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or 200 (summa-fmt nil))
                           :fmt (when 200 :raha)}])))

;; TODO: laskentalogiikat täysin tekemättä
(defn- sanktiot
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    "Sanktiot"
    [:varillinen-teksti {:arvo (or 300 (summa-fmt nil))
                         :fmt (when 300 :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or 300 (summa-fmt nil))
                           :fmt (when 300 :raha)}])))
;; TODO: laskentalogiikat täysin tekemättä
(defn- suolasanktiot
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    "Suolasanktiot"
    [:varillinen-teksti {:arvo (or 400 (summa-fmt nil))
                         :fmt (when 400 :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or 400 (summa-fmt nil))
                           :fmt (when 400 :raha)}])))

;; TODO: laskentalogiikat täysin tekemättä
(defn- yhteensa
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    "Yhteensä"
    [:varillinen-teksti {:arvo (or 1000 (summa-fmt nil))
                         :fmt (when 1000 :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or 1000 (summa-fmt nil))
                           :fmt (when 1000 :raha)}])))


(defn- taulukko [{:keys [tp-rivi laskutettu-teksti laskutetaan-teksti
                         kyseessa-kk-vali?]}]
  (log/debug "jarno tprivi" tp-rivi)
  (let [rivit (into []
                    (remove nil?
                          [(hankinnat tp-rivi kyseessa-kk-vali?)
                           (lisatyot tp-rivi kyseessa-kk-vali?)
                           (sanktiot tp-rivi kyseessa-kk-vali?)
                           (when (= "Talvihoito" (:nimi tp-rivi))
                             (suolasanktiot tp-rivi kyseessa-kk-vali?))
                           (yhteensa tp-rivi kyseessa-kk-vali?)]))]
    (log/debug "jarno rivit " rivit)
    [:taulukko {:oikealle-tasattavat-kentat #{1 2}
               :viimeinen-rivi-yhteenveto? true}
    ;; otsikot
    (rivi
      {:otsikko (:nimi tp-rivi) :leveys 36}
      {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti}
      (when kyseessa-kk-vali?
        {:otsikko laskutetaan-teksti :leveys 29 :tyyppi :varillinen-teksti}))

    ;; arvot
    rivit]))

(defn suorita [db user {:keys [alkupvm loppupvm urakka-id hallintayksikko-id] :as parametrit}]
  (log/debug "LASKUTUSYHTEENVETO PARAMETRIT: " (pr-str parametrit))
  (let [;; Aikavälit ja otsikkotekstit
        kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        kyseessa-hoitokausi-vali? (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
        kyseessa-vuosi-vali? (pvm/kyseessa-vuosi-vali? alkupvm loppupvm)
        laskutettu-teksti (str "Hoito\u00ADkauden alusta")
        laskutetaan-teksti (str "Laskutetaan " (pvm/kuukausi-ja-vuosi alkupvm))

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
                     (map #(merge {:nimi (key %)} (val %)) kaikki-tuotteittain-summattuna))]

    [:raportti {:nimi "Laskutusyhteenveto MHU"}
     [:otsikko (str (or (str alueen-nimi ", ") "") (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))]
     (when perusluku
       (yleinen/urakan-indlask-perusluku {:perusluku perusluku}))
     (when (and ainakin-yhdessa-urakassa-indeksit-kaytossa? urakka-id)
       (yleinen/kkn-indeksiarvo {:kyseessa-kk-vali? kyseessa-kk-vali?
                                 :alkupvm alkupvm :kkn-indeksiarvo kkn-indeksiarvo}))


     (lyv-yhteiset/aseta-sheet-nimi
       (concat
         (for [tp-rivi tiedot]
          (do
            (taulukko {:tp-rivi tp-rivi
                       :laskutettu-teksti laskutettu-teksti
                       :laskutetaan-teksti laskutetaan-teksti
                       :kyseessa-kk-vali? kyseessa-kk-vali?})))))


     (when (and hallintayksikko-id (= 0 (count urakat)))
       [:teksti " Hallintayksikössä ei aktiivisia urakoita valitulla aikavälillä"])

     (when (and hallintayksikko-id (< 0 (count urakat)))
       [:otsikko "Raportti sisältää seuraavien urakoiden tiedot: "])
     (when (and hallintayksikko-id (< 0 (count urakat)))
       (for [u (sort-by :nimi urakat)]
         [:teksti (str (:nimi u))]))]))
