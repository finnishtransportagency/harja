(ns harja.kyselyt.kevyen-liikenteen-talvisuola-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]))

(use-fixtures :once tietokanta-fixture)

(defn suolaa-kelvilla-kysely [urakka alku loppu]
  (q-map (str "SELECT rp.talvihoitoluokka, SUM(mat.maara) AS maara FROM toteuman_reittipisteet trp join toteuma t ON t.id = trp.toteuma join lateral unnest(trp.reittipisteet) rp ON TRUE join lateral unnest(rp.materiaalit) mat ON TRUE WHERE t.urakka = " urakka " AND t.alkanut BETWEEN '" alku "'::DATE AND '" loppu "'::DATE GROUP BY rp.talvihoitoluokka")))

(deftest siirra-suola-pois-kelveilta
  (let [oulun-au-id (hae-oulun-alueurakan-2014-2019-id)
        suolaa-kelveilla-ennen (suolaa-kelvilla-kysely oulun-au-id
                           "2018-10-01"
                           "2019-09-30")
        mat-yht-ennen (apply + (map :maara suolaa-kelveilla-ennen))
        mat-kelvi-ennen (apply + (map :maara (filter #(#{9, 10, 11} (:talvihoitoluokka %)) suolaa-kelveilla-ennen)))
        _poista-suola (q (str "SELECT siirra_talvisuola_kelvilta(null, '2018-10-01', '2019-09-30')"))
        suolaa-kelveilla-jalkeen (suolaa-kelvilla-kysely oulun-au-id
                           "2018-10-01"
                           "2019-09-30")
        mat-yht-jalkeen (apply + (map :maara suolaa-kelveilla-jalkeen))
        mat-kelvi-jalkeen (apply + (map :maara (filter #(#{9, 10, 11} (:talvihoitoluokka %)) suolaa-kelveilla-jalkeen)))]
      (is (< 0 mat-kelvi-ennen))
      (is (zero? mat-kelvi-jalkeen))
      (is (= mat-yht-ennen mat-yht-jalkeen))))