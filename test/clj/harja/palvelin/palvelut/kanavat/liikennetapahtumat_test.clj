(ns harja.palvelin.palvelut.kanavat.liikennetapahtumat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [harja.palvelin.palvelut.kanavat.liikennetapahtumat :as kan-liikennetapahtumat]
            [clojure.string :as str]

            [harja.domain.urakka :as ur]
            [harja.domain.sopimus :as sop]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-toiminto :as toiminto]))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :kan-liikennetapahtumat (component/using
                                                  (kan-liikennetapahtumat/->Liikennetapahtumat)
                                                  [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest tapahtumien-haku
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        params {:urakka-idt #{urakka-id}}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-liikennetapahtumat
                                +kayttaja-jvh+
                                params)]

    (is (s/valid? ::lt/hae-liikennetapahtumat-kysely params))
    (is (s/valid? ::lt/hae-liikennetapahtumat-vastaus vastaus))


    (is (true?
          (boolean
            (some
              (fn [tapahtuma]
                (not-empty (::lt/alukset tapahtuma)))
              vastaus))))))

(deftest tapahtumien-haku-eri-filttereilla
  (let [saimaan-urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        params {:urakka-idt #{saimaan-urakka-id}}]

    (testing "Aluslajit-suodatin toimii"
      (let [vastaus-kaikki (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-liikennetapahtumat
                                           +kayttaja-jvh+
                                           (merge params {::lt-alus/aluslajit #{}}))
            vastaus-rajattu (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :hae-liikennetapahtumat
                                            +kayttaja-jvh+
                                            (merge params {::lt-alus/aluslajit #{:ÖLJ}}))]
        (is (>= (count vastaus-kaikki) 1))
        (is (> (count vastaus-kaikki) (count vastaus-rajattu)))))

    (testing "Toimenpidetyyppi-suodatin toimii"
      (let [vastaus-kaikki (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-liikennetapahtumat
                                           +kayttaja-jvh+
                                           (merge params {::toiminto/toimenpiteet #{}}))
            vastaus-rajattu (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :hae-liikennetapahtumat
                                            +kayttaja-jvh+
                                            (merge params {::toiminto/toimenpiteet #{:sulutus}}))]
        (is (>= (count vastaus-kaikki) 1))
        (is (> (count vastaus-kaikki) (count vastaus-rajattu)))))

    (testing "Aluksen nimi -suodatin toimii"
      (let [vastaus-kaikki (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-liikennetapahtumat
                                           +kayttaja-jvh+
                                           (merge params {::lt-alus/nimi ""}))
            vastaus-rajattu (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :hae-liikennetapahtumat
                                            +kayttaja-jvh+
                                            (merge params {::lt-alus/nimi "Antin onni"}))]
        (is (>= (count vastaus-kaikki) 1))
        (is (> (count vastaus-kaikki) (count vastaus-rajattu)))))

    (testing "Urakoiden valinta -suodatin toimii"
      (let [joensuun-urakka-id (hae-joensuun-kanavaurakan-id)
            vastaus-kaikki-urakat (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :hae-liikennetapahtumat
                                                   +kayttaja-jvh+
                                                  {:urakka-idt #{saimaan-urakka-id joensuun-urakka-id}})
            vastaus-joensuun-urakka (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :hae-liikennetapahtumat
                                                    +kayttaja-jvh+
                                                    {:urakka-idt #{joensuun-urakka-id}})
            vastaus-saimaan-urakka (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :hae-liikennetapahtumat
                                                   +kayttaja-jvh+
                                                   {:urakka-idt #{saimaan-urakka-id}})
            vastaus-ei-urakoita-valittu (kutsu-palvelua (:http-palvelin jarjestelma)
                                                         :hae-liikennetapahtumat
                                                         +kayttaja-jvh+
                                                        {:urakka-idt #{}})]
        (is (= (count vastaus-kaikki-urakat) 9))
        (is (= (count vastaus-joensuun-urakka) 2))
        (is (= (count vastaus-saimaan-urakka) 7))
        (is (= (count vastaus-ei-urakoita-valittu) 0))))))

(deftest edellisten-tapahtumien-haku
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        kohde-id (hae-kohde-soskua)
        params {::lt/urakka-id urakka-id
                ::lt/sopimus-id sopimus-id
                ::lt/kohde-id kohde-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-edelliset-tapahtumat
                                +kayttaja-jvh+
                                params)]

    (is (s/valid? ::lt/hae-edelliset-tapahtumat-kysely params))
    (is (s/valid? ::lt/hae-edelliset-tapahtumat-vastaus vastaus))


    (is (some? (get-in vastaus [:ylos ::kohde/nimi])))
    (is (some? (get-in vastaus [:ylos ::kohde/id])))
    (is (not-empty (get-in vastaus [:ylos :edelliset-alukset])))
    (is (every?
          (first (get-in vastaus [:ylos :edelliset-alukset]))
          [:harja.domain.kanavat.kohde/id
           :harja.domain.kanavat.kohde/nimi
           :harja.domain.kanavat.liikennetapahtuma/aika
           :harja.domain.kanavat.liikennetapahtuma/lisatieto
           :harja.domain.kanavat.lt-alus/id
           :harja.domain.kanavat.lt-alus/laji
           :harja.domain.kanavat.lt-alus/lkm
           :harja.domain.kanavat.lt-alus/nimi
           :harja.domain.kanavat.lt-alus/suunta
           :harja.domain.kanavat.lt-ketjutus/alus-id
           :harja.domain.kanavat.lt-ketjutus/kohteelle-id
           :harja.domain.kanavat.lt-ketjutus/kohteelta-id
           :harja.domain.kanavat.lt-ketjutus/sopimus-id
           :harja.domain.kanavat.lt-ketjutus/urakka-id]))

    ;; Testidatassa ei ole ketjutuksia alaspäin
    (is (nil? (:alas vastaus)))

    (is (and
          (some? (:edellinen vastaus))
          (number? (get-in vastaus [:edellinen ::lt/id]))))))

(deftest tapahtuman-tallentaminen
  (testing "Uuden luonti"
    (let [urakka-id (hae-saimaan-kanavaurakan-id)
          sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
          kohde-id (hae-kohde-palli)
          [kohteenosa-id tyyppi] (hae-kohteenosat-palli)
          _ (is (= tyyppi "silta") "Pällin kohteenosan tyyppiä on vaihdettu, päivitä testi tai testidata.")
          hakuparametrit {:urakka-idt #{urakka-id}}
          vanhat (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-liikennetapahtumat
                                 +kayttaja-jvh+
                                 hakuparametrit)
          _ (is (not-empty vanhat))
          params {::lt/urakka-id urakka-id
                  ::lt/sopimus-id sopimus-id
                  ::lt/kohde-id kohde-id
                  ::lt/aika (pvm/nyt)
                  ::lt/id -1
                  ::lt/toiminnot [{::toiminto/kohteenosa-id kohteenosa-id
                                   ::toiminto/kohde-id kohde-id
                                   ::toiminto/toimenpide :ei-avausta}]
                  ::lt/alukset [{::lt-alus/laji :HUV
                                 ::lt-alus/lkm 1
                                 ::lt-alus/suunta :ylos
                                 ::lt-alus/nimi "HUPILAIVA"}]
                  ::lt/vesipinta-alaraja 500
                  ::lt/vesipinta-ylaraja 1000.9
                  ::lt/kuittaaja-id (:id +kayttaja-jvh+)
                  ::lt/lisatieto "Sitten mennään"
                  :hakuparametrit hakuparametrit}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-liikennetapahtuma
                                  +kayttaja-jvh+
                                  params)]

      (is (s/valid? ::lt/tallenna-liikennetapahtuma-kysely params))
      (is (s/valid? ::lt/tallenna-liikennetapahtuma-vastaus vastaus))

      (is (= (count vastaus) (inc (count vanhat))))))

  (testing "Ketjutus luotiin"
    (let [tapahtuma-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Sitten mennään';")))]
      (is (= 1 (count (q (str "SELECT * FROM kan_liikennetapahtuma_ketjutus WHERE \"tapahtumasta-id\"=" tapahtuma-id)))))))

  (testing "Ketjutuksen käyttöönotto"
    (let [urakka-id (hae-saimaan-kanavaurakan-id)
          sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
          kohde-id (hae-kohde-kansola)
          [kohteenosa-id tyyppi] (hae-kohteenosat-kansola)
          laiva-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma_alus WHERE nimi = 'HUPILAIVA'")))
          _ (is (some? laiva-id))
          _ (is (= tyyppi "sulku") "Kansolan kohteenosan tyyppiä on vaihdettu, päivitä testi tai testidata.")
          hakuparametrit {:urakka-idt #{urakka-id}}
          vanhat (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-liikennetapahtumat
                                 +kayttaja-jvh+
                                 hakuparametrit)
          _ (is (not-empty vanhat))
          params {::lt/urakka-id urakka-id
                  ::lt/sopimus-id sopimus-id
                  ::lt/kohde-id kohde-id
                  ::lt/aika (pvm/nyt)
                  ::lt/id -1
                  ::lt/toiminnot [{::toiminto/kohteenosa-id kohteenosa-id
                                   ::toiminto/kohde-id kohde-id
                                   ::toiminto/toimenpide :sulutus
                                   ::toiminto/palvelumuoto :kauko
                                   ::toiminto/lkm 1}]
                  ::lt/alukset [{::lt-alus/laji :HUV
                                 ::lt-alus/lkm 1
                                 ::lt-alus/suunta :ylos
                                 ::lt-alus/nimi "HUPILAIVA"
                                 ::lt-alus/id laiva-id}
                                {::lt-alus/laji :HUV
                                 ::lt-alus/nimi "TESTILAIVA"
                                 ::lt-alus/lkm 1
                                 ::lt-alus/suunta :ylos}]
                  ::lt/vesipinta-alaraja 500
                  ::lt/vesipinta-ylaraja 1000.9
                  ::lt/kuittaaja-id (:id +kayttaja-jvh+)
                  ::lt/lisatieto "FOOBAR FOOBAR"
                  :hakuparametrit hakuparametrit}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-liikennetapahtuma
                                  +kayttaja-jvh+
                                  params)]

      (is (s/valid? ::lt/tallenna-liikennetapahtuma-kysely params))
      (is (s/valid? ::lt/tallenna-liikennetapahtuma-vastaus vastaus))

      (is (some #(= 2 (count (::lt/alukset %))) vastaus))

      (is (= (count vastaus) (inc (count vanhat))))))

  (testing "Ketjutus otettiin käyttöön"
    (let [tapahtuma-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'FOOBAR FOOBAR';")))]
      (is (= 1 (count (q (str "SELECT * FROM kan_liikennetapahtuma_ketjutus WHERE \"tapahtumaan-id\"=" tapahtuma-id)))))))

  (testing "Uudet ketjutukset luotiin"
    (let [tapahtuma-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'FOOBAR FOOBAR';")))]
      (is (= 2 (count (q (str "SELECT * FROM kan_liikennetapahtuma_ketjutus WHERE \"tapahtumasta-id\"=" tapahtuma-id)))))))

  (testing "Muokkaaminen"
    (let [urakka-id (hae-saimaan-kanavaurakan-id)
          sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
          [kohteenosa-id tyyppi] (hae-kohteenosat-kansola)
          _ (is (= tyyppi "sulku") "Kansolan kohteenosan tyyppiä on vaihdettu, päivitä testi tai testidata.")
          kohde-id (hae-kohde-kansola)
          tapahtuma-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'FOOBAR FOOBAR';")))
          hakuparametrit {:urakka-idt #{urakka-id}}
          vanhat (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-liikennetapahtumat
                                 +kayttaja-jvh+
                                 hakuparametrit)
          _ (is (not-empty vanhat))
          params {::lt/urakka-id urakka-id
                  ::lt/sopimus-id sopimus-id
                  ::lt/kohde-id kohde-id
                  ::lt/id tapahtuma-id
                  ::lt/aika (pvm/nyt)
                  ;; Ei laiteta testiparametreihin laivoja tai toimintoja - jos näille ei anneta
                  ;; id:tä, luodaan tietenkin uudet, ja ne on vaan turhan haastava kaivaa, ilman
                  ;; merkittävää hyötyä
                  ::lt/toiminnot []
                  ::lt/alukset []
                  ::lt/vesipinta-alaraja 500
                  ::lt/vesipinta-ylaraja 1000.9
                  ::lt/kuittaaja-id (:id +kayttaja-jvh+)
                  ::lt/lisatieto "FOOBAR FOOBAR FOOBAR"
                  :hakuparametrit hakuparametrit}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-liikennetapahtuma
                                  +kayttaja-jvh+
                                  params)]

      (is (s/valid? ::lt/tallenna-liikennetapahtuma-kysely params))
      (is (s/valid? ::lt/tallenna-liikennetapahtuma-vastaus vastaus))

      (is (= (count vastaus) (count vanhat)))

      (is (some #(= 2 (count (::lt/alukset %))) vastaus))

      (is (some #(= (::lt/lisatieto %) "FOOBAR FOOBAR") vanhat))
      (is (some #(= (::lt/lisatieto %) "FOOBAR FOOBAR FOOBAR") vastaus))))

  (testing "Laivan poistaminen"
    (let [urakka-id (hae-saimaan-kanavaurakan-id)
          sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
          [kohteenosa-id tyyppi] (hae-kohteenosat-kansola)
          _ (is (= tyyppi "sulku") "Kansolan kohteenosan tyyppiä on vaihdettu, päivitä testi tai testidata.")
          kohde-id (hae-kohde-kansola)
          tapahtuma-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'FOOBAR FOOBAR FOOBAR';")))
          laiva-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma_alus WHERE nimi = 'TESTILAIVA'")))
          hakuparametrit {:urakka-idt #{urakka-id}}
          vanhat (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-liikennetapahtumat
                                 +kayttaja-jvh+
                                 hakuparametrit)
          _ (is (not-empty vanhat))
          params {::lt/urakka-id urakka-id
                  ::lt/sopimus-id sopimus-id
                  ::lt/kohde-id kohde-id
                  ::lt/id tapahtuma-id
                  ::lt/aika (pvm/nyt)
                  ::lt/toiminnot []
                  ::lt/alukset [{::lt-alus/laji :HUV
                                 ::lt-alus/id laiva-id
                                 ::lt-alus/nimi "TESTILAIVA"
                                 ::lt-alus/lkm 1
                                 ::lt-alus/suunta :ylos
                                 ::m/poistettu? true}]
                  ::lt/vesipinta-alaraja 500
                  ::lt/vesipinta-ylaraja 1000.9
                  ::lt/kuittaaja-id (:id +kayttaja-jvh+)
                  ::lt/lisatieto "FOOBAR FOOBAR FOOBAR"
                  :hakuparametrit hakuparametrit}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-liikennetapahtuma
                                  +kayttaja-jvh+
                                  params)]

      (is (s/valid? ::lt/tallenna-liikennetapahtuma-kysely params))
      (is (s/valid? ::lt/tallenna-liikennetapahtuma-vastaus vastaus))

      (is (= (count vastaus) (count vanhat)))

      (is (some #(= 1 (count (::lt/alukset %))) vastaus))

      (is (some #(= (::lt/lisatieto %) "FOOBAR FOOBAR FOOBAR") vastaus))))

  (testing "Ketjutus poistettiin"
    (let [tapahtuma-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'FOOBAR FOOBAR FOOBAR';")))]
      (is (= 1 (count (q (str "SELECT * FROM kan_liikennetapahtuma_ketjutus WHERE \"tapahtumasta-id\"=" tapahtuma-id)))))))

  (testing "Alus poistettiin"
    (let [laivatiedot (q (str "SELECT poistettu FROM kan_liikennetapahtuma_alus WHERE nimi = 'TESTILAIVA'"))]
      (is (= 1 (count laivatiedot)) "Testiä on muutettu, ja TESTILAIVA nimellä löytyy monta alusta. Päivitä testi!")
      (is (true? (ffirst laivatiedot)) "Alusta ei merkattu poistetuksi")))

  (testing "Poistaminen"
    (let [urakka-id (hae-saimaan-kanavaurakan-id)
          sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
          kohde-id (hae-kohde-kansola)
          tapahtuma-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'FOOBAR FOOBAR FOOBAR';")))
          _ (is (some? tapahtuma-id))
          toiminto-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma_toiminto WHERE \"liikennetapahtuma-id\"=" tapahtuma-id ";")))
          hakuparametrit {:urakka-idt #{urakka-id}}
          laiva-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma_alus WHERE nimi = 'HUPILAIVA' AND \"liikennetapahtuma-id\"=" tapahtuma-id ";")))
          vanhat (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-liikennetapahtumat
                                 +kayttaja-jvh+
                                 hakuparametrit)
          _ (is (not-empty vanhat))

          params {::lt/urakka-id urakka-id
                  ::lt/sopimus-id sopimus-id
                  ::lt/kohde-id kohde-id
                  ::lt/id tapahtuma-id
                  ::lt/aika (pvm/nyt)
                  ::lt/toiminnot [{::toiminto/id toiminto-id}]
                  ::lt/alukset [{::lt-alus/id laiva-id}]
                  ::lt/vesipinta-alaraja 500
                  ::lt/vesipinta-ylaraja 1000.9
                  ::lt/kuittaaja-id (:id +kayttaja-jvh+)
                  ::lt/lisatieto "FOOBAR FOOBAR FOOBAR"
                  ::m/poistettu? true
                  :hakuparametrit hakuparametrit}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-liikennetapahtuma
                                  +kayttaja-jvh+
                                  params)]

      (is (s/valid? ::lt/tallenna-liikennetapahtuma-kysely params))
      (is (s/valid? ::lt/tallenna-liikennetapahtuma-vastaus vastaus))

      (is (= (count vastaus) (dec (count vanhat))))

      (is (some #(= (::lt/lisatieto %) "FOOBAR FOOBAR FOOBAR") vanhat))
      (is (empty? (filter #(= (::lt/lisatieto %) "FOOBAR FOOBAR FOOBAR") vastaus)))))

  (testing "Alukset ja ketjutukset poistettiin, käytetyt ketjutukset vapautettiin"
    (let [[tapahtuma-id poistettu?] (first (q (str "SELECT id, poistettu FROM kan_liikennetapahtuma WHERE lisatieto = 'FOOBAR FOOBAR FOOBAR';")))
          _ (is (true? poistettu?) "Tapahtumaa ei poistettu!")
          hupilaivat (q (str "SELECT poistettu FROM kan_liikennetapahtuma_alus WHERE nimi = 'HUPILAIVA' AND \"liikennetapahtuma-id\"=" tapahtuma-id ";"))]
      (is (= (count hupilaivat) 1) "Testejä on muutettu, ja tapahtumasta löytyy monta alusta nimellä HUPILAIVA..")
      (is (true? (ffirst hupilaivat)) "Hupilaivaa ei poistettu")

      (is (empty? (q (str "SELECT * FROM kan_liikennetapahtuma_ketjutus WHERE \"tapahtumasta-id\"=" tapahtuma-id)))
          "Tapahtuman ketjutuksia ei poistettu, kun tapahtuma poistettiin")

      (let [toiminnot (q (str "SELECT poistettu FROM kan_liikennetapahtuma_toiminto WHERE \"liikennetapahtuma-id\"=" tapahtuma-id ";"))]
        (is (= 1 (count toiminnot)))
        (is (every? true? (first toiminnot)) "Tapahtuman toimintoja ei poistettu")))

    (let [tapahtuma-id (ffirst (q (str "SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Sitten mennään';")))
          ketjutus (q (str "SELECT \"tapahtumaan-id\" FROM kan_liikennetapahtuma_ketjutus WHERE \"tapahtumasta-id\"=" tapahtuma-id))]
      (is (= 1 (count ketjutus)))
      (is (every? nil? (first ketjutus)) "Pällistä tulevia ketjutuksia ei vapautettu, kun Kansolan tapahtuma poistettiin"))))