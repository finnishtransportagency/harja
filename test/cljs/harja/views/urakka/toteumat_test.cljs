(ns harja.views.urakka.toteumat-test
  (:require [cljs.test :as test :refer-macros [deftest is]]
            [harja.views.urakka.toteumat :as toteumat]
            [harja.testutils :refer [fake-palvelut-fixture jvh-fixture]]
            [harja.testutils.shared-testutils :as u])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(test/use-fixtures :each u/komponentti-fixture fake-palvelut-fixture jvh-fixture)

(deftest varusteet-valilehti-nakyy-jvh-kayttajalle
  (let [urakka {:id 35 :tyyppi :teiden-hoito}]
    (komponenttitesti
      [toteumat/toteumat urakka]

      "Klikataan Varusteet välilehteä"
      (u/click "[data-cy=tabs-taso2-Varusteet]"))))

(deftest varusteet-valilehtea-ei-nay-herrahuu
  (let [urakka {:id 35 :tyyppi :teiden-hoito}]
    (komponenttitesti
      [toteumat/toteumat urakka]

      "Klikataan Varusteet välilehteä"
      (u/click "[data-cy=tabs-taso2-Varusteet]")
      (is (= 0 (count (u/sel "[data-cy=tabs-taso2-Varusteet]")))))))
