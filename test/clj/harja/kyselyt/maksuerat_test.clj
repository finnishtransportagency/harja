(ns harja.kyselyt.maksuerat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.maksuerat :as maksuerat-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]))

(use-fixtures :once tietokantakomponentti-fixture)

(deftest hae-urakan-maksueran-summat-yksikkohintaiset-summat-kanavaurakalle
  (let [db (:db jarjestelma)
        urakka-id (ffirst (q "select id from urakka where nimi = 'Saimaan kanava';"))
        odotettu [{:kokonaishintainen 13030.0M
                   :lisatyo 1545.000000000000000000000000M
                   :muu 9000M
                   :sakko 5000M
                   :tpi_id (ffirst (q "SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP' and urakka = '" urakka-id "';"))}
                  {:kokonaishintainen 2000M
                   :lisatyo 0M
                   :muu 1000M
                   :sakko 0M
                   :tpi_id (ffirst (q "SELECT id FROM toimenpideinstanssi WHERE nimi = 'Testitoimenpideinstanssi' and urakka = '" urakka-id "';"))}]]
    (is (= odotettu (vec (maksuerat-q/hae-urakan-maksueran-summat db urakka-id))))))

;; HUOM: Tämä testi failasi alunperin PostgreSQL versiolla 13, mutta ei versioilla 11 tai 12
;;       Testin tuloksia kannattaa seurata. Korjaus tehtiin järjestemällä palautettu vastausvektori tpi_id mukaisesti.
(deftest hae-urakan-maksueran-summat-yksikkohintaiset-summat--teiden-hoidon-urakalle
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        odotettu [{:akillinen-hoitotyo 0.0M
                   :bonus 21000.0M
                   :indeksi 2005.0065862068957647290000000M
                   :kokonaishintainen 42010.0M
                   :lisatyo 0.0M
                   :muu 11000.0M
                   :sakko -31526.66600M
                   :tpi_id 4
                   :urakka_id 4
                   :yksikkohintainen 0.0M}
                  {:akillinen-hoitotyo 0.0M
                   :bonus 0.0M
                   :indeksi 2236.177203065134370450M
                   :kokonaishintainen 0.0M
                   :lisatyo 0.0M
                   :muu 0.0M
                   :sakko -2434.0M
                   :tpi_id 5
                   :urakka_id 4
                   :yksikkohintainen 7882.50M}
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
    (is (= odotettu (vec
                      (sort-by :tpi_id
                        (maksuerat-q/hae-urakan-maksueran-summat db urakka-id)))))))

;; HUOM: Tämä testi failasi alunperin PostgreSQL versiolla 13, mutta ei versioilla 11 tai 12
;;       Testin tuloksia kannattaa seurata. Korjaus tehtiin järjestemällä palautettu vastausvektori tpi_id mukaisesti.
(deftest hae-urakan-maksueran-summat-mhu-urakalle
  (let [db (:db jarjestelma)
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        ;; 48	Oulu MHU Talvihoito TP
        ;; 49	Oulu MHU Liikenneympäristön hoito TP
        ;; 50	Oulu MHU Soratien hoito TP
        ;; 51	Oulu MHU Hallinnolliset toimenpiteet TP
        ;; 52	Oulu MHU Päällystepaikkaukset TP
        ;; 53	Oulu MHU MHU Ylläpito TP
        ;; 54	Oulu MHU MHU Korvausinvestointi TP
        ;; Nämä summat ikävä kyllä vaihtuu joka kerta, kun indeksit vaihtuu
        ;; Eli voit olettaa päivittäväsi näitä summia aina 1.10. joka vuosi
        odotettu [{:kokonaishintainen 4150.791430M
                   :tpi_id 45
                   :urakka_id 35}
                  {:kokonaishintainen 6251.487630M
                   :tpi_id 46
                   :urakka_id 35}
                  {:kokonaishintainen 8801.94M
                   :tpi_id 47
                   :urakka_id 35}
                  {:kokonaishintainen 5544.254000M
                   :tpi_id 48
                   :urakka_id 35}
                  {:kokonaishintainen 11001.94M
                   :tpi_id 49
                   :urakka_id 35}
                  {:kokonaishintainen 16401.94M
                   :tpi_id 50
                   :urakka_id 35}
                  {:kokonaishintainen 13201.94M
                   :tpi_id 51
                   :urakka_id 35}]
        vastaus (vec
                  (sort-by :tpi_id
                    (maksuerat-q/hae-urakan-maksueran-summat db urakka-id)))]
    (is (= vastaus odotettu))))
