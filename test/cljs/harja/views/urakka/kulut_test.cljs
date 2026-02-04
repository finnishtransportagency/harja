(ns harja.views.urakka.kulut-test
  "Kulut näkymän testi"
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja.testutils.shared-testutils :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.domain.kulut :as kulut-domain]
            [harja.ui.pvm :as pvm-valinta]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.kulut.mhu-kulut :as kulut-tiedot]))

(t/use-fixtures :each u/komponentti-fixture)

(deftest paivamaaran-valinta-erapaiva-validointi
  (with-redefs [tila/yleiset (atom {:urakka {:alkupvm (pvm/->pvm "1.10.2014") :loppupvm (pvm/->pvm "30.09.2019")}})]
    (let [koontilaskun-kuukausi "huhtikuu/1-hoitovuosi"
          parametrit {:paivitys-fn (fn [] nil)
                      :paivamaara (kulut-domain/koontilaskun-kuukausi->pvm
                                    koontilaskun-kuukausi
                                    (-> @tila/yleiset :urakka :alkupvm)
                                    (-> @tila/yleiset :urakka :loppupvm))
                      :luokat #{}
                      :valittava?-fn (kulut-domain/koontilaskun-kuukauden-sisalla?-fn
                                       koontilaskun-kuukausi
                                       (-> @tila/yleiset :urakka :alkupvm)
                                       (-> @tila/yleiset :urakka :loppupvm))
                      :disabled false}
          tulos [pvm-valinta/pvm-valintakalenteri-inputilla parametrit]
          valittava?-fn (:valittava?-fn (second tulos))]
      (is (valittava?-fn (pvm/->pvm "04.04.2015")))
      (is (valittava?-fn (pvm/->pvm "01.04.2015")))
      (is (valittava?-fn (pvm/->pvm "30.04.2015")))
      (is (not (valittava?-fn (pvm/->pvm "31.03.2015"))))
      (is (not (valittava?-fn (pvm/->pvm "01.05.2015"))))
      (is (not (valittava?-fn (pvm/->pvm "04.04.1985")))))))

;; Laskutusraja-komponentin testit
(deftest kuukauden-kulujen-jako-laskutusrajassa
  (testing "Kuukauden kulujen jako laskutusrajaan sisältyvään ja ylittävään osaan"
    (let [laskutusraja 5000000M
          kulut-yhteensa-hakukuukauteen-asti 4800000M
          haetun-aikarajan-kulujen-summa 400000M
          yhteensa (+ haetun-aikarajan-kulujen-summa kulut-yhteensa-hakukuukauteen-asti)
          ylitys (- yhteensa laskutusraja)
          laskutusrajaan-sisaltyva (- haetun-aikarajan-kulujen-summa ylitys)
          laskutusrajan-ylittava (- yhteensa laskutusraja)]
      (is (= 5200000M yhteensa) "Kulujen yhteissumman pitäisi olla 5200000")
      (is (= 200000M ylitys) "Ylityksen pitäisi olla 200000")
      (is (= 200000M laskutusrajaan-sisaltyva) "Laskutusrajaan sisältyvän pitäisi olla 200000")
      (is (= 200000M laskutusrajan-ylittava) "Laskutusrajan ylittävän pitäisi olla 200000")))

  (testing "Kuukauden kulut kun edellisten kuukausien kulut ylittävät jo laskutusrajan"
    (let [laskutusraja 5000000M
          kulut-yhteensa-hakukuukauteen-asti 5500000M
          haetun-aikarajan-kulujen-summa 300000M
          yhteensa (+ haetun-aikarajan-kulujen-summa kulut-yhteensa-hakukuukauteen-asti)
          laskutusrajaan-sisaltyva (if (> kulut-yhteensa-hakukuukauteen-asti laskutusraja)
                                     0
                                     haetun-aikarajan-kulujen-summa)
          laskutusrajan-ylittava (if (> kulut-yhteensa-hakukuukauteen-asti laskutusraja)
                                   haetun-aikarajan-kulujen-summa
                                   (- yhteensa laskutusraja))]
      (is (= 0M laskutusrajaan-sisaltyva) "Laskutusrajaan sisältyvän pitäisi olla 0 kun raja ylitetty jo aiemmin")
      (is (= 300000M laskutusrajan-ylittava) "Kaikki kuukauden kulut pitäisi olla ylittävää osaa"))))
