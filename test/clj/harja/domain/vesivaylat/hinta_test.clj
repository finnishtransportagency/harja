(ns harja.domain.vesivaylat.hinta-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]))

(deftest hinnan-ominaisuudet
  (is (= :baz
         (hinta/hinnan-ominaisuus [{::hinta/otsikko :foobar :barbar :baz}]
                                  :foobar
                                  :barbar)))
  (is (= :baz
         (hinta/hinnan-maara [{::hinta/otsikko :foobar ::hinta/maara :baz}]
                             :foobar)))
  (is (= :baz
         (hinta/hinnan-yleiskustannuslisa [{::hinta/otsikko :foobar ::hinta/yleiskustannuslisa :baz}]
                                          :foobar))))