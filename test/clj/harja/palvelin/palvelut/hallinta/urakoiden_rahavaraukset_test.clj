(ns harja.palvelin.palvelut.hallinta.urakoiden-rahavaraukset-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.hallinta.rahavaraukset :as rahavaraukset]
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
          :rahavaraukset-hallinta (component/using
                                    (rahavaraukset/->RahavarauksetHallinta)
                                    [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-kaikki-rahavaraukset
  (let [tietokantaan-lisatty-maara 13
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                :hae-rahavaraukset +kayttaja-jvh+ {})
        rahavaraukset (:rahavaraukset tulos)]
    (is (= tietokantaan-lisatty-maara (count rahavaraukset)))))

(deftest hae-urakan-rahavaraukset
  (let [;; Rahavaraukset kuuluu teiden-hoito tyyppisille urakoille
        mhurakat (q-map "SELECT id FROM urakka WHERE tyyppi = 'teiden-hoito';")
        tietokanta-urakoiden-maara (count mhurakat)
        ;; Tällä hetkellä on määritelty, että kaikilla urakoilla on 3 rahavarausta ja muutamalla on pari enemmän.
        ;; Tämä on kovakoodattu määrä ja jos se muuttuu, niin tämä testi failaa.
        tietokantaan-lisatty-maara 38
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                :hae-rahavaraukset +kayttaja-jvh+ {})]
    ;; Jos default rahavarauksia muutetaan, niin tämä tulee failaamaan.
    ;; 2024 alkavilla urakoilla voi olla eri määrä rahavarauksia ja ne pitää silloin ottaa tässä huomioon
    (is (= tietokantaan-lisatty-maara (count (:urakoiden-rahavaraukset tulos))))))

(deftest lisaa-rahavaukselle-tehtava
  (let [;; Käytetään testaamisessa rahavarausta: Äkilliset hoitotyöt
        ;; Tiedämme, että tehtäviä pitäisi olla kolme
        akillisten-tehtava-maara 3
        ;; Rahavarauksen id
        rahavaraus-id (:id (first (q-map "SELECT id FROM rahavaraus WHERE nimi LIKE '%Äkilliset hoitotyöt%';")))
        tehtavat-tietokananssa (q-map (format "SELECT id FROM rahavaraus_tehtava WHERE rahavaraus_id = %s;" rahavaraus-id))
        rahavaraukset-tehtavineen (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-rahavaraukset-tehtavineen +kayttaja-jvh+ {})
        akillisten-tehtavat (:tehtavat (first (filter #(= rahavaraus-id (:id %)) rahavaraukset-tehtavineen)))

        ;; Lisätään yksi tehtävä
        ;; Mutta haetaan ensin joku random tehtävä, joka on tietokannassa, koska keksittyä tehtävää ei voi rahavaraukselle lisätä
        tehtava (first (q-map "SELECT t.id, t.nimi FROM tehtava t
                                              JOIN tehtavaryhma tr ON t.tehtavaryhma = tr.id
                                              JOIN tehtavaryhmaotsikko tro ON tro.id = tr.tehtavaryhmaotsikko_id
                                        WHERE t.poistettu IS FALSE
                                          AND t.tehtavaryhma IS NOT NULL
                                        ORDER BY t.id DESC
                                        LIMIT 1"))

        rahavaraukset-tehtavineen2 (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-rahavarauksen-tehtava +kayttaja-jvh+
                                    {:vanha-tehtava-id 0
                                     :uusi-tehtava tehtava
                                     :rahavaraus-id rahavaraus-id})

        akillisten-tehtavat2 (:tehtavat (first (filter #(= rahavaraus-id (:id %)) rahavaraukset-tehtavineen2)))]

    (is (= akillisten-tehtava-maara (count tehtavat-tietokananssa)))
    (is (= (count akillisten-tehtavat) (count tehtavat-tietokananssa)))
    (is (= (count akillisten-tehtavat2) (+ 1 (count tehtavat-tietokananssa))))))

(deftest lisaa-rahavaukselle-tehtava-jota-ei-ole
  (let [;; Rahavarauksen id
        rahavaraus-id (:id (first (q-map "SELECT id FROM rahavaraus WHERE nimi LIKE '%Äkilliset hoitotyöt%';")))

        ;; Lisätään yksi tehtävä - jota ei ole
        _ (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-rahavarauksen-tehtava +kayttaja-jvh+
                                           {:vanha-tehtava-id 0
                                            :uusi-tehtava {:id 1234
                                                           :nimi "Testi tehtävä"}
                                            :rahavaraus-id rahavaraus-id})))]))

(deftest poista-rahavaraukset-tehtavata
  (let [;; Rahavarauksen id
        rahavaraus-id (:id (first (q-map "SELECT id FROM rahavaraus WHERE nimi LIKE '%Äkilliset hoitotyöt%';")))

        ;; Poistetaan yksi tehtävä, joka rahavarauksella on - joten haetaan rahavarauksen tehtävät
        rahavaraukset-tehtavineen (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-rahavaraukset-tehtavineen +kayttaja-jvh+ {})
        akillisten-tehtavat (:tehtavat (first (filter #(= rahavaraus-id (:id %)) rahavaraukset-tehtavineen)))

        rahavaraukset-tehtavineen2 (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :poista-rahavarauksen-tehtava +kayttaja-jvh+ {:rahavaraus-id rahavaraus-id
                                                                                   :tehtava-id (:id (first akillisten-tehtavat))})

        akillisten-tehtavat2 (:tehtavat (first (filter #(= rahavaraus-id (:id %)) rahavaraukset-tehtavineen2)))]

    (is (= (count akillisten-tehtavat) (+ 1 (count akillisten-tehtavat2))))))
