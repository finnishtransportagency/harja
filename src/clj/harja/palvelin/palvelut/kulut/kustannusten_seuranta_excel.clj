(ns harja.palvelin.palvelut.kulut.kustannusten-seuranta-excel
  "Excelin luonti kustannus seuranta -datasta."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [throw+ try+]]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.kustannusten-seuranta :as kustannusten-seuranta-q]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.string :as str]))


(defn- laske-prosentti [tot bud]
  (let [tot (bigdec tot)
        bud (bigdec bud)]
    (if (or (= (bigdec 0) tot) (= (bigdec 0) bud))
      0
      (* 100 (* 100 (with-precision 4 (/ tot bud)))))))

(defn- kokoa-toimenpiteen-alle
  "Kustannus seurannan ui ja tämä excel on rakennettu ajatukselle, että alimmaisen kolmannen tason tehtäviä (osa on tehtäväryhmiä)
  ei näytetä samalla tavalla kuin ylempiä tasoja. Eli budjetoituja summia ja sitä kautta erotusta ja prosentteja.
  Mutta jostain syystä on haluttu tehdä poikkeus, että rahavaraukset näytetäänkin eri tavalla kuin muut tiedot.

  Siitä syystä tässä funktiossa tarkistetaan, että mikäli kolmannen tason rivi kuuluu rahavaraus -toimenpiteelle/pääryhmään,
  niin lasketaan erotukset ja prosentit.

  Muille näytetään vain toteutumat, kun se on se pääasiallinen tapa näyttää näitä kolmannen tason asioita."
  [toimenpide tehtavat toimenpideryhma yht-toteuma]
  (concat
    (when (> (count tehtavat) 0)
      (mapcat
        (fn [tehtava]
          (let [toteutunut-summa (or (:toteutunut_summa tehtava) 0)
                budjetoitu-summa (or (:budjetoitu_summa tehtava) 0)
                budjetoitu-summa-indeksikorjattu (or (:budjetoitu_summa_indeksikorjattu tehtava) 0)
                erotus (- toteutunut-summa budjetoitu-summa-indeksikorjattu)
                prosentti (laske-prosentti toteutunut-summa budjetoitu-summa-indeksikorjattu)]
            [{:paaryhma nil
              :toimenpide nil
              :tehtava_nimi (:tehtava_nimi tehtava)
              :toteutunut_summa toteutunut-summa
              :budjetoitu_summa (when (= "rahavaraus" (:toimenpideryhma tehtava)) budjetoitu-summa)
              :budjetoitu_summa_indeksikorjattu (when (= "rahavaraus" (:toimenpideryhma tehtava)) budjetoitu-summa-indeksikorjattu)
              :erotus (when (= "rahavaraus" (:toimenpideryhma tehtava)) erotus)
              :prosentti (when (= "rahavaraus" (:toimenpideryhma tehtava)) prosentti)
              :lihavoi? false}]))
        tehtavat))))

(defn- listaa-pelkat-tehtavat [tehtavat]
  (mapcat
    (fn [rivi]
      (let [toteutunut-summa (or (:toteutunut_summa rivi) 0)
            budjetoitu-summa (or (:budjetoitu_summa rivi) 0)
            budjetoitu-summa-indeksikorjattu (or (:budjetoitu_summa_indeksikorjattu rivi) 0)]
        [{:paaryhma nil
          :toimenpide nil
          :tehtava_nimi (str/capitalize (:tehtava_nimi rivi))
          :toteutunut_summa (when-not (= 0M toteutunut-summa) toteutunut-summa)
          :budjetoitu_summa (when-not (= 0M budjetoitu-summa) budjetoitu-summa)
          :budjetoitu_summa_indeksikorjattu (when-not (= 0M budjetoitu-summa-indeksikorjattu) budjetoitu-summa-indeksikorjattu)
          :erotus nil
          :prosentti nil
          :lihavoi? false}]))
    tehtavat))

(defn- rivita-toimenpiteet [toimenpiteet paaryhma]
  (let [toimenpide-rivit
        (mapcat (fn [toimenpide]
                  (let [toimenpide-tot (or (:toimenpide-toteutunut-summa toimenpide) 0)
                        toimenpide-bud (or (:toimenpide-budjetoitu-summa toimenpide) 0)
                        toimenpide-bud-indeksikorjattu (or (:toimenpide-budjetoitu-summa-indeksikorjattu toimenpide) 0)
                        erotus (when (not= 0 toimenpide-bud-indeksikorjattu) (- toimenpide-tot toimenpide-bud-indeksikorjattu))
                        hankinta-tehtavat (filter #(= "hankinta" (:toimenpideryhma %)) (:tehtavat toimenpide))
                        hankinta-toteuma (reduce (fn [summa rivi]
                                                   (+ (or summa 0) (or (:toteutunut_summa rivi) 0)))
                                                 0
                                                 hankinta-tehtavat)
                        toimistokulu-tehtavat (filter #(= "toimistokulut" (:toimenpideryhma %)) (:tehtavat toimenpide))
                        toimistokulu-toteuma (reduce (fn [summa rivi]
                                                       (+ (or summa 0) (or (:toteutunut_summa rivi) 0)))
                                                     0
                                                     toimistokulu-tehtavat)
                        palkka-tehtavat (filter #(= "palkat" (:toimenpideryhma %)) (:tehtavat toimenpide))
                        palkka-toteumat (reduce (fn [summa rivi]
                                                  (+ (or summa 0) (or (:toteutunut_summa rivi) 0)))
                                                0
                                                palkka-tehtavat)
                        rahavaraus-tehtavat (filter #(= "rahavaraus" (:toimenpideryhma %)) (:tehtavat toimenpide))
                        rahavaraus-toteuma (reduce (fn [summa rivi]
                                                     (+ (or summa 0) (or (:toteutunut_summa rivi) 0)))
                                                   0
                                                   rahavaraus-tehtavat)]
                    (concat [{:paaryhma paaryhma
                              :toimenpide (:toimenpide toimenpide)
                              :tehtava_nimi nil
                              :toteutunut_summa toimenpide-tot
                              :budjetoitu_summa toimenpide-bud
                              :budjetoitu_summa_indeksikorjattu toimenpide-bud-indeksikorjattu
                              :erotus erotus
                              :prosentti (laske-prosentti toimenpide-tot toimenpide-bud-indeksikorjattu)
                              :lihavoi? true}]
                            (kokoa-toimenpiteen-alle toimenpide hankinta-tehtavat "Hankinnat" hankinta-toteuma)
                            (kokoa-toimenpiteen-alle toimenpide rahavaraus-tehtavat "Rahavaraus" rahavaraus-toteuma)
                            (kokoa-toimenpiteen-alle toimenpide palkka-tehtavat "Palkat" palkka-toteumat)
                            (kokoa-toimenpiteen-alle toimenpide toimistokulu-tehtavat "Toimistokulu" toimistokulu-toteuma))))
                toimenpiteet)
        toimenpide-toteutumat (reduce (fn [summa rivi]
                                        (if-not (nil? (:toimenpide rivi))
                                          (+ (or summa 0) (or (:toteutunut_summa rivi) 0))
                                          summa))
                                      0
                                      toimenpide-rivit)
        toimenpide-budjetoidut (reduce (fn [summa rivi]
                                         (if-not (nil? (:toimenpide rivi))
                                           (+ (or summa 0) (or (:budjetoitu_summa rivi) 0))
                                           summa))
                                       0
                                       toimenpide-rivit)
        toimenpide-budjetoidut-indeksikorjatut (reduce (fn [summa rivi]
                                                         (if-not (nil? (:toimenpide rivi))
                                                           (+ (or summa 0) (or (:budjetoitu_summa_indeksikorjattu rivi) 0))
                                                           summa))
                                                 0
                                                 toimenpide-rivit)
        toimenpide-erotus (when (not= 0 toimenpide-toteutumat) (- toimenpide-toteutumat toimenpide-budjetoidut-indeksikorjatut))
        yhteenvetorivi [{:paaryhma paaryhma
                         :toimenpide nil
                         :tehtava_nimi nil
                         :toteutunut_summa toimenpide-toteutumat
                         :budjetoitu_summa toimenpide-budjetoidut
                         :budjetoitu_summa_indeksikorjattu toimenpide-budjetoidut-indeksikorjatut
                         :erotus toimenpide-erotus
                         :prosentti (laske-prosentti toimenpide-toteutumat toimenpide-budjetoidut-indeksikorjatut)
                         :lihavoi? true}]]
    (concat yhteenvetorivi toimenpide-rivit)))

(defn- rivita-lisatyot [lisatyot yhteensa]
  (concat [{:paaryhma "Lisätyöt"
            :toimenpide nil
            :tehtava_nimi nil
            :toteutunut_summa yhteensa
            :budjetoitu_summa nil
            :budjetoitu_summa_indeksikorjattu nil
            :erotus nil
            :prosentti nil}]
          (mapcat
            (fn [l]
              [{:paaryhma "Lisätyöt"
                :toimenpide (:toimenpide l)
                :tehtava_nimi (or (:tehtava_nimi l) (:toimenpidekoodi_nimi l))
                :toteutunut_summa (or (:toteutunut_summa l) 0)
                :budjetoitu_summa nil
                :budjetoitu_summa_indeksikorjattu nil
                :erotus nil
                :prosentti nil
                :lihavoi? true}])
            lisatyot)))

(defn- luo-excel-rivi-toimenpiteelle [rivi ensimmainen?]
  (if ensimmainen?
    {:rivi [(:paaryhma rivi)
            (:toimenpide rivi)
            (:tehtava_nimi rivi)
            (:budjetoitu_summa rivi)
            (:budjetoitu_summa_indeksikorjattu rivi)
            (:toteutunut_summa rivi)
            (:erotus rivi)
            (:prosentti rivi)]
     :lihavoi? true}
    (merge {:rivi [nil
                   (:toimenpide rivi)
                   (:tehtava_nimi rivi)
                   (:budjetoitu_summa rivi)
                   (:budjetoitu_summa_indeksikorjattu rivi)
                   (:toteutunut_summa rivi)
                   (:erotus rivi)
                   (:prosentti rivi)]
            :lihavoi? (:lihavoi? rivi)})))

(defn- luo-excel-rivit [kustannusdata avain excel-nimi]
  (let [bud (get-in kustannusdata [:taulukon-rivit (keyword (str avain "-budjetoitu"))])
        bud-indeksikorjattu (get-in kustannusdata [:taulukon-rivit (keyword (str avain "-budjetoitu-indeksikorjattu"))])
        tot (get-in kustannusdata [:taulukon-rivit (keyword (str avain "-toteutunut"))])
        erotus (- tot bud-indeksikorjattu)
        prosentti (if (or (= 0M tot) (= 0M bud-indeksikorjattu))
                    0
                    (laske-prosentti tot bud-indeksikorjattu))
        tehtavat (listaa-pelkat-tehtavat (get-in kustannusdata [:taulukon-rivit (keyword (str avain)) :tehtavat]))]
    (concat
      [{:rivi [excel-nimi nil nil bud bud-indeksikorjattu tot erotus prosentti] :lihavoi? true}]
      (mapcat (fn [rivi]
                [{:rivi [(:paaryhma rivi)
                         (:toimenpide rivi)
                         (:tehtava_nimi rivi)
                         (:budjetoitu_summa rivi)
                         (:budjetoitu_summa_indeksikorjattu rivi)
                         (:toteutunut_summa rivi)
                         (:erotus rivi)
                         (:prosentti rivi)]}]) tehtavat))))

(defn- luo-excel-rivi-yhteensa [kustannusdata]
  (let [bud (get-in kustannusdata [:yhteensa :yht-budjetoitu-summa])
        bud-indeksikorjattu (get-in kustannusdata [:yhteensa :yht-budjetoitu-summa-indeksikorjattu])
        tot (get-in kustannusdata [:yhteensa :yht-toteutunut-summa])
        erotus (when (not= 0 bud) (- tot bud-indeksikorjattu))
        prosentti (if (or (= 0M tot) (= 0M bud-indeksikorjattu))
                    0
                    (laske-prosentti tot bud-indeksikorjattu))]
    [{:rivi ["Yhteensä" nil nil bud bud-indeksikorjattu tot erotus prosentti] :lihavoi? true}]))

(defn- luo-excel-rivi-vuoden-paatos [kustannusdata]
  (let [tavoitepalkkio (get-in kustannusdata [:taulukon-rivit :tavoitepalkkio])
        tavoitehinnan-ylitys (get-in kustannusdata [:taulukon-rivit :tavoitehinnan-ylitys])
        kattohinnan-ylitys (get-in kustannusdata [:taulukon-rivit :kattohinnan-ylitys])]
    (keep (fn [rivi]
           (when (:toimenpide rivi)
             {:rivi [(:toimenpide rivi) nil nil (:toimenpide-budjetoitu-summa rivi) nil (:toimenpide-toteutunut-summa rivi) nil nil]
              :lihavoi? true}))
      [tavoitepalkkio
       tavoitehinnan-ylitys
       kattohinnan-ylitys])))

(defn- luo-excel-rivi-lisatyot [rivi ensimmainen?]
  (if ensimmainen?
    {:rivi ["Lisätyöt" (:toimenpide rivi) (:tehtava_nimi rivi) nil nil (:toteutunut_summa rivi) nil nil] :lihavoi? true}
    [nil (:toimenpide rivi) (:tehtava_nimi rivi) nil nil (:toteutunut_summa rivi) nil nil]))

(defn kustannukset-excel
  [db workbook user {:keys [urakka-id urakka-nimi hoitokauden-alkuvuosi alkupvm loppupvm] :as tiedot}]
  (oikeudet/voi-lukea? oikeudet/urakat-toteumat-kokonaishintaisettyot urakka-id user)
  (let [kustannukset-tehtavittain (kustannusten-seuranta-q/listaa-kustannukset-paaryhmittain
                                    db {:urakka urakka-id
                                        :alkupvm alkupvm
                                        :loppupvm loppupvm
                                        :hoitokauden-alkuvuosi (int hoitokauden-alkuvuosi)})
        kustannusdata (kustannusten-seuranta/jarjesta-tehtavat kustannukset-tehtavittain)
        hankintakustannusten-toimenpiteet (rivita-toimenpiteet
                                            (get-in kustannusdata [:taulukon-rivit :hankintakustannukset])
                                            "Suunnitellut hankinnat")
        rahavarausten-toimenpiteet (rivita-toimenpiteet
                                     (get-in kustannusdata [:taulukon-rivit :rahavaraukset])
                                     "Rahavaraukset")
        hallintakorvausten-toimenpiteet (rivita-toimenpiteet
                                          (get-in kustannusdata [:taulukon-rivit :johto-ja-hallintakorvaus])
                                          "Johto- ja Hallintokorvaukset")
        lisatyot (rivita-lisatyot (get-in kustannusdata [:taulukon-rivit :lisatyot]) (get-in kustannusdata [:taulukon-rivit :lisatyot-summa]))
        sarakkeet [{:otsikko "Ryhmä"} {:otsikko "Toimenpide"} {:otsikko "Tehtavä"}
                   {:otsikko "Suunniteltu (€)" :fmt :raha} {:otsikko "Indeksikorjattu (€)" :fmt :raha}
                   {:otsikko "Toteuma (€)" :fmt :raha}
                   {:otsikko "Erotus (€)" :fmt :raha} {:otsikko "%" :fmt :prosentti}]
        optiot {:nimi urakka-nimi
                :sheet-nimi urakka-nimi
                :tyhja (if (empty? kustannukset-tehtavittain) "Ei kustannuksia valitulla aikavälillä.")}
        taulukot [[:taulukko optiot sarakkeet
                   (concat
                     (mapv #(luo-excel-rivi-toimenpiteelle % (if (= % (first hankintakustannusten-toimenpiteet))
                                                               true
                                                               false)) hankintakustannusten-toimenpiteet)
                     (mapv #(luo-excel-rivi-toimenpiteelle % (if (= % (first rahavarausten-toimenpiteet))
                                                               true
                                                               false)) rahavarausten-toimenpiteet)
                     (mapv #(luo-excel-rivi-toimenpiteelle % (if (= % (first hallintakorvausten-toimenpiteet))
                                                               true
                                                               false)) hallintakorvausten-toimenpiteet)
                     (luo-excel-rivit kustannusdata "hoidonjohdonpalkkio" "Hoidonjohdonpalkkio")
                     (luo-excel-rivit kustannusdata "erillishankinnat" "Erillishankinnat")
                     (luo-excel-rivit kustannusdata "tavoitehinnanoikaisu" "Tavoitehinnan oikaisut")
                     (luo-excel-rivit kustannusdata "siirto" "Siirto edelliseltä vuodelta")
                     (luo-excel-rivi-yhteensa kustannusdata)
                     (luo-excel-rivit kustannusdata "bonukset" "Tavoitehinnan ulkopuoliset rahavaraukset")
                     (luo-excel-rivi-vuoden-paatos kustannusdata)
                     (mapv (fn [rivi]
                             (luo-excel-rivi-lisatyot rivi (if (= rivi (first lisatyot))
                                                             true
                                                             false)))
                           lisatyot))]]
        taulukko (concat
                   [:raportti {:nimi (str urakka-nimi "_" alkupvm "-" loppupvm)
                               :raportin-yleiset-tiedot {:raportin-nimi "Kustannusten seuranta"
                                                         :urakka urakka-nimi
                                                         :alkupvm alkupvm
                                                         :loppupvm loppupvm}
                               :orientaatio :landscape}]
                   (if (empty? taulukot)
                     [[:taulukko optiot nil [["Ei kustannuksia valitulla aikavälillä"]]]]
                     taulukot))]
    (excel/muodosta-excel (vec taulukko)
                          workbook)))
