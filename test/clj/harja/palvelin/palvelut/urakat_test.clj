(ns harja.palvelin.palvelut.urakat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.urakat :as urk-q]
            [harja.domain.urakka :as u]
            [harja.domain.sopimus :as sop]
            [harja.domain.hanke :as h]
            [harja.domain.organisaatio :as o]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :tallenna-urakan-sopimustyyppi (component/using
                                                         (->Urakat)
                                                         [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest tallenna-urakan-sopimustyyppi-testi
  (let [urakanvalvoja (oulun-urakan-tilaajan-urakanvalvoja)
        uusi-sopimustyyppi
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-urakan-sopimustyyppi urakanvalvoja
                        {:urakka-id @oulun-alueurakan-2005-2010-id
                         :sopimustyyppi :kokonaisurakka})]
    (is (= uusi-sopimustyyppi :kokonaisurakka))
    (u (str "UPDATE urakka SET sopimustyyppi = NULL WHERE id = " @oulun-alueurakan-2005-2010-id))))


(deftest hae-urakka-testi
  (let [urakanvalvoja (oulun-urakan-tilaajan-urakanvalvoja)
        haettu-urakka
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakka urakanvalvoja @oulun-alueurakan-2005-2010-id)
        sopimukset (:sopimukset haettu-urakka)
        [eka-sopimuksen-id eka-sopimuksen-sampoid] (first sopimukset)
        [toka-sopimuksen-id toka-sopimuksen-sampoid] (second sopimukset)]
    (is (= (:id haettu-urakka) @oulun-alueurakan-2005-2010-id) "haetun urakan id")
    (is (= (count sopimukset) 2) "haetun urakan sopimusten määrä")
    (is (= eka-sopimuksen-id 1) "haetun urakan sopimustesti")
    (is (= eka-sopimuksen-sampoid "8H05228/01") "haetun urakan sopimustesti")
    (is (= toka-sopimuksen-id 3) "haetun urakan sopimustesti")
    (is (= toka-sopimuksen-sampoid "THII-12-28555") "haetun urakan sopimustesti")
    (is (= (:alkupvm haettu-urakka) (java.sql.Date. 105 9 1)) "haetun urakan alkupvm")
    (is (= (:loppupvm haettu-urakka) (pvm/aikana (pvm/->pvm "30.9.2012") 23 59 59 999)) "haetun urakan loppupvm")))

(deftest urakoiden-haku-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-harjassa-luodut-urakat +kayttaja-jvh+ {})]
    (is (>= (count vastaus) 3))
    (is (s/valid? ::u/hae-harjassa-luodut-urakat-vastaus vastaus))))

(deftest urakan-tallennus-test
  (let [hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
        urakoitsija-id (hae-vapaa-urakoitsija-id)
        [eka-sopimus-id toka-sopimus-id] (hae-vapaat-sopimus-idt)
        urakka {::u/nimi "lolurakka"
                ::u/alkupvm #inst "2017-04-25T21:00:00.000-00:00"
                ::u/loppupvm #inst "2017-04-26T21:00:00.000-00:00"
                ::u/sopimukset [{::sop/id eka-sopimus-id
                                 ::sop/paasopimus-id nil}
                                {::sop/id toka-sopimus-id
                                 ::sop/paasopimus-id eka-sopimus-id}]
                ::u/hallintayksikko {::o/id hallintayksikko-id}
                ::u/urakoitsija {::o/id urakoitsija-id}}
        urakat-lkm-ennen-testia (ffirst (q "SELECT COUNT(id) FROM urakka"))]
    (assert hallintayksikko-id "Hallintayksikkö pitää olla")
    (assert urakoitsija-id "Urakoitsija pitää olla")
    (assert eka-sopimus-id "Sopimus pitää olla")
    (assert toka-sopimus-id "Sopimus pitää olla")

    (is (s/valid? ::u/tallenna-urakka-kysely urakka) "Lähtevä kysely on validi")

    ;; Luo uusi urakka
    (let [urakka-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :tallenna-urakka +kayttaja-jvh+
                                          urakka)
          urakan-sopimukset-kannassa (q-map "SELECT * FROM sopimus WHERE urakka = " (::u/id urakka-kannassa) ";")
          urakat-lkm-testin-jalkeen (ffirst (q "SELECT COUNT(id) FROM urakka"))]

      (is (= (+ urakat-lkm-ennen-testia 1) urakat-lkm-testin-jalkeen)
          "Urakoiden määrä kasvoi yhdellä")

      ;; Vastauksessa on uuden urakan tiedot
      (is (integer? (::u/id urakka-kannassa)))
      (is (= (::u/nimi urakka-kannassa (::u/nimi urakka))))
      (is (= (::u/alkupvm urakka-kannassa (::u/alkupvm urakka))))
      (is (= (::u/loppupvm urakka-kannassa (::u/loppupvm urakka))))

      ;; Sopparikin on tallentunut oikein
      (is (= (count urakan-sopimukset-kannassa) 2))
      (is (= (set (map :paasopimus urakan-sopimukset-kannassa))
             #{nil eka-sopimus-id})
          "Yksi urakan sopimuksista on pääsopimus ja toinen sopimus viittaa pääsoppariin")

      ;; Päivitetään urakka
      (let [paivitetty-urakka (assoc urakka ::u/nimi (str (::u/nimi urakka) " päivitetty"))
            paivitetty-urakka-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                       :tallenna-urakka +kayttaja-jvh+
                                                       paivitetty-urakka)]

        ;; Urakka päivittyi
        (is (= (::u/nimi paivitetty-urakka-kannassa)
               (::u/nimi paivitetty-urakka)))))))