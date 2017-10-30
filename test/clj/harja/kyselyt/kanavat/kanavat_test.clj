(ns harja.kyselyt.kanavat.kanavat-test
  (:require [clojure.test :refer :all]
            [harja.kyselyt.kanavat.kanavat :as q]

            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.kanavat.kanava :as kanava]))

(deftest kohteiden-urakkatiedot
  (is (= [{::kohde/id 1 ::kohde/urakat [{:foo :bar}
                                        {:foo :baz}]}
          {::kohde/id 2 ::kohde/urakat [{:foo :baz}]}
          {::kohde/id 3 ::kohde/urakat nil}]
         (#'q/hae-kohteiden-urakkatiedot*
           [{::kohde/id 1}
            {::kohde/id 2}
            {::kohde/id 3}]
           [{::kohde/kohde-id 1 ::kohde/linkin-urakka {:foo :bar}}
            {::kohde/kohde-id 1 ::kohde/linkin-urakka {:foo :baz}}
            {::kohde/kohde-id 2 ::kohde/linkin-urakka {:foo :baz}}]))))

(deftest hae-kanavat
  (is (= [{::kanava/id 1 ::kanava/kohteet 1}
          {::kanava/id 2 ::kanava/kohteet 2}]
         (#'q/hae-kanavat-ja-kohteet*
           [{::kanava/id 1 ::kanava/kohteet 0}
            {::kanava/id 2 ::kanava/kohteet 1}]
           inc))))