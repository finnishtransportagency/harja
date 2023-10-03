(ns harja.kyselyt.asiakastyytyvaisyysbonus-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clj-time.core :as t]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.palvelut.indeksit :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto :as laskutusyhteenveto]
            [harja.testi :refer :all]
            [harja.pvm :as pvm]))


(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (urakkatieto-alustus!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :indeksit (component/using
                                    (->Indeksit)
                                    [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (urakkatieto-lopetus!))

(use-fixtures :once jarjestelma-fixture)

(deftest laske-hoitourakan-indeksilaskennan-perusluku
  (let [ur @oulun-alueurakan-2014-2019-id

        perusluku-maku2005 (ffirst (q (str "select * from indeksilaskennan_perusluku(" ur ");")))]
    (is (= 104.4M perusluku-maku2005))))

(deftest laske-hoitokauden-asiakastyytyvaisyysbonus
  (let [ur @tampereen-alueurakan-2017-2022-id
        sop (hae-annetun-urakan-paasopimuksen-id ur)
        maksupvm (ffirst (q (str "select laskutuskuukausi from erilliskustannus
        WHERE tyyppi = 'asiakastyytyvaisyysbonus' AND rahasumma = 10000 AND sopimus = " sop)))
        ind_nimi (ffirst (q (str "select indeksin_nimi from erilliskustannus
        WHERE tyyppi = 'asiakastyytyvaisyysbonus' AND rahasumma = 10000 AND sopimus = " sop)))
        summa (ffirst (q (str "select rahasumma from erilliskustannus
        WHERE tyyppi = 'asiakastyytyvaisyysbonus' AND rahasumma = 10000 AND sopimus = " sop)))
        kyselyn-kautta (laskutusyhteenveto/laske-erilliskustannuksen-indeksit
                         (:db jarjestelma)
                         {:urakka-id ur
                          :pvm maksupvm
                          :laskutuskuukausi maksupvm
                          :indeksinimi ind_nimi
                          :summa summa
                          :erilliskustannustyyppi "asiakastyytyvaisyysbonus"
                          :pyorista? false})
        haku (format "select * from erilliskustannuksen_indeksilaskenta(
        '2017-10-15'::DATE,'%s',%s,%s, 'asiakastyytyvaisyysbonus', FALSE);", ind_nimi, summa, ur)
        bonarit (first (q haku))
        bonarit-jos-indekseja-ei-ole-syotetty (first (q (str "select * from laske_hoitokauden_asiakastyytyvaisyysbonus(" ur ",'2025-11-15'::DATE, '"
                                                             ind_nimi "', '"
                                                             summa "');")))]
    (testing "Testidatan Tampereen alueurakka 2017 - 2022 lasketaan oikein"
      ; Korotus on pienempi, koska vuodelta 2016 tuleva perusluku on tosi suuri.
      (is (= {:summa 10000M, :korotettuna 9773.25245522819179380000M, :korotus -226.74754477180820620000M} kyselyn-kautta))
      (is (= 10000M (first bonarit)) "bonari ilman korotusta")
      (is (=marginaalissa? 9773.25M (second bonarit)) "bonari korotuksen kera")
      (is (=marginaalissa? -226.74M (nth bonarit 2)) "bonarin korotus")
      (is (= [10000M nil nil] bonarit-jos-indekseja-ei-ole-syotetty)))))


(defn laske-bonus [urakka-id maksupvm indeksinimi summa]
  (let [haku (format "select * from erilliskustannuksen_indeksilaskenta(
        '%s'::DATE,'%s',%s,%s, 'asiakastyytyvaisyysbonus', FALSE);",maksupvm, indeksinimi, summa, urakka-id)
        tulos (first (q haku))]
    tulos))

(defspec muuta-bonuksen-maaraa
         100
         ;; Muuta bonuksen laskennassa käytettyjä arvoja
         ;; - summa
         ;; - indeksinimi
         ;; - maksupvm
         (let [ur @tampereen-alueurakan-2017-2022-id
               indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :indeksit +kayttaja-jvh+)
               double-laskujen-tarkkuus 0.015]

           (prop/for-all [summa (gen/fmap #(BigDecimal. %) (gen/double* {:min 100 :max 500000 :NaN? false}))
                          maksupvm (gen/elements [(pvm/->pvm "01.12.2017") (pvm/->pvm "01.12.2018")  (pvm/->pvm "01.7.2017")
                                                   (pvm/->pvm "01.12.2019")  (pvm/->pvm "01.12.2020")  (pvm/->pvm "01.12.2021")])]
                         (let [indeksinimi "MAKU 2010"
                               bonus (laske-bonus ur maksupvm indeksinimi summa)
                               perusluku (when indeksinimi
                                           (ffirst (q (str "select * from indeksilaskennan_perusluku(" ur ");"))))
                               palvelun-kautta (laskutusyhteenveto/laske-erilliskustannuksen-indeksit
                                                 (:db jarjestelma)
                                                 {:urakka-id ur
                                                  :pvm maksupvm
                                                  :laskutuskuukausi maksupvm
                                                  :indeksinimi indeksinimi
                                                  :summa summa
                                                  :erilliskustannustyyppi "asiakastyytyvaisyysbonus"
                                                  :pyorista? false})
                               hk-alkuvuosi (if (or (= 10 (pvm/kuukausi maksupvm))
                                                    (= 11 (pvm/kuukausi maksupvm))
                                                    (= 12 (pvm/kuukausi maksupvm)))
                                              (- (pvm/vuosi maksupvm) 0)
                                              (- (pvm/vuosi maksupvm) 1))
                               indeksit-alkuvuonna (vec (vals
                                                          (select-keys
                                                            (get indeksit [indeksinimi hk-alkuvuosi])
                                                            [10 11 12])))
                               indeksit-loppuvuonna (vec (vals (select-keys
                                                                 (get indeksit [indeksinimi (inc hk-alkuvuosi)])
                                                                 [1 2 3 4 5 6 7 8 9])))
                               indeksilukujen-lkm (count (concat indeksit-alkuvuonna indeksit-loppuvuonna))
                               indeksiluvut-loytyy-koko-hoitokaudelle? (= 12 indeksilukujen-lkm)
                               vertailuluku (when indeksiluvut-loytyy-koko-hoitokaudelle?
                                              (/ (apply + (concat indeksit-alkuvuonna indeksit-loppuvuonna)) indeksilukujen-lkm))

                               korotettuna (when (and indeksiluvut-loytyy-koko-hoitokaudelle? perusluku)
                                             (* summa (/ vertailuluku perusluku)))
                               kasin-laskettuna {:summa       summa
                                                 :korotettuna (if-not indeksinimi
                                                                summa
                                                                korotettuna)
                                                 :korotus     (if-not indeksinimi
                                                                0
                                                                (when korotettuna (- korotettuna summa)))}]
                           true
                           (if indeksiluvut-loytyy-koko-hoitokaudelle?
                            (do
                              (is (= (nth bonus 0) (:summa palvelun-kautta) (:summa kasin-laskettuna)) "summa")
                              (is (=marginaalissa? (nth bonus 1) (:korotettuna palvelun-kautta) double-laskujen-tarkkuus) "korotettuna")
                              (is (=marginaalissa? (nth bonus 1) (:korotettuna kasin-laskettuna) double-laskujen-tarkkuus) "korotettuna")
                              (is (=marginaalissa? (nth bonus 2) (:korotus palvelun-kautta) double-laskujen-tarkkuus) "korotus")
                              (is (=marginaalissa? (nth bonus 2) (:korotus kasin-laskettuna) double-laskujen-tarkkuus) "korotus"))
                            (do
                              (is (= (nth bonus 0) (:summa palvelun-kautta) (:summa kasin-laskettuna)) "summa")
                              (is (=marginaalissa? (nth bonus 1) (:korotettuna palvelun-kautta) double-laskujen-tarkkuus) "korotettuna")
                              (is (=marginaalissa? (nth bonus 2) (:korotus palvelun-kautta) double-laskujen-tarkkuus) "korotus")))))))
