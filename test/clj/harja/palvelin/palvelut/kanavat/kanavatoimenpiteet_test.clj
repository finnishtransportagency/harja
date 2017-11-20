(ns harja.palvelin.palvelut.kanavat.kanavatoimenpiteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]
            [harja
             [testi :refer :all]]
            [harja.tyokalut.functor :refer [fmap]]

            [clojure.spec.alpha :as s]
            [harja.palvelin.palvelut.kanavat.kanavatoimenpiteet :as kan-toimenpiteet]

            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet testi-pois-kytketyt-ominaisuudet
                        :kan-toimenpiteet (component/using
                                            (kan-toimenpiteet/->Kanavatoimenpiteet)
                                            [:http-palvelin :db :pois-kytketyt-ominaisuudet])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest toimenpiteiden-haku
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        hakuargumentit {::kanavan-toimenpide/urakka-id urakka-id
                        ::kanavan-toimenpide/sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
                        ::toimenpidekoodi/id 597
                        :alkupvm (pvm/luo-pvm 2017 1 1)
                        :loppupvm (pvm/luo-pvm 2018 1 1)
                        ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kanavatoimenpiteet
                                +kayttaja-jvh+
                                hakuargumentit)]
    (is (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuargumentit) "Kutsu on validi")
    (is (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-vastaus vastaus) "Vastaus on validi")

    (is (>= (count vastaus) 1))
    (is (every? ::kanavan-toimenpide/id vastaus))
    (is (every? ::kanavan-toimenpide/kohde vastaus))
    (is (every? ::kanavan-toimenpide/toimenpidekoodi vastaus))
    (is (every? ::kanavan-toimenpide/huoltokohde vastaus))

    (testing "Aikavälisuodatus toimii"
      (is (zero? (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-kanavatoimenpiteet
                                        +kayttaja-jvh+
                                        (assoc hakuargumentit :alkupvm (pvm/luo-pvm 2030 1 1)
                                                              :loppupvm (pvm/luo-pvm 2040 1 1)))))))

    (testing "Toimenpidekoodisuodatus toimii"
      (is (zero? (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-kanavatoimenpiteet
                                        +kayttaja-jvh+
                                        (assoc hakuargumentit ::toimenpidekoodi/id -1))))))

    (testing "Sopimussuodatus toimii"
      (is (zero? (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-kanavatoimenpiteet
                                        +kayttaja-jvh+
                                        (assoc hakuargumentit ::kanavan-toimenpide/sopimus-id -1))))))

    (testing "Tyyppisuodatus toimii"
      (is (every? #(= (::kanavan-toimenpide/tyyppi %) :kokonaishintainen)
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kanavatoimenpiteet
                                  +kayttaja-jvh+
                                  (assoc hakuargumentit ::kanavan-toimenpide/kanava-toimenpidetyyppi
                                                        :kokonaishintainen))))

      (is (every? #(= (::kanavan-toimenpide/tyyppi %) :muutos-lisatyo)
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kanavatoimenpiteet
                                  +kayttaja-jvh+
                                  (assoc hakuargumentit ::kanavan-toimenpide/kanava-toimenpidetyyppi
                                                        :muutos-lisatyo)))))))


(deftest toimenpiteiden-haku-tyhjalla-urakalla-ei-toimi
  (let [hakuargumentit {::kanavan-toimenpide/urakka-id nil
                        ::kanavan-toimenpide/sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
                        ::toimenpidekoodi/id 597
                        :alkupvm (pvm/luo-pvm 2017 1 1)
                        :loppupvm (pvm/luo-pvm 2018 1 1)
                        ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}]

    (is (not (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely
                       hakuargumentit)))))

(deftest toimenpiteiden-haku-ilman-oikeutta-ei-toimi
  (let [parametrit {::kanavan-toimenpide/urakka-id (hae-saimaan-kanavaurakan-id)
                    ::kanavan-toimenpide/sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
                    ::toimenpidekoodi/id 597
                    :alkupvm (pvm/luo-pvm 2017 1 1)
                    :loppupvm (pvm/luo-pvm 2018 1 1)
                    ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-kanavatoimenpiteet
                                           +kayttaja-ulle+
                                           parametrit)))))

(deftest toimenpiteiden-haku-ilman-tyyppia-ei-toimi
  (let [parametrit {::kanavan-toimenpide/urakka-id (hae-saimaan-kanavaurakan-id)
                    ::kanavan-toimenpide/sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
                    ::toimenpidekoodi/id 597
                    :alkupvm (pvm/luo-pvm 2017 1 1)
                    :loppupvm (pvm/luo-pvm 2018 1 1)}]

    (is (not (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely
                       parametrit)))))

#_(deftest toimenpiteen-tallentaminen-toimii
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kayttaja (ffirst (q "select id from kayttaja limit 1;"))
        kohde (ffirst (q "select id from kan_kohde limit 1;"))
        huoltokohde (ffirst (q "select id from kan_huoltokohde limit 1;"))
        kolmostason-toimenpide-id (ffirst (q "SELECT tpk3.id
                                               FROM toimenpidekoodi tpk1
                                                JOIN toimenpidekoodi tpk2 ON tpk1.id = tpk2.emo
                                                  JOIN toimenpidekoodi tpk3 ON tpk2.id = tpk3.emo
                                                  WHERE tpk1.nimi ILIKE '%Hoito, meri%' AND
                                                        tpk2.nimi ILIKE '%Väylänhoito%' AND
                                                              tpk3.nimi ILIKE '%Laaja toimenpide%';"))
        tehtava-id (ffirst (q (format "select id from toimenpidekoodi where emo = %s" kolmostason-toimenpide-id)))
        toimenpideinstanssi (ffirst (q "select id from toimenpideinstanssi where nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP';"))
        toimenpide {::kanavan-toimenpide/suorittaja "suorittaja"
                    ::kanavan-toimenpide/muu-toimenpide "muu"
                    ::kanavan-toimenpide/kuittaaja-id kayttaja
                    ::kanavan-toimenpide/sopimus-id sopimus-id
                    ::kanavan-toimenpide/toimenpideinstanssi-id toimenpideinstanssi
                    ::kanavan-toimenpide/toimenpidekoodi-id tehtava-id
                    ::kanavan-toimenpide/lisatieto "tämä on testitoimenpide"
                    ::kanavan-toimenpide/tyyppi :kokonaishintainen
                    ::muokkaustiedot/luoja-id kayttaja
                    ::kanavan-toimenpide/kohde-id kohde
                    ::kanavan-toimenpide/pvm (pvm/luo-pvm 2017 2 2)
                    ::kanavan-toimenpide/huoltokohde-id huoltokohde
                    ::kanavan-toimenpide/urakka-id urakka-id}
        hakuehdot {::kanavan-toimenpide/urakka-id urakka-id
                   ::kanavan-toimenpide/sopimus-id sopimus-id
                   ::toimenpidekoodi/id kolmostason-toimenpide-id
                   :alkupvm (pvm/luo-pvm 2017 1 1)
                   :loppupvm (pvm/luo-pvm 2018 1 1)
                   ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}
        parametrit {::kanavan-toimenpide/kanava-toimenpide toimenpide
                    ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-kanavatoimenpide +kayttaja-jvh+ parametrit)]
    (is (some #(= "tämä on testitoimenpide" (::kanavan-toimenpide/lisatieto %)) vastaus))))