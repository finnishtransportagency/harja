(ns harja.palvelin.palvelut.vesivaylat.kiintiot-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.muokkaustiedot :as m]
            [harja.palvelin.palvelut.vesivaylat.kiintiot :as pal]))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :vv-kiintiot (component/using
                                       (pal/->Kiintiot)
                                       [:db :http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :each (compose-fixtures jarjestelma-fixture
                                      tietokanta-fixture))

(deftest kiintioiden-haku
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        params {::kiintio/urakka-id urakka-id
                ::kiintio/sopimus-id sopimus-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kiintiot-ja-toimenpiteet
                                +kayttaja-jvh+
                                params)]
    (is (s/valid? ::kiintio/hae-kiintiot-kysely params))
    (is (s/valid? ::kiintio/hae-kiintiot-vastaus vastaus))
    (is (>= (count vastaus) 2))))

(deftest kiintioiden-haku
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        params {::kiintio/urakka-id urakka-id
                ::kiintio/sopimus-id sopimus-id}
        kiintiot (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-kiintiot
                                 +kayttaja-jvh+
                                 params)]

    (is (s/valid? ::kiintio/hae-kiintiot-kysely params))
    (is (s/valid? ::kiintio/hae-kiintiot-vastaus kiintiot))

    (is (>= (count kiintiot) 2))
    (is (not (some (comp (partial = "POISTETTU KIINTIÖ EI SAA NÄKYÄ") ::kiintio/kuvaus) kiintiot)))
    (is (every? #(nil? (::kiintio/toimenpiteet %)) kiintiot))))

(deftest kiintioiden-haku-toimenpiteineen
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        params {::kiintio/urakka-id urakka-id
                ::kiintio/sopimus-id sopimus-id}
        kiintiot (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-kiintiot-ja-toimenpiteet
                                 +kayttaja-jvh+
                                 params)]

    (is (s/valid? ::kiintio/hae-kiintiot-kysely params))
    (is (s/valid? ::kiintio/hae-kiintiot-ja-toimenpiteet-vastaus kiintiot))

    (is (some #(not (empty? (::kiintio/toimenpiteet %))) kiintiot)
        "Kiintiöille palautuu toimenpiteet, kuten luvataan")
    (is (not (some (comp (partial = "POISTETTU KIINTIÖ EI SAA NÄKYÄ") ::kiintio/kuvaus) kiintiot)))))

(deftest kiintioiden-muokkaaminen
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        params {::kiintio/urakka-id urakka-id
                ::kiintio/sopimus-id sopimus-id}
        kiintiot (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-kiintiot-ja-toimenpiteet
                                 +kayttaja-jvh+
                                 params)]
    (testing "Muokkaaminen"
      (let [kiintiotiedot (assoc-in kiintiot [0 ::kiintio/kuvaus] "Testi123")
            params (assoc params ::kiintio/tallennettavat-kiintiot kiintiotiedot)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-kiintiot
                                    +kayttaja-jvh+
                                    params)]

        (is (s/valid? ::kiintio/tallenna-kiintiot-kysely params))
        (is (s/valid? ::kiintio/tallenna-kiintiot-vastaus vastaus))

        (is (not (some (comp (partial = "Testi123") ::kiintio/kuvaus) kiintiot)))
        (is (some #(not (empty? (::kiintio/toimenpiteet %))) kiintiot))
        (is (>= (count vastaus) 2))
        (is (>= (count (::kiintio/toimenpiteet (first vastaus))) 1))
        (is (some (comp (partial = "Testi123") ::kiintio/kuvaus) vastaus))
        (is (not (some (comp (partial = "POISTETTU KIINTIÖ EI SAA NÄKYÄ") ::kiintio/kuvaus) vastaus)))))

    (testing "Luominen"
      (let [kiintiot-ennen (ffirst (q "SELECT COUNT(*) FROM vv_kiintio"))
            kiintiotiedot (conj kiintiot {::kiintio/nimi "Foo" ::kiintio/koko 30})
            params (assoc params ::kiintio/tallennettavat-kiintiot kiintiotiedot)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-kiintiot
                                    +kayttaja-jvh+
                                    params)
            kiintiot-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_kiintio"))]
        (is (s/valid? ::kiintio/tallenna-kiintiot-kysely params))
        (is (s/valid? ::kiintio/tallenna-kiintiot-vastaus vastaus))
        (is (>= (count vastaus) 3))
        (is (= (+ kiintiot-ennen 1) kiintiot-jalkeen))
        (is (some (comp (partial = "Foo") ::kiintio/nimi) vastaus))))

    (testing "Poistaminen"
      (let [kiintiotiedot (assoc-in kiintiot [0 ::m/poistettu?] true)
            params (assoc params ::kiintio/tallennettavat-kiintiot kiintiotiedot)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-kiintiot
                                    +kayttaja-jvh+
                                    params)]
        (is (s/valid? ::kiintio/tallenna-kiintiot-kysely params))
        (is (s/valid? ::kiintio/tallenna-kiintiot-vastaus vastaus))
        (is (>= (count vastaus) 1))))

    (testing "Kiintiöiden tallennus ilman oikeutta"
      (let [kiintiotiedot (assoc-in kiintiot [0 ::kiintio/kuvaus] "Testi123")
            params (assoc params ::kiintio/tallennettavat-kiintiot kiintiotiedot)]


        (is (thrown? Exception
                     (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :tallenna-kiintiot
                                     +kayttaja-tero+
                                     params)))))

    (testing "Kiintiöiden tallennus eri urakkaan"
      (let [kiintiotiedot (assoc-in kiintiot [0 ::kiintio/id] (hae-kiintio-id-nimella "Joku kiintiö Vantaalla"))
            params (assoc params ::kiintio/tallennettavat-kiintiot kiintiotiedot)]


        (is (thrown? SecurityException
                     (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :tallenna-kiintiot
                                     +kayttaja-jvh+
                                     params)))))))

(deftest toimenpiteen-liittaminen-kiintioon
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        toimenpide-id (hae-reimari-toimenpide-poiujen-korjaus)
        kiintio-id (hae-kiintio-siirtyneiden-poijujen-korjaus)
        toimenpiteen-kiintio-id-ennen (ffirst (q "SELECT \"kiintio-id\" FROM reimari_toimenpide WHERE id = " toimenpide-id ";"))
        params {::kiintio/id kiintio-id
                ::kiintio/urakka-id urakka-id
                ::to/idt #{toimenpide-id}}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :liita-toimenpiteet-kiintioon
                                +kayttaja-jvh+
                                params)
        toimenpiteen-kiintio-id-jalkeen (ffirst (q "SELECT \"kiintio-id\" FROM reimari_toimenpide WHERE id = " toimenpide-id ";"))]

    (is (s/valid? ::kiintio/liita-toimenpiteet-kiintioon-kysely params))
    (is (s/valid? ::kiintio/liita-toimenpiteet-kiintioon-vastaus vastaus))

    (is (nil? toimenpiteen-kiintio-id-ennen) "Toimenpide ei kuulu kiintiöön ennen testiä")
    (is (= toimenpiteen-kiintio-id-jalkeen kiintio-id) "Toimenpide liitettiin kiintiöön")))

(deftest toimenpiteen-liittaminen-kiintioon-kun-toimenpide-ei-kuulu-urakkaan
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        toimenpide-id (hae-reimari-toimenpide-poiujen-korjaus)
        kiintio-id (hae-kiintio-siirtyneiden-poijujen-korjaus)
        params {::kiintio/id kiintio-id
                ::kiintio/urakka-id urakka-id
                ::to/idt #{toimenpide-id}}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :liita-toimenpiteet-kiintioon
                                                   +kayttaja-jvh+
                                                   params)))))

(deftest toimenpiteen-liittaminen-kiintioon-ilman-oikeutta
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        toimenpide-id (hae-reimari-toimenpide-poiujen-korjaus)
        kiintio-id (hae-kiintio-siirtyneiden-poijujen-korjaus)
        params {::kiintio/id kiintio-id
                ::kiintio/urakka-id urakka-id
                ::to/idt #{toimenpide-id}}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :liita-toimenpiteet-kiintioon
                                           +kayttaja-tero+
                                           params)))))

(deftest toimenpiteen-liittaminen-kiintioon-joka-ei-kuulu-urakkaan
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        toimenpide-id (hae-reimari-toimenpide-poiujen-korjaus)
        kiintio-id (hae-kiintio-id-nimella "Joku kiintiö Vantaalla")
        params {::kiintio/id kiintio-id
                ::kiintio/urakka-id urakka-id
                ::to/idt #{toimenpide-id}}]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :liita-toimenpiteet-kiintioon
                                                   +kayttaja-jvh+
                                                   params)))))

(deftest toimenpiteen-irrotus-kiintiosta
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        toimenpide-id (hae-kiintioon-kuuluva-reimari-toimenpide)
        toimenpiteen-kiintio-id-ennen (ffirst (q "SELECT \"kiintio-id\" FROM reimari_toimenpide WHERE id = " toimenpide-id ";"))
        params {::to/urakka-id urakka-id
                ::to/idt #{toimenpide-id}}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :irrota-toimenpiteet-kiintiosta
                                +kayttaja-jvh+
                                params)
        toimenpiteen-kiintio-id-jalkeen (ffirst (q "SELECT \"kiintio-id\" FROM reimari_toimenpide WHERE id = " toimenpide-id ";"))]

    (is (s/valid? ::kiintio/irrota-toimenpiteet-kiintiosta-kysely params))
    (is (s/valid? ::kiintio/irrota-toimenpiteet-kiintiosta-vastaus vastaus))

    (is (integer? toimenpiteen-kiintio-id-ennen) "Toimenpide kuului kiintiöön ennen testiä")
    (is (nil? toimenpiteen-kiintio-id-jalkeen) "Toimenpide irrotettiin kiintiöstä")))

(deftest toimenpiteen-irrotus-kun-toimenpiteet-eivat-kuulu-urakkaan
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        toimenpide-id (hae-kiintioon-kuuluva-reimari-toimenpide)
        params {::to/urakka-id urakka-id
                ::to/idt #{toimenpide-id}}]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :irrota-toimenpiteet-kiintiosta
                                                   +kayttaja-jvh+
                                                   params)))))

(deftest toimenpiteen-irrotus-ilman-oikeuksia
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        toimenpide-id (hae-kiintioon-kuuluva-reimari-toimenpide)
        params {::to/urakka-id urakka-id
                ::to/idt #{toimenpide-id}}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :irrota-toimenpiteet-kiintiosta
                                           +kayttaja-tero+
                                           params)))))