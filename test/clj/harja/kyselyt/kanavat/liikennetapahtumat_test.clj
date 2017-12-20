(ns harja.kyselyt.kanavat.liikennetapahtumat-test
  (:require [clojure.test :refer :all]
            [harja.kyselyt.kanavat.liikennetapahtumat :as q]
            [clj-time.core :as t]

            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-toiminto :as toiminto]
            [harja.domain.kanavat.lt-ketjutus :as ketjutus]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]))

(deftest urakkatietojen-liittaminen
  (is (= [{::lt/kohde {::kohde/id 1 ::kohde/urakat [1 2 3]}}
          {::lt/kohde {::kohde/id 3 ::kohde/urakat []}}]
         (#'q/liita-kohteen-urakkatiedot
           (constantly [{::kohde/id 1 ::kohde/urakat [1 2 3]}
                        {::kohde/id 2 ::kohde/urakat [4 5 6]}])
           [{::lt/kohde {::kohde/id 1}}
            {::lt/kohde {::kohde/id 3}}]))))

(deftest urakat-idlla
  (is (= {::lt/kohde {::kohde/urakat [{::ur/id 1}]}}
         (#'q/urakat-idlla
           1
           {::lt/kohde {::kohde/urakat [{::ur/id 1}
                                        {::ur/id 2}
                                        {::ur/id 3}]}})))

  (is (= {::lt/kohde {::kohde/urakat []}}
         (#'q/urakat-idlla
           4
           {::lt/kohde {::kohde/urakat [{::ur/id 1}
                                        {::ur/id 2}
                                        {::ur/id 3}]}}))))

(deftest liikennetapahtumien-haku
  (is (= [{::lt/kohde {::kohde/id 1 ::kohde/urakat [{::ur/id 1}]}}]
         (#'q/hae-liikennetapahtumat*
           [{::lt/kohde {::kohde/id 1}}
            {::lt/kohde {::kohde/id 3}}]
           (constantly [{::kohde/id 1 ::kohde/urakat [{::ur/id 1}
                                                      {::ur/id 2}]}
                        {::kohde/id 2 ::kohde/urakat [{::ur/id 4}
                                                      {::ur/id 5}
                                                      {::ur/id 6}]}])
           1))))

(deftest ilman-poistettuja
  (is (= [{:id 1 ::lt/alukset [{::m/poistettu? false :id 1}]}
          {:id 2
           ::lt/alukset []}]
         (into [] q/ilman-poistettuja-aluksia [{:id 1
                                                ::lt/alukset [{::m/poistettu? true}
                                                              {::m/poistettu? true}
                                                              {::m/poistettu? false :id 1}]}
                                               {:id 2
                                                ::lt/alukset [{::m/poistettu? true}
                                                              {::m/poistettu? true}]}]))))

(deftest vain-niput
  (is (= [{:id 1 ::lt/alukset [{::lt-alus/nippulkm 10}]}]
         (into [] q/vain-uittoniput [{:id 1 ::lt/alukset [{::lt-alus/nippulkm 10}
                                                          {:id 1}]}
                                     {:id 2 ::lt/alukset [{:id 2} {:id 3}]}]))))

(deftest hae-palvelumuodot
  (is (= [{::lt/id 1
           :foo :bar
           ::lt/toiminnot [{::toiminto/id 1}
                      {::toiminto/id 2}]}
          {::lt/id 2
           :foo :bar
           ::lt/toiminnot [{::toiminto/id 3}
                      {::toiminto/id 4}]}]
         (#'q/hae-tapahtumien-palvelumuodot*
           [{::lt/id 1
             ::lt/toiminnot [{::toiminto/id 1}
                        {::toiminto/id 2}]}
            {::lt/id 2
             ::lt/toiminnot [{::toiminto/id 3}
                        {::toiminto/id 4}]}]
           [{::lt/id 1
             :foo :bar}
            {::lt/id 2
             :foo :bar}]))))

(deftest hae-kohdetiedot
  (is (= [{::lt/id 1
           :foo :bar
           ::lt/kohde-id 1
           ::lt/kohde {::kohde/id 1
                       :baz :bar}}
          {::lt/id 2
           :foo :bar
           ::lt/kohde-id 1
           ::lt/kohde {::kohde/id 1
                       :baz :bar}}]
         (#'q/hae-tapahtumien-kohdetiedot*
           [{::kohde/id 1
             :baz :bar}]
           [{::lt/id 1
             :foo :bar
             ::lt/kohde-id 1}
            {::lt/id 2
             :foo :bar
             ::lt/kohde-id 1}]))))

(deftest hae-perustiedot
  (is (= [{::lt/id 1
           ::lt/alukset [{::m/poistettu? false
                          ::lt-alus/id 4
                          ::lt-alus/nippulkm 10}]}
          {::lt/id 2
           ::lt/alukset [{::m/poistettu? false
                          ::lt-alus/id 8
                          ::lt-alus/nippulkm 10}]}]
         (#'q/hae-tapahtumien-perustiedot*
           [{::lt/id 1
             ::lt/alukset [{::m/poistettu? true
                            ::lt-alus/id 1}
                           {::m/poistettu? false
                            ::lt-alus/id 2}
                           {::m/poistettu? true
                            ::lt-alus/id 3
                            ::lt-alus/nippulkm 10}
                           {::m/poistettu? false
                            ::lt-alus/id 4
                            ::lt-alus/nippulkm 10}]}
            {::lt/id 2
             ::lt/alukset [{::m/poistettu? true
                            ::lt-alus/id 5}
                           {::m/poistettu? false
                            ::lt-alus/id 6}
                           {::m/poistettu? true
                            ::lt-alus/id 7
                            ::lt-alus/nippulkm 10}
                           {::m/poistettu? false
                            ::lt-alus/id 8
                            ::lt-alus/nippulkm 10}]}]
           {:niput? true})))

  (is (= [{::lt/id 1
           ::lt/alukset [{::m/poistettu? false
                          ::lt-alus/id 2}
                         {::m/poistettu? false
                          ::lt-alus/id 4
                          ::lt-alus/nippulkm 10}]}
          {::lt/id 2
           ::lt/alukset [{::m/poistettu? false
                          ::lt-alus/id 6}
                         {::m/poistettu? false
                          ::lt-alus/id 8
                          ::lt-alus/nippulkm 10}]}]
         (#'q/hae-tapahtumien-perustiedot*
           [{::lt/id 1
             ::lt/alukset [{::m/poistettu? true
                            ::lt-alus/id 1}
                           {::m/poistettu? false
                            ::lt-alus/id 2}
                           {::m/poistettu? true
                            ::lt-alus/id 3
                            ::lt-alus/nippulkm 10}
                           {::m/poistettu? false
                            ::lt-alus/id 4
                            ::lt-alus/nippulkm 10}]}
            {::lt/id 2
             ::lt/alukset [{::m/poistettu? true
                            ::lt-alus/id 5}
                           {::m/poistettu? false
                            ::lt-alus/id 6}
                           {::m/poistettu? true
                            ::lt-alus/id 7
                            ::lt-alus/nippulkm 10}
                           {::m/poistettu? false
                            ::lt-alus/id 8
                            ::lt-alus/nippulkm 10}]}]
           {:niput? false}))))

(deftest kohteen-edellinen-tapahtuma
  (let [nyt {::lt/aika (t/now)}]
    (is (= nyt
           (#'q/hae-kohteen-edellinen-tapahtuma* [{::lt/aika (t/minus (t/now) (t/hours 5))}
                                                  nyt
                                                  {::lt/aika (t/minus (t/now) (t/hours 10))}])))))

(deftest kuittaamattomat-alukset
  (is (= {:ylos {::kohde/id 1
                 ::kohde/nimi "Pälli"
                 :edelliset-alukset [{::lt-alus/suunta :ylos
                                      ::lt-alus/id 1
                                      ::kohde/id 1
                                      ::kohde/nimi "Pälli"
                                      ::lt/id 1}
                                     {::lt-alus/suunta :ylos
                                      ::lt-alus/id 2
                                      ::kohde/id 1
                                      ::kohde/nimi "Pälli"
                                      ::lt/id 1}
                                     {::lt-alus/suunta :ylos
                                      ::lt-alus/id 4
                                      ::kohde/id 1
                                      ::kohde/nimi "Pälli"
                                      ::lt/id 3}]}
          :alas {::kohde/id 2
                 ::kohde/nimi "Soskua"
                 :edelliset-alukset [{::lt-alus/suunta :alas
                                      ::lt-alus/id 3
                                      ::kohde/id 2
                                      ::kohde/nimi "Soskua"
                                      ::lt/id 2}]}}
         (#'q/hae-kuittaamattomat-alukset* [{::ketjutus/alus {::lt-alus/suunta :ylos
                                                              ::lt-alus/id 1}
                                             ::ketjutus/kohteelta {::kohde/id 1
                                                                   ::kohde/nimi "Pälli"}
                                             ::ketjutus/tapahtumasta {::lt/id 1}}
                                            {::ketjutus/alus {::lt-alus/suunta :ylos
                                                              ::lt-alus/id 2}
                                             ::ketjutus/kohteelta {::kohde/id 1
                                                                   ::kohde/nimi "Pälli"}
                                             ::ketjutus/tapahtumasta {::lt/id 1}}
                                            {::ketjutus/alus {::lt-alus/suunta :ylos
                                                              ::lt-alus/id 4}
                                             ::ketjutus/kohteelta {::kohde/id 1
                                                                   ::kohde/nimi "Pälli"}
                                             ::ketjutus/tapahtumasta {::lt/id 3}}
                                            {::ketjutus/alus {::lt-alus/suunta :alas
                                                              ::lt-alus/id 3}
                                             ::ketjutus/kohteelta {::kohde/id 2
                                                                   ::kohde/nimi "Soskua"}
                                             ::ketjutus/tapahtumasta {::lt/id 2}}]))))

(deftest hae-seuraavat-kohteet
  (is (= [1 2 3]
         (#'q/hae-seuraavat-kohteet* [{:id 1} {:id 2} {:id 3}]))))

(deftest sama-suunta?
  (is (true? (#'q/sama-suunta? :a :a)))
  (is (true? (#'q/sama-suunta? :a "a")))
  (is (true? (#'q/sama-suunta? "a" :a)))
  (is (true? (#'q/sama-suunta? "a" "a")))

  (is (false? (#'q/sama-suunta? :b :a)))
  (is (false? (#'q/sama-suunta? :b "a")))
  (is (false? (#'q/sama-suunta? "b" :a)))
  (is (false? (#'q/sama-suunta? "b" "a")))
  (is (false? (#'q/sama-suunta? "b" nil)))
  (is (false? (#'q/sama-suunta? nil "a"))))