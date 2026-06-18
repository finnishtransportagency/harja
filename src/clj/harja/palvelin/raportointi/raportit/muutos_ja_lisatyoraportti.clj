(ns harja.palvelin.raportointi.raportit.muutos-ja-lisatyoraportti
  "Muutos- ja lisätyöraportti"
  (:require [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.budjettisuunnittelu :as budjetti-q]
            [harja.kyselyt.kulut :as kulut-q]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as ks-kyselyt]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.palvelin.raportointi.raportit.yleinen :refer [rivi]]))

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
      (* tav-muutos (or talvisuola_kerroin muutos-domain/+talvisuolakerroin+))
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
                                              :loppupvm loppupvm
                                              :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
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
        ;; Laskutusrajan automaattiset tarkistukset = kirjallisesti sovitut muutokset
        laskutusrajan-tarkistukset kirjallisesti-sovitut-yht
        tarkistettu-laskutusraja (:laskutusraja budjettitavoite)]
    ;; Jos sekä kirjalliset muutokset, että toteutumiin perustuvat muutokset on nil tai nolla, niin yhteenvetoa ei näytetä
    (if (and (= 0 kirjallisesti-sovitut-yht) (= 0 toteumiin-perustuvat-yht))
      [[:tyhja-rivi nil]
       [:teksti "Ei tavoitehinnan muutoksia."]
       [:tyhja-rivi nil]]
      (if (= kasittelija :excel)
        ;; Excel raportteihin tulee vain tavalliset taulukot
        (concat
          [[:otsikko-heading "Muutosten yhteenveto"]
           [:taulukko {:otsikko "Muutosten vaikutus tavoitehintaan"
                       :sheet-otsikko "Muutosten yhteenveto"
                       :viimeinen-rivi-yhteenveto? true
                       :sheet-nimi "Muutosten yhteenveto"
                       :samalle-sheetille? true}
            [{:leveys 20 :otsikko ""}
             {:leveys 5 :otsikko "" :fmt :raha}]
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
                         :samalle-sheetille? true}
              [{:leveys 20 :otsikko ""}
               {:leveys 5 :otsikko "" :fmt :raha}]
              [{:rivi (rivi "Laskutusraja hoitovuoden alussa" laskutusraja-hoitovuoden-alussa)}
               (if (= 0 laskutusrajan-tarkistukset)
                 {:rivi (rivi "Ei vaikutusta laskutusrajaan" nil)}
                 {:rivi (rivi "Laskutusrajan automaattiset tarkistukset" laskutusrajan-tarkistukset)})
               {:lihavoi? true
                :korosta-hennosti? true
                :rivi (rivi "Tarkistettu laskutusraja" tarkistettu-laskutusraja)}]]]))

        ;; HTML raporttiin tulee hieno sininen tausta ja kaksi vierekkäistä laatikkoa
        [[:tyhja-rivi nil]
         [:otsikko-heading "Muutosten yhteenveto"]
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

          ;; Laskutusrajan laatikko näytetään vain, jos laskutusraja on käytössä
          (when laskutusraja-kaytossa?
            [:sininen-laatikko {:otsikko "Muutosten vaikutus laskutusrajaan"}
             [{:avain "Laskutusraja hoitovuoden alussa"
               :arvo laskutusraja-hoitovuoden-alussa :fmt :raha}
              (if (= 0 laskutusrajan-tarkistukset)
                {:avain "Ei vaikutusta laskutusrajaan" :arvo nil :fmt :raha}
                {:avain "Laskutusrajan automaattiset tarkistukset" :arvo laskutusrajan-tarkistukset :fmt :raha})
              {:avain "Tarkistettu laskutusraja"
               :arvo tarkistettu-laskutusraja :fmt :raha :lihavoi? true}]])]]))))

(defn muodosta-kirjalliset-muutokset [db urakka-id alkupvm loppupvm hoitovuosinro hoitokauden-alkuvuosi kasittelija]
  (let [muutokset (hae-kirjallisesti-sovitut-muutokset-raportille db {:urakka-id urakka-id
                                                                      :alkupvm alkupvm
                                                                      :loppupvm loppupvm
                                                                      :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
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
                                     [:otsikko-heading (str hoitovuosinro ". hoitovuoden (" (pvm/vuosi alkupvm) " - " (pvm/vuosi loppupvm) ") kirjallisesti sovitut muutokset")]]}
      [{:leveys 10 :otsikko "Tyyppi"}
       {:leveys 15 :otsikko "Lisätieto"}
       {:leveys 5 :otsikko "Voimassa alkaen"}
       {:leveys 5 :otsikko "Tavoitehinnan muutos (€)" :fmt :raha}]
      (into [] (concat muutosrivit (when-not (empty? muutokset) muutokset-yhteensarivi)))]]))

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
     (into [] (concat aiemmat-rivit (when-not (empty? aiemmat-pysyvat) aiemmat-yhteensarivi)))]))

(defn muodosta-tehtava-ja-maaratoteuma-muutokset
  "Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset.
  Vastaavanlainen taulukko, kuin muutos -sivulla."
  [maaramuutokset]
  (let [maaramuutosrivit (mapv (fn [r]
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
                                     (or (:yksikkohinta r) 0)
                                     (laske-tavoitehinnan-muutos r))))
                           maaramuutokset)
        maaramuutokset-yhteensa (reduce + 0 (map laske-tavoitehinnan-muutos maaramuutokset))
        maaramuutokset-yhteensarivi [{:lihavoi? true
                                      :korosta-hennosti? true
                                      :rivi (rivi "Yhteensä" "" "" "" "" "" "" maaramuutokset-yhteensa)}]]
    [:taulukko {:otsikko "Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset"
                :viimeinen-rivi-yhteenveto? true
                :sheet-nimi "Tehtävä- ja määrämuutokset"
                :tyhja (when (empty? maaramuutokset) "Ei muutoksia.")
                :ei-footer-muokkauspaneelia? true}
     [{:leveys 10 :otsikko "Tehtävä"}
      {:leveys 4 :otsikko "Yksikkö"}
      {:leveys 8 :otsikko "Muutoksen syy / lisätieto"}
      {:leveys 4 :otsikko "Suunniteltu määrä" :fmt :numero-opt}
      {:leveys 4 :otsikko "Toteutunut määrä" :fmt :numero-opt}
      {:leveys 4 :otsikko "Määrämuutos (+/-)" :fmt :numero-opt}
      {:leveys 5 :otsikko "Kohdistetut kulut (€)" :fmt :raha}
      {:leveys 5 :otsikko "Yksikköhinnan keskiarvo (€)" :fmt :raha}
      {:leveys 5 :otsikko "Tavoitehinnan muutos (€)" :fmt :raha}]
     (into [] (concat maaramuutosrivit (when-not (empty? maaramuutokset) maaramuutokset-yhteensarivi)))]))

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
     (into [] (concat rahavarausrivit (when-not (empty? rahavaraukset) rahavaraukset-yhteensarivi)))]))

(defn muodosta-laskutusrajan-tarkistukset [db urakka-id hoitokauden-alkuvuosi _budjettitavoite
                                           hoitovuoden-alun-indeksikorjattu-tavoitehinta]
  (let [laskutusrajan-tarkistukset (ks-kyselyt/hae-laskutusrajan-tarkistukset
                                     db
                                     {:urakka urakka-id
                                      :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                                      :hoitovuoden_indeksikorjattu_tavoitehinta hoitovuoden-alun-indeksikorjattu-tavoitehinta})
        tarkistusprosentti 3
        tarkistusprosenttimaara (fmt/desimaaliluku-opt
                                  (* (/ tarkistusprosentti 100)
                                    (or hoitovuoden-alun-indeksikorjattu-tavoitehinta 0))
                                  2
                                  true)
        tarkistusrivit (mapv (fn [{:keys [voimassa_alkaen yhteensa prosenttiosuus laskutusrajan-tarkistus tarkistettu-laskutusraja]}]
                               [voimassa_alkaen
                                (or yhteensa 0)
                                (if (some? prosenttiosuus)
                                  (str (fmt/prosentti-opt prosenttiosuus))
                                  "")
                                (if (some? laskutusrajan-tarkistus)
                                  (str "+" (fmt/desimaaliluku-opt laskutusrajan-tarkistus 2 true))
                                  0.00)
                                (or tarkistettu-laskutusraja 0)])
                          laskutusrajan-tarkistukset)]

    [[:taulukko {:otsikko "Laskutusrajan automaattiset tarkistukset"
                 :viimeinen-rivi-yhteenveto? false
                 :sheet-nimi "Laskutusrajan tarkistukset"
                 :tyhja (when (empty? tarkistusrivit) "Ei muutostyötilauksia")}
      [{:leveys 7 :otsikko "Pvm" :fmt :pvm}
       {:leveys 5 :otsikko "Muutostyötilaukset yhteensä (€)" :fmt :raha}
       {:leveys 5 :otsikko "%-osuus hoitovuoden alun indeksikorjatusta tavoitehinnasta" :tasaa :oikea}
       {:leveys 5 :otsikko "Laskutusrajan tarkistus (€)" :tasaa :oikea}
       {:leveys 5 :otsikko "Laskutusraja (€)" :fmt :raha}]
      tarkistusrivit]
     [:teksti (format "Laskutusrajaa voidaan tarkistaa hoitovuoden aikana, mikäli tilaaja teettää muutostöitä ja kirjallisten muutostyötilausten yhteismäärä kyseiselle hoitovuodelle on vähintään %s %% em. hoitovuoden alun indeksikorjatussa tavoitehinnasta." tarkistusprosentti) {:leveysprosentti 60}]
     [:tyhja-rivi nil]
     [:teksti "Harja laskee laskutusrajan tarkistukset automaattisesti. Laskennassa huomioidaan Kirjallisesti sovitut muutokset -osioon tallennetut erillisrahoitetut muutostyöt sekä tavoitehintaa nostavat pysyvät muutokset." {:leveysprosentti 60}]
     [:tyhja-rivi nil]
     [:teksti (format "Hoitovuoden alun indeksikorjattu tavoitehinta: %s €, josta %s %% on %s €." (fmt/desimaaliluku-opt hoitovuoden-alun-indeksikorjattu-tavoitehinta 2 true) tarkistusprosentti tarkistusprosenttimaara) {:leveysprosentti 60}]
     [:tyhja-rivi nil]
     [:tyhja-rivi nil]
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
    (if
      ;; HTML ja PDF raporteille ei näytetä taulukkoa
      (and (not (= kasittelija :excel)) (= 0 (count muutostyorivit)))
      [[:jakaja nil]
       (when-not (= kasittelija :excel) otsikko-title)
       (when-not (= kasittelija :excel) ajankohtakuvaus)
       [:tyhja-rivi nil]
       [:tyhja-rivi nil]
       [:teksti "Ei muutostöiden kulukohdistuksia." {:vari "#5C5C5C"}]
       [:tyhja-rivi nil]]

      [(when-not (= kasittelija :excel) otsikko-title)
       (when-not (= kasittelija :excel) ajankohtakuvaus)
       [:taulukko {:otsikko (when-not (= kasittelija :excel) "Muutostyöt (erillisrahoitetut)")
                   :viimeinen-rivi-yhteenveto? true
                   :tyhja (when (empty? muutostyot) "Ei muutostöitä.")
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
        (into [] (concat muutostyorivit (when-not (empty? muutostyot) muutostyot-yhteensarivi)))]])))

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
                                :tyhja (when (empty? oikaisut) "Ei oikaisuja.")
                                :rivi (rivi "Yhteensä" "" oikaisut-yhteensa)}]
        otsikko-title [:otsikko-title "Tavoitehinnan oikaisut"]
        ajankohtakuvaus [:teksti (str urakka-nimi " | Aikaväli: " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]]
    (if (= 0 (count oikaisurivit))
      [[:tyhja-rivi nil]
       [:teksti "Ei tavoitehinnan muutoksia."]
       [:tyhja-rivi nil]]
      [[:taulukko {:viimeinen-rivi-yhteenveto? true
                                                :sheet-nimi "Tavoitehinnan muutokset"
                                                :excel-alkutekstit (when (= kasittelija :excel) [otsikko-title ajankohtakuvaus])}
                                     [{:leveys 7 :otsikko "Muutos"}
                                      {:leveys 15 :otsikko "Perustelu"}
                                      {:leveys 5 :otsikko "Määrä (€)" :fmt :raha}]
                                     (into [] (concat oikaisurivit (when-not (empty? oikaisut) oikaisut-yhteensarivi)))]])))

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
    (if
      ;; HTML ja PDF raporteille ei näytetä taulukkoa, jos dataa ei ole
      (and (not (= kasittelija :excel)) (= 0 (count lisatyorivit)))
      [[:jakaja nil]
       (when-not (= kasittelija :excel) otsikko-title)
       (when-not (= kasittelija :excel) ajankohta)
       [:tyhja-rivi nil]
       [:tyhja-rivi nil]
       [:teksti "Ei lisätöiden kulukohdistuksia." {:vari "#5C5C5C"}]
       [:tyhja-rivi nil]]

      [(when-not (= kasittelija :excel) otsikko-title)
       (when-not (= kasittelija :excel) ajankohta)
       [:taulukko {:viimeinen-rivi-yhteenveto? true
                   :sheet-nimi "Lisätyöt"
                   :tyhja (when (empty? lisatyot) "Ei lisätöitä.")
                   :excel-alkutekstit (when (= kasittelija :excel) [otsikko-title ajankohta])}
        [{:leveys 3 :otsikko "Lasku pvm"}
         {:leveys 5 :otsikko "Toimenpide"}
         {:leveys 10 :otsikko "Lisätieto"}
         {:leveys 5 :otsikko "Määrä (€)" :fmt :raha}]
        (into [] (concat lisatyorivit (when-not (empty? lisatyot) lisatyot-yhteensarivi)))]])))

(defn suorita [db _user {:keys [urakka-id alkupvm loppupvm kasittelija] :as parametrit}]
  (log/info "Suoritetaan muutos- ja lisätyöraportti parametreilla: " (pr-str parametrit))
  (let [urakan-tiedot (first (urakat-q/hae-urakka db urakka-id))
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id}))
        urakan-hoitokaudet (mapv (fn [m] (vec (vals m))) (urakat-q/hae-urakan-hoitokaudet db urakka-id))
        raportin-otsikko "Muutos- ja lisätyöraportti"
        hoitokauden-alkuvuosi (pvm/vuosi alkupvm) ;; Kun raportti on vain hoitokauden ajalta, niin hoitokauden alkupvm on aina validi
        hoitovuosinro (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) alkupvm)
        aikajakso (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))
        maaramuutokset (when urakka-id
                         (muutos-palvelu/hae-tehtava-maaramuutokset db _user {:urakka-id urakka-id
                                                                              :valittu-hoitokausi [alkupvm loppupvm]
                                                                              :hoitokaudet urakan-hoitokaudet
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
          (muodosta-kirjalliset-muutokset db urakka-id alkupvm loppupvm hoitovuosinro hoitokauden-alkuvuosi kasittelija))

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
