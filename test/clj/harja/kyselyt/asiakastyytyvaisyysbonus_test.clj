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
  (let [ur @oulun-alueurakan-2014-2019-id
        sop @oulun-alueurakan-2014-2019-paasopimuksen-id
        maksupvm (ffirst (q (str "select pvm from erilliskustannus
        WHERE tyyppi = 'asiakastyytyvaisyysbonus' AND rahasumma = 1000 AND sopimus = " sop)))
        _ (log/debug "maksupvm" maksupvm)
        ind_nimi (ffirst (q (str "select indeksin_nimi from erilliskustannus
        WHERE tyyppi = 'asiakastyytyvaisyysbonus' AND rahasumma = 1000 AND sopimus = " sop)))
        summa (ffirst (q (str "select rahasumma from erilliskustannus
        WHERE tyyppi = 'asiakastyytyvaisyysbonus' AND rahasumma = 1000 AND sopimus = " sop)))
        kyselyn-kautta (laskutusyhteenveto/laske-asiakastyytyvaisyysbonus
                         (:db jarjestelma)
                         {:urakka-id   ur
                          :maksupvm    maksupvm
                          :indeksinimi ind_nimi
                          :summa       summa})
        bonarit (first (q (str "select * from laske_hoitokauden_asiakastyytyvaisyysbonus(" ur ",'2015-11-15'::DATE, '"
                               ind_nimi "', '"
                               summa "');")))
        _ (println "bonarit " bonarit " ind nimi " ind_nimi " summa " summa)
        bonarit-jos-indekseja-ei-ole-syotetty (first (q (str "select * from laske_hoitokauden_asiakastyytyvaisyysbonus(" ur ",'2025-11-15'::DATE, '"
                                                             ind_nimi "', '"
                                                             summa "');")))]
    (testing "Testidatan Oulun alueurakka 2014 - 2019 lasketaan oikein"
      (is {:summa 1000M, :korotettuna 1050.1666666666667000M, :korotus 50.1666666666667000M} kyselyn-kautta)
      (is (= 1000M (first bonarit)) "bonari ilman korotusta")
      (is (=marginaalissa? 1297.97M (second bonarit)) "bonari korotuksen kera")
      (is (=marginaalissa? 297.97M (nth bonarit 2)) "bonarin korotus")
      (is (= [1000M nil nil] bonarit-jos-indekseja-ei-ole-syotetty)))))



(defn laske-bonus [urakka-id maksupvm indeksinimi summa]
  (first (q
           (str "select * from laske_hoitokauden_asiakastyytyvaisyysbonus("
                urakka-id ",
                '"
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
         (let [ur @oulun-alueurakan-2014-2019-id
               indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :indeksit +kayttaja-jvh+)
               double-laskujen-tarkkuus 0.015]

           (prop/for-all [summa (gen/fmap #(BigDecimal. %) (gen/double* {:min 100 :max 500000 :NaN? false}))
                          maksupvm (gen/elements [(pvm/->pvm "01.12.2015") (pvm/->pvm "01.12.2014") (pvm/->pvm "01.7.2014")
                                                  (pvm/->pvm "01.12.2016") (pvm/->pvm "01.12.2017") (pvm/->pvm "01.12.2018")])]
                         (let [indeksinimi "MAKU 2005"
                               bonus (laske-bonus ur
                                                  maksupvm
                                                  indeksinimi
                                                  summa)
                               perusluku (when indeksinimi
                                           (ffirst (q (str "select * from indeksilaskennan_perusluku(" ur ");"))))
                               palvelun-kautta (laskutusyhteenveto/laske-asiakastyytyvaisyysbonus
                                                 (:db jarjestelma)
                                                 {:urakka-id   ur
                                                  :maksupvm    maksupvm
                                                  :indeksinimi indeksinimi
                                                  :summa       summa})
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
                           (if
                             (and (or
                                    (not indeksiluvut-loytyy-koko-hoitokaudelle?)
                                    (= maksupvm (pvm/->pvm "01.12.2016"))
                                    (= maksupvm (pvm/->pvm "01.12.2017"))
                                    (= maksupvm (pvm/->pvm "01.12.2018")))
                                  (some? indeksinimi))
                             ;; jos indeksilukuja ei löydy kaikille kuukausille, odotetaan summa = summa, korotettuna ja korotus = nil
                             (do
                               (is (= (nth bonus 0) (:summa palvelun-kautta) (:summa kasin-laskettuna)) "summa")
                               (is (= (nth bonus 1) (:korotettuna palvelun-kautta) nil) "korotettuna")
                               (is (= (nth bonus 2) (:korotus palvelun-kautta) nil) "korotus"))
                             (do
                               (is (= (nth bonus 0) (:summa palvelun-kautta) (:summa kasin-laskettuna)) "summa")
                               (is (= (nth bonus 1) (:korotettuna palvelun-kautta)) "korotettuna")

                               ;; jos indeksinimi on nil, korotus on 0M tai 0, emme vertaile sitä
                               (if indeksinimi
                                 (do
                                   (is (= (nth bonus 2) (:korotus palvelun-kautta)) "korotus")
                                   (is (=marginaalissa? (:korotus kasin-laskettuna) (:korotus palvelun-kautta) double-laskujen-tarkkuus) "korotus")))
                               ;; double epätarkkuus sallitaan käsin laskettuihin verratessa
                               (is (=marginaalissa? (:korotettuna kasin-laskettuna) (:korotettuna palvelun-kautta) double-laskujen-tarkkuus) "korotettuna")))))))
