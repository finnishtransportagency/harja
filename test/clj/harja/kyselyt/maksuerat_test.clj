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

(deftest hae-urakan-maksueran-summat-mhu-urakalle
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        ; Talvihoito	23100	45
        ; Liikenneympäristön hoito	23110	46
        ; Soratien hoito	23120	47
        ; MHU ja HJU hoidon johto	23150	48
        ; Päällyste	20100	49
        ; MHU Ylläpito	20190	50

        ; MHU Korvausinvestointi	14300	51
      #_  odotettu #_[{:tpi_id 45, :urakka_id 36, :kokonaishintainen 6601.94M, :sanktiot -1100.97M, :bonukset 0.0M}
                  {:tpi_id 46, :urakka_id 36, :kokonaishintainen 2888.88M, :sanktiot -1000.77M, :bonukset 0.0M}
                  {:tpi_id 48, :urakka_id 36, :kokonaishintainen 1166.0M, :sanktiot -3000.0M, :bonukset 0.0M}
                  {:tpi_id 49, :urakka_id 36, :kokonaishintainen 11001.94M, :sanktiot 0.0M, :bonukset 0.0M}
                  {:tpi_id 50, :urakka_id 36, :kokonaishintainen 15401.94M, :sanktiot 0.0M, :bonukset 0.0M}
                  {:tpi_id 51, :urakka_id 36, :kokonaishintainen 13201.94M, :sanktiot 0.0M, :bonukset 0.0M}]

        ; MHU Korvausinvestointi	14300	51ﬂ
        odotettu [{:tpi_id 45, :urakka_id urakka-id, :kokonaishintainen 0.0M, :akillinen-hoitotyo nil, :vahinkojen-korjaukset nil, :lisatyot nil, :sanktiot 0.0M, :bonukset 0.0M}
                  {:tpi_id 46, :urakka_id urakka-id, :kokonaishintainen 2888.88M, :akillinen-hoitotyo nil, :vahinkojen-korjaukset nil, :lisatyot nil, :sanktiot 0.0M, :bonukset 0.0M}
                  {:tpi_id 47, :urakka_id urakka-id, :kokonaishintainen 0.0M, :akillinen-hoitotyo nil, :vahinkojen-korjaukset nil, :lisatyot nil, :sanktiot 0.0M, :bonukset 0.0M}
                  {:tpi_id 48, :urakka_id urakka-id, :kokonaishintainen 666.0M, :akillinen-hoitotyo nil, :vahinkojen-korjaukset nil, :lisatyot nil, :sanktiot 0.0M, :bonukset 0.0M}
                  {:tpi_id 49, :urakka_id urakka-id, :kokonaishintainen 0.0M, :akillinen-hoitotyo nil, :vahinkojen-korjaukset nil, :lisatyot nil, :sanktiot 0.0M, :bonukset 0.0M}
                  {:tpi_id 50, :urakka_id urakka-id, :kokonaishintainen 0.0M, :akillinen-hoitotyo nil, :vahinkojen-korjaukset nil, :lisatyot nil, :sanktiot 0.0M, :bonukset 0.0M}
                  {:tpi_id 51, :urakka_id urakka-id, :kokonaishintainen 0.0M, :akillinen-hoitotyo nil, :vahinkojen-korjaukset nil, :lisatyot nil, :sanktiot 0.0M, :bonukset 0.0M}]


        vastaus (vec (maksuerat-q/hae-urakan-maksueran-summat db urakka-id))]
    (is (= odotettu vastaus))))