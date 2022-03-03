(ns harja.palvelin.palvelut.tiemerkinta-test
  (:require [clojure.test :refer :all]


            [harja.palvelin.palvelut
             [yllapitokohteet :refer :all]]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :refer :all]

            [harja.testi :refer :all]
            [harja.pvm :as pvm]
            [harja.domain.tiemerkinta :as tm-domain]))

(deftest laske-tiemerkinnan-kesto-maalivaatimustie
  (is (= tm-domain/tiemerkinnan-kesto-pitka (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
              {:merkinta "maali" :jyrsinta "ei jyrsintää"})
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "maali" :jyrsinta "keski"})
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "maali" :jyrsinta "reuna"})
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "maali" :jyrsinta "keski- ja reuna"})
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "maali" :jyrsinta nil}))
      "kesto-maalivaatimustiellä oltava 21vrk"))

(deftest laske-tiemerkinnan-kesto-jyrsintaa
  (is (= tm-domain/tiemerkinnan-kesto-pitka
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "massa" :jyrsinta "keski"})
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "maali" :jyrsinta "keski"})
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "muu" :jyrsinta "keski"})
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta nil :jyrsinta "reuna"})
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta nil :jyrsinta "keski- ja reuna"}))
      "jyrsintäkohteella keston oltava 21vrk"))

(deftest laske-tiemerkinnan-kesto-massavaatimustie-mutta-ei-jyrsintaa
  (is (= tm-domain/tiemerkinnan-kesto-lyhyt
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "massa" :jyrsinta "ei jyrsintää"})
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "massa" :jyrsinta nil}))
      "jos massavaatimustie ilman jyrsintää, keston oltava 14vrk"))

(deftest laske-tiemerkinnan-kesto-merkinta-muu-ei-jyrsintaa
  (is (= tm-domain/tiemerkinnan-kesto-lyhyt
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "muu" :jyrsinta "ei jyrsintää"})
         (tm-domain/tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
           {:merkinta "muu" :jyrsinta nil}))
      "jos muu merkintä ilman jyrsintää, keston oltava 14vrk"))

(defn luo-kohde-testia-varten [valmis-tiemerkintaan-pvm]
  {:valmis-tiemerkintaan valmis-tiemerkintaan-pvm})

(deftest laske-tiemerkinnan-keston-alkupvm-ma-to-tai-su
  (let [maanantai (luo-kohde-testia-varten (pvm/->pvm "28.2.2022"))
        tiistai (luo-kohde-testia-varten (pvm/->pvm "1.3.2022"))
        keskiviikko (luo-kohde-testia-varten (pvm/->pvm "2.3.2022"))
        torstai (luo-kohde-testia-varten (pvm/->pvm "3.3.2022"))
        perjantai (luo-kohde-testia-varten (pvm/->pvm "4.3.2022"))
        lauantai (luo-kohde-testia-varten (pvm/->pvm "5.3.2022"))
        sunnuntai (luo-kohde-testia-varten (pvm/->pvm "6.3.2022"))]
    (is (= (tm-domain/tiemerkinnan-keston-alkupvm maanantai)
           (pvm/->pvm "1.3.2022")))
    (is (= (tm-domain/tiemerkinnan-keston-alkupvm tiistai)
           (pvm/->pvm "2.3.2022")))
    (is (= (tm-domain/tiemerkinnan-keston-alkupvm keskiviikko)
           (pvm/->pvm "3.3.2022")))
    (is (= (tm-domain/tiemerkinnan-keston-alkupvm torstai)
           (pvm/->pvm "4.3.2022")))
    (is (= (tm-domain/tiemerkinnan-keston-alkupvm perjantai)
           (pvm/->pvm "7.3.2022")))
    (is (= (tm-domain/tiemerkinnan-keston-alkupvm lauantai)
           (pvm/->pvm "7.3.2022")))
    (is (= (tm-domain/tiemerkinnan-keston-alkupvm sunnuntai)
           (pvm/->pvm "7.3.2022")))))
