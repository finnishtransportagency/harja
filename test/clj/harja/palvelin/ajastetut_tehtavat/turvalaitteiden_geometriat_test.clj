(ns harja.palvelin.ajastetut-tehtavat.turvalaitteiden-geometriat-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.ajastetut-tehtavat.turvalaitteiden-geometriat :as tg]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))


(deftest tarkista-paivitysehdot
  (let [db (tietokanta/luo-tietokanta testitietokanta)]
    (u "INSERT INTO geometriapaivitys (nimi) VALUES ('turvalaitteet') ON CONFLICT(nimi) DO NOTHING;")

    (u "UPDATE geometriapaivitys SET viimeisin_paivitys = NULL WHERE nimi = 'turvalaitteet';")
    (is (tg/paivitys-tarvitaan? db 10) "Päivitys tarvitaan, kun sitä ei ole koskaan tehty")

    (u "UPDATE geometriapaivitys SET viimeisin_paivitys = now() - interval '10' day WHERE nimi = 'turvalaitteet';")
    (is (tg/paivitys-tarvitaan? db 10) "Päivitys tarvitaan, kun se on viimeksi tehty tarpeeksi kauan sitten");

    (u "UPDATE geometriapaivitys SET viimeisin_paivitys = now() - interval '1' day WHERE nimi = 'turvalaitteet';")
    (is (false? (tg/paivitys-tarvitaan? db 10)) "Päivitystä ei tarvita, kun se on tehty tarpeeksi vasta")))




