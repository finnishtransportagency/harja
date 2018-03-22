(ns harja.kyselyt.kanavat.kohteet-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.kanavat.kohteet :as q]

            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohdekokonaisuus :as kok]))

(deftest kohteiden-urakkatiedot
  (is (= [{::kohde/id 1 ::kohde/urakat [{:foo :bar}
                                        {:foo :baz}]}
          {::kohde/id 2 ::kohde/urakat [{:foo :baz}]}
          {::kohde/id 3 ::kohde/urakat nil}]
         (#'q/hae-kohteiden-urakkatiedot*
           +kayttaja-jvh+
           [{::kohde/id 1}
            {::kohde/id 2}
            {::kohde/id 3}]
           [{::kohde/kohde-id 1 ::kohde/linkin-urakka {:foo :bar}}
            {::kohde/kohde-id 1 ::kohde/linkin-urakka {:foo :baz}}
            {::kohde/kohde-id 2 ::kohde/linkin-urakka {:foo :baz}}]))))

(deftest hae-kokonaisuudet-ja-kohteet
  (is (= [{::kok/id 1 ::kok/kohteet 1}
          {::kok/id 2 ::kok/kohteet 2}]
         (#'q/hae-kokonaisuudet-ja-kohteet*
           [{::kok/id 1 ::kok/kohteet 0}
            {::kok/id 2 ::kok/kohteet 1}]
           inc))))