(ns harja.palvelin.palvelut.hallinta.urakoiden-toimenkuvat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.hallinta.toimenkuvat-palvelu :as toimenkuvat-palvelu]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja
             [testi :refer :all]]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :toimenkuvat-hallinta (component/using
                                    (toimenkuvat-palvelu/->ToimenkuvatHallinta)
                                    [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-kaikki-toimenkuvat
  (let [tietokantaan-lisatty-maara 10
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                :hae-toimenkuvat +kayttaja-jvh+ {})
        toimenkuvat (:toimenkuvat tulos)]
    (is (= tietokantaan-lisatty-maara (count toimenkuvat)))))

(deftest hae-urakan-toimenkuvat
  (let [;; Toimenkuvat kuuluu teiden-hoito tyyppisille urakoille - Aloitetaan 21 vuosimallista
        urakka-id-2021 (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakka-id-2022 (hae-urakan-id-nimella "Tampereen MHU 2022-2026")
        urakka-id-2024 (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")

        ;; Eri vuosina alkanevilla urakoilla voi olla eri määrä toimenkuvia
        tietokantaan-lisatty-maara-2021 7
        tietokantaan-lisatty-maara-2022 7
        tietokantaan-lisatty-maara-2024 7
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                :hae-toimenkuvat +kayttaja-jvh+ {})
        toimenkuvat-2021 (filter #(= urakka-id-2021 (:urakka-id %)) (:urakoiden-toimenkuvat tulos))
        toimenkuvat-2022 (filter #(= urakka-id-2022 (:urakka-id %)) (:urakoiden-toimenkuvat tulos))
        toimenkuvat-2024 (filter #(= urakka-id-2024 (:urakka-id %)) (:urakoiden-toimenkuvat tulos))]

    ;; Jos default rahavarauksia muutetaan, niin tämä tulee failaamaan.
    ;; 2024 alkavilla urakoilla voi olla eri määrä rahavarauksia ja ne pitää silloin ottaa tässä huomioon
    (is (= tietokantaan-lisatty-maara-2021 (count toimenkuvat-2021)))
    (is (= tietokantaan-lisatty-maara-2022 (count toimenkuvat-2022)))
    (is (= tietokantaan-lisatty-maara-2024 (count toimenkuvat-2024)))))

(deftest lisaa-urakalle-toimenkuva
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")

        ;; Toimenkuvat ennen lisäämistä
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                :hae-toimenkuvat +kayttaja-jvh+ {})

        toimenkuvat-kaikki-ennen (:toimenkuvat tulos)
        urakan-toimenkuvat-ennen (filter #(= urakka-id (:urakka-id %)) (:urakoiden-toimenkuvat tulos))

        ;; Lisätään uusi toimenkuva tietokantaan
        lisattava-toimenkuva {:id -1 :nimi "Uusi toimenkuva" :urakka urakka-id :valittu? nil}
        lisaa-uusi-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :paivita-urakan-toimenkuva +kayttaja-jvh+ lisattava-toimenkuva)
        toimenkuvat-kaikki-jalkeen (:toimenkuvat lisaa-uusi-vastaus)
        uusin-toimenkuva-db (last toimenkuvat-kaikki-jalkeen)

        ;; Valitaan uusi toimenkuva urakalle
        valittava-toimenkuva {:id (:id uusin-toimenkuva-db) :urakka urakka-id :valittu? true}
        valittu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :paivita-urakan-toimenkuva +kayttaja-jvh+ valittava-toimenkuva)

        urakan-toimenkuvat-jalkeen (filter #(= urakka-id (:urakka-id %)) (:urakoiden-toimenkuvat valittu-vastaus))]
    
    (is (= (+ 1 (count toimenkuvat-kaikki-ennen)) (count toimenkuvat-kaikki-jalkeen)))
    (is (= (+ 1 (count urakan-toimenkuvat-ennen)) (count urakan-toimenkuvat-jalkeen)))))

(deftest lisaa-urakalle-null-toimenkuva
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")

        ;; Lisätään uusi NULL toimenkuva tietokantaan
        null-toimenkuva {:id -1 :nimi nil :urakka urakka-id :valittu? nil}
        tyhja-toimenkuva {:id -1 :nimi "" :urakka urakka-id :valittu? nil}
        melkein-tyhja-toimenkuva {:id -1 :nimi "    " :urakka urakka-id :valittu? nil}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                             :paivita-urakan-toimenkuva +kayttaja-jvh+ null-toimenkuva)))
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                             :paivita-urakan-toimenkuva +kayttaja-jvh+ tyhja-toimenkuva)))
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                             :paivita-urakan-toimenkuva +kayttaja-jvh+ melkein-tyhja-toimenkuva)))))
