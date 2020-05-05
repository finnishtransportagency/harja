(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-mhu
  "Laskutusyhteenveto MHU-urakoissa"
  (:require [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikko-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.budjettisuunnittelu :as budjetti-q]
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
    (let [tulos (vec
                  (sort-by (juxt (comp toimenpidekoodit/tuotteen-jarjestys :tuotekoodi) :nimi)
                           (into []
                                 (laskutus-q/hae-laskutusyhteenvedon-tiedot-teiden-hoito db
                                                                                         (konv/sql-date hk-alkupvm)
                                                                                         (konv/sql-date hk-loppupvm)
                                                                                         (konv/sql-date alkupvm)
                                                                                         (konv/sql-date loppupvm)
                                                                                         urakka-id))))]
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

(defn- laskettavat-kentat [rivi konteksti]
  (let [kustannusten-kentat (into []
                                  (apply concat [(lyv-yhteiset/kustannuslajin-kaikki-kentat "lisatyot")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hankinnat")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "sakot")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hoidonjohto")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_erillishankinnat")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "bonukset")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_palkkio")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "tavoitehintaiset")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "kaikki")
                                                 (when (= :urakka konteksti) [:tpi])]))]
    (if (and (some? (:suolasakot_laskutettu rivi))
             (some? (:suolasakot_laskutetaan rivi)))
      (into []
            (concat kustannusten-kentat
                    (lyv-yhteiset/kustannuslajin-kaikki-kentat "suolasakot")))
      kustannusten-kentat)))

(def summa-fmt fmt/euro-opt)

(defn- koosta-yhteenveto [tiedot]
  (let [kaikki-yhteensa-laskutettu (apply + (map #(:kaikki_laskutettu %) tiedot))
        kaikki-yhteensa-laskutetaan (apply + (map #(:kaikki_laskutetaan %) tiedot))
        kaikki-tavoitehintaiset-laskutettu (apply + (map #(if (not (nil? (:tavoitehintaiset_laskutettu %)))
                                                            (:tavoitehintaiset_laskutettu %)
                                                            0) tiedot))
        kaikki-tavoitehintaiset-laskutetaan (apply + (map #(if (not (nil? (:tavoitehintaiset_laskutetaan %)))
                                                             (:tavoitehintaiset_laskutetaan %)
                                                             0) tiedot))]
    (conj tiedot
          {:kaikki-tavoitehintaiset-laskutettu kaikki-tavoitehintaiset-laskutettu
           :kaikki-tavoitehintaiset-laskutetaan kaikki-tavoitehintaiset-laskutetaan
           :kaikki-yhteensa-laskutettu kaikki-yhteensa-laskutettu
           :kaikki-yhteensa-laskutetaan kaikki-yhteensa-laskutetaan
           :nimi "Kaikki toteutuneet kustannukset"})))

(defn- koosta-tavoite [tiedot urakka-tavoite]
  (let [kaikki-tavoitehintaiset-laskutettu (apply + (map #(if (not (nil? (:tavoitehintaiset_laskutettu %)))
                                                            (:tavoitehintaiset_laskutettu %)
                                                            0) tiedot))]
    (conj tiedot
          {
           :tavoite-hinta (:tavoitehinta urakka-tavoite)
           :jaljella (- (:tavoitehinta urakka-tavoite) kaikki-tavoitehintaiset-laskutettu)
           :nimi "Tavoite"})))

(defn- hankinnat
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Hankinnat")
    [:varillinen-teksti {:arvo (:hankinnat_laskutettu tp-rivi) #_(or 100 (summa-fmt nil))
                         :fmt (when (:hankinnat_laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:hankinnat_laskutetaan tp-rivi) (summa-fmt nil))

                           :fmt (when (:hankinnat_laskutetaan tp-rivi) :raha)}])))

(defn- lisatyot
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Lisätyöt")
    [:varillinen-teksti {:arvo (or (:lisatyot_laskutettu tp-rivi) (summa-fmt nil))
                         :fmt (when (:lisatyot_laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:lisatyot_laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt (when (:lisatyot_laskutetaan tp-rivi) :raha)}])))

(defn- sanktiot
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Sanktiot")
    [:varillinen-teksti {:arvo (or (:sakot_laskutettu tp-rivi) (summa-fmt nil))
                         :fmt (when (:sakot_laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:sakot_laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt (when (:sakot_laskutetaan tp-rivi) :raha)}])))
;; TODO: laskentalogiikat täysin tekemättä
(defn- suolasanktiot
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Suolasanktiot")
    [:varillinen-teksti {:arvo (or (:suolasakot_laskutettu tp-rivi) (summa-fmt nil))
                         :fmt (when (:suolasakot_laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:suolasakot_laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt (when (:suolasakot_laskutetaan tp-rivi) :raha)}])))

(defn- johto-hallintakorvaukset
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Johto- ja hallintakorvaukset")
    [:varillinen-teksti {:arvo (:hoidonjohto_laskutettu tp-rivi) #_(or 100 (summa-fmt nil))
                         :fmt (when (:hoidonjohto_laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:hoidonjohto_laskutetaan tp-rivi) (summa-fmt nil))

                           :fmt (when (:hoidonjohto_laskutetaan tp-rivi) :raha)}])))

(defn- erillishankinnat
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Erillishankinnat")
    [:varillinen-teksti {:arvo (:hj_erillishankinnat_laskutettu tp-rivi) #_(or 100 (summa-fmt nil))
                         :fmt (when (:hj_erillishankinnat_laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (summa-fmt (:hj_erillishankinnat_laskutetaan tp-rivi)) (summa-fmt nil))

                           :fmt (when (summa-fmt (:hj_erillishankinnat_laskutetaan tp-rivi)) :raha)}])))

(defn- hj-palkkio
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "HJ-palkkio")
    [:varillinen-teksti {:arvo (:hj_palkkio_laskutettu tp-rivi) #_(or 100 (summa-fmt nil))
                         :fmt (when (:hj_palkkio_laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (summa-fmt (:hj_palkkio_laskutetaan tp-rivi)) (summa-fmt nil))

                           :fmt (when (summa-fmt (:hj_palkkio_laskutetaan tp-rivi)) :raha)}])))

(defn- bonukset
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Bonukset")
    [:varillinen-teksti {:arvo (:bonukset_laskutettu tp-rivi) #_(or 100 (summa-fmt nil))
                         :fmt (when (:bonukset_laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:bonukset_laskutetaan tp-rivi) (summa-fmt nil))

                           :fmt (when (:bonukset_laskutetaan tp-rivi) :raha)}])))

(defn- yhteensa
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Yhteensä")
    [:varillinen-teksti {:arvo (or (:kaikki_laskutettu tp-rivi) (summa-fmt nil))
                         :fmt (when (:kaikki_laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:kaikki_laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt (when (:kaikki_laskutetaan tp-rivi) :raha)}])))

(defn- toteutuneet-yhteensa [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Toteutuneet kustannukset yhteensä")
    [:varillinen-teksti {:arvo (or (:kaikki-yhteensa-laskutettu tp-rivi) (summa-fmt nil))
                         :fmt (when (:kaikki-yhteensa-laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:kaikki-yhteensa-laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt (when (:kaikki-yhteensa-laskutetaan tp-rivi) :raha)}])))

(defn- tavoitteeseen-kuuluvat-toteutuneet-yhteensa [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Toteutuneet kustannukset, jotka kuuluvat tavoitehintaan")
    [:varillinen-teksti {:arvo (or (:kaikki-tavoitehintaiset-laskutettu tp-rivi) (summa-fmt nil))
                         :fmt (when (:kaikki-tavoitehintaiset-laskutettu tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:kaikki-tavoitehintaiset-laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt (when (:kaikki-tavoitehintaiset-laskutetaan tp-rivi) :raha)}])))

(defn- tavoitehinta [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Tavoite / Jäljellä")
    [:varillinen-teksti {:arvo (or (:tavoite-hinta tp-rivi) (summa-fmt nil))
                         :fmt (when (:tavoite-hinta tp-rivi) :raha)}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:jaljella tp-rivi) (summa-fmt nil))
                           :fmt (when (:jaljella tp-rivi) :raha)}])))

(defn- taulukko [{:keys [tp-rivi laskutettu-teksti laskutetaan-teksti
                         kyseessa-kk-vali?]}]
  (let [rivit (into []
                    (remove nil?
                            (cond
                              (= "MHU ja HJU hoidon johto" (:nimi tp-rivi))
                              [(johto-hallintakorvaukset tp-rivi kyseessa-kk-vali?)
                               (erillishankinnat tp-rivi kyseessa-kk-vali?)
                               (hj-palkkio tp-rivi kyseessa-kk-vali?)
                               (bonukset tp-rivi kyseessa-kk-vali?)
                               (sanktiot tp-rivi kyseessa-kk-vali?)
                               (yhteensa tp-rivi kyseessa-kk-vali?)]
                              (= "Kaikki toteutuneet kustannukset" (:nimi tp-rivi))
                              [(toteutuneet-yhteensa tp-rivi kyseessa-kk-vali?)
                               (tavoitteeseen-kuuluvat-toteutuneet-yhteensa tp-rivi kyseessa-kk-vali?)]
                              (= "Tavoite" (:nimi tp-rivi))
                              [(tavoitehinta tp-rivi kyseessa-kk-vali?)]
                              :default
                              [(hankinnat tp-rivi kyseessa-kk-vali?)
                               (lisatyot tp-rivi kyseessa-kk-vali?)
                               (sanktiot tp-rivi kyseessa-kk-vali?)
                               (when (= "Talvihoito" (:nimi tp-rivi))
                                 (suolasanktiot tp-rivi kyseessa-kk-vali?))
                               (yhteensa tp-rivi kyseessa-kk-vali?)])))]

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
        laskutettu-teksti (str "Hoitokauden alusta")
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
        urakka-tavoite (first (budjetti-q/hae-budjettitavoite db {:urakka urakka-id}))
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

        ;; Indeksitiedot - mhu raporteissa vain sanktiot ja bonukset perustuvat indeksiin
        ;; Perusluku tulee urakkaa edeltävän vuoden syys,loka,marraskuun keskiarvosta
        perusluku (when (= 1 (count urakat)) (:perusluku (val (first urakoiden-lahtotiedot))))
        ;; Indeksiä käytetään vain sanktioissa ja bonuksissa
        ;; Indeksinä käytetään hoitokautta edeltävän syyskuun arvoa, mikäli se on olemassa.
        raportin-indeksiarvo (indeksipalvelu/hae-urakan-kuukauden-indeksiarvo db urakka-id (dec (pvm/vuosi alkupvm)) 9)
        urakat-joissa-indeksilaskennan-perusluku-puuttuu (lyv-yhteiset/urakat-joissa-indeksilaskennan-perusluku-puuttuu urakoiden-lahtotiedot)

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
        tiedot (-> tiedot
                   (koosta-yhteenveto)
                   (koosta-tavoite urakka-tavoite))
        _ (log/debug "tiedot :: " (pr-str tiedot))]

    [:raportti {:nimi "Laskutusyhteenveto MHU"}
     [:otsikko (str (or (str alueen-nimi ", ") "") (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))]
     (when perusluku
       (yleinen/urakan-indlask-perusluku {:perusluku perusluku}))
     [:teksti (str "Käytetään edellisen vuoden syyskuun indeksiarvoa: " (fmt/desimaaliluku (:arvo raportin-indeksiarvo) 1))]

     (lyv-yhteiset/aseta-sheet-nimi
       (concat
         (for [tp-rivi tiedot
               :let [yhteensa-otsikko (if (not (= (:nimi tp-rivi) "Tavoite")) laskutettu-teksti "Tavoitehinta")
                     kuukausi-otsikko (if (not (= (:nimi tp-rivi) "Tavoite")) laskutetaan-teksti "Jäljellä")]]
           (do
             (taulukko {:tp-rivi tp-rivi
                        :laskutettu-teksti yhteensa-otsikko
                        :laskutetaan-teksti kuukausi-otsikko
                        :kyseessa-kk-vali? kyseessa-kk-vali?})))))


     (when (and hallintayksikko-id (= 0 (count urakat)))
       [:teksti " Hallintayksikössä ei aktiivisia urakoita valitulla aikavälillä"])

     (when (and hallintayksikko-id (< 0 (count urakat)))
       [:otsikko "Raportti sisältää seuraavien urakoiden tiedot: "])
     (when (and hallintayksikko-id (< 0 (count urakat)))
       (for [u (sort-by :nimi urakat)]
         [:teksti (str (:nimi u))]))]))
