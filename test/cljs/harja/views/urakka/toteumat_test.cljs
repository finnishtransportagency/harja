(ns harja.views.urakka.toteumat-test
  (:require [harja.views.urakka.toteumat :as toteumat]
            [harja.testutils.shared-testutils :as u]
            [cljs.test :as test :refer-macros [deftest is]]
            [harja.ui.grid :as grid]))

(deftest varusteet2-valilehti-nakyy
  (let [urakka {:id 123 :tyyppi :teiden-hoito}]
    (komponenttitesti
      [toteumat/toteumat urakka]

      "Aluksi kolme päivystäjäriviä joista ei yksikään boldattu (koska päivystyksiä ei voimassa)"
      (is (= 3 (count (u/sel [:tbody :tr]))))

      --
      (is (= 1 4))

      "Muokkaustoimintojen nappien määrä kun toiminnot kiinni"
      (is (= 1 5))
      (u/click :button.nappi-ensisijainen)
      )))
