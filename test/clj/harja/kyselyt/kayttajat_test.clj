(ns harja.kyselyt.kayttajat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.kayttajat :as kayttajat-kysely]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (urakkatieto-alustus!)
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (urakkatieto-lopetus!))

(use-fixtures :once jarjestelma-fixture)

(defn- poista-kayttaja [kayttajanimi]
  (u (str "DELETE FROM kayttaja WHERE kayttajanimi = '" kayttajanimi "';")))

(defn luo-kayttaja [puhelin]
  (u (str "INSERT INTO kayttaja (kayttajanimi, etunimi, sukunimi, sahkoposti, puhelin, organisaatio) VALUES
                ('peke', 'Pekka', 'Päivystäjä', 'peke@example.org', " puhelin ",
                (SELECT id FROM organisaatio WHERE nimi = 'YIT Rakennus Oy'));")))

(deftest onko-kayttaja-urakan-organisaatiossa-on-test
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        db (:db jarjestelma)
        puhelin "123123123"]

    (testing "Käyttäjä on urakan organisaatiossa matkapuhelimella"
      (let [;; Deletoidaan varalta ja luodaan ensin käyttäjä, jota haetaan
            _ (poista-kayttaja "peke")
            _ (luo-kayttaja puhelin)
            ilmoitus {:ilmoittaja {:etunimi "Pekka"
                                   :sukunimi "Päivystäjä"
                                   :matkapuhelin puhelin}}
            loytyy? (kayttajat-kysely/onko-kayttaja-nimella-urakan-organisaatiossa? db urakka-id ilmoitus)]
        (is (= true loytyy?))))
    (testing "Käyttäjä on urakan organisaatiossa työpuhelimella"
      (let [;; Deletoidaan varalta ja luodaan ensin käyttäjä, jota haetaan
            _ (poista-kayttaja "peke")
            _ (luo-kayttaja puhelin)
            ilmoitus {:ilmoittaja {:etunimi "Pekka"
                                   :sukunimi "Päivystäjä"
                                   :tyopuhelin puhelin}}
            loytyy? (kayttajat-kysely/onko-kayttaja-nimella-urakan-organisaatiossa? db urakka-id ilmoitus)]
        (is (= true loytyy?))))
    (testing "Käyttäjä on urakan organisaatiossa, koska ei annettu puhelinta"
      (let [;; Deletoidaan varalta ja luodaan ensin käyttäjä, jota haetaan
            _ (poista-kayttaja "peke")
            _ (luo-kayttaja puhelin)
            ilmoitus {:ilmoittaja {:etunimi "Pekka"
                                   :sukunimi "Päivystäjä"}}
            loytyy? (kayttajat-kysely/onko-kayttaja-nimella-urakan-organisaatiossa? db urakka-id ilmoitus)]
        (is (= true loytyy?))))))

(deftest onko-kayttaja-urakan-organisaatiossa-eiole-test
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        db (:db jarjestelma)
        puhelin "123123123"]

    (testing "Käyttäjä ei urakan organisaatiossa, koska etunimi ei täsmää"
      (let [;; Deletoidaan varalta ja luodaan ensin käyttäjä, jota haetaan
            _ (poista-kayttaja "peke")
            _ (luo-kayttaja puhelin)
            ilmoitus {:ilmoittaja {:etunimi "Pekka-eitäsmää"
                                   :sukunimi "Päivystäjä"
                                   :matkapuhelin puhelin}}
            loytyy? (kayttajat-kysely/onko-kayttaja-nimella-urakan-organisaatiossa? db urakka-id ilmoitus)]
        (is (= false loytyy?))))
    (testing "Käyttäjä ei urakan organisaatiossa, koska sukunimi ei täsmää"
      (let [;; Deletoidaan varalta ja luodaan ensin käyttäjä, jota haetaan
            _ (poista-kayttaja "peke")
            _ (luo-kayttaja puhelin)
            ilmoitus {:ilmoittaja {:etunimi "Pekka"
                                   :sukunimi "Päivystäjä-eitäsmää"
                                   :tyopuhelin puhelin}}
            loytyy? (kayttajat-kysely/onko-kayttaja-nimella-urakan-organisaatiossa? db urakka-id ilmoitus)]
        (is (= false loytyy?))))
    (testing "Käyttäjä ei urakan organisaatiossa, koska puhelin ei täsmää"
      (let [;; Deletoidaan varalta ja luodaan ensin käyttäjä, jota haetaan
            _ (poista-kayttaja "peke")
            _ (luo-kayttaja puhelin)
            ilmoitus {:ilmoittaja {:etunimi "Pekka"
                                   :sukunimi "Päivystäjä"
                                   :tyopuhelin "556677"}}
            loytyy? (kayttajat-kysely/onko-kayttaja-nimella-urakan-organisaatiossa? db urakka-id ilmoitus)]
        (is (= false loytyy?))))))
