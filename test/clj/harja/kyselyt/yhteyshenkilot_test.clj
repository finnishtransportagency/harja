(ns harja.kyselyt.yhteyshenkilot-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]))

(use-fixtures :once tietokantakomponentti-fixture)

(defn tee-paivystys [vastuuhenkilo varahenkilo yhteyshenkilo urakka]
  (u (format "INSERT INTO paivystys (vastuuhenkilo, varahenkilo, yhteyshenkilo, urakka, alku, loppu)
  VALUES (%s, %s, %s, %s, (SELECT now() - INTERVAL '1 day'), (SELECT now() + INTERVAL '1 day'));"
             vastuuhenkilo, varahenkilo, yhteyshenkilo, urakka)))

(defn poista-paivystykset [urakka]
  (u (format "DELETE FROM paivystys WHERE urakka = %s" urakka)))

(defn ovatko-sama-paivystaja? [oletettu-paivystaja paivystaja]
  (and (= (nth oletettu-paivystaja 1) (:etunimi paivystaja))
       (= (nth oletettu-paivystaja 2) (:sukunimi paivystaja))
       (= (nth oletettu-paivystaja 3) (:tyopuhelin paivystaja))
       (= (nth oletettu-paivystaja 4) (:matkapuhelin paivystaja))
       (= (nth oletettu-paivystaja 5) (:sahkoposti paivystaja))))

(deftest tarkista-taman-hetkisen-paivystajan-haku
  (let [db (:db jarjestelma)
        urakka (first (first (q "SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012';")))
        yhteyshenkilot (q "SELECT id, etunimi, sukunimi, tyopuhelin, matkapuhelin, sahkoposti FROM yhteyshenkilo LIMIT 3;")]

    (tee-paivystys true false (first (first yhteyshenkilot)) urakka)
    (let [paivystaja (first (yhteyshenkilot/hae-urakan-tamanhetkiset-paivystajat db urakka))
          oletettu-paivystaja (first yhteyshenkilot)]
      (is (ovatko-sama-paivystaja? oletettu-paivystaja paivystaja) "Päivystäjä valittu, kun kannassa vain yksi päivystäjä")
      (poista-paivystykset urakka))

    (tee-paivystys false false (first (first yhteyshenkilot)) urakka)
    (tee-paivystys true false (first (second yhteyshenkilot)) urakka)
    (let [paivystajat (yhteyshenkilot/hae-urakan-tamanhetkiset-paivystajat db urakka)]
      (is (= 2 (count paivystajat)) "Molemmat vastuuhenkilö ja varahenkilö haettu oikein")
      (poista-paivystykset urakka))))
