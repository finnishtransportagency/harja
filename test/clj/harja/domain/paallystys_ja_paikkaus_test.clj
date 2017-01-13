(ns harja.domain.paallystys-ja-paikkaus-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]))

(deftest muutos-kokonaishintaan-laskettu-oikein
  (let [tyot [{:tilattu-maara 10 :toteutunut-maara 15 :yksikkohinta 1}
              {:tilattu-maara 15 :toteutunut-maara 15  :yksikkohinta 666}
              {:tilattu-maara 4 :toteutunut-maara 5 :yksikkohinta 8}]
        tyot2 [{:tilattu-maara 4 :toteutunut-maara 2 :yksikkohinta 15}]]
    (is (= (paallystys-ja-paikkaus/summaa-maaramuutokset tyot) 13))
    (is (= (paallystys-ja-paikkaus/summaa-maaramuutokset tyot2) -30))))