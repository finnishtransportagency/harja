(ns harja.views.urakka.kulut-test
  "Kulut näkymän testi"
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja.testutils.shared-testutils :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.domain.kulut :as kulut-domain]
            [harja.ui.pvm :as pvm-valinta]
            [harja.pvm :as pvm]))

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
