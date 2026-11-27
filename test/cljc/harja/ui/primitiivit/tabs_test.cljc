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

(deftest tabin-id-on-html-yhteensopiva
  (is (= "harja-tab-tabs-taso1-Suunnittelu"
         (tabs/tabin-id "Suunnittelu" "tabs-taso1"))))

(deftest tabin-seuraava-indeksi-kiertaa
  (is (= 1 (tabs/seuraava-tabin-indeksi 0 3 "ArrowRight")))
  (is (= 2 (tabs/seuraava-tabin-indeksi 0 3 "ArrowLeft")))
  (is (= 0 (tabs/seuraava-tabin-indeksi 2 3 "ArrowRight")))
  (is (= 0 (tabs/seuraava-tabin-indeksi 2 3 "Home")))
  (is (= 2 (tabs/seuraava-tabin-indeksi 0 3 "End")))
  (is (nil? (tabs/seuraava-tabin-indeksi 0 3 "Enter"))))
