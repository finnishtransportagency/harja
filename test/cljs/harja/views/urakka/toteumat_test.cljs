(ns harja.views.urakka.toteumat-test
  (:require [cljs.test :as test :refer-macros [deftest is testing async]]
            [harja.views.urakka.toteumat :as toteumat]
            [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu jvh-fixture]]
            [harja.testutils.shared-testutils :as u]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.grid :as grid])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(test/use-fixtures :each u/komponentti-fixture fake-palvelut-fixture jvh-fixture)

(deftest varusteet2-valilehti-nakyy-jvh-kayttajalle
  (let [urakka {:id 35 :tyyppi :teiden-hoito}]
    (komponenttitesti
      [toteumat/toteumat urakka]

      "Klikataan Varusteet2 välilehteä"
      (u/click "[data-cy=tabs-taso2-Varusteet2]"))))

(deftest varusteet2-valilehtea-ei-nay-herrahuu
  (let [urakka {:id 35 :tyyppi :teiden-hoito}]
    (komponenttitesti
      [toteumat/toteumat urakka]

      "Klikataan Varusteet2 välilehteä"
      (u/click "[data-cy=tabs-taso2-Varusteet2]")
      (is (= 0 (count (u/sel "[data-cy=tabs-taso2-Varusteet2]"))))
      )))
