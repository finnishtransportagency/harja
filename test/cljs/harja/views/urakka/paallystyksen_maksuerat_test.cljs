(ns harja.views.urakka.paallystyksen-maksuerat-test
  (:require
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.pvm :refer [->pvm]]
    [harja.tiedot.urakka.paallystyksen-maksuerat :as maksuerat]
    [harja.loki :refer [log]]
    [harja.tyokalut.functor :refer [fmap]]))


(deftest maksuerien-muunto-grid-muotoon-toimi
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat ["Eka erä" "Toka erä" "Kolmas erä"]})
         {:maksuera1 "Eka erä" :maksuera2 "Toka erä" :maksuera3 "Kolmas erä"}))
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat []})
         {}))
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat nil})
         {})))

(deftest maksuerien-muunto-tallennusmuotoon-toimi
  (is (= (maksuerat/maksuerarivi-tallennusmuotoon
           {:maksuera3 "Ylimääräinen" :maksuera1 "Eka puolikas" :maksuera2 "Toka puolikas"})
         {:maksuerat ["Eka puolikas" "Toka puolikas" "Ylimääräinen"]}))
  (is (= (maksuerat/maksuerarivi-tallennusmuotoon {})
         {:maksuerat []})))

