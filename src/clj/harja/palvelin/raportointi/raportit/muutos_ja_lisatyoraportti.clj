(ns harja.palvelin.raportointi.raportit.muutos-ja-lisatyoraportti
  "Muutos- ja lisätyöraportti"
  (:require [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.muutos-kyselyt :as muutos-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.budjettisuunnittelu :as budjetti-q]
            [harja.kyselyt.kulut :as kulut-q]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/palvelin/raportointi/raportit/muutos_ja_lisatyoraportti.sql"
  {:positional? true})

(declare hae-aiempien-vuosien-pysyvat-muutokset-raportille hae-kirjallisesti-sovitut-muutokset-raportille
  hae-tavoitehinnan-oikaisut hae-lisatoiden-kulukohdistukset hae-muutostoiden-kulukohdistukset)

(defn tyypin-nimi
  "Palauttaa muutostyypin selkokielisen nimen"
  [tyyppi]
  (case tyyppi
    "johto-ja-hallintokorvaus" "Johto- ja hallintokorvauksen muutos"
    "muutostyo" "Muutostyöt"
    "pysyva" "Pysyvä muutos"
    tyyppi))

(defn muutoksen-tavoitehinnan-muutos
  "Laskee yksittäisen muutoksen tavoitehinnan muutoksen. JJH-muutosten summa tulee kuluista,
   muiden kustannusvaikutuksista."
  [muutos]
  (if (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
    (or (:jjh-muutosten-summa muutos) 0)
    (or (:kustannusvaikutusten-summa muutos) 0)))

(defn- laske-tavoitehinnan-muutos
  "Laskee tehtävän tavoitehinnan muutoksen. Soveltaa talvisuola-kerrointa tarvittaessa."
  [{:keys [tavoitehinnan_muutos talvisuola talvisuola_kerroin maara suunniteltu_maara] :as _rivi}]
  (let [tav-muutos (or tavoitehinnan_muutos 0)
        kayta-talvisuola-kerrointa? (and talvisuola
                                      (> (or maara 0) 0)
                                      (> (or suunniteltu_maara 0) (or maara 0)))]
    (if kayta-talvisuola-kerrointa?
      (* tav-muutos (or talvisuola_kerroin 0.7)) ;; Kovakoodattu luku. Mutta niin se on muutostenkin puolella. Olisi ehkä hyvä yhtenäistää.
      tav-muutos)))

(defn muodosta-muutosten-yhteenveto
  "Muutosten yhteenveto on speksattu HTML raportissa sellaiseksi, ettei sitä voida toteuttaa PDF tai Excel formaatissa.
  Päätellään siis kasittelija parametrista, että millainen osio renderöidään."
  [db urakka-id alkupvm loppupvm urakan-tiedot maaramuutokset
   hoitokauden-alkuvuosi kasittelija budjettitavoite hoitovuoden-alun-indeksikorjattu-tavoitehinta]
  (let [;; Kirjallisesti sovitut muutokset - hyödynnetään samaa hakua kuin kirjalliset muutokset -osiossa
        kirjallisesti-sovitut-muutokset (hae-kirjallisesti-sovitut-muutokset-raportille
                                          db {:urakka-id urakka-id
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm})
        kirjallisesti-sovitut-yht (reduce + 0 (map muutoksen-tavoitehinnan-muutos kirjallisesti-sovitut-muutokset))

        maaramuutokset-yht (reduce + 0 (map laske-tavoitehinnan-muutos maaramuutokset))

        rahavaraukset (when urakka-id
                        (rahavaraus-kyselyt/muutosten-rahavaraukset db urakka-id hoitokauden-alkuvuosi))
        rahavaraus-yhteenveto (first (filter #(= (:id %) :yhteenveto) rahavaraukset))
        rahavaraukset-yht (or (:tavoitehinnan-muutos rahavaraus-yhteenveto) 0)

        toteumiin-perustuvat-yht (+ maaramuutokset-yht rahavaraukset-yht)

        ;; Yhteensä
        yhteensa (+ hoitovuoden-alun-indeksikorjattu-tavoitehinta
                   kirjallisesti-sovitut-yht
                   toteumiin-perustuvat-yht)

        ;; Laskutusraja
        hoitovuosinro (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) alkupvm)
        laskutusraja-rivi (first (kulut-q/hae-urakan-laskutusraja db {:urakka-id urakka-id
                                                                      :hoitokausinro hoitovuosinro}))
        laskutusraja-kaytossa? (:laskutusraja-kaytossa laskutusraja-rivi)
        laskutusraja-hoitovuoden-alussa (or (:laskutusraja laskutusraja-rivi) 0)
        ;; Laskutusrajan automaattiset tarkistukset = kirjallisesti sovitut + toteumiin perustuvat muutokset
        laskutusrajan-tarkistukset (+ kirjallisesti-sovitut-yht toteumiin-perustuvat-yht)
        tarkistettu-laskutusraja (+ laskutusraja-hoitovuoden-alussa laskutusrajan-tarkistukset)]

    (if (= kasittelija :excel)
      ;; Excel raportteihin tulee vain tavalliset taulukot
      (concat
        [[:otsikko-heading "Muutosten yhteenveto"]
         [:taulukko {:otsikko "Muutosten vaikutus tavoitehintaan"
                     :viimeinen-rivi-yhteenveto? true
                     :sheet-nimi "Muutosten yhteenveto"
                     :samalle-sheetille? true}
          [{:leveys 20 :otsikko ""}
           {:leveys 5 :otsikko "€" :fmt :raha}]
          [{:rivi (rivi "Hoitovuoden alun indeksikorjattu tavoitehinta" hoitovuoden-alun-indeksikorjattu-tavoitehinta)}
           {:rivi (rivi "Kirjallisesti sovitut muutokset" kirjallisesti-sovitut-yht)}
           {:rivi (rivi "Toteumiin perustuvat muutokset" toteumiin-perustuvat-yht)}
           {:lihavoi? true
            :korosta-hennosti? true
            :rivi (rivi "Yhteensä" yhteensa)}]]]

        ;; Laskutusrajan taulukko näytetään vain, jos laskutusraja on käytössä
        (when laskutusraja-kaytossa?
          [[:taulukko {:otsikko "Muutosten vaikutus laskutusrajaan"
                       :viimeinen-rivi-yhteenveto? true
                       :nimi "Laskutusraja"
                       :samalle-sheetille? true}
            [{:leveys 20 :otsikko ""}
             {:leveys 5 :otsikko "€" :fmt :raha}]
            [{:rivi (rivi "Laskutusraja hoitovuoden alussa" laskutusraja-hoitovuoden-alussa)}
             {:rivi (rivi "Laskutusrajan automaattiset tarkistukset" laskutusrajan-tarkistukset)}
             {:lihavoi? true
              :korosta-hennosti? true
              :rivi (rivi "Tarkistettu laskutusraja" tarkistettu-laskutusraja)}]]]))

      ;; HTML raporttiin tulee hieno sininen tausta ja kaksi vierekkäistä laatikkoa
      [[:otsikko-heading "Muutosten yhteenveto"]
       [:display-flex
        [:sininen-laatikko {:otsikko "Muutosten vaikutus tavoitehintaan"}
         [{:avain "Hoitovuoden alun indeksikorjattu tavoitehinta"
           :arvo hoitovuoden-alun-indeksikorjattu-tavoitehinta :fmt :raha}
          {:avain "Kirjallisesti sovitut muutokset"
           :arvo kirjallisesti-sovitut-yht :fmt :raha}
          {:avain "Toteumiin perustuvat muutokset"
           :arvo toteumiin-perustuvat-yht :fmt :raha}
          {:avain "Yhteensä"
           :arvo yhteensa :fmt :raha :lihavoi? true}]]

        [:sininen-laatikko {:otsikko "Muutosten vaikutus laskutusrajaan"}
         [{:avain "Laskutusraja hoitovuoden alussa"
           :arvo laskutusraja-hoitovuoden-alussa :fmt :raha}
          {:avain "Laskutusrajan automaattiset tarkistukset"
           :arvo laskutusrajan-tarkistukset :fmt :raha}
          {:avain "Tarkistettu laskutusraja"
           :arvo tarkistettu-laskutusraja :fmt :raha :lihavoi? true}]]]])))

(defn muodosta-kirjalliset-muutokset [db urakka-id alkupvm loppupvm hoitovuosinro kasittelija]
  (let [muutokset (hae-kirjallisesti-sovitut-muutokset-raportille db {:urakka-id urakka-id
                                                                      :alkupvm alkupvm
                                                                      :loppupvm loppupvm})
        muutosrivit (mapv (fn [m]
                            (rivi (tyypin-nimi (:tyyppi m))
                              (or (:syy m) "")
                              (pvm/pvm (:voimassa_alkaen m))
                              (muutoksen-tavoitehinnan-muutos m)))
                      muutokset)
        muutokset-yhteensa (reduce + 0 (map muutoksen-tavoitehinnan-muutos muutokset))
        muutokset-yhteensarivi [{:lihavoi? true
                                 :korosta-hennosti? true
                                 :rivi (rivi "Yhteensä" "" "" muutokset-yhteensa)}]]
    [(when-not (= :excel kasittelija) [:otsikko-heading "Kirjallisesti sovitut muutokset"])
     [:taulukko {:viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Kirjallisesti sovitut"
                 :tyhja (when (empty? muutokset) "Ei muutoksia.")
                 :excel-alkutekstit [[:otsikko-heading "Kirjallisesti sovitut muutokset"]
                                     [:teksti (str "Hoitovuosi: " hoitovuosinro " | Aikaväli: " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]
                                     [:teksti ""]
                                     [:otsikko-heading (str hoitovuosinro ". hoitovuoden (" (pvm/vuosi alkupvm) " - " (pvm/vuosi loppupvm) ") kirjallisesti sovitut muutokset")]]

                 }
      [{:leveys 10 :otsikko "Tyyppi"}
       {:leveys 15 :otsikko "Muutoksen syy"}
       {:leveys 5 :otsikko "Voimassa alkaen"}
       {:leveys 5 :otsikko "Tavoitehinnan muutos (€)" :fmt :raha}]
      (into [] (concat muutosrivit muutokset-yhteensarivi))]]))

(defn muodosta-aiempien-vuosien-muutokset [db urakka-id alkupvm]
  (let [aiemmat-pysyvat (hae-aiempien-vuosien-pysyvat-muutokset-raportille
                          db {:urakka-id urakka-id
                              :alkupvm alkupvm})
        aiemmat-rivit (mapv (fn [m]
                              (rivi
                                (pvm/pvm (:voimassa_alkaen m))
                                (or (:syy m) "Pysyvä muutos")
                                (or (:kustannusvaikutusten-summa m) 0)
                                (or (:indeksikorjattu-summa m) 0)))
                        aiemmat-pysyvat)
        aiemmat-yhteensa (reduce + 0 (map #(or (:kustannusvaikutusten-summa %) 0) aiemmat-pysyvat))
        indeksikorjatut-yhteensa (reduce + 0 (map #(or (:indeksikorjattu-summa %) 0) aiemmat-pysyvat))
        aiemmat-yhteensarivi [{:lihavoi? true
                               :korosta-hennosti? true
                               :rivi (rivi "Yhteensä" "" aiemmat-yhteensa indeksikorjatut-yhteensa)}]]

    [:taulukko {:otsikko "Aiemmilta hoitovuosilta jatkuvat pysyvät muutokset"
                :viimeinen-rivi-yhteenveto? true
                :tyhja (when (empty? aiemmat-pysyvat) "Ei muutoksia.")
                :sheet-nimi "Aiemmat pysyvät muutokset"}
     [{:leveys 5 :otsikko "Voimassa alkaen"}
      {:leveys 15 :otsikko "Muutoksen syy"}
      {:leveys 5 :otsikko "Tavoitehinnan muutos (€)" :fmt :raha}
      {:leveys 5 :otsikko "Indeksikorjattu (€)" :fmt :raha}]
     (into [] (concat aiemmat-rivit aiemmat-yhteensarivi))]))

(defn muodosta-tehtava-ja-maaratoteuma-muutokset
  "Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset.
  Vastaavanlainen taulukko, kuin muutos -sivulla."
  [maaramuutokset]
  (let [;; Lisää väliotsikkorivit toimenpiteiden mukaan (sama logiikka kuin muutos_palvelu.clj)
        rivit-valiotsikoineen
        (reduce (fn [[nahty acc] rivi]
                  (let [tp (:toimenpide rivi)]
                    (if (contains? nahty tp)
                      [nahty (conj acc rivi)]
                      [(conj nahty tp)
                       (conj acc {:valiotsikko tp} rivi)])))
          [#{} []]
          maaramuutokset)
        maaramuutokset-valiotsikoineen (second rivit-valiotsikoineen)
        maaramuutosrivit (mapv (fn [r]
                                 (if (:valiotsikko r)
                                   ;; Väliotsikkorivi: näytetään toimenpiteen nimi
                                   {:korosta-harmaa? true
                                    :lihavoi? true
                                    :rivi (rivi (:valiotsikko r) "" "" "" "" "" "" "")}
                                   ;; Normaali datarivi
                                   (rivi (or (:tehtava r) "")
                                     (or (:yksikko r) "")
                                     (or (:syy r) "")
                                     (:suunniteltu_maara r)
                                     (or (:maara r) 0)
                                     (or (:maaramuutos r) 0)
                                     (or (:kirjatut_kulut_summa r) 0)
                                     (laske-tavoitehinnan-muutos r))))
                           maaramuutokset-valiotsikoineen)
        maaramuutokset-yhteensa (reduce + 0 (map laske-tavoitehinnan-muutos maaramuutokset))
        maaramuutokset-yhteensarivi [{:lihavoi? true
                                      :korosta-hennosti? true
                                      :rivi (rivi "Yhteensä" "" "" "" "" "" "" maaramuutokset-yhteensa)}]]
    [:taulukko {:otsikko "Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset"
                :viimeinen-rivi-yhteenveto? true
                :sheet-nimi "Tehtävä- ja määrämuutokset"}
     [{:leveys 10 :otsikko "Tehtävä"}
      {:leveys 4 :otsikko "Yksikkö"}
      {:leveys 8 :otsikko "Muutoksen syy / lisätieto"}
      {:leveys 4 :otsikko "Suunniteltu määrä"}
      {:leveys 4 :otsikko "Toteutunut määrä"}
      {:leveys 4 :otsikko "Määrämuutos (+/-)"}
      {:leveys 5 :otsikko "Kohdistetut kulut (€)" :fmt :raha}
      {:leveys 5 :otsikko "Tavoitehinnan muutos (€)" :fmt :raha}]
     (into [] (concat maaramuutosrivit maaramuutokset-yhteensarivi))]))

(defn muodosta-rahavarausten-muutokset [db urakka-id hoitokauden-alkuvuosi kasittelija]
  (let [;; Rahavarausten muutokset
        rahavaraukset (when urakka-id
                        (rahavaraus-kyselyt/muutosten-rahavaraukset db urakka-id hoitokauden-alkuvuosi))
        ;; Viimeinen rivi on yhteenveto (:id :yhteenveto)
        rahavaraus-datarivit (filterv #(not= (:id %) :yhteenveto) rahavaraukset)
        rahavaraus-yhteenveto (first (filter #(= (:id %) :yhteenveto) rahavaraukset))
        rahavarausrivit (mapv (fn [r]
                                (rivi (or (:nimi r) "")
                                  (or (:syy r) "")
                                  (or (:summa-indeksikorjattu r) 0)
                                  (or (:toteumat r) 0)
                                  (or (:tavoitehinnan-muutos r) 0)))
                          rahavaraus-datarivit)
        rahavaraukset-yhteensarivi [{:lihavoi? true
                                     :korosta-hennosti? true
                                     :rivi (rivi "Yhteensä"
                                             ""
                                             (or (:summa-indeksikorjattu rahavaraus-yhteenveto) 0)
                                             (or (:toteumat rahavaraus-yhteenveto) 0)
                                             (or (:tavoitehinnan-muutos rahavaraus-yhteenveto) 0))}]]
    [:taulukko {:otsikko (when-not (= kasittelija :excel) "Rahavarausten muutokset") ;; Näytä otsikko, jos ei excel
                :viimeinen-rivi-yhteenveto? true
                :sheet-nimi "Rahavarausten muutokset"
                :tyhja (when (empty? rahavaraukset) "Ei muutoksia.")
                :excel-alkutekstit (when (= kasittelija :excel) [[:otsikko-heading "Rahavarausten muutokset"]])} ;; Näytä otsikko, jos excel
     [{:leveys 10 :otsikko "Rahavaraus"}
      {:leveys 10 :otsikko "Muutoksen syy"}
      {:leveys 5 :otsikko "Suunniteltu määrä (€)" :fmt :raha}
      {:leveys 5 :otsikko "Toteutunut määrä (€)" :fmt :raha}
      {:leveys 5 :otsikko "Tavoitehinnan muutos (€)" :fmt :raha}]
     (into [] (concat rahavarausrivit rahavaraukset-yhteensarivi))]))

(defn muodosta-laskutusrajan-tarkistukset [db urakka-id hoitokauden-alkuvuosi budjettitavoite
                                           hoitovuoden-alun-indeksikorjattu-tavoitehinta]
  (let [tarkistusprosentti 3 ;; Oletettavasti joskus tämä kovakoodattu prosentti voidaan hakea tietokannasta
        tarkistusprosenttimaara (* (/ tarkistusprosentti 100) hoitovuoden-alun-indeksikorjattu-tavoitehinta)
        tarkistusrivit (mapv (fn [r]
                               (rivi (or (:pvm r) "")
                                 (or (:muutokset-yhteensa r) "")
                                 (or (:prosenttia-tavoitehinnasta r) 0)
                                 (or (:tarkistus-summa r) 0)
                                 (or (:laskutusraja r) 0)))
                         (list {:pvm "1.1.2025"
                                :muutokset-yhteensa 15000M
                                :prosenttia-tavoitehinnasta 10.0
                                :tarkistus-summa (* 0.1 hoitovuoden-alun-indeksikorjattu-tavoitehinta)
                                :laskutusraja hoitovuoden-alun-indeksikorjattu-tavoitehinta}))

        yhteensarivi [{:lihavoi? true
                       :korosta-hennosti? true
                       :rivi (rivi ""
                               15000M
                               ""
                               (* 0.1 hoitovuoden-alun-indeksikorjattu-tavoitehinta)
                               hoitovuoden-alun-indeksikorjattu-tavoitehinta)}]]
    [[:taulukko {:otsikko "Laskutusrajan automaattiset tarkistukset"
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Laskutusrajan tarkistukset"
                 :tyhja (when (empty? tarkistusrivit) "Ei tarkistuksia.")}
      [{:leveys 6 :otsikko "Pvm"}
       {:leveys 5 :otsikko "Muutostyötilaukset yhteensä (€)" :fmt :raha}
       {:leveys 5 :otsikko "%-osuus hoitovuoden alun indeksikorjatusta tavoitehinnasta"}
       {:leveys 5 :otsikko "Laskutusrajan tarkistus (€)" :fmt :raha}
       {:leveys 5 :otsikko "Laskutusraja (€)" :fmt :raha}]
      (into [] (concat tarkistusrivit yhteensarivi))]
     [:teksti (format "Laskutusrajaa voidaan tarkistaa hoitovuoden aikana, mikäli tilaaja teettää muutostöitä ja kirjallisten
     muutostyötilausten yhteismäärä kyseiselle hoitovuodelle on vähintään %s %% em. hoitovuoden alun indeksikorjatusta tavoitehinnasta." tarkistusprosentti)]
     [:tyhja-rivi nil]
     [:teksti "Harja laskee laskutusrajan tarkistukset automaattisesti. Laskennassa huomioidaan Kirjallisesti sovitut muutokset -osioon
     tallennetut erillisrahoitetut muutostyöt sekä tavoitehintaa nostavat pysyvät muutokset. "]
     [:tyhja-rivi nil]
     [:teksti (format "Hoitovuoden alun indeksikorjattu tavoitehinta: %s €, josta %s %% on %s €." hoitovuoden-alun-indeksikorjattu-tavoitehinta tarkistusprosentti tarkistusprosenttimaara)]
     [:tyhja-rivi nil]]))

(defn muodosta-muutostoiden-kulukohdistukset [db urakka-id alkupvm loppupvm urakka-nimi kasittelija]
  (let [muutostyot (hae-muutostoiden-kulukohdistukset db {:urakka-id urakka-id
                                                          :alkupvm alkupvm
                                                          :loppupvm loppupvm})
        muutostyorivit (mapv (fn [r]
                               (rivi (or (pvm/pvm (:ajankohta r)) "")
                                 (or (:muutostyon_nimi r) "")
                                 (or (:toimenpide r) "")
                                 (or (:muutostyon_syy r) "")
                                 (or (:summa r) 0)))
                         muutostyot)
        muutostyot-yhteensa (reduce + 0 (map #(or (:summa %) 0) muutostyot))
        muutostyot-yhteensarivi [{:lihavoi? true
                                  :korosta-hennosti? true
                                  :rivi (rivi "Yhteensä" "" "" "" muutostyot-yhteensa)}]
        otsikko-title [:otsikko-title "Muutostöiden kulukohdistukset"]
        ajankohtakuvaus [:teksti (str urakka-nimi " | Aikaväli: " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]]
    [(when-not (= kasittelija :excel) otsikko-title)
     (when-not (= kasittelija :excel) ajankohtakuvaus)
     [:taulukko {:otsikko (when-not (= kasittelija :excel) "Muutostyöt (erillisrahoitetut)")
                 :viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Muutostöiden kulut"
                 :excel-alkutekstit (when (= kasittelija :excel)
                                      [otsikko-title
                                       ajankohtakuvaus
                                       [:teksti ""]
                                       [:otsikko-heading "Muutostyöt (erillisrahoitetut)"]])}
      [{:leveys 3 :otsikko "Lasku pvm"}
       {:leveys 5 :otsikko "Muutostyö"}
       {:leveys 5 :otsikko "Toimenpide"}
       {:leveys 10 :otsikko "Muutoksen syy"}
       {:leveys 5 :otsikko "Määrä (€)" :fmt :raha}]
      (into [] (concat muutostyorivit muutostyot-yhteensarivi))]]))

(defn muodosta-tavoitehinnan-oikaisut [db urakka-id alkupvm loppupvm urakka-nimi kasittelija]
  (let [oikaisut (hae-tavoitehinnan-oikaisut db {:urakka-id urakka-id
                                                 :alkupvm alkupvm})
        oikaisurivit (mapv (fn [r]
                             (rivi
                               (or (:otsikko r) "")
                               (or (:syy r) "")
                               (or (:tavoitehinnan_muutos r) 0)))
                       oikaisut)
        oikaisut-yhteensa (reduce + 0 (map #(or (:tavoitehinnan_muutos %) 0) oikaisut))
        oikaisut-yhteensarivi [{:lihavoi? true
                                :korosta-hennosti? true
                                :rivi (rivi "Yhteensä" "" oikaisut-yhteensa)}]
        otsikko-title [:otsikko-title "Tavoitehinnan oikaisut"]
        ajankohtakuvaus [:teksti (str urakka-nimi " | Aikaväli: " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]]
    [[:taulukko {:viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Tavoitehinnan muutokset"
                 :excel-alkutekstit (when (= kasittelija :excel) [otsikko-title ajankohtakuvaus])}
      [{:leveys 7 :otsikko "Muutos"}
       {:leveys 15 :otsikko "Perustelu"}
       {:leveys 5 :otsikko "Määrä (€)" :fmt :raha}]
      (into [] (concat oikaisurivit oikaisut-yhteensarivi))]]))

(defn muodosta-lisatoiden-kulukohdistukset [db urakka-id alkupvm loppupvm urakka-nimi kasittelija]
  (let [lisatyot (hae-lisatoiden-kulukohdistukset db {:urakka-id urakka-id
                                                      :alkupvm alkupvm
                                                      :loppupvm loppupvm})
        lisatyorivit (mapv (fn [r]
                             (rivi (or (pvm/pvm (:ajankohta r)) "")
                               (or (:toimenpide r) "")
                               (or (:lisatieto r) "")
                               (or (:summa r) 0)))
                       lisatyot)
        lisatyot-yhteensa (reduce + 0 (map #(or (:summa %) 0) lisatyot))
        lisatyot-yhteensarivi [{:lihavoi? true
                                :korosta-hennosti? true
                                :rivi (rivi "Yhteensä" "" "" lisatyot-yhteensa)}]
        otsikko-title [:otsikko-title "Lisätöiden kulukohdistukset"]
        ajankohta [:teksti (str urakka-nimi " | Aikaväli: " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]]
    [(when-not (= kasittelija :excel) otsikko-title)
     (when-not (= kasittelija :excel) ajankohta)
     [:taulukko {:viimeinen-rivi-yhteenveto? true
                 :sheet-nimi "Lisätyöt"
                 :excel-alkutekstit (when (= kasittelija :excel) [otsikko-title ajankohta])}
      [{:leveys 3 :otsikko "Lasku pvm"}
       {:leveys 5 :otsikko "Toimenpide"}
       {:leveys 10 :otsikko "Lisätieto"}
       {:leveys 5 :otsikko "Määrä (€)" :fmt :raha}]
      (into [] (concat lisatyorivit lisatyot-yhteensarivi))]]))

(defn suorita [db _user {:keys [urakka-id alkupvm loppupvm kasittelija] :as parametrit}]
  (log/info "Suoritetaan muutos- ja lisätyöraportti parametreilla: " (pr-str parametrit))
  (let [urakan-tiedot (first (urakat-q/hae-urakka db urakka-id))
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id}))
        raportin-otsikko "Muutos- ja lisätyöraportti"
        hoitokauden-alkuvuosi (pvm/vuosi alkupvm) ;; Kun raportti on vain hoitokauden ajalta, niin hoitokauden alkupvm on aina validi
        hoitovuosinro (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) alkupvm)
        aikajakso (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))
        maaramuutokset (when urakka-id
                         (muutos-kyselyt/hae-tehtava-maaramuutokset
                           db {:urakka urakka-id
                               :alkupvm alkupvm
                               :loppupvm loppupvm
                               :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                               :tehtava nil
                               :tehtavaryhma nil
                               :laskenta-automatiikka? true}))
        ;; Hoitovuoden alun indeksikorjattu tavoitehinta budjettisuunnittelun kautta
        budjettitavoite (budjetti-q/budjettitavoite-vuodelle db urakka-id hoitokauden-alkuvuosi)
        hoitovuoden-alun-indeksikorjattu-tavoitehinta (or (:tavoitehinta-indeksikorjattu budjettitavoite) 0)]
    (into [:raportti {:nimi raportin-otsikko
                      :orientaatio :landscape
                      :urakan-nimi (:nimi urakan-tiedot)
                      :aikajakso aikajakso
                      :otsikon-koko :iso
                      :raportin-yleiset-tiedot {:raportin-nimi raportin-otsikko}
                      :alkupvm alkupvm
                      :loppupvm loppupvm}
           [:jakaja nil]]
      (concat

        ;; Raportin toinen otsikko
        (when-not (= kasittelija :excel) [[:otsikko-title "Tavoitehinnan muutokset"]])
        (when-not (= kasittelija :excel) [[:teksti (str (:nimi urakan-tiedot) " | Aikaväli: " aikajakso)]])

        ;; Jos muutoshallinta on päällä, niin muutosten yhteenveto esitetään ensin. Esitystapa vaihtelee html/pdf/excel formaattien välillä hieman
        (when (:muutosten_hallinta urakan-parametrit)
          (muodosta-muutosten-yhteenveto db urakka-id alkupvm loppupvm urakan-tiedot
            maaramuutokset hoitokauden-alkuvuosi kasittelija budjettitavoite hoitovuoden-alun-indeksikorjattu-tavoitehinta))

        ;; Jos muutokset on päällä - Kirjallisesti sovitut muutokset
        (when (:muutosten_hallinta urakan-parametrit)
          (muodosta-kirjalliset-muutokset db urakka-id alkupvm loppupvm hoitovuosinro kasittelija))

        (when (:muutosten_hallinta urakan-parametrit)
          [(muodosta-aiempien-vuosien-muutokset db urakka-id alkupvm)])

        (when (:muutosten_hallinta urakan-parametrit)
          [(muodosta-tehtava-ja-maaratoteuma-muutokset maaramuutokset)])

        (when (:muutosten_hallinta urakan-parametrit)
          [(muodosta-rahavarausten-muutokset db urakka-id hoitokauden-alkuvuosi kasittelija)])

        (when (:muutosten_hallinta urakan-parametrit)
          (muodosta-laskutusrajan-tarkistukset db urakka-id hoitokauden-alkuvuosi budjettitavoite
            hoitovuoden-alun-indeksikorjattu-tavoitehinta))

        (when (:muutosten_hallinta urakan-parametrit)
          (muodosta-muutostoiden-kulukohdistukset db urakka-id alkupvm loppupvm (:nimi urakan-tiedot) kasittelija))

        (when (:muutosten_hallinta urakan-parametrit)
          (muodosta-lisatoiden-kulukohdistukset db urakka-id alkupvm loppupvm (:nimi urakan-tiedot) kasittelija))

        ;; Kaikilla urakoilla ei ole muutosten hallintaa käytössä, joten näytetään vain osa tiedoista ja vähän eri järjestyksessä
        ;; Näytetään heille tavoitehinnan oikaisut ja lisätöiden kulukohdistukset
        (when-not (:muutosten_hallinta urakan-parametrit)
          (muodosta-tavoitehinnan-oikaisut db urakka-id alkupvm loppupvm (:nimi urakan-tiedot) kasittelija))

        ;; Lisätöiden kulukohdistukset
        (when-not (:muutosten_hallinta urakan-parametrit)
          (muodosta-lisatoiden-kulukohdistukset db urakka-id alkupvm loppupvm (:nimi urakan-tiedot) kasittelija))))))
