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
                   :indeksi 2046.1943256704972237290000000M
                   :kokonaishintainen 42010.0M
                   :lisatyo 2000.0M
                   :muu 11000.0M
                   :sakko -31526.66600M
                   :tpi_id 4
                   :urakka_id 4
                   :yksikkohintainen 2000.0M}
                  {:akillinen-hoitotyo 3000.0M
                   :bonus 0.0M
                   :indeksi 2363.57183908045982945000M
                   :kokonaishintainen 0.0M
                   :lisatyo 10000.0M
                   :muu 1000.0M
                   :sakko -2434.0M
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
    (is (= odotettu (vec
                      (sort-by :tpi_id
                        (maksuerat-q/hae-urakan-maksueran-summat db urakka-id)))))))

;; HUOM: Tämä testi failasi alunperin PostgreSQL versiolla 13, mutta ei versioilla 11 tai 12
;;       Testin tuloksia kannattaa seurata. Korjaus tehtiin järjestemällä palautettu vastausvektori tpi_id mukaisesti.
(deftest hae-urakan-maksueran-summat-mhu-urakalle
         (let [db (:db jarjestelma)
               urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
               ; Talvihoito	23100	45
               ; Liikenneympäristön hoito	23110	46
               ; Soratien hoito	23120	47
               ; MHU ja HJU hoidon johto	23150	48
               ; Päällyste	20100	49
               ; MHU Ylläpito	20190	50
               ; MHU Korvausinvestointi	14300	51
               ;; Nämä summat ikävä kyllä vaihtuu joka kerta, kun indeksit vaihtuu
               ;; Eli voit olettaa päivittäväsi näitä summia aina 1.10. joka vuosi
               odotettu [{:kokonaishintainen 4150.791430M
                          :tpi_id 48
                          :urakka_id 35}
                         {:kokonaishintainen 6251.487630M
                          :tpi_id 49
                          :urakka_id 35}
                         {:kokonaishintainen 8801.94M
                          :tpi_id 50
                          :urakka_id 35}
                         {:kokonaishintainen 5814.654000M
                          :tpi_id 51
                          :urakka_id 35}
                         {:kokonaishintainen 11001.94M
                          :tpi_id 52
                          :urakka_id 35}
                         {:kokonaishintainen 15401.94M
                          :tpi_id 53
                          :urakka_id 35}
                         {:kokonaishintainen 13201.94M
                          :tpi_id 54
                          :urakka_id 35}]
               vastaus (vec
                         (sort-by :tpi_id
                           (maksuerat-q/hae-urakan-maksueran-summat db urakka-id)))]
              (is (= vastaus odotettu))))
