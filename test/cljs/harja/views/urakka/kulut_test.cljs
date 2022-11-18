(ns harja.views.urakka.kulut-test
  "Kulut näkymän testi"
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja.testutils.shared-testutils :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.urakka.kulut.kulut :refer [paivamaaran-valinta]]
            [harja.pvm :as pvm]))

(t/use-fixtures :each u/komponentti-fixture)

(deftest paivamaaran-valinta-erapaiva-validointi
  (with-redefs [tila/yleiset (atom {:urakka {:alkupvm (pvm/->pvm "1.10.2014") :loppupvm (pvm/->pvm "30.09.2019")}})]
               (let [parametrit {:paivitys-fn           (fn [] nil)
                                 :erapaiva              (pvm/->pvm "1.10.2015")
                                 :erapaiva-meta         {}
                                 :disabled              false
                                 :koontilaskun-kuukausi "huhtikuu/1-hoitovuosi"}
                     tulos (paivamaaran-valinta parametrit)
                     valittava?-fn (:valittava?-fn (second tulos))]
                 (is (valittava?-fn (pvm/->pvm "04.04.2015")))
                 (is (valittava?-fn (pvm/->pvm "01.04.2015")))
                 (is (valittava?-fn (pvm/->pvm "30.04.2015")))
                 (is (not (valittava?-fn (pvm/->pvm "31.03.2015"))))
                 (is (not (valittava?-fn (pvm/->pvm "01.05.2015"))))
                 (is (not (valittava?-fn (pvm/->pvm "04.04.1985")))))))
