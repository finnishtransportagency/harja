(ns harja.views.urakka.kulut-test
  "Kulut näkymän testi"
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja.testutils.shared-testutils :as u]
            [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu
                                     jvh-fixture]]
            [reagent.core :as r]
            [harja.views.urakka.kulut :as kulut]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.urakka.kulut :refer [paivamaaran-valinta]]
            [harja.pvm :as pvm])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

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
                 (is (valittava?-fn (pvm/->pvm "04.04.2015"))))))
