(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-mhu
  "Laskutusyhteenveto MHU-urakoissa"
  (:require [harja.kyselyt.hallintayksikot :as hallintayksikko-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.budjettisuunnittelu :as budjetti-q]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as lyv-yhteiset]

            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
            [harja.palvelin.palvelut.indeksit :as indeksipalvelu]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.tyokalut.big :as big]
            [harja.fmt :as fmt])
  (:import java.math.BigDecimal))

(defn- laskettavat-kentat [rivi konteksti]
  (let [kustannusten-kentat (into []
                                  (apply concat [(lyv-yhteiset/kustannuslajin-kaikki-kentat "lisatyot")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hankinnat")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "sakot")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "johto_ja_hallinto")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_erillishankinnat")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_tavoitepalkkio")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_hoitovuoden_paattaminen_kattohinnan_ylitys")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "bonukset")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "hj_palkkio")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "tavoitehintaiset")
                                                 (lyv-yhteiset/kustannuslajin-kaikki-kentat "kaikki")
                                                 (when (= :urakka konteksti) [:tpi :maksuera_numero])]))]
    kustannusten-kentat))

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
    {:kaikki-tavoitehintaiset-laskutettu kaikki-tavoitehintaiset-laskutettu
     :kaikki-tavoitehintaiset-laskutetaan kaikki-tavoitehintaiset-laskutetaan
     :kaikki-yhteensa-laskutettu kaikki-yhteensa-laskutettu
     :kaikki-yhteensa-laskutetaan kaikki-yhteensa-laskutetaan
     :nimi "Kaikki toteutuneet kustannukset"}))

(defn- koosta-tavoite [tiedot urakka-tavoite]
  (let [kaikki-tavoitehintaiset-laskutettu (apply + (map #(if (not (nil? (:tavoitehintaiset_laskutettu %)))
                                                            (:tavoitehintaiset_laskutettu %)
                                                            0) tiedot))]
    (if urakka-tavoite
      {:tavoite-hinta (or (:tavoitehinta-oikaistu urakka-tavoite) 0M)
       :jaljella (- (or (:tavoitehinta-oikaistu urakka-tavoite) 0M) kaikki-tavoitehintaiset-laskutettu)
       :nimi "Tavoite"}
      {:tavoite-hinta 0
       :jaljella 0
       :nimi "Tavoite"})))

(defn- hankinnat
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Hankinnat")
    [:varillinen-teksti {:arvo (or (:hankinnat_laskutettu tp-rivi) (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:hankinnat_laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt :raha}])))

(defn- lisatyot
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Lisätyöt")
    [:varillinen-teksti {:arvo (or (:lisatyot_laskutettu tp-rivi) (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:lisatyot_laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt :raha}])))

(defn- sanktiot
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Sanktiot")
    [:varillinen-teksti {:arvo (if (:sakot_laskutettu tp-rivi)
                                 (fmt/formatoi-arvo-raportille (:sakot_laskutettu tp-rivi))
                                 (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (if (:sakot_laskutetaan tp-rivi)
                                   (fmt/formatoi-arvo-raportille (:sakot_laskutetaan tp-rivi))
                                   (summa-fmt nil))
                           :fmt :raha}])))

(defn- johto-hallintakorvaukset
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Johto- ja hallintakorvaukset")
    [:varillinen-teksti {:arvo (or (:johto_ja_hallinto_laskutettu tp-rivi) (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:johto_ja_hallinto_laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt :raha}])))

(defn- erillishankinnat
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Erillishankinnat")
    [:varillinen-teksti {:arvo (or (:hj_erillishankinnat_laskutettu tp-rivi)  (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:hj_erillishankinnat_laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt :raha}])))

(defn- hj-hoitovuoden-paattaminen-tavoitepalkkio
  [tp-rivi kyseessa-kk-vali?]
  (let [laskutettu (:hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu tp-rivi)
        laskutetaan (:hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan tp-rivi)]
    (when (or
            (not (zero? laskutettu))
            (not (zero? laskutetaan)))
      (rivi
        (str "Hoitovuoden päättäminen / Tavoitepalkkio")
        [:varillinen-teksti {:arvo (or laskutettu (summa-fmt nil))
                             :fmt :raha}]
        (when kyseessa-kk-vali?
          [:varillinen-teksti {:arvo (or laskutetaan (summa-fmt nil))
                               :fmt :raha}])))))

(defn- hj-hoitovuoden-paattaminen-tavoitehinnan-ylitys
  [tp-rivi kyseessa-kk-vali?]
  (let [laskutettu (:hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu tp-rivi)
        laskutetaan (:hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan tp-rivi)]
    (when (or
            (not (zero? laskutettu))
            (not (zero? laskutetaan)))
      (rivi
        (str "Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä")
        [:varillinen-teksti {:arvo (or laskutettu (summa-fmt nil))
                             :fmt :raha}]
        (when kyseessa-kk-vali?
          [:varillinen-teksti {:arvo (or laskutetaan (summa-fmt nil))
                               :fmt :raha}])))))

(defn- hj-hoitovuoden-paattaminen-kattohinnan-ylitys
       [tp-rivi kyseessa-kk-vali?]
  (let [laskutettu (:hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu tp-rivi)
        laskutetaan (:hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan tp-rivi)]
    (when (or
            (not (zero? laskutettu))
            (not (zero? laskutetaan)))
      (rivi
        (str "Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä")
        [:varillinen-teksti {:arvo (or laskutettu (summa-fmt nil))
                             :fmt :raha}]
        (when kyseessa-kk-vali?
          [:varillinen-teksti {:arvo (or laskutetaan (summa-fmt nil))
                               :fmt :raha}])))))

(defn- hj-palkkio
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "HJ-palkkio")
    [:varillinen-teksti {:arvo (or (:hj_palkkio_laskutettu tp-rivi) (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (or (:hj_palkkio_laskutetaan tp-rivi) (summa-fmt nil))
                           :fmt :raha} ])))

(defn- bonukset
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Bonukset")
    [:varillinen-teksti {:arvo (if (:bonukset_laskutettu tp-rivi)
                                 (fmt/formatoi-arvo-raportille (:bonukset_laskutettu tp-rivi))
                                 (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (if (:bonukset_laskutetaan tp-rivi)
                                   (fmt/formatoi-arvo-raportille (:bonukset_laskutetaan tp-rivi))
                                   (summa-fmt nil))
                           :fmt :raha}])))

(defn- yhteensa
  [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Yhteensä")
    [:varillinen-teksti {:arvo (if (:kaikki_laskutettu tp-rivi)
                                 (fmt/formatoi-arvo-raportille (:kaikki_laskutettu tp-rivi))
                                 (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (if (:kaikki_laskutetaan tp-rivi)
                                   (fmt/formatoi-arvo-raportille (:kaikki_laskutetaan tp-rivi))
                                   (summa-fmt nil))
                           :fmt :raha}])))

(defn- toteutuneet-yhteensa [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Toteutuneet kustannukset yhteensä")
    [:varillinen-teksti {:arvo (if (:kaikki-yhteensa-laskutettu tp-rivi)
                                 (fmt/formatoi-arvo-raportille (:kaikki-yhteensa-laskutettu tp-rivi))
                                 (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (if (:kaikki-yhteensa-laskutetaan tp-rivi)
                                   (fmt/formatoi-arvo-raportille (:kaikki-yhteensa-laskutetaan tp-rivi))
                                   (summa-fmt nil))
                           :fmt :raha}])))

(defn- tavoitteeseen-kuuluvat-toteutuneet-yhteensa [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Toteutuneet kustannukset, jotka kuuluvat tavoitehintaan")
    [:varillinen-teksti {:arvo (if (:kaikki-tavoitehintaiset-laskutettu tp-rivi)
                                 (fmt/formatoi-arvo-raportille (:kaikki-tavoitehintaiset-laskutettu tp-rivi))
                                 (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (if (:kaikki-tavoitehintaiset-laskutetaan tp-rivi)
                                   (fmt/formatoi-arvo-raportille (:kaikki-tavoitehintaiset-laskutetaan tp-rivi))
                                   (summa-fmt nil))
                           :fmt :raha}])))

(defn- tavoitehinta [tp-rivi kyseessa-kk-vali?]
  (rivi
    (str "Tavoite / Jäljellä")
    [:varillinen-teksti {:arvo (if (:tavoite-hinta tp-rivi)
                                 (fmt/formatoi-arvo-raportille (:tavoite-hinta tp-rivi))
                                 (summa-fmt nil))
                         :fmt :raha}]
    (when kyseessa-kk-vali?
      [:varillinen-teksti {:arvo (if (:jaljella tp-rivi)
                                   (fmt/formatoi-arvo-raportille (:jaljella tp-rivi))
                                   (summa-fmt nil))
                           :fmt :raha}])))

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
                               (hj-hoitovuoden-paattaminen-tavoitepalkkio tp-rivi kyseessa-kk-vali?)
                               (hj-hoitovuoden-paattaminen-tavoitehinnan-ylitys tp-rivi kyseessa-kk-vali?)
                               (hj-hoitovuoden-paattaminen-kattohinnan-ylitys tp-rivi kyseessa-kk-vali?)
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
                               (yhteensa tp-rivi kyseessa-kk-vali?)])))]

    [:taulukko {:oikealle-tasattavat-kentat #{1 2}
                :viimeinen-rivi-yhteenveto? true}
     ;; otsikot
     (rivi
       {:otsikko (if (:maksuera_numero tp-rivi)
                   (str (:nimi tp-rivi) " (HA" (:maksuera_numero tp-rivi) ") ")
                   (:nimi tp-rivi))
        :leveys 36}
       {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti}
       (when kyseessa-kk-vali?
         {:otsikko laskutetaan-teksti :leveys 29 :tyyppi :varillinen-teksti}))

     ;; arvot
     rivit]))

(defn- laske-indeksikerroin
  "Indeksikertoimen kaava: indeksiarvo / perusluku = indeksikerroin. Indeksiarvo ja perusluku pyöristetään yhteen desimaaliin
  ennen käyttämistä. Indeksikerroin pyöristetään kolmeen desimaaliin."
  [indeksiarvo perusluku]
  (let [b-indeksiarvo (big/parse (clojure.string/replace (big/fmt (big/->big indeksiarvo) 1) "," ".")) ;; Varmista 1 desimaali
        b-perusluku (big/parse (clojure.string/replace (big/fmt (big/->big perusluku) 1) "," ".")) ;; Varmista 1 desimaali
        indeksikerroin (big/fmt (big/div-decimal b-indeksiarvo b-perusluku 3) 3)]
    indeksikerroin))

(defn etsi-syvalta-arvoa
  "Haetaan syvältä datarakenteesta, kun annettu ehto täyttyy."
  [ehto data kun-ei-loydy]
  (->> data
    (tree-seq coll? seq)
    (some #(when (ehto %) [%]))
    ((fnil first [kun-ei-loydy]))))

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
        hoitokausi (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm (first urakat)) alkupvm)
        urakka-tavoite (first (filter #(= (:hoitokausi %) hoitokausi) (budjetti-q/hae-budjettitavoite db {:urakka urakka-id})))
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
        ;_ (log/debug "laskutusyhteenvedot" (pr-str laskutusyhteenvedot))

        urakoiden-lahtotiedot (lyv-yhteiset/urakoiden-lahtotiedot laskutusyhteenvedot)

        ;; Datan tuotteittain ryhmittely
        tiedot-tuotteittain (fmap #(group-by :nimi %) laskutusyhteenvedot)
        kaikki-tuotteittain (apply merge-with concat tiedot-tuotteittain)

        ;; Indeksitiedot - mhu raporteissa vain sanktiot ja bonukset perustuvat indeksiin
        ;; Perusluku tulee urakkaa edeltävän vuoden syys,loka,marraskuun keskiarvosta
        perusluku (when (= 1 (count urakat)) (:perusluku (val (first urakoiden-lahtotiedot))))
        ;; Indeksiä käytetään vain sanktioissa ja bonuksissa
        ;; Indeksinä käytetään hoitokautta edeltävän syyskuun arvoa, mikäli se on olemassa.
        ;; tiedot-tuotteittain on hieman monimutkainen rakenteeltaan, joten käytetään erillistä fn:ää datan löytämiseksi
        indeksi-puuttuu (etsi-syvalta-arvoa #(not (nil? (:indeksi_puuttuu %))) tiedot-tuotteittain false)
        indeksi-puuttuu (if indeksi-puuttuu (:indeksi_puuttuu indeksi-puuttuu) true)
        raportin-indeksiarvo (when-not indeksi-puuttuu
                               (indeksipalvelu/hae-urakan-kuukauden-indeksiarvo db urakka-id
                                 (if (> (pvm/kuukausi alkupvm) 9)
                                   (pvm/vuosi alkupvm)
                                   (dec (pvm/vuosi alkupvm)))
                                 9))

        ;; Urakoiden datan yhdistäminen yhteenlaskulla. Datapuutteet (indeksi, jne) voivat aiheuttaa nillejä,
        ;; joiden yli ratsastetaan (fnil + 0 0):lla. Tämä vuoksi pidetään käsin kirjaa mm. indeksipuuteiden sotkemista kentistä
        kaikki-tuotteittain-summattuna (when kaikki-tuotteittain
                                         (fmap #(apply merge-with (fnil + 0 0)
                                                  (map (fn [rivi]
                                                         (select-keys rivi (laskettavat-kentat rivi konteksti)))
                                                    %))
                                           kaikki-tuotteittain))
        tiedot (into []
                 (map #(merge {:nimi (key %)} (val %)) kaikki-tuotteittain-summattuna))
        yhteenveto (koosta-yhteenveto tiedot)
        tavoite (koosta-tavoite tiedot urakka-tavoite)
        koostettu-yhteenveto (conj [] yhteenveto tavoite)
        ;; Laske indeksikerroin vain, jos arvot ovat saatavilla
        indeksikerroin (when (and raportin-indeksiarvo perusluku)
                         (laske-indeksikerroin (:arvo raportin-indeksiarvo) perusluku))]

    [:raportti {:nimi "Laskutusyhteenveto MHU"}
     [:otsikko (str (or (str alueen-nimi ", ") "") (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]
     (when perusluku
       (yleinen/urakan-indlask-perusluku {:perusluku perusluku}))

     ;; Tarkistetaan puuttuuko indeksi
     (if indeksi-puuttuu
       [:varoitusteksti "Hoitokautta edeltävän syyskuun indeksiä ei ole asetettu."]
       [:teksti (str "Käytetään hoitokautta edeltävän syyskuun indeksiarvoa: " (fmt/desimaaliluku-opt (:arvo raportin-indeksiarvo) 1))])

     (when-not indeksi-puuttuu
       [:teksti (str "Indeksikerroin: " indeksikerroin)
        {:infopallura {:infoteksti [:span [:strong "Indeksikertoimenlaskukaava"] [:br]
                                    [:p "Indeksikertoimen laskemiseen käytetään yhden desimaalin tarkkuutta indeksistä ja perusluvusta.
                          Itse indeksikerroin pyöristetään kolmen desimaalin tarkkuuteen."]
                                    [:p "Laskukaava: indeksi / perusluku = indeksikerroin."]]}}])

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
     [:teksti ""]
     (concat
       (for [tp-rivi koostettu-yhteenveto
             :let [yhteensa-otsikko (if (not (= (:nimi tp-rivi) "Tavoite")) laskutettu-teksti "Tavoitehinta")
                   kuukausi-otsikko (if (not (= (:nimi tp-rivi) "Tavoite")) laskutetaan-teksti "Jäljellä")]]
         (do
           (taulukko {:tp-rivi tp-rivi
                      :laskutettu-teksti yhteensa-otsikko
                      :laskutetaan-teksti kuukausi-otsikko
                      :kyseessa-kk-vali? kyseessa-kk-vali?}))))


     (when (and hallintayksikko-id (= 0 (count urakat)))
       [:teksti " Hallintayksikössä ei aktiivisia urakoita valitulla aikavälillä"])

     (when (and hallintayksikko-id (< 0 (count urakat)))
       [:otsikko "Raportti sisältää seuraavien urakoiden tiedot: "])
     (when (and hallintayksikko-id (< 0 (count urakat)))
       (for [u (sort-by :nimi urakat)]
         [:teksti (str (:nimi u))]))]))
