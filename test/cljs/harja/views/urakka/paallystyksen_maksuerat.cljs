(ns harja.views.urakka.paallystyksen-maksuerat-test
  (:require
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.pvm :refer [->pvm]]
    [harja.tiedot.urakka.paallystyksen-maksuerat :as maksuerat]
    [harja.loki :refer [log]]
    [harja.tyokalut.functor :refer [fmap]]))


(deftest maksuerien-muunto-grid-muotoon-toimi
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat ["Eka puolikas" "Toinen puolikas"]})
         {:maksuera1 "Eka puolikas" :maksuera2 "Toinen puolikas"})))

