(ns harja.palvelin.integraatiot.api.tyokalut-test
  (:require [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]))

(deftest usean-vapaan-idn-haku-toimii []
  (loop [index 0]
    (let [idt (tyokalut/hae-usea-vapaa-toteuma-ulkoinen-id 10)]
      (is (= (distinct idt) idt)))
    (when (< index 10)
      (recur (inc index)))))
