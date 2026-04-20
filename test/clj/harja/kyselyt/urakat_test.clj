(ns harja.kyselyt.urakat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.urakat :as urakat-q]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest listaa-urakat-elinvoimakeskukselle-palauttaa-oikeat-urakat
  (let [db (:db jarjestelma)
        psu-evk-id (hae-pohjois-suomen-evk-id)
        urakat (urakat-q/listaa-urakat-elinvoimakeskukselle db
                 {:elinvoimakeskusid psu-evk-id
                  :kayttajan_org_id 1
                  :kayttajan_org_tyyppi "liikennevirasto"
                  :sallitut_urakat nil})]

    (testing "PSU EVK löytyy"
      (is (some? psu-evk-id) "Pohjois-Suomen elinvoimakeskus löytyy kannasta"))

    (testing "Urakoita palautuu"
      (is (seq urakat) "Löytyy vähintään yksi urakka"))

    (testing "Urakoilla on pakolliset kentät"
      (doseq [urakka urakat]
        (is (integer? (:id urakka)) "Urakalla on id")
        (is (string? (:nimi urakka)) "Urakalla on nimi")
        (is (some? (:elinvoimakeskus_id urakka)) "Urakalla on elinvoimakeskus_id")))

    (testing "Kaikki urakat kuuluvat oikeaan EVK:iin"
      (is (every? #(= psu-evk-id (:elinvoimakeskus_id %)) urakat)
          "Jokainen urakka kuuluu PSU:n elinvoimakeskukseen"))))

(deftest hae-elinvoimakeskuksen-urakat-palauttaa-oikeat-tiedot
  (let [db (:db jarjestelma)
        psu-evk-id (hae-pohjois-suomen-evk-id)
        urakat (urakat-q/hae-elinvoimakeskuksen-urakat db {:evk_id psu-evk-id})
        odotetut-urakat-kannasta (map first (q (format "SELECT id FROM urakka WHERE elinvoimakeskus_id = %s AND poistettu = false" psu-evk-id)))]

    (testing "Kysely palauttaa urakoita"
      (is (seq urakat) "Urakoita löytyy"))

    (testing "Urakoiden lukumäärä täsmää suoraan kantahakuun"
      (is (= (count odotetut-urakat-kannasta) (count urakat))
          "SQL-kyselyn ja suoran kantahaun urakoiden lukumäärät täsmäävät"))

    (testing "Urakoilla on id, nimi ja tyyppi"
      (doseq [urakka urakat]
        (is (integer? (:id urakka)))
        (is (string? (:nimi urakka)))
        (is (some? (:tyyppi urakka)))))))

(deftest listaa-urakat-elinvoimakeskukselle-olemattomalle-evk-idlle
  (let [db (:db jarjestelma)
        urakat (urakat-q/listaa-urakat-elinvoimakeskukselle db
                 {:elinvoimakeskusid -999
                  :kayttajan_org_id 1
                  :kayttajan_org_tyyppi "liikennevirasto"
                  :sallitut_urakat nil})]
    (testing "Olemattomalle EVK ID:lle ei löydy urakoita"
      (is (empty? urakat) "Olemattomalla EVK:lla ei pitäisi olla urakoita"))))

(deftest listaa-urakat-elinvoimakeskukselle-urakoitsijana
  (let [db (:db jarjestelma)
        psu-evk-id (hae-pohjois-suomen-evk-id)
        urakoitsija-id (ffirst (q "SELECT id FROM organisaatio WHERE tyyppi = 'urakoitsija' LIMIT 1"))
        urakat (urakat-q/listaa-urakat-elinvoimakeskukselle db
                 {:elinvoimakeskusid psu-evk-id
                  :kayttajan_org_id urakoitsija-id
                  :kayttajan_org_tyyppi "urakoitsija"
                  :sallitut_urakat nil})]

    (testing "Urakoitsija näkee vain omat urakkansa"
      (doseq [urakka urakat]
        (is (= urakoitsija-id (:urakoitsija_id urakka))
            "Urakoitsija näkee vain omia urakoitaan")))))
