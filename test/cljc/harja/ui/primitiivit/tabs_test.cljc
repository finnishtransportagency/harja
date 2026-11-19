(ns harja.ui.primitiivit.tabs-test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.ui.primitiivit.tabs :as tabs]))

(deftest tabin-data-cy-muodostaa-ankkurin
  (testing "taso-luokka ja otsikko yhdistyvät"
    (is (= "tabs-taso1-Suunnittelu"
           (tabs/tabin-data-cy "Suunnittelu" "tabs-taso1"))))
  (testing "ä ja ö korvataan a:lla ja o:lla"
    (is (= "tabs-taso2-Hinnoittelu-paatos"
           (tabs/tabin-data-cy "Hinnoittelu-päätös" "tabs-taso2 muu-luokka"))))
  (testing "ilman taso-luokkaa palautetaan pelkkä otsikko"
    (is (= "Suunnittelu" (tabs/tabin-data-cy "Suunnittelu" "muu-luokka"))))
  (testing "map-otsikon :data-cy voittaa"
    (is (= "oma-ankkuri"
           (tabs/tabin-data-cy {:teksti "Suunnittelu" :data-cy "oma-ankkuri"} "tabs-taso1")))))
