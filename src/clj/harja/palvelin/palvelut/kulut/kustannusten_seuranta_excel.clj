(ns harja.palvelin.palvelut.kulut.kustannusten-seuranta-excel
  "Excelin luonti kustannus seuranta -datasta."
  (:require [clojure.string :as str]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.kyselyt.kustannusten-seuranta :as kustannusten-seuranta-q]
            [harja.kyselyt.urakat :as urakat-q]))


(defn- laske-prosentti [tot bud]
  (let [tot (bigdec tot)
        bud (bigdec bud)]
    (if (or (= (bigdec 0) tot) (= (bigdec 0) bud))
      0
      (* 100 (with-precision 4 (/ tot bud))))))

(defn- kokoa-toimenpiteen-alle
  "Kustannus seurannan ui ja tämä excel on rakennettu ajatukselle, että alimmaisen kolmannen tason tehtäviä (osa on tehtäväryhmiä)
  ei näytetä samalla tavalla kuin ylempiä tasoja. Eli budjetoituja summia ja sitä kautta erotusta ja prosentteja.
  Paitsi rahavaraukset ovat poikkeus.

  Siitä syystä tässä funktiossa tarkistetaan, että mikäli kolmannen tason rivi kuuluu rahavaraus -toimenpiteelle/pääryhmään,
  niin lasketaan erotukset ja prosentit.

  Muille näytetään vain toteutumat, kun se on se pääasiallinen tapa näyttää näitä kolmannen tason asioita."
  [toimenpide tehtavat toimenpideryhma yht-toteuma muutokset? muutostyon-erotus?]
  (concat
    (when (> (count tehtavat) 0)
      (mapcat
        (fn [tehtava]
          (let [toteutunut-summa (or (:toteutunut_summa tehtava) 0)
                budjetoitu-summa (or (:budjetoitu_summa tehtava) 0)
                budjetoitu-summa-indeksikorjattu (or (:budjetoitu_summa_indeksikorjattu tehtava) 0)
                erotus (- toteutunut-summa budjetoitu-summa-indeksikorjattu)
                prosentti (laske-prosentti toteutunut-summa budjetoitu-summa-indeksikorjattu)
                nayta-budjetoitu-summa? (or muutokset? (= "rahavaraus" (:toimenpideryhma tehtava)))]
            [{:paaryhma nil
              :toimenpide nil
              :tehtava_nimi (or (:muutostyo_syy tehtava) (:tehtava_nimi tehtava))
              :toteutunut_summa toteutunut-summa
              :budjetoitu_summa (when nayta-budjetoitu-summa? budjetoitu-summa)
              :budjetoitu_summa_indeksikorjattu (when nayta-budjetoitu-summa? budjetoitu-summa-indeksikorjattu)
              :erotus (when nayta-budjetoitu-summa? erotus)
              :prosentti (when nayta-budjetoitu-summa? prosentti)
              :muutostyon-erotus? muutostyon-erotus?
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

(defn- rivita-toimenpiteet
  "Kun kustannukset pitää saada kolmeen portaaseen (pääryhmä, toimenpide, tehtävä), niin tämä funktio tekee sen.
  Eli ensin pääryhmät, sitten toimenpiteet ja lopuksi tehtävät. UI:lla ryhmät ovat avattavia. Excelissä joudutaan
  listaamaan kaikki vain auki."
  [toimenpiteet paaryhma]
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
                                             rahavaraus-tehtavat)
                        arvonvahennys-tehtavat (filter #(= "arvonvahennykset" (:toimenpideryhma %)) (:tehtavat toimenpide))
                        arvonvahennys-toteuma (reduce (fn [summa rivi]
                                                        (+ (or summa 0) (or (:toteutunut_summa rivi) 0)))
                                                0
                                                arvonvahennys-tehtavat)
                        muutostyon-erotus? (and
                                             (= paaryhma "Muutokset")
                                             (= (:toimenpide toimenpide) "Muutostyöt (erillisrahoitetut)"))]
                    (concat [{:paaryhma paaryhma
                              :toimenpide (:toimenpide toimenpide)
                              :tehtava_nimi nil
                              :toteutunut_summa toimenpide-tot
                              :budjetoitu_summa toimenpide-bud
                              :budjetoitu_summa_indeksikorjattu toimenpide-bud-indeksikorjattu
                              :erotus erotus
                              :prosentti (laske-prosentti toimenpide-tot toimenpide-bud-indeksikorjattu)
                              :lihavoi? true}]
                      (kokoa-toimenpiteen-alle toimenpide hankinta-tehtavat "Hankinnat" hankinta-toteuma (= paaryhma "Muutokset") muutostyon-erotus?)
                      (kokoa-toimenpiteen-alle toimenpide rahavaraus-tehtavat "Rahavaraus" rahavaraus-toteuma (= paaryhma "Muutokset") muutostyon-erotus?)
                      (kokoa-toimenpiteen-alle toimenpide palkka-tehtavat "Palkat" palkka-toteumat (= paaryhma "Muutokset") muutostyon-erotus?)
                      (kokoa-toimenpiteen-alle toimenpide toimistokulu-tehtavat "Toimistokulu" toimistokulu-toteuma (= paaryhma "Muutokset") muutostyon-erotus?)
                      (kokoa-toimenpiteen-alle toimenpide arvonvahennys-tehtavat "Arvonvähennykset" arvonvahennys-toteuma (= paaryhma "Muutokset") muutostyon-erotus?))))
                toimenpiteet)
        toimenpide-rivit (mapv
                           #(assoc % :muutokset? (= paaryhma "Muutokset")
                              :arvonvahennykset? (= paaryhma "Arvonvähennykset"))
                           toimenpide-rivit)
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
                         :muutokset? (= paaryhma "Muutokset")
                         :arvonvahennykset? (= paaryhma "Arvonvähennykset")
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
            :muutokset nil
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
                :muutokset nil
                :erotus nil
                :prosentti nil
                :lihavoi? true}])
            lisatyot)))

(defn- luo-excel-rivi-toimenpiteelle [rivi ensimmainen?]
  (let [budjetti-muutoksiin? (or
                               (= (:paaryhma rivi) "Muutokset")
                               (= (:paaryhma rivi) "Arvonvähennykset")
                               (:muutokset? rivi)
                               (:arvonvahennykset? rivi))
     arvonvahennykset? (or
                         (= (:paaryhma rivi) "Arvonvähennykset")
                         (:arvonvahennykset? rivi))
     tavoitehinnan-muutos (if arvonvahennykset?
                            (:toteutunut_summa rivi)
                            (:budjetoitu_summa rivi))
     toteutunut-summa (or (:toteutunut_summa rivi) 0)
     nayta-toteuma? (or (not budjetti-muutoksiin?) arvonvahennykset? (not (zero? toteutunut-summa)))
     nayta-erotus? (cond
                     (:muutostyon-erotus? rivi)
                     true

                     (and budjetti-muutoksiin? (= (:toimenpide rivi) "Muutostyöt (erillisrahoitetut)"))
                     true

                     budjetti-muutoksiin?
                     false

                     :else
                     true)]
    
    (if ensimmainen?
    {:rivi [(:paaryhma rivi)
            (:toimenpide rivi)
            (:tehtava_nimi rivi)
            (when-not budjetti-muutoksiin? (:budjetoitu_summa rivi))
            (when-not budjetti-muutoksiin? (:budjetoitu_summa_indeksikorjattu rivi))
            (when budjetti-muutoksiin? tavoitehinnan-muutos)
            (when nayta-toteuma? (:toteutunut_summa rivi))
            (when nayta-erotus? (:erotus rivi))
            (when nayta-erotus? (:prosentti rivi))]
     :lihavoi? true}
    (merge {:rivi [nil
                   (:toimenpide rivi)
                   (:tehtava_nimi rivi)
                   (when-not budjetti-muutoksiin? (:budjetoitu_summa rivi))
                   (when-not budjetti-muutoksiin? (:budjetoitu_summa_indeksikorjattu rivi))
                   (when budjetti-muutoksiin? tavoitehinnan-muutos)
                   (when nayta-toteuma? (:toteutunut_summa rivi))
                   (when nayta-erotus? (:erotus rivi))
                   (when nayta-erotus? (:prosentti rivi))]
            :lihavoi? false}))))

(defn- luo-excel-rivit [kustannusdata avain excel-nimi budjetti-muutoksiin?]
  (let [bud (get-in kustannusdata [:taulukon-rivit (keyword (str avain "-budjetoitu"))])
        bud-indeksikorjattu (get-in kustannusdata [:taulukon-rivit (keyword (str avain "-budjetoitu-indeksikorjattu"))])
        tot (get-in kustannusdata [:taulukon-rivit (keyword (str avain "-toteutunut"))])
        erotus (- tot bud-indeksikorjattu)
        prosentti (if (or (= 0M tot) (= 0M bud-indeksikorjattu))
                    0
                    (laske-prosentti tot bud-indeksikorjattu))
        tehtavadata (get-in kustannusdata
                      [:taulukon-rivit
                       (keyword avain)
                       :tehtavat])
        tehtavat (listaa-pelkat-tehtavat tehtavadata)
        tavoitehinnan-oikaisu? (= avain "tavoitehinnanoikaisu")
        arvonvahennykset? (= avain "arvonvahennykset")
        nayta-rivi? (or (not tavoitehinnan-oikaisu?)
                      (seq tehtavadata))
        muutokset-sarakkeeseen? (or budjetti-muutoksiin? tavoitehinnan-oikaisu?)
        muutokseksi-vietava-summa (if tavoitehinnan-oikaisu? bud (if budjetti-muutoksiin? tot bud))
        nayta-toteuma? (or (not muutokset-sarakkeeseen?) arvonvahennykset?)
        nayta-erotus? (not muutokset-sarakkeeseen?)]
    (when nayta-rivi?
      (concat
        [{:rivi [excel-nimi
                 nil
                 nil
                 (when-not muutokset-sarakkeeseen?
                   bud)
                 (when-not muutokset-sarakkeeseen?
                   bud-indeksikorjattu)
                 (when muutokset-sarakkeeseen?
                   muutokseksi-vietava-summa)
                 (when nayta-toteuma? tot)
                 (when nayta-erotus? erotus)
                 (when nayta-erotus? prosentti)]
          :lihavoi? true}]
        (mapcat (fn [rivi]
                  [{:rivi [(:paaryhma rivi)
                           (:toimenpide rivi)
                           (:tehtava_nimi rivi)
                           (when-not muutokset-sarakkeeseen?
                             (:budjetoitu_summa rivi))
                           (when-not muutokset-sarakkeeseen?
                             (:budjetoitu_summa_indeksikorjattu rivi))
                           (when muutokset-sarakkeeseen?
                             (if budjetti-muutoksiin?
                               (:toteutunut_summa rivi)
                               (:budjetoitu_summa rivi)))
                           (when nayta-toteuma? (:toteutunut_summa rivi))
                           (when nayta-erotus? (:erotus rivi))
                           (when nayta-erotus? (:prosentti rivi))]}]) tehtavat)))))

(defn- luo-excel-rivi-yhteensa [kustannusdata muutosten-hallinta-kaytossa?]
  (let [bud (get-in kustannusdata [:yhteensa :yht-budjetoitu-summa-ilman-muutoksia])
        bud-indeksikorjattu (get-in kustannusdata [:yhteensa :yht-budjetoitu-summa-indeksikorjattu-ilman-muutoksia])
        arvonvahennykset-toteutunut (or
                                      (get-in kustannusdata [:taulukon-rivit :arvonvahennykset-toteutunut])
                                      0)
        tot (get-in kustannusdata [:yhteensa :yht-toteutunut-summa])
        tot-ilman-arvonvahennyksia (- tot arvonvahennykset-toteutunut)
        muutokset-budjetoitu (or
                               (get-in kustannusdata [:taulukon-rivit :muutokset-budjetoitu])
                               0)
        tavoitehinnanoikaisu-budjetoitu (or
                                          (get-in kustannusdata [:taulukon-rivit :tavoitehinnanoikaisu-budjetoitu])
                                          0)
        erotus (-
                 tot-ilman-arvonvahennyksia
                 bud-indeksikorjattu
                 muutokset-budjetoitu)
        vertailubudjetti (+
                           bud-indeksikorjattu
                           muutokset-budjetoitu)
        prosentti (if (or (= 0M tot-ilman-arvonvahennyksia) (= 0M vertailubudjetti))
                    0
                    (laske-prosentti tot-ilman-arvonvahennyksia vertailubudjetti))
        muutokset (reduce + 0
                    (cond-> [(or (get-in kustannusdata [:taulukon-rivit :arvonvahennykset-toteutunut]) 0)
                             (or (get-in kustannusdata [:taulukon-rivit :tavoitehinnanoikaisu-budjetoitu]) 0)]
                      muutosten-hallinta-kaytossa? (conj (or (get-in kustannusdata [:taulukon-rivit :muutokset-budjetoitu]) 0))))]
    [{:rivi ["Yhteensä" nil nil bud bud-indeksikorjattu muutokset tot erotus prosentti] :lihavoi? true}]))

(defn- luo-excel-rivi-vuoden-paatos [kustannusdata]
  (let [tavoitepalkkio (get-in kustannusdata [:taulukon-rivit :tavoitepalkkio])
        tavoitehinnan-ylitys (get-in kustannusdata [:taulukon-rivit :tavoitehinnan-ylitys])
        kattohinnan-ylitys (get-in kustannusdata [:taulukon-rivit :kattohinnan-ylitys])]
    (keep (fn [rivi]
           (when (:toimenpide rivi)
             {:rivi [(:toimenpide rivi) nil nil (:toimenpide-budjetoitu-summa rivi)
                     nil nil (:toimenpide-toteutunut-summa rivi) nil nil]
              :lihavoi? true}))
      [tavoitepalkkio
       tavoitehinnan-ylitys
       kattohinnan-ylitys])))

(defn- luo-excel-rivi-lisatyot [rivi ensimmainen?]
  (if ensimmainen?
    {:rivi ["Lisätyöt" (:toimenpide rivi) (:tehtava_nimi rivi)
            nil nil nil (:toteutunut_summa rivi) nil nil] :lihavoi? true}
    [nil (:toimenpide rivi) (:tehtava_nimi rivi) nil nil nil (:toteutunut_summa rivi) nil nil]))

(defn kustannukset-excel
  [db workbook user {:keys [urakka-id urakka-nimi hoitokauden-alkuvuosi alkupvm loppupvm] :as tiedot}]
  (oikeudet/voi-lukea? oikeudet/urakat-toteumat-kokonaishintaisettyot urakka-id user)
  (let [kustannukset-tehtavittain (kustannusten-seuranta-q/listaa-kustannukset-paaryhmittain
                                    db {:urakka urakka-id
                                        :alkupvm alkupvm
                                        :loppupvm loppupvm
                                        :hoitokauden-alkuvuosi (int hoitokauden-alkuvuosi)})
        urakan-sopimustyyppi (keyword (:sopimustyyppi (first (urakat-q/hae-urakan-tiedot db {:id urakka-id}))))
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id}))
        muutosten-hallinta-kaytossa? (boolean (:muutosten_hallinta urakan-parametrit))
        kustannusdata (kustannusten-seuranta/jarjesta-tehtavat kustannukset-tehtavittain urakan-sopimustyyppi)
        hankintakustannusten-toimenpiteet (rivita-toimenpiteet
                                            (get-in kustannusdata [:taulukon-rivit :hankintakustannukset])
                                            "Kilpailutettavat hankinnat")
        rahavarausten-toimenpiteet (rivita-toimenpiteet
                                     (get-in kustannusdata [:taulukon-rivit :rahavaraukset])
                                     "Rahavaraukset")
        muutosten-toimenpiteet (when muutosten-hallinta-kaytossa?
                                 (rivita-toimenpiteet
                                   (get-in kustannusdata [:taulukon-rivit :muutokset])
                                   "Muutokset"))
        arvonvahennykset-rivit (get-in kustannusdata [:taulukon-rivit :arvonvahennykset])
        ;; MHU25+ urakoilla arvonvähennykset tulevat toimenpidetasoisena rakenteena (:tehtavat löytyy riveiltä)
        arvonvahennykset-kolmiportainen? (and (sequential? arvonvahennykset-rivit)
                                              (some #(contains? % :tehtavat) arvonvahennykset-rivit))
        arvonvahennysten-toimenpiteet (when arvonvahennykset-kolmiportainen?
                                    (rivita-toimenpiteet
                                      arvonvahennykset-rivit
                                      "Arvonvähennykset"))

        lisatyot (rivita-lisatyot (get-in kustannusdata [:taulukon-rivit :lisatyot]) (get-in kustannusdata [:taulukon-rivit :lisatyot-summa]))
        sarakkeet [{:otsikko "Ryhmä"} {:otsikko "Toimenpide"} {:otsikko "Tehtavä"}
                   {:otsikko "Hoitovuoden alun suunnitelma (€)" :fmt :raha}
                   {:otsikko "Hoitovuoden alun suunnitelma, indeksikorjattu (€)" :fmt :raha}
                   {:otsikko "Tavoitehinnan muutokset (€)" :fmt :raha}
                   {:otsikko "Toteuma (€)" :fmt :raha}
                   {:otsikko "Alitus/ylitys (€)" :fmt :raha} {:otsikko "%" :fmt :prosentti}]
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
                     (luo-excel-rivit kustannusdata "erillishankinnat" "Erillishankinnat" false)
                     (luo-excel-rivit kustannusdata "johto-ja-hallintokorvaus" "Johto- ja Hallintokorvaus" false)
                     (luo-excel-rivit kustannusdata "hoidonjohdonpalkkio" "Hoidonjohdonpalkkio" false)
                     (when muutosten-hallinta-kaytossa?
                       (mapv #(luo-excel-rivi-toimenpiteelle
                                %
                                (= % (first muutosten-toimenpiteet)))
                         muutosten-toimenpiteet))
                     ;; Jos data on kolmikerroksinen, käytetään toimenpidetasoista rivitystä
                     (if arvonvahennykset-kolmiportainen?
                       (mapv #(luo-excel-rivi-toimenpiteelle % (if (= % (first arvonvahennysten-toimenpiteet))
                                                                 true
                                                                 false)) arvonvahennysten-toimenpiteet)
                       (luo-excel-rivit kustannusdata "arvonvahennykset" "Arvonvahennykset" true))
                     (luo-excel-rivit kustannusdata "tavoitehinnanoikaisu" "Tavoitehinnan muutokset" false)
                     (luo-excel-rivit kustannusdata "muukulu-tavoitehintainen" "Muut kulut" false)
                     (luo-excel-rivit kustannusdata "siirto" "Siirto edelliseltä vuodelta" false)
                     (luo-excel-rivi-yhteensa kustannusdata muutosten-hallinta-kaytossa?)
                     (luo-excel-rivit kustannusdata "ulkopuoliset-rahavaraukset" "Tavoitehinnan ulkopuoliset rahavaraukset" false)
                     (luo-excel-rivit kustannusdata "bonukset" "Bonukset" false)
                     (luo-excel-rivit kustannusdata "sanktiot" "Sanktiot" false)
                     (luo-excel-rivit kustannusdata "muukulu-eitavoitehintainen" "Muut kulut" false)
                     (mapv (fn [rivi]
                             (luo-excel-rivi-lisatyot rivi (if (= rivi (first lisatyot))
                                                             true
                                                             false)))
                       lisatyot)
                     (luo-excel-rivi-vuoden-paatos kustannusdata))]]
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
