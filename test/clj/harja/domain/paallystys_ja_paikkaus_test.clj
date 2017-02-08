(ns harja.domain.paallystys-ja-paikkaus-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]))

(deftest muutos-kokonaishintaan-laskettu-oikein
  (let [tyot [{:tilattu-maara 10 :toteutunut-maara 15 :yksikkohinta 1}
              {:tilattu-maara 15 :toteutunut-maara 15  :yksikkohinta 666}
              {:tilattu-maara 4 :toteutunut-maara 5 :yksikkohinta 8}]
        tyot2 [{:tilattu-maara 4 :toteutunut-maara 2 :yksikkohinta 15}]
        tyot3 [{:tilattu-maara 4 :ennustettu-maara 666 :toteutunut-maara 2 :yksikkohinta 15}]
        tyot4 [{:tilattu-maara 1 :ennustettu-maara 2 :yksikkohinta 1}]]
    ;; Toteutuneiden määrien lasku toimii oikein
    (is (= (paallystys-ja-paikkaus/summaa-maaramuutokset tyot) 13))
    (is (= (paallystys-ja-paikkaus/summaa-maaramuutokset tyot2) -30))
    ;; Sekä ennustettu että toteutunut, toteutunut on vahvempi
    (is (= (paallystys-ja-paikkaus/summaa-maaramuutokset tyot3) -30))
    ;; Vain ennustettu
    (is (= (paallystys-ja-paikkaus/summaa-maaramuutokset tyot4) 1))))