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
    ;; -- Laskut menee oikein --

    ;; Toteutuneiden määrien lasku toimii oikein
    (is (= (:tulos (paallystys-ja-paikkaus/summaa-maaramuutokset tyot)) 13))
    (is (= (:tulos (paallystys-ja-paikkaus/summaa-maaramuutokset tyot2)) -30))
    ;; Sekä ennustettu että toteutunut, toteutunut on vahvempi
    (is (= (:tulos (paallystys-ja-paikkaus/summaa-maaramuutokset tyot3)) -30))
    ;; Vain ennustettu
    (is (= (:tulos (paallystys-ja-paikkaus/summaa-maaramuutokset tyot4)) 1))

    ;; -- Ennustettu tieto näytetään oikein --

    ;; Sekä ennustettu että toteutunut, ennustetta ei huomioida, tulos ei ennustettu
    (is (false? (:ennustettu? (paallystys-ja-paikkaus/summaa-maaramuutokset tyot3))))
    ;; Vain ennuste, joten sitä käytetään laskussa. Ennuste on true.
    (is (true? (:ennustettu? (paallystys-ja-paikkaus/summaa-maaramuutokset tyot4))))

    ;; -- Eipä kaaduta outoihin syötteisiin --
    (is (= (paallystys-ja-paikkaus/summaa-maaramuutokset nil)
    {:tulos 0 :ennustettu? false}))))