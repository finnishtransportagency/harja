(ns harja.kyselyt.kanavat.liikennetapahtumat-test
  (:require [clojure.test :refer :all]
            [harja.kyselyt.kanavat.liikennetapahtumat :as q]

            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.urakka :as ur]))

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