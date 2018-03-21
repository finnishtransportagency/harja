(ns harja.kyselyt.maksuerat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.maksuerat :as maksuerat-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(deftest hae-urakan-maksueran-summat-yksikkohintaiset-summat-kanavaurakalle
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        urakka-id (ffirst (q "select id from urakka where nimi = 'Saimaan kanava';"))
        odotettu [{:kokonaishintainen 13030.0M
                   :lisatyo 1545.000000000000000000000000M
                   :muu 9000M
                   :sakko 5000M
                   :tpi_id 42}
                  {:kokonaishintainen 2000M
                   :lisatyo 0M
                   :muu 1000M
                   :sakko 0M
                   :tpi_id 43}]]
    (is (= odotettu (vec (maksuerat-q/hae-urakan-maksueran-summat db urakka-id))))))

(deftest hae-urakan-maksueran-summat-yksikkohintaiset-summat--teiden-hoidon-urakalle
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        urakka-id (ffirst (q "select id from urakka where nimi = 'Oulun alueurakka 2014-2019';"))
        odotettu [{:akillinen-hoitotyo 0.0M
                   :bonus 21000.0M
                   :indeksi 8345.2044093231159362450000000M
                   :kokonaishintainen 42010.0M
                   :lisatyo 2000.0M
                   :muu 11000.0M
                   :sakko -31526.66600M
                   :tpi_id 4
                   :urakka_id 4
                   :yksikkohintainen 2000.0M}
                  {:akillinen-hoitotyo 3000.0M
                   :bonus 0.0M
                   :indeksi 2410.41666666666671345000M
                   :kokonaishintainen 0.0M
                   :lisatyo 10000.0M
                   :muu 1000.0M
                   :sakko -1434.0M
                   :tpi_id 5
                   :urakka_id 4
                   :yksikkohintainen 11882.50M}
                  {:akillinen-hoitotyo 0.0M
                   :bonus 0.0M
                   :indeksi -1616.36015325670619390000M
                   :kokonaishintainen 120000.0M
                   :lisatyo 0.0M
                   :muu 0.0M
                   :sakko -22860.0M
                   :tpi_id 6
                   :urakka_id 4
                   :yksikkohintainen 0.0M}]]
    (is (= odotettu (vec (maksuerat-q/hae-urakan-maksueran-summat db urakka-id))))))

