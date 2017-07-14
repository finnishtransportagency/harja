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

(deftest hintojen-lasku
  (is (= (hinta/perushinta [{::hinta/maara 1} {::hinta/maara 2} {::hinta/maara 3}])
         6))
  (is (= (hinta/yleiskustannuslisien-osuus [{::hinta/maara 1}
                                            {::hinta/maara 100 ::hinta/yleiskustannuslisa 10}])
         10))
  (is (= (hinta/kokonaishinta-yleiskustannuslisineen [{::hinta/maara 1}
                                                      {::hinta/maara 100 ::hinta/yleiskustannuslisa 10}])
         111)))

(deftest hinnan-ominaisuus
  (is (= (hinta/hinnan-ominaisuus [{::hinta/maara 1 ::hinta/otsikko "A"}
                                   {::hinta/maara 2 ::hinta/otsikko "B"}
                                   {::hinta/maara 3 ::hinta/otsikko "C"}]
                                  "B" ::hinta/maara)
         2))
  (is (= (hinta/hinnan-maara [{::hinta/maara 1 ::hinta/otsikko "A"}
                              {::hinta/maara 2 ::hinta/otsikko "B"}
                              {::hinta/maara 3 ::hinta/otsikko "C"}]
                             "C")
         3))
  (is (= (hinta/hinnan-yleiskustannuslisa [{::hinta/maara 1 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666}
                                           {::hinta/maara 2 ::hinta/otsikko "B"}
                                           {::hinta/maara 3 ::hinta/otsikko "C"}]
                                          "A")
         666))
  (is (= (hinta/hinta-otsikolla "A"
                                [{::hinta/maara 1 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666}
                                 {::hinta/maara 2 ::hinta/otsikko "B"}
                                 {::hinta/maara 3 ::hinta/otsikko "C"}])
         {::hinta/maara 1 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666})))