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


(def testikohde-maali-ei-jyrsintaa {:merkinta "maali" :jyrsinta "ei jyrsintää"
                                    :valmis-tiemerkintaan (pvm/->pvm "1.3.2022")})
(def testikohde-maali-ei-jyrsintaa-helatorstain-yli {:merkinta "maali" :jyrsinta "ei jyrsintää"
                                                     :valmis-tiemerkintaan (pvm/->pvm "23.5.2022")})
(def testikohde-jyrsinta {:merkinta "muu" :jyrsinta "keski"
                          :valmis-tiemerkintaan (pvm/->pvm "1.3.2022")})
(def testikohde-jyrsinta-juhannuksen-yli {:merkinta "muu" :jyrsinta "keski"
                                          :valmis-tiemerkintaan (pvm/->pvm "20.6.2022")})
(def testikohde-jyrsinta-juhannuksen-yli-valmistuu-sunnuntaina {:merkinta "muu" :jyrsinta "keski"
                                                                :valmis-tiemerkintaan (pvm/->pvm "19.6.2022")})
(def testikohde-jyrsinta-juhannuksen-yli-valmistuu-lauantaina {:merkinta "muu" :jyrsinta "keski"
                                                               :valmis-tiemerkintaan (pvm/->pvm "18.6.2022")})
(def testikohde-jyrsinta-juhannuksen-yli-valmistuu-perjantaina {:merkinta "muu" :jyrsinta "keski"
                                                               :valmis-tiemerkintaan (pvm/->pvm "17.6.2022")})
(def testikohde-jyrsinta-vapun-yli {:merkinta "muu" :jyrsinta "keski"
                                    ;; 2023 1.5. on maanantai
                                    :valmis-tiemerkintaan (pvm/->pvm "27.4.2023")})
(def testikohde-massa-ei-jyrsintaa {:merkinta "massa" :jyrsinta "ei jyrsintää"
                                    :valmis-tiemerkintaan (pvm/->pvm "1.3.2022")})

(deftest laske-tiemerkinnan-takaraja-test
  (let [maali-ei-jyrsintaa (tm-domain/laske-tiemerkinnan-takaraja
                         testikohde-maali-ei-jyrsintaa)
        maali-ei-jyrsintaa-helatorstain-yli (tm-domain/laske-tiemerkinnan-takaraja
                                              testikohde-maali-ei-jyrsintaa-helatorstain-yli)
        jyrsinta (tm-domain/laske-tiemerkinnan-takaraja
                   testikohde-jyrsinta)
        jyrsinta-juhannuksen-yli (tm-domain/laske-tiemerkinnan-takaraja
                                   testikohde-jyrsinta-juhannuksen-yli)
        jyrsinta-juhannuksen-yli-valmistuu-sunnuntaina (tm-domain/laske-tiemerkinnan-takaraja
                                                         testikohde-jyrsinta-juhannuksen-yli-valmistuu-sunnuntaina)
        jyrsinta-juhannuksen-yli-valmistuu-lauantaina (tm-domain/laske-tiemerkinnan-takaraja
                                                        testikohde-jyrsinta-juhannuksen-yli-valmistuu-lauantaina)
        jyrsinta-juhannuksen-yli-valmistuu-perjantaina (tm-domain/laske-tiemerkinnan-takaraja
                                                        testikohde-jyrsinta-juhannuksen-yli-valmistuu-perjantaina)
        jyrsinta-vapun-yli (tm-domain/laske-tiemerkinnan-takaraja
                             testikohde-jyrsinta-vapun-yli)
        massa-ei-jyrsintaa (tm-domain/laske-tiemerkinnan-takaraja
                             testikohde-massa-ei-jyrsintaa)]

    ;; 1.3. valmis tiistaina -> välitavoite alkaa + 1 vrk eli 2.3. Siihen +21vrk koska maali, eli 23.3.
    (is (= (:aikataulu-tiemerkinta-takaraja maali-ei-jyrsintaa) #inst "2022-03-22T22:00:00.000-00:00"))

    ;; 23.5. valmis maanantaina -> välitavoite alkaa + 1 vrk eli 24.5. Siihen +21vrk koska maali, eli 14.6.
    (is (= (:aikataulu-tiemerkinta-takaraja maali-ei-jyrsintaa-helatorstain-yli) #inst "2022-06-13T21:00:00.000-00:00"))

    ;; 1.3. valmis tiistaina -> välitavoite alkaa + 1 vrk eli 2.3. Siihen +21vrk koska jyrsintä, eli 23.3.
    (is (= (:aikataulu-tiemerkinta-takaraja jyrsinta) #inst "2022-03-22T22:00:00.000-00:00"))

    ;; 20.6. valmis maanantaina -> välitavoite alkaa + 1 vrk eli 21.6. Siihen +21vrk koska jyrsintä, eli 12.7.
    (is (= (:aikataulu-tiemerkinta-takaraja jyrsinta-juhannuksen-yli) #inst "2022-07-11T21:00:00.000-00:00"))
    ;; 19.6. valmis sunnuntaina -> välitavoite alkaa + 1 vrk eli 20.6. Siihen +21vrk koska jyrsintä, eli 11.7.
    (is (= (:aikataulu-tiemerkinta-takaraja jyrsinta-juhannuksen-yli-valmistuu-sunnuntaina) #inst "2022-07-10T21:00:00.000-00:00"))
    ;; 18.6. valmis lauantaina -> välitavoite alkaa + 2 vrk eli 20.6. Siihen +21vrk koska jyrsintä, eli 11.7.
    (is (= (:aikataulu-tiemerkinta-takaraja jyrsinta-juhannuksen-yli-valmistuu-lauantaina) #inst "2022-07-10T21:00:00.000-00:00"))
    ;; 17.6. valmis perjantaina -> välitavoite alkaa + 3 vrk eli 20.6. Siihen +21vrk koska jyrsintä, eli 11.7.
    (is (= (:aikataulu-tiemerkinta-takaraja jyrsinta-juhannuksen-yli-valmistuu-perjantaina) #inst "2022-07-10T21:00:00.000-00:00"))
    (is (= (:aikataulu-tiemerkinta-takaraja jyrsinta-vapun-yli) #inst "2023-05-18T21:00:00.000-00:00"))
    (is (= (:aikataulu-tiemerkinta-takaraja massa-ei-jyrsintaa) #inst "2022-03-15T22:00:00.000-00:00"))))