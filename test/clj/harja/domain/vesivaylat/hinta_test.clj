(ns harja.domain.vesivaylat.hinta-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]))

(deftest hinnan-ominaisuudet
  (is (= :baz
         (hinta/hinnan-ominaisuus-otsikolla [{::hinta/otsikko :foobar :barbar :baz}]
                                            :foobar
                                            :barbar)))
  (is (= :baz
         (hinta/hinnan-summa-otsikolla [{::hinta/otsikko :foobar ::hinta/summa :baz}]
                                       :foobar))))

(deftest hintojen-lasku
  (is (= (hinta/hintojen-summa-ilman-yklisaa [{::hinta/summa 1} {::hinta/summa 2} {::hinta/summa 3}])
         6))
  (is (= (hinta/yklisien-osuus [{::hinta/summa 1}
                                {::hinta/summa 100 ::hinta/yleiskustannuslisa 10}])
         10))
  (is (= (hinta/kokonaishinta-yleiskustannuslisineen [{::hinta/summa 1}
                                                      {::hinta/summa 100 ::hinta/yleiskustannuslisa 10}])
         111)))

(deftest hinnan-ominaisuus
  (is (= (hinta/hinnan-ominaisuus-otsikolla [{::hinta/summa 1 ::hinta/otsikko "A"}
                                             {::hinta/summa 2 ::hinta/otsikko "B"}
                                             {::hinta/summa 3 ::hinta/otsikko "C"}]
                                            "B" ::hinta/summa)
         2))
  (is (= (hinta/hinnan-summa-otsikolla [{::hinta/summa 1 ::hinta/otsikko "A"}
                                        {::hinta/summa 2 ::hinta/otsikko "B"}
                                        {::hinta/summa 3 ::hinta/otsikko "C"}]
                                       "C")
         3))

  (is (= (hinta/hinta-otsikolla [{::hinta/summa 1 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666}
                                 {::hinta/summa 2 ::hinta/otsikko "B"}
                                 {::hinta/summa 3 ::hinta/otsikko "C"}]
                                "A")
         {::hinta/summa 1 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666}))

  (is (= (hinta/hinta-idlla [{::hinta/id 2 ::hinta/summa 1 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666}
                             {::hinta/id 1 ::hinta/summa 2 ::hinta/otsikko "B"}
                             {::hinta/id 3 ::hinta/summa 3 ::hinta/otsikko "C"}]
                            2)
         {::hinta/id 2 ::hinta/summa 1 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666})))

(deftest hintajoukon-paivitus
  (is (= (#'harja.domain.vesivaylat.hinta/paivita-hintajoukon-hinnan-tiedot-idlla
           [{::hinta/id 2 ::hinta/summa 1 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666}
            {::hinta/id 1 ::hinta/summa 2 ::hinta/otsikko "B"}
            {::hinta/id 3 ::hinta/summa 3 ::hinta/otsikko "C"}]
           {::hinta/id 2 ::hinta/otsikko "A" ::hinta/summa 100})
         [{::hinta/id 2 ::hinta/summa 100 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666}
          {::hinta/id 1 ::hinta/summa 2 ::hinta/otsikko "B"}
          {::hinta/id 3 ::hinta/summa 3 ::hinta/otsikko "C"}]))

  (is (= (#'harja.domain.vesivaylat.hinta/paivita-hintajoukon-hinnan-tiedot-otsikolla
           [{::hinta/id 2 ::hinta/summa 1 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666}
            {::hinta/id 1 ::hinta/summa 2 ::hinta/otsikko "B"}
            {::hinta/id 3 ::hinta/summa 3 ::hinta/otsikko "C"}]
           {::hinta/id 3 ::hinta/otsikko "C" ::hinta/summa 100})
         [{::hinta/id 2 ::hinta/summa 1 ::hinta/otsikko "A" ::hinta/yleiskustannuslisa 666}
          {::hinta/id 1 ::hinta/summa 2 ::hinta/otsikko "B"}
          {::hinta/id 3 ::hinta/summa 100 ::hinta/otsikko "C"}])))