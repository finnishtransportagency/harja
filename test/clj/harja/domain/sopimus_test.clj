(ns harja.domain.sopimus-test
  (:require [clojure.test :refer :all]
            [harja.domain.sopimus :as s]))

(deftest paasopimus
  (testing "Löydetään aina vain yksi pääsopimus"
    (is (false? (sequential? (s/ainoa-paasopimus [{::s/id 1 ::s/paasopimus-id nil}
                                            {::s/id 2 ::s/paasopimus-id nil}
                                            {::s/id 3 ::s/paasopimus-id 1}
                                            {::s/id 4 ::s/paasopimus-id 2}])))))

  (testing "Pääsopimus löytyy sopimusten joukosta"
    (is (= {::s/id 1 ::s/paasopimus-id nil} (s/ainoa-paasopimus [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id 3 ::s/paasopimus-id 1}])))
    (is (= {::s/id 1 ::s/paasopimus-id nil} (s/ainoa-paasopimus [{::s/id 2 ::s/paasopimus-id 1} {::s/id 1 ::s/paasopimus-id nil}]))))

  (testing "Jos pääsopimusta ei ole, sitä ei myöskään palauteta"
    (is (= nil (s/ainoa-paasopimus [{::s/id 1 ::s/paasopimus-id 2} {::s/id 3 ::s/paasopimus-id 2}])))
    (is (= nil (s/ainoa-paasopimus [{::s/id 1 ::s/paasopimus-id nil} {::s/id 3 ::s/paasopimus-id nil}])))
    (is (= nil (s/ainoa-paasopimus [])))
    (is (some? (s/ainoa-paasopimus [{::s/id 1 ::s/paasopimus-id nil}])))
    (is (= nil (s/ainoa-paasopimus [{::s/id nil ::s/paasopimus-id nil}]))))

  (testing "Pääsopimusta päätellessä ei välitetä poistetuista sopimuksista tai uusista riveistä,
              joille ei ole vielä sopimusta valittu"
    (is (= nil (s/ainoa-paasopimus [{::s/id 1 ::s/paasopimus-id nil :poistettu true}
                              {::s/id 3 ::s/paasopimus-id 1}])))
    (is (= nil (s/ainoa-paasopimus [{::s/id -1 ::s/paasopimus-id nil}])))))

(deftest paasopimus-jokaiselle?
  (testing "Sopimus tunnistetaan pääsopimukseksi"
    (is (true? (s/paasopimus-jokaiselle? [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id 3 ::s/paasopimus-id 1}]
                                         {::s/id 1 ::s/paasopimus-id nil})))
    (is (true? (s/paasopimus-jokaiselle? [{::s/id 2 ::s/paasopimus-id 1} {::s/id 1 ::s/paasopimus-id nil}]
                                         {::s/id 1 ::s/paasopimus-id nil}))))

  (testing "Tunnistetaan, että sopimus ei ole pääsopimus"
    (is (false? (s/paasopimus-jokaiselle? [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id 3 ::s/paasopimus-id 1}]
                                          {::s/id 2 ::s/paasopimus-id 1})))
    (is (false? (s/paasopimus-jokaiselle? [{::s/id 2 ::s/paasopimus-id 1} {::s/id 1 ::s/paasopimus-id nil}]
                                          {::s/id 2 ::s/paasopimus-id 1}))))

  (testing "Jos pääsopimusta ei ole, sopimusta ei tunnisteta pääsopimukseksi"
    (is (false? (s/paasopimus-jokaiselle? [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id nil} {::s/id 3 ::s/paasopimus-id nil}]
                                          {::s/id 2 ::s/paasopimus-id nil})))
    (is (false? (s/paasopimus-jokaiselle? [{::s/id 2 ::s/paasopimus-id nil} {::s/id 1 ::s/paasopimus-id nil}]
                                          {::s/id 1 ::s/paasopimus-id nil})))
    (is (false? (s/paasopimus-jokaiselle? [{::s/id 2 ::s/paasopimus-id nil} {::s/id 1 ::s/paasopimus-id nil}]
                                          {::s/id nil ::s/paasopimus-id nil}))))

  (testing "Jos vektorissa on monta pääsopimusta, sopimusta ei tunnisteta pääsopimukseksi."
    (is (false? (s/paasopimus-jokaiselle? [{::s/id 1 ::s/paasopimus-id nil}
                                          {::s/id 2 ::s/paasopimus-id 1}
                                          {::s/id 3 ::s/paasopimus-id nil}
                                          {::s/id 4 ::s/paasopimus-id 3}]
                                         {::s/id 1 ::s/paasopimus-id nil})))))

(deftest paasopimus-jollekin?
  (testing "Sopimus tunnistetaan pääsopimukseksi"
    (is (true? (s/paasopimus-jollekin? [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id 3 ::s/paasopimus-id 1}]
                                         {::s/id 1 ::s/paasopimus-id nil})))
    (is (true? (s/paasopimus-jollekin? [{::s/id 2 ::s/paasopimus-id 1} {::s/id 1 ::s/paasopimus-id nil}]
                                         {::s/id 1 ::s/paasopimus-id nil}))))

  (testing "Tunnistetaan, että sopimus ei ole pääsopimus"
    (is (false? (s/paasopimus-jollekin? [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id 1} {::s/id 3 ::s/paasopimus-id 1}]
                                          {::s/id 2 ::s/paasopimus-id 1})))
    (is (false? (s/paasopimus-jollekin? [{::s/id 2 ::s/paasopimus-id 1} {::s/id 1 ::s/paasopimus-id nil}]
                                          {::s/id 2 ::s/paasopimus-id 1}))))

  (testing "Jos pääsopimusta ei ole, sopimusta ei tunnisteta pääsopimukseksi"
    (is (false? (s/paasopimus-jollekin? [{::s/id 1 ::s/paasopimus-id nil} {::s/id 2 ::s/paasopimus-id nil} {::s/id 3 ::s/paasopimus-id nil}]
                                          {::s/id 2 ::s/paasopimus-id nil})))
    (is (false? (s/paasopimus-jollekin? [{::s/id 2 ::s/paasopimus-id nil} {::s/id 1 ::s/paasopimus-id nil}]
                                          {::s/id 1 ::s/paasopimus-id nil})))
    (is (false? (s/paasopimus-jollekin? [{::s/id 2 ::s/paasopimus-id nil} {::s/id 1 ::s/paasopimus-id nil}]
                                          {::s/id nil ::s/paasopimus-id nil}))))

  (testing "Vaikka vektorissa on monta pääsopimusta, sopimus tunnistetaan pääsopimukseksi."
    (is (true? (s/paasopimus-jollekin? [{::s/id 1 ::s/paasopimus-id nil}
                                        {::s/id 2 ::s/paasopimus-id 1}
                                        {::s/id 3 ::s/paasopimus-id nil}
                                        {::s/id 4 ::s/paasopimus-id 3}]
                                       {::s/id 1 ::s/paasopimus-id nil})))))