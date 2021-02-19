(ns harja.ui.validointi-test
  (:require [harja.validointi :as val]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as ur]
            [harja.pvm :as pvm]
            [cljs.test :as t :refer-macros [deftest is testing async]]))

(defn validi-data? [saanto data & optiot]
  (apply val/validoi-saanto saanto :testi data {} {} optiot))

(defn validi-rivi? [saanto data rivi & optiot]
  (apply val/validoi-saanto saanto :testi data rivi {} optiot))

(defn validi? [saanto nimi data rivi taulukko & optiot]
  (apply val/validoi-saanto saanto nimi data rivi taulukko optiot))

(def pvm-2015 (pvm/->pvm "01.01.2015"))
(def pvm-2016 (pvm/->pvm "01.01.2016"))
(def pvm-2017 (pvm/->pvm "01.01.2017"))

(deftest pvm-saatiin-generoitua
  (is (some? pvm-2015))
  (is (some? pvm-2016))
  (is (some? pvm-2017)))


(deftest hoitokaudella
  (testing "Täyttää säännön hoitokaudella"
    (with-redefs
     [ur/valittu-hoitokausi (atom [pvm-2015 pvm-2017])]
     (is (nil? (validi-data? :hoitokaudella pvm-2015)))
     (is (nil? (validi-data? :hoitokaudella pvm-2016)))
     (is (nil? (validi-data? :hoitokaudella pvm-2017)))))

  (testing "Ei täytä sääntöä hoitokaudella"
    (with-redefs
      [ur/valittu-hoitokausi (atom [pvm-2015 pvm-2016])]
      (is (some? (validi-data? :hoitokaudella pvm-2017))))))

(deftest urakan-aikana
  (testing "Täyttää säännön urakan-aikana"
    (with-redefs [nav/valittu-urakka (atom {:alkupvm pvm-2015 :loppupvm pvm-2017 })]
      (is (nil? (validi-data? :urakan-aikana pvm-2016)))))
  (testing "Ei täytä sääntöä urakan-aikana"
    (with-redefs [nav/valittu-urakka (atom {:alkupvm pvm-2015 :loppupvm pvm-2016 })]
      (is (some? (validi-data? :urakan-aikana pvm-2017))))))

(deftest urakan-aikana-ja-hoitokaudella
  (testing "Täyttää säännön urakan-aikana-ja-hoitokaudella"
    (with-redefs [nav/valittu-urakka (atom {:alkupvm pvm-2015 :loppupvm pvm-2017})
                  ur/valittu-hoitokausi (atom [pvm-2015 pvm-2017])]
      (is (nil? (validi-data? :urakan-aikana-ja-hoitokaudella pvm-2016)))))
  (testing "Ei täytä sääntöä urakan-aikana-ja-hoitokaudella"
    (with-redefs [nav/valittu-urakka (atom {:alkupvm pvm-2015 :loppupvm pvm-2016})
                  ur/valittu-hoitokausi (atom [pvm-2015 pvm-2016])]
      (is (some? (validi-data? :urakan-aikana-ja-hoitokaudella pvm-2017))))))

(deftest valitun-kkn-aikana-urakan-hoitokaudella
  ;; toteuta joku sunnuntai
  (is true))

(deftest vakiohuomautus
  (is (some? (validi-data? :vakiohuomautus nil "Virhe"))))

(deftest validi-tr
  (testing "Täyttää säännön validi-tr"
    (is (nil? (validi-rivi? :validi-tr {:tie 20 :aosa 1 :aet 1} {:geometria 123} "viesti" [:geometria])))
    ;; Pitääkö tämän tosiaan olla validi tr?
    (is (nil? (validi-rivi? :validi-tr {:tie 20 :aosa 1 :losa 1} {:geometria 123} "viesti" [:geometria]))))
  (testing "Ei täytä sääntöä validi-tr"
    (is (some? (validi-rivi? :validi-tr {:tie 20 :aosa 1 :aet 1} {:geometria 123} "viesti" [:reitti])))
    (is (some? (validi-rivi? :validi-tr {:tie 0 :aosa 1 :aet 1} {:geometria 123} "viesti" [:geometria])))))

(deftest uusi-arvo-ei-setissa
  (testing "Täyttää säännön uusi-arvo-ei-setissa"
    (is (nil? (validi-rivi? :uusi-arvo-ei-setissa 1 {:id 1} (atom #{1}))))
    (is (nil? (validi-rivi? :uusi-arvo-ei-setissa 1 {:id -1} (atom #{2})))))
  (testing "Ei täytä sääntöä uusi-arvo-ei-setissa"
    (is (some? (validi-rivi? :uusi-arvo-ei-setissa 1 {:id -1} (atom #{1}))))))

(deftest ei-tyhja
  (testing "Täyttää säännön ei-tyhja"
    (is (nil? (validi-data? :ei-tyhja "123"))))
  (testing "Ei täytä sääntöä ei-tyhja"
    (is (some? (validi-data? :ei-tyhja "")))
    (is (some? (validi-data? :ei-tyhja "     ")))
    (is (some? (validi-data? :ei-tyhja "\t")))
    (is (some? (validi-data? :ei-tyhja nil)))))

(deftest ei-negatiivinen-jos-avaimen-arvo
  (testing "Täyttää säännön ei-negatiivinen-jos-avaimen-arvo"
    (is (nil? (validi-rivi? :ei-negatiivinen-jos-avaimen-arvo 0 {:id 1} :id 1)))
    (is (nil? (validi-rivi? :ei-negatiivinen-jos-avaimen-arvo 1 {:id 1} :foo 1)))
    (is (nil? (validi-rivi? :ei-negatiivinen-jos-avaimen-arvo -1 {:id 1} :id 2))))
  (testing "Ei täytä sääntöä ei-negatiivinen-jos-avaimen-arvo"
    (is (some? (validi-rivi? :ei-negatiivinen-jos-avaimen-arvo -1 {:id 1} :id 1)))))

(deftest ei-tyhja-jos-toinen-avain-nil
  (testing "Täyttää säännön ei-tyhja-jos-toinen-avain-nil"
    (is (nil? (validi-rivi? :ei-tyhja-jos-toinen-avain-nil "moi" {:id 1} :nimi)))
    (is (nil? (validi-rivi? :ei-tyhja-jos-toinen-avain-nil "" {:id 1} :id)))
    (is (nil? (validi-rivi? :ei-tyhja-jos-toinen-avain-nil "" {:id 1 :nimi "Seppo"} :nimi))))
  (testing "Ei täytä sääntöä ei-tyhja-jos-toinen-avain-nil"
    (is (some? (validi-rivi? :ei-tyhja-jos-toinen-avain-nil "" {:id 1} :nimi)))))

(deftest ei-tulevaisuudessa
  (testing "Täyttää säännön ei-tulevaisuudessa"
    (with-redefs [pvm/nyt (constantly pvm-2016)]
      (is (nil? (validi-data? :ei-tulevaisuudessa pvm-2015)))))
  (testing "Ei täytä sääntöä ei-tulevaisuudessa"
    (with-redefs [pvm/nyt (constantly pvm-2016)]
      (is (some? (validi-data? :ei-tulevaisuudessa pvm-2017))))))

(deftest ei-avoimia-korjaavia-toimenpiteitä
  (testing "Täyttää säännön ei-avoimia-korjaavia-toimenpiteitä"
    (is (nil? (validi-rivi? :ei-avoimia-korjaavia-toimenpiteitä :suljettu {:korjaavattoimenpiteet [{:tila :toteutettu} {:tila :toteutettu}]})))
    (is (nil? (validi-rivi? :ei-avoimia-korjaavia-toimenpiteitä :kasitelty {:korjaavattoimenpiteet [{:tila :toteutettu} {:tila :toteutettu}]})))
    (is (nil? (validi-rivi? :ei-avoimia-korjaavia-toimenpiteitä :kesken {:korjaavattoimenpiteet [{:tila :kesken} {:tila :toteutettu}]}))))
  (testing "Ei täytä sääntöä ei-avoimia-korjaavia-toimenpiteitä"
    (is (some? (validi-rivi? :ei-avoimia-korjaavia-toimenpiteitä :suljettu {:korjaavattoimenpiteet [{:tila :foo}]})))
    (is (some? (validi-rivi? :ei-avoimia-korjaavia-toimenpiteitä :kasitelty {:korjaavattoimenpiteet [{:tila :bar}]})))
    (is (some? (validi-rivi? :ei-avoimia-korjaavia-toimenpiteitä :suljettu {:korjaavattoimenpiteet [{:tila :foo}]})))
    (is (some? (validi-rivi? :ei-avoimia-korjaavia-toimenpiteitä :kasitelty {:korjaavattoimenpiteet [{:tila :foo} {:tila :toteutettu}]})))))

(deftest joku-naista
  (testing "Täyttää säännön joku-naista"
    (is (nil? (validi-rivi? :joku-naista nil {:id 1 :nimi "Max" :intohimo :clojure} :id :nimi :intohimo)))
    (is (nil? (validi-rivi? :joku-naista nil {:id 1 :nimi nil :intohimo nil} :id :nimi :intohimo)))
    (is (nil? (validi-rivi? :joku-naista nil {:id nil :nimi "Max" :intohimo nil} :id :nimi :intohimo)))
    (is (nil? (validi-rivi? :joku-naista nil {:id nil :nimi nil :intohimo :clojure} :id :nimi :intohimo))))
  (testing "Ei täytä sääntöä joku-naista"
    (is (some? (validi-rivi? :joku-naista nil {:id nil :nimi nil :intohimo nil} :id :nimi :intohimo)))))

(deftest uniikki
  (testing "Täyttää säännön uniikki"
    (is (nil? (validi? :uniikki :intohimo nil nil {1 {:id 1 :intohimo :clojure}
                                                    2 {:id 2 :intohimo :clojure}
                                                    3 {:id 3 :intohimo :jalkapallo}})))
    (is (nil? (validi? :uniikki :intohimo :clojure nil {1 {:id 1 :intohimo :clojure}
                                                        2 {:id 2 :intohimo :coca-cola}
                                                        3 {:id 3 :intohimo :jalkapallo}}))))
  (testing "Ei täytä sääntöä uniikki"
    (is (some? (validi? :uniikki :intohimo :clojure nil {1 {:id 1 :intohimo :clojure}
                                                         2 {:id 2 :intohimo :clojure}
                                                         3 {:id 3 :intohimo :jalkapallo}})))))

(deftest pvm-kentan-jalkeen
  (testing "Täyttää säännön pvm-kentan-jalkeen"
    (is (nil? (validi-rivi? :pvm-kentan-jalkeen pvm-2016 {:pvm pvm-2015} :pvm))))
  (testing "Ei täytä sääntöä pvm-kentan-jalkeen"
    (is (some? (validi-rivi? :pvm-kentan-jalkeen pvm-2016 {:pvm pvm-2017} :pvm)))))

(deftest pvm-toisen-pvmn-jalkeen
  (testing "Täyttää säännön pvm-toisen-pvmn-jalkeen"
    (is (nil? (validi-data? :pvm-toisen-pvmn-jalkeen pvm-2016 pvm-2015))))
  (testing "Ei täytä sääntöä pvm-toisen-pvmn-jalkeen"
    (is (some? (validi-data? :pvm-toisen-pvmn-jalkeen pvm-2016 pvm-2017)))))

(deftest pvm-ennen
  (testing "Täyttää säännön pvm-ennen"
    (is (nil? (validi-data? :pvm-ennen pvm-2016 pvm-2017))))
  (testing "Ei täytä sääntöä pvm-ennen"
    (is (some? (validi-data? :pvm-ennen pvm-2016 pvm-2015)))))

(deftest aika-jalkeen
  (let
    [aika-12 (pvm/->Aika 12 0 0)
     aika-13 (pvm/->Aika 13 0 0)
     aika-14 (pvm/->Aika 14 0 0)]
    (testing "Täyttää säännön aika-jalkeen"
         (is (nil? (validi-rivi? :aika-jalkeen aika-13 {:aika aika-12} :aika)))
         (is (nil? (validi-rivi? :aika-jalkeen aika-13 {:aika aika-12} aika-12)))
         (is (nil? (validi-rivi? :aika-jalkeen nil {:aika aika-12} aika-12))))
    (testing "Ei täytä sääntöä pvm-toisen-pvmn-jalkeen"
      (is (some? (validi-rivi? :aika-jalkeen aika-13 {:aika aika-14} :aika)))
      (is (some? (validi-rivi? :aika-jalkeen aika-13 {:aika aika-14} aika-14))))))

(deftest toinen-arvo-annettu-ensin
  (testing "Täyttää säännön toinen-arvo-annettu-ensin"
    (is (nil? (validi-rivi? :toinen-arvo-annettu-ensin 1 {:id 1} :id)))
    (is (nil? (validi-rivi? :toinen-arvo-annettu-ensin nil {:id 1} :tyyppi))))
  (testing "Ei täytä sääntöä aika-jalkeen"
    (is (some? (validi-rivi? :toinen-arvo-annettu-ensin 1 {:id 1} :tyyppi))))) "Ei täytä sääntöä toinen-arvo-annettu-ensin"(deftest ei-tyhja-jos-toinen-arvo-annettu
  (testing "Täyttää säännön ei-tyhja-jos-toinen-arvo-annettu"
    (is (nil? (validi-rivi? :ei-tyhja-jos-toinen-arvo-annettu nil {:id 1} :foo)))
    (is (nil? (validi-rivi? :ei-tyhja-jos-toinen-arvo-annettu 1 {:id 1} :id))))
  (testing "Ei täytä sääntöä ei-tyhja-jos-toinen-arvo-annettu"
    (is (some? (validi-rivi? :ei-tyhja-jos-toinen-arvo-annettu nil {:id 1} :id)))))

(deftest ainakin-toinen-annettu
  (testing "Täyttää säännön ainakin-toinen-annettu"
    (is (nil? (validi-rivi? :ainakin-toinen-annettu nil {:id 1 :tyyppi :foo} [:id])))
    (is (nil? (validi-rivi? :ainakin-toinen-annettu nil {:id 1 :tyyppi :foo} [:tyyppi])))
    (is (nil? (validi-rivi? :ainakin-toinen-annettu nil {:id 1 :tyyppi :foo} [:id :tyyppi]))))
  (testing "Ei täytä sääntöä ainakin-toinen-annettu"
    (is (some? (validi-rivi? :ainakin-toinen-annettu nil {:id 1 :tyyppi :foo} [:nimi :intohimo])))))

(deftest yllapitoluokka
  (testing "Täyttää säännön yllapitoluokka"
    (is (nil? (validi-data? :yllapitoluokka nil)))
    (is (nil? (validi-data? :yllapitoluokka 1)))
    (is (nil? (validi-data? :yllapitoluokka 2)))
    (is (nil? (validi-data? :yllapitoluokka 3))))
  (testing "Ei täytä sääntöä yllapitoluokka"
    (is (some? (validi-data? :yllapitoluokka "1")))))

(deftest lampotila
  (testing "Täyttää säännön lampotila"
    (is (nil? (validi-data? :lampotila -55)))
    (is (nil? (validi-data? :lampotila -54)))
    (is (nil? (validi-data? :lampotila 0)))
    (is (nil? (validi-data? :lampotila 10)))
    (is (nil? (validi-data? :lampotila 54)))
    (is (nil? (validi-data? :lampotila 55))))
  (testing "Ei täytä sääntöä lampotila"
    (is (some? (validi-data? :lampotila -56)))
    (is (some? (validi-data? :lampotila 56)))))

(deftest rajattu-numero
  (testing "Täyttää säännön rajattu-numero"
    (is (nil? (validi-data? :rajattu-numero 0 0 100)))
    (is (nil? (validi-data? :rajattu-numero 1 0 100)))
    (is (nil? (validi-data? :rajattu-numero 50 0 100)))
    (is (nil? (validi-data? :rajattu-numero 99 0 100)))
    (is (nil? (validi-data? :rajattu-numero 100 0 100)))
    (is (nil? (validi-data? :rajattu-numero nil 0 100))))
  (testing "Ei täytä sääntöä rajattu-numero"
    (is (some? (validi-data? :rajattu-numero -1 0 100)))
    (is (some? (validi-data? :rajattu-numero 101 0 100)))))

(deftest rajattu-numero-tai-tyhja
  (testing "Täyttää säännön rajattu-numero-tai-tyhja"
    (is (nil? (validi-data? :rajattu-numero-tai-tyhja nil 0 100)))
    (is (nil? (validi-data? :rajattu-numero-tai-tyhja 0 0 100)))
    (is (nil? (validi-data? :rajattu-numero-tai-tyhja 1 0 100)))
    (is (nil? (validi-data? :rajattu-numero-tai-tyhja 50 0 100)))
    (is (nil? (validi-data? :rajattu-numero-tai-tyhja 99 0 100)))
    (is (nil? (validi-data? :rajattu-numero-tai-tyhja 100 0 100))))
  (testing "Ei täytä sääntöä rajattu-numero-tai-tyhja"
    (is (some? (validi-data? :rajattu-numero-tai-tyhja -1 0 100)))
    (is (some? (validi-data? :rajattu-numero-tai-tyhja 101 0 100)))))

(deftest ytunnus
  (testing "Täyttää säännön ytunnus"
    (is (nil? (validi-data? :ytunnus nil)))
    (is (nil? (validi-data? :ytunnus "1234567-8"))))
  (testing "Ei täytä sääntöä ytunnus"
    (is (some? (validi-data? :ytunnus "")))
    (is (some? (validi-data? :ytunnus "123")))
    (is (some? (validi-data? :ytunnus "Y1234567")))
    (is (some? (validi-data? :ytunnus "123456789")))
    (is (some? (validi-data? :ytunnus "123456-78")))
    (is (some? (validi-data? :ytunnus "1234567-Y")))))

(deftest numeron-validointi
  (testing "testaa numeron validointia"
    (is (true? (val/validoi-numero "3,6" 2 4 2)))
    (is (true? (val/validoi-numero "3" 2 4 2)))
    (is (false? (val/validoi-numero "3,61231233" 2 4 2)))
    (is (false? (val/validoi-numero "13,6" 1 10 2)))))