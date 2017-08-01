(ns harja.views.urakka.suunnittelu.yksikkohintaiset-tyot-test
  (:require [harja.views.urakka.suunnittelu.yksikkohintaiset-tyot :as sut]
            [cljs.test :as test :refer-macros [deftest is]]
            [harja.ui.grid :as grid]))

(deftest etuliitteen-mukainen-valiotsikointi
  (let [[foo jotain quux ihan muuta]
        (sut/etuliitteen-mukaan-valiotsikoilla
          [{:tehtavan_nimi "Quux: ihan"}
           {:tehtavan_nimi "Foo: jotain"}
           {:tehtavan_nimi "Quux: muuta"}])]

    (is (grid/otsikko? foo))
    (is (= "Foo" (:teksti foo)))
    (is (grid/otsikko? quux))
    (is (= "Quux" (:teksti quux)))

    (is (= {:tehtavan_nimi "jotain" :valiotsikko "Foo"} jotain))
    (is (= {:tehtavan_nimi "ihan" :valiotsikko "Quux"} ihan))
    (is (= {:tehtavan_nimi "muuta" :valiotsikko "Quux"} muuta))))
