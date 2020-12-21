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
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.tyo :as tyo]
            [harja.domain.vesivaylat.materiaali :as materiaali]
            [harja.pvm :as pvm]
            [harja.kyselyt.kanavat.kanavan-toimenpide :as q-toimenpide]
            [taoensso.timbre :as log]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db ds
                        :http-palvelin (testi-http-palvelin)
                        :kan-toimenpiteet (component/using
                                            (kan-toimenpiteet/->Kanavatoimenpiteet)
                                            [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture
                                      jarjestelma-fixture))

#_(deftest toimenpiteiden-haku
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        hakuargumentit {::kanavan-toimenpide/urakka-id urakka-id
                        ::kanavan-toimenpide/sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
                        ::toimenpidekoodi/id 534
                        :alkupvm (pvm/luo-pvm 2017 1 1)
                        :loppupvm (pvm/luo-pvm 2018 1 1)
                        ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen
                        ::kanavan-toimenpide/kohde-id nil}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kanavatoimenpiteet
                                +kayttaja-jvh+
                                hakuargumentit)]
    (is (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuargumentit) "Kutsu on validi")
    (is (s/valid? ::kanavan-toimenpide/hae-kanavatoimenpiteet-vastaus vastaus) "Vastaus (hae-kanavatoimenpiteet) on validi")

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

#_(deftest kanavatoimenpiteiden-siirtaminen-lisatoihin-ja-kokonaishintaisiin
  (let [toimenpiteet (hae-saimaan-kanavaurakan-toimenpiteet true)
        kokonaishintaisten-toimenpiteiden-tehtavat (into #{}
                                                         (apply concat
                                                                (q "SELECT tk4.id
                                                                    FROM toimenpidekoodi tk4
                                                                     JOIN toimenpidekoodi tk3 ON tk4.emo=tk3.id
                                                                    WHERE tk3.koodi='27105' AND
                                                                          'kokonaishintainen'::hinnoittelutyyppi=ANY(tk4.hinnoittelu);")))
        muutoshintaisten-toimenpiteiden-tehtavat (into #{}
                                                       (apply concat
                                                              (q "SELECT tk4.id
                                                                  FROM toimenpidekoodi tk4
                                                                   JOIN toimenpidekoodi tk3 ON tk4.emo=tk3.id
                                                                  WHERE tk3.koodi='27105' AND
                                                                        'muutoshintainen'::hinnoittelutyyppi=ANY(tk4.hinnoittelu);")))
        ei-yksiloity-tehtava (into #{}
                                   (first (q "SELECT tk4.id
                                              FROM toimenpidekoodi tk4
                                               JOIN toimenpidekoodi tk3 ON tk4.emo=tk3.id
                                              WHERE tk4.nimi='Ei yksilöity' AND
                                                    tk3.koodi='27105';")))
        tyypin-toimenpiteet #(into #{} (keep (fn [toimenpide]
                                               (when (= %1 (:tyyppi toimenpide))
                                                 (:id toimenpide)))
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
        paivitetyt-toimenpiteet (hae-saimaan-kanavaurakan-toimenpiteet true)
        ei-kokonaishintaisia-toimenpiteita? (empty? (transduce
                                                      (comp (map #(nil? ((set/union muutoshintaisten-toimenpiteiden-tehtavat
                                                                                    ei-yksiloity-tehtava)
                                                                          (:toimenpidekoodi %))))
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
          paivitetyt-toimenpiteet (hae-saimaan-kanavaurakan-toimenpiteet true)
          ei-muutoshintaisia-toimenpiteita? (empty? (transduce
                                                      (comp (map #(nil? (kokonaishintaisten-toimenpiteiden-tehtavat (:toimenpidekoodi %))))
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
        ;; Käytetään kohdetta, joka on varmasti liitetty urakkaan
        kohde-id (ffirst (q "select \"kohde-id\" from kan_kohde_urakka limit 1;"))
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
        toimenpide-template {::kanavan-toimenpide/suorittaja "suorittaja"
                             ::kanavan-toimenpide/muu-toimenpide "muu"
                             ::kanavan-toimenpide/sopimus-id sopimus-id
                             ::kanavan-toimenpide/toimenpideinstanssi-id toimenpideinstanssi
                             ::kanavan-toimenpide/toimenpidekoodi-id tehtava-id
                             ::kanavan-toimenpide/lisatieto "tämä on testitoimenpide"
                             ::kanavan-toimenpide/kohde-id kohde-id
                             ::kanavan-toimenpide/pvm (pvm/luo-pvm 2017 2 2)
                             ::kanavan-toimenpide/huoltokohde-id huoltokohde
                             ::kanavan-toimenpide/urakka-id urakka-id}

        hakuehdot-template {::kanavan-toimenpide/urakka-id urakka-id
                            ::kanavan-toimenpide/sopimus-id sopimus-id
                            ::toimenpidekoodi/id kolmostason-toimenpide-id
                            ::kanavan-toimenpide/kohde-id nil
                            :alkupvm (pvm/luo-pvm 2017 1 1)
                            :loppupvm (pvm/luo-pvm 2018 1 1)}]
    (testing "Kokonaishintaisen toimenpiteen tallentaminen"
      (let [toimenpide (merge toimenpide-template {::kanavan-toimenpide/tyyppi :kokonaishintainen
                                                   ::kanavan-toimenpide/materiaalikirjaukset
                                                   (list {::materiaali/urakka-id urakka-id,
                                                          ::materiaali/nimi "Ämpäreitä",
                                                          ::materiaali/maara
                                                          -1,
                                                          ::materiaali/lisatieto
                                                          "foo"})})
            hakuehdot (merge hakuehdot-template {::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen})
            argumentit {::kanavan-toimenpide/tallennettava-kanava-toimenpide toimenpide
                        ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-kanavatoimenpide
                                    +kayttaja-jvh+
                                    argumentit)]
        (is (some #(= "tämä on testitoimenpide" (::kanavan-toimenpide/lisatieto %)) (:kanavatoimenpiteet vastaus)))
        (is (= "foo" (->> vastaus :materiaalilistaus (mapcat ::materiaali/muutokset) (keep ::materiaali/lisatieto) first)))))

    (testing "Muutos- ja lisatyo toimenpiteen materiaalin ja itse toimenpiteen poistaminen hintatietoineen"
      (let [;; Toimenpiteen tallentaminen
            maara-nauloja -1
            maara-ampareita -1
            toimenpide (merge toimenpide-template {::kanavan-toimenpide/tyyppi :muutos-lisatyo
                                                   ::kanavan-toimenpide/lisatieto "Testataan poistamista hintatietoineen"
                                                   ::kanavan-toimenpide/materiaalikirjaukset
                                                   (list {::materiaali/urakka-id urakka-id,
                                                          ::materiaali/nimi "Ämpäreitä",
                                                          ::materiaali/maara
                                                          maara-ampareita,
                                                          ::materiaali/lisatieto
                                                          "foo"}
                                                         {::materiaali/urakka-id urakka-id,
                                                          ::materiaali/nimi "Naulat",
                                                          ::materiaali/maara
                                                          maara-nauloja,
                                                          ::materiaali/lisatieto
                                                          "bar"})})
            hakuehdot (merge hakuehdot-template {::kanavan-toimenpide/kanava-toimenpidetyyppi :muutos-lisatyo})
            toimenpide-argumentit {::kanavan-toimenpide/tallennettava-kanava-toimenpide toimenpide
                                   ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}
            toimenpide-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :tallenna-kanavatoimenpide
                                               +kayttaja-jvh+
                                               toimenpide-argumentit)
            ;; Hinnoittelun tallentaminen toimenpiteelle
            toimenpide-id (some #(when (= (::kanavan-toimenpide/lisatieto %)
                                          "Testataan poistamista hintatietoineen")
                                   (::kanavan-toimenpide/id %))
                                (:kanavatoimenpiteet toimenpide-vastaus))
            amparin-yksikkohinta 2
            materiaalit (flatten (map (fn [materiaali]
                                        (keep #(when (= toimenpide-id (::materiaali/toimenpide %))
                                                 {:maara (::materiaali/maara %)
                                                  :materiaali-id (::materiaali/id %)
                                                  :nimi (::materiaali/nimi materiaali)})
                                              (::materiaali/muutokset materiaali)))
                                      (:materiaalilistaus toimenpide-vastaus)))
            hinnoittelu-argumentit {::kanavan-toimenpide/urakka-id urakka-id
                                    ::kanavan-toimenpide/id toimenpide-id
                                    ::hinta/tallennettavat-hinnat
                                    [{::hinta/summa 3
                                      ::hinta/yleiskustannuslisa 0
                                      ::hinta/otsikko "Yleiset materiaalit"
                                      ::hinta/id -1
                                      ::hinta/ryhma "muu"}
                                     {::hinta/summa 4
                                      ::hinta/yleiskustannuslisa 0
                                      ::hinta/otsikko "Matkakulut"
                                      ::hinta/id -2
                                      ::hinta/ryhma "muu"}
                                     {::hinta/summa 5
                                      ::hinta/yleiskustannuslisa 0
                                      ::hinta/otsikko "Muut kulut"
                                      ::hinta/id -3
                                      ::hinta/ryhma "muu"}
                                     {::hinta/yleiskustannuslisa 12
                                      ::hinta/otsikko "Naulat"
                                      ::hinta/id -4
                                      ::hinta/ryhma "materiaali"
                                      ::hinta/maara (- maara-nauloja)
                                      ::hinta/yksikkohinta 2
                                      ::hinta/materiaali-id (some #(when (and (= "Naulat" (:nimi %))
                                                                              (= maara-nauloja (:maara %)))
                                                                     (:materiaali-id %))
                                                                  materiaalit)
                                      ::hinta/yksikko "kpl"}
                                     {::hinta/yleiskustannuslisa 0
                                      ::hinta/otsikko "Ämpäreitä"
                                      ::hinta/id -5
                                      ::hinta/ryhma "materiaali"
                                      ::hinta/maara (- maara-ampareita)
                                      ::hinta/yksikkohinta amparin-yksikkohinta
                                      ::hinta/materiaali-id (some #(when (and (= "Ämpäreitä" (:nimi %))
                                                                              (= maara-ampareita (:maara %)))
                                                                     (:materiaali-id %))
                                                                  materiaalit)
                                      ::hinta/yksikko "kpl"}
                                     {::hinta/yleiskustannuslisa 12
                                      ::hinta/otsikko "foo-muutyo"
                                      ::hinta/id -6
                                      ::hinta/ryhma "tyo"
                                      ::hinta/yksikkohinta 21
                                      ::hinta/maara 3
                                      ::hinta/yksikko "h"}
                                     {::hinta/yleiskustannuslisa 0
                                      ::hinta/otsikko "bar-muutyo"
                                      ::hinta/id -7
                                      ::hinta/ryhma "tyo"
                                      ::hinta/yksikkohinta 23
                                      ::hinta/maara 2
                                      ::hinta/yksikko "h"}
                                     {::hinta/yleiskustannuslisa 12
                                      ::hinta/otsikko "foo-materiaali"
                                      ::hinta/id -8
                                      ::hinta/ryhma "materiaali"
                                      ::hinta/yksikkohinta 3
                                      ::hinta/maara 4
                                      ::hinta/yksikko "bar"}
                                     {::hinta/summa 20
                                      ::hinta/yleiskustannuslisa 0
                                      ::hinta/otsikko "foo-kulurivi"
                                      ::hinta/id -9
                                      ::hinta/ryhma "muu"}]
                                    ::tyo/tallennettavat-tyot
                                    [{::tyo/id -2
                                      ::tyo/maara 30
                                      ::tyo/toimenpidekoodi-id 3148}
                                     {::tyo/id -1
                                      ::tyo/maara 2
                                      ::tyo/toimenpidekoodi-id 4714}]}
            hinnoittelu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :tallenna-kanavatoimenpiteen-hinnoittelu
                                                +kayttaja-jvh+
                                                hinnoittelu-argumentit)

            ;; Ämpärin poistaminen toimenpiteeltä
            amparin-poisto-argumentit (-> toimenpide-argumentit
                                          (assoc-in [::kanavan-toimenpide/tallennettava-kanava-toimenpide ::kanavan-toimenpide/materiaalipoistot]
                                                    [{::materiaali/id (some #(when (and (= "Ämpäreitä" (:nimi %))
                                                                                        (= maara-ampareita (:maara %)))
                                                                               (:materiaali-id %))
                                                                            materiaalit)}])
                                          (assoc-in [::kanavan-toimenpide/tallennettava-kanava-toimenpide ::kanavan-toimenpide/id] toimenpide-id)
                                          (assoc-in [::kanavan-toimenpide/tallennettava-kanava-toimenpide ::kanavan-toimenpide/materiaalikirjaukset] nil))
            amparin-poisto-toimenpiteelta-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                                                  :tallenna-kanavatoimenpide
                                                                  +kayttaja-jvh+
                                                                  amparin-poisto-argumentit)
            toimenpiteen-hinnat (some #(when (= toimenpide-id (::kanavan-toimenpide/id %))
                                         (::kanavan-toimenpide/hinnat %))
                                      (:kanavatoimenpiteet amparin-poisto-toimenpiteelta-vastaus))

            ;; Koko toimenpiteen poistaminen
            toimenpiteen-poisto-argumentit (-> amparin-poisto-argumentit
                                               (assoc-in [::kanavan-toimenpide/tallennettava-kanava-toimenpide ::kanavan-toimenpide/materiaalipoistot] nil)
                                               (assoc-in [::kanavan-toimenpide/tallennettava-kanava-toimenpide ::muokkaustiedot/poistettu?] true))
            toimenpiteen-poisto-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :tallenna-kanavatoimenpide
                                                        +kayttaja-jvh+
                                                        toimenpiteen-poisto-argumentit)
            toimenpiteen-poiston-jalkeiset-hinnat (q-toimenpide/hae-toimenpiteen-hinnat (:db jarjestelma) toimenpide-id)
            toimenpiteen-poiston-jalkeiset-tyot (q-toimenpide/hae-toimenpiteen-tyot (:db jarjestelma) toimenpide-id)]
        (is (= (bigdec amparin-yksikkohinta) (some #(when (= (::hinta/otsikko %) "Ämpäreitä")
                                                      (::hinta/yksikkohinta %))
                                                   (::kanavan-toimenpide/hinnat hinnoittelu-vastaus))))
        (is (nil? (some #(= "Ämpäreitä" (::hinta/otsikko %))
                        toimenpiteen-hinnat)))

        (is (nil? (some #(= toimenpide-id (::kanavan-toimenpide/id %))
                        (:kanavatoimenpiteet toimenpiteen-poisto-vastaus))))
        (is (empty? toimenpiteen-poiston-jalkeiset-hinnat))
        (is (empty? toimenpiteen-poiston-jalkeiset-tyot))))))

(deftest toimenpiteen-tallentaminen-ilman-oikeutta
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kohde-id (ffirst (q "select id from kan_kohde limit 1;"))
        toimenpide {::kanavan-toimenpide/suorittaja "suorittaja"
                    ::kanavan-toimenpide/muu-toimenpide "muu"
                    ::kanavan-toimenpide/sopimus-id sopimus-id
                    ::kanavan-toimenpide/toimenpideinstanssi-id 2
                    ::kanavan-toimenpide/toimenpidekoodi-id 3
                    ::kanavan-toimenpide/lisatieto "tämä on testitoimenpide"
                    ::kanavan-toimenpide/tyyppi :kokonaishintainen
                    ::kanavan-toimenpide/kohde-id kohde-id
                    ::kanavan-toimenpide/pvm (pvm/luo-pvm 2017 2 2)
                    ::kanavan-toimenpide/huoltokohde-id 123
                    ::kanavan-toimenpide/urakka-id urakka-id}
        hakuehdot {::kanavan-toimenpide/urakka-id urakka-id
                   ::kanavan-toimenpide/sopimus-id sopimus-id
                   ::toimenpidekoodi/id 13
                   ::kanavan-toimenpide/kohde-id nil
                   :alkupvm (pvm/luo-pvm 2017 1 1)
                   :loppupvm (pvm/luo-pvm 2018 1 1)
                   ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}
        argumentit {::kanavan-toimenpide/tallennettava-kanava-toimenpide toimenpide
                    ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-kanavatoimenpide
                                           +kayttaja-ulle+
                                           argumentit)))))

(deftest toimenpiteen-tallentaminen-eri-urakkaan
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kohde-id (ffirst (q "select id from kan_kohde limit 1;"))
        id (ffirst (q "select id from kan_toimenpide limit 1;"))
        toimenpide {::kanavan-toimenpide/id id
                    ::kanavan-toimenpide/suorittaja "suorittaja"
                    ::kanavan-toimenpide/muu-toimenpide "muu"
                    ::kanavan-toimenpide/sopimus-id sopimus-id
                    ::kanavan-toimenpide/toimenpideinstanssi-id 2
                    ::kanavan-toimenpide/toimenpidekoodi-id 3
                    ::kanavan-toimenpide/lisatieto "tämä on testitoimenpide"
                    ::kanavan-toimenpide/tyyppi :kokonaishintainen
                    ::kanavan-toimenpide/kohde-id kohde-id
                    ::kanavan-toimenpide/pvm (pvm/luo-pvm 2017 2 2)
                    ::kanavan-toimenpide/huoltokohde-id 123
                    ::kanavan-toimenpide/urakka-id urakka-id}
        hakuehdot {::kanavan-toimenpide/urakka-id urakka-id
                   ::kanavan-toimenpide/sopimus-id sopimus-id
                   ::toimenpidekoodi/id 13
                   ::kanavan-toimenpide/kohde-id nil
                   :alkupvm (pvm/luo-pvm 2017 1 1)
                   :loppupvm (pvm/luo-pvm 2018 1 1)
                   ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}
        argumentit {::kanavan-toimenpide/tallennettava-kanava-toimenpide toimenpide
                    ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-kanavatoimenpide
                                                   +kayttaja-jvh+
                                                   argumentit)))))

(deftest toimenpiteen-tallentaminen-eri-kohteen-kohdeosalle
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kohde-id (ffirst (q "select id from kan_kohde limit 1;"))
        kohteenosa-id 66666
        id (ffirst (q "select id from kan_toimenpide limit 1;"))
        toimenpide {::kanavan-toimenpide/id id
                    ::kanavan-toimenpide/suorittaja "suorittaja"
                    ::kanavan-toimenpide/muu-toimenpide "muu"
                    ::kanavan-toimenpide/sopimus-id sopimus-id
                    ::kanavan-toimenpide/toimenpideinstanssi-id 2
                    ::kanavan-toimenpide/toimenpidekoodi-id 3
                    ::kanavan-toimenpide/lisatieto "tämä on testitoimenpide"
                    ::kanavan-toimenpide/tyyppi :kokonaishintainen
                    ::kanavan-toimenpide/kohde-id kohde-id
                    ::kanavan-toimenpide/kohteenosa-id kohteenosa-id
                    ::kanavan-toimenpide/pvm (pvm/luo-pvm 2017 2 2)
                    ::kanavan-toimenpide/huoltokohde-id 123
                    ::kanavan-toimenpide/urakka-id urakka-id}
        hakuehdot {::kanavan-toimenpide/urakka-id urakka-id
                   ::kanavan-toimenpide/sopimus-id sopimus-id
                   ::toimenpidekoodi/id 13
                   ::kanavan-toimenpide/kohde-id nil
                   :alkupvm (pvm/luo-pvm 2017 1 1)
                   :loppupvm (pvm/luo-pvm 2018 1 1)
                   ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}
        argumentit {::kanavan-toimenpide/tallennettava-kanava-toimenpide toimenpide
                    ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-kanavatoimenpide
                                                   +kayttaja-jvh+
                                                   argumentit)))))

(deftest toimenpiteen-tallentaminen-eri-kohteelle
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kohde-id 6666
        id (ffirst (q "select id from kan_toimenpide limit 1;"))
        toimenpide {::kanavan-toimenpide/id id
                    ::kanavan-toimenpide/suorittaja "suorittaja"
                    ::kanavan-toimenpide/muu-toimenpide "muu"
                    ::kanavan-toimenpide/sopimus-id sopimus-id
                    ::kanavan-toimenpide/toimenpideinstanssi-id 2
                    ::kanavan-toimenpide/toimenpidekoodi-id 3
                    ::kanavan-toimenpide/lisatieto "tämä on testitoimenpide"
                    ::kanavan-toimenpide/tyyppi :kokonaishintainen
                    ::kanavan-toimenpide/kohde-id kohde-id
                    ::kanavan-toimenpide/pvm (pvm/luo-pvm 2017 2 2)
                    ::kanavan-toimenpide/huoltokohde-id 123
                    ::kanavan-toimenpide/urakka-id urakka-id}
        hakuehdot {::kanavan-toimenpide/urakka-id urakka-id
                   ::kanavan-toimenpide/sopimus-id sopimus-id
                   ::toimenpidekoodi/id 13
                   ::kanavan-toimenpide/kohde-id nil
                   :alkupvm (pvm/luo-pvm 2017 1 1)
                   :loppupvm (pvm/luo-pvm 2018 1 1)
                   ::kanavan-toimenpide/kanava-toimenpidetyyppi :kokonaishintainen}
        argumentit {::kanavan-toimenpide/tallennettava-kanava-toimenpide toimenpide
                    ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-kanavatoimenpide
                                                   +kayttaja-jvh+
                                                   argumentit)))))
