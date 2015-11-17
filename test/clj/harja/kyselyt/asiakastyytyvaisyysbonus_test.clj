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
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :indeksit (component/using
                                    (->Indeksit)
                                    [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(deftest laske-hoitokauden-asiakastyytyvaisyysbonus
  (let [sop @oulun-alueurakan-2014-2019-paasopimuksen-id
        maksupvm (ffirst (q (str "select pvm from erilliskustannus
        where tyyppi  = 'asiakastyytyvaisyysbonus' AND sopimus = " sop)))
        ind_nimi (ffirst (q (str "select indeksin_nimi from erilliskustannus
        where tyyppi  = 'asiakastyytyvaisyysbonus' AND sopimus = " sop)))
        summa (ffirst (q (str "select rahasumma from erilliskustannus
        where tyyppi  = 'asiakastyytyvaisyysbonus' AND sopimus = " sop)))
        kyselyn-kautta (laskutusyhteenveto/laske-asiakastyytyvaisyysbonus
                         (:db jarjestelma)
                         {:maksupvm    maksupvm
                          :indeksinimi ind_nimi
                          :summa       summa})
        bonarit (first (q (str "select * from laske_hoitokauden_asiakastyytyvaisyysbonus('2015-11-15'::DATE, '"
                               ind_nimi "', '"
                               summa "');")))
        bonarit-jos-indekseja-ei-ole-syotetty (first (q (str "select * from laske_hoitokauden_asiakastyytyvaisyysbonus('2025-11-15'::DATE, '"
                                                             ind_nimi "', '"
                                                             summa "');")))]
    (testing "Testidatan Oulun alueurakka 2014 - 2019 lasketaan oikein"
      (is {:summa 1000M, :korotettuna 1050.1666666666667000M, :korotus 50.1666666666667000M} kyselyn-kautta)
      (is (= 1000M (first bonarit)) "bonari ilman korotusta")
      (is (= 1050.1666666666667000M (second bonarit)) "bonari korotuksen kera")
      (is (= 50.1666666666667000M (nth bonarit 2)) "bonarin korotus")
      (is (= [1000M nil nil] bonarit-jos-indekseja-ei-ole-syotetty)))))



(defn laske-bonus [maksupvm indeksinimi summa]
  (first (q
           (str "select * from laske_hoitokauden_asiakastyytyvaisyysbonus('"
                maksupvm "'::DATE,"
                (if indeksinimi
                  (str "'" indeksinimi "', '")
                  (str "null, '"))
                summa "');"))))

(defspec muuta-bonuksen-maaraa
         100
         ;; Muuta bonuksen laskennassa käytettyjä arvoja
         ;; - summa
         ;; - indeksinimi
         ;; - maksupvm
         (let [indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :indeksit +kayttaja-tero+)
               double-laskujen-tarkkuus 0.015]

           (prop/for-all [summa (gen/fmap #(BigDecimal. %) (gen/double* {:min 100 :max 500000 :NaN? false}))
                          indeksinimi (gen/elements ["MAKU 2005" "MAKU 2010" nil])
                          maksupvm (gen/elements [(pvm/->pvm "01.12.2015") (pvm/->pvm "01.12.2014") (pvm/->pvm "01.7.2014")
                                                  (pvm/->pvm "01.12.2016") (pvm/->pvm "01.12.2017") (pvm/->pvm "01.12.2018")])]

                         (let [bonus (laske-bonus maksupvm
                                                  indeksinimi
                                                  summa)
                               kyselyn-kautta (laskutusyhteenveto/laske-asiakastyytyvaisyysbonus
                                                (:db jarjestelma)
                                                {:maksupvm    maksupvm
                                                 :indeksinimi indeksinimi
                                                 :summa       summa})
                               hk-alkuvuosi (if (or (= 10 (pvm/kuukausi maksupvm))
                                                    (= 11 (pvm/kuukausi maksupvm))
                                                    (= 12 (pvm/kuukausi maksupvm)))
                                              (- (pvm/vuosi maksupvm) 1)
                                              (- (pvm/vuosi maksupvm) 2))
                               indeksit-alkuvuonna (vec (vals
                                                          (select-keys
                                                            (get indeksit [indeksinimi hk-alkuvuosi])
                                                            [10 11 12])))

                               indeksit-loppuvuonna (vec (vals (select-keys
                                                                 (get indeksit [indeksinimi (inc hk-alkuvuosi)])
                                                                 [1 2 3 4 5 6 7 8 9])))
                               indeksilukujen-lkm (count (concat indeksit-alkuvuonna indeksit-loppuvuonna))
                               indeksiluvut-loytyy-koko-hoitokaudelle? (= 12 indeksilukujen-lkm)
                               indeksilukujen-ka-hoitokaudella (when indeksiluvut-loytyy-koko-hoitokaudelle?
                                                                 (/ (apply + (concat indeksit-alkuvuonna indeksit-loppuvuonna)) indeksilukujen-lkm))

                               korotettuna (when indeksiluvut-loytyy-koko-hoitokaudelle?
                                         (* summa (/ indeksilukujen-ka-hoitokaudella 100)))
                               kasin-laskettuna {:summa       summa
                                                 :korotettuna (if-not indeksinimi
                                                                summa
                                                                korotettuna)
                                                 :korotus     (if-not indeksinimi
                                                                0
                                                              (when korotettuna (- korotettuna summa)))}]
                           (if
                             (and (or
                                    (not indeksiluvut-loytyy-koko-hoitokaudelle?)
                                    (= maksupvm (pvm/->pvm "01.12.2016"))
                                    (= maksupvm (pvm/->pvm "01.12.2017"))
                                    (= maksupvm (pvm/->pvm "01.12.2018")))
                                  (some? indeksinimi))
                             ;; jos indeksilukuja ei löydy kaikille kuukausille, odotetaan summa = summa, korotettuna ja korotus = nil
                             (do
                               (is (= (nth bonus 0) (:summa kyselyn-kautta) (:summa kasin-laskettuna)) "summa")
                               (is (= (nth bonus 1) (:korotettuna kyselyn-kautta) nil) "korotettuna")
                               (is (= (nth bonus 2) (:korotus kyselyn-kautta) nil) "korotus"))
                             (do
                               (is (= (nth bonus 0) (:summa kyselyn-kautta) (:summa kasin-laskettuna)) "summa")
                               (is (= (nth bonus 1) (:korotettuna kyselyn-kautta)) "korotettuna")
                               (is (= (nth bonus 2) (:korotus kyselyn-kautta)) "korotus")
                               ;; double epätarkkuus sallitaan käsin laskettuihin verratessa
                               (is (=marginaalissa? (:korotettuna kasin-laskettuna) (:korotettuna kyselyn-kautta) double-laskujen-tarkkuus) "korotettuna")
                               (is (=marginaalissa? (:korotus kasin-laskettuna) (:korotus kyselyn-kautta) double-laskujen-tarkkuus) "korotus")))))))