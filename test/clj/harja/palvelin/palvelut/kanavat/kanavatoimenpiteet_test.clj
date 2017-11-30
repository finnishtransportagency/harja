(ns harja.palvelin.palvelut.kanavat.kanavatoimenpiteet-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
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
                        ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen
                        ::kanavan-toimenpide/kohde-id nil}
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
                    ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen
                    ::kanavan-toimenpide/kohde-id nil}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-kanavatoimenpiteet
                                           +kayttaja-ulle+
                                           parametrit)))))

(deftest kanavatoimenpiteiden-siirtaminen-lisatoihin-ja-kokonaishintaisiin
  (let [toimenpiteet (hae-saimaan-kanavaurakan-toimenpiteet)
        toimenpiteiden-kentta (fn [toimenpiteet kentta]
                                ())
        kokonaishintaisten-toimenpiteiden-tehtavat (into #{}
                                                         (apply concat
                                                                (q "SELECT tk4.id
                                                                    FROM toimenpidekoodi tk4
                                                                     JOIN toimenpidekoodi tk3 ON tk4.emo=tk3.id
                                                                    WHERE tk3.koodi='24104' AND
                                                                          'kokonaishintainen'::hinnoittelutyyppi=ANY(tk4.hinnoittelu);")))
        muutoshintaisten-toimenpiteiden-tehtavat (into #{}
                                                       (apply concat
                                                              (q "SELECT tk4.id
                                                                  FROM toimenpidekoodi tk4
                                                                   JOIN toimenpidekoodi tk3 ON tk4.emo=tk3.id
                                                                  WHERE tk3.koodi='24104' AND
                                                                        'muutoshintainen'::hinnoittelutyyppi=ANY(tk4.hinnoittelu);")))
        ei-yksiloity-tehtava (into #{}
                                   (first (q "SELECT tk4.id
                                              FROM toimenpidekoodi tk4
                                               JOIN toimenpidekoodi tk3 ON tk4.emo=tk3.id
                                              WHERE tk4.nimi='Ei yksilöity' AND
                                                    tk3.koodi='24104';")))
        tyypin-toimenpiteet #(into #{} (keep (fn [toimenpide]
                                               (when (= %1 (last toimenpide))
                                                 (first toimenpide)))
                                             %2))
        kokonaishintaisten-toimenpiteiden-idt (tyypin-toimenpiteet "kokonaishintainen" toimenpiteet)
        muutos-ja-lisatyo-toimenpiteiden-idt (tyypin-toimenpiteet "muutos-lisatyo" toimenpiteet)
        urakka-id (hae-saimaan-kanavaurakan-id)
        parametrit {::kanavan-toimenpide/toimenpide-idt kokonaishintaisten-toimenpiteiden-idt
                    ::kanavan-toimenpide/urakka-id urakka-id
                    ::kanavan-toimenpide/tyyppi :muutos-lisatyo}
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :siirra-kanavatoimenpiteet
                          +kayttaja-jvh+
                          parametrit)
        paivitetyt-toimenpiteet (hae-saimaan-kanavaurakan-toimenpiteet)
        ei-kokonaishintaisia-toimenpiteita? (empty? (transduce
                                                      (comp (map #(nil? ((set/union muutoshintaisten-toimenpiteiden-tehtavat
                                                                                    ei-yksiloity-tehtava)
                                                                          (second %))))
                                                            (filter true?))
                                                      conj paivitetyt-toimenpiteet))]
    (is (= (tyypin-toimenpiteet "muutos-lisatyo" paivitetyt-toimenpiteet)
           (set/union kokonaishintaisten-toimenpiteiden-idt
                      muutos-ja-lisatyo-toimenpiteiden-idt)))
    (is ei-kokonaishintaisia-toimenpiteita?)
    (let [uudet-parametrit {::kanavan-toimenpide/toimenpide-idt kokonaishintaisten-toimenpiteiden-idt
                            ::kanavan-toimenpide/urakka-id urakka-id
                            ::kanavan-toimenpide/tyyppi :kokonaishintainen}
          _ (kutsu-palvelua (:http-palvelin jarjestelma)
                            :siirra-kanavatoimenpiteet
                            +kayttaja-jvh+
                            uudet-parametrit)
          paivitetyt-toimenpiteet (hae-saimaan-kanavaurakan-toimenpiteet)
          ei-muutoshintaisia-toimenpiteita? (empty? (transduce
                                                      (comp (map #(nil? (kokonaishintaisten-toimenpiteiden-tehtavat (second %))))
                                                            (filter true?))
                                                      conj paivitetyt-toimenpiteet))]
      (is (= (into #{} paivitetyt-toimenpiteet) (into #{} toimenpiteet)))
      (is ei-muutoshintaisia-toimenpiteita?))))

(deftest toimenpiteiden-siirtaminen-ilman-oikeutta-ei-toimi
  (let [toimenpiteet (hae-saimaan-kanavaurakan-toimenpiteet)
        tyypin-toimenpiteet #(into #{} (keep (fn [toimenpide]
                                               (when (= %1 (second toimenpide))
                                                 (first toimenpide)))
                                             %2))
        kokonaishintaisten-toimenpiteiden-idt (tyypin-toimenpiteet "kokonaishintainen" toimenpiteet)
        muutos-ja-lisatyo-toimenpiteiden-idt (tyypin-toimenpiteet "muutos-lisatyo" toimenpiteet)
        urakka-id (hae-saimaan-kanavaurakan-id)
        parametrit {::kanavan-toimenpide/toimenpide-idt kokonaishintaisten-toimenpiteiden-idt
                    ::kanavan-toimenpide/urakka-id urakka-id
                    ::kanavan-toimenpide/tyyppi :muutos-lisatyo}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :siirra-kanavatoimenpiteet
                                           +kayttaja-ulle+
                                           parametrit)))
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :siirra-kanavatoimenpiteet
                                           +kayttaja-ulle+
                                           (assoc parametrit ::kanavan-toimenpide/tyyppi :kokonaishintainen
                                                             ::kanavan-toimenpide/toimenpide-idt muutos-ja-lisatyo-toimenpiteiden-idt))))))

(deftest toimenpiteiden-haku-ilman-tyyppia-ei-toimi
  (let [parametrit {::kanavan-toimenpide/urakka-id (hae-saimaan-kanavaurakan-id)
                    ::kanavan-toimenpide/sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
                    ::toimenpidekoodi/id 597
                    :alkupvm (pvm/luo-pvm 2017 1 1)
                    :loppupvm (pvm/luo-pvm 2018 1 1)}]

    (is (not (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely
                       parametrit)))))

(deftest toimenpiteen-tallentaminen-toimii
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kayttaja (ffirst (q "select id from kayttaja limit 1;"))
        kohde (ffirst (q "select id from kan_kohde limit 1;"))
        huoltokohde (ffirst (q "select id from kan_huoltokohde limit 1;"))
        kolmostason-toimenpide-id (ffirst (q "select tpk3.id
                                               from toimenpidekoodi tpk1
                                                join toimenpidekoodi tpk2 on tpk1.id = tpk2.emo
                                                  join toimenpidekoodi tpk3 on tpk2.id = tpk3.emo
                                                  where tpk1.nimi ILIKE '%Hoito, meri%' and
                                                        tpk2.nimi ILIKE '%Väylänhoito%' and
                                                              tpk3.nimi ilike '%Laaja toimenpide%';"))
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
