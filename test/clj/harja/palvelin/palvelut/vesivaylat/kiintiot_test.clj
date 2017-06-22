(ns harja.palvelin.palvelut.vesivaylat.kiintiot-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.muokkaustiedot :as m]
            [harja.palvelin.palvelut.vesivaylat.kiintiot :as pal]))

(defn jarjestelma-fixture [testit]
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
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures jarjestelma-fixture
                                      tietokanta-fixture))

(deftest kiintioiden-haku
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        params {::kiintio/urakka-id urakka-id
                ::kiintio/sopimus-id sopimus-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kiintiot
                                +kayttaja-jvh+
                                params)]
    (is (s/valid? ::kiintio/hae-kiintiot-kysely params))
    (is (s/valid? ::kiintio/hae-kiintiot-vastaus vastaus))
    (is (>= (count vastaus) 2))))

(deftest kiintioiden-tallennus
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        params {::kiintio/urakka-id urakka-id
           ::kiintio/sopimus-id sopimus-id}
        kiintiot (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-kiintiot
                                 +kayttaja-jvh+
                                 params)]
    (testing "Muokkaaminen"
      (let [kiintiotiedot (assoc-in kiintiot [0 ::kiintio/kuvaus] "Testi123")
            params (assoc params ::kiintio/tallennettavat-kiintiot kiintiotiedot)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-kiintiot
                                    +kayttaja-jvh+
                                    params)]
        (is (not (some (comp (partial = "Testi123") ::kiintio/kuvaus) kiintiot)))
        (is (s/valid? ::kiintio/tallenna-kiintiot-kysely params))
        (is (s/valid? ::kiintio/tallenna-kiintiot-vastaus vastaus))
        (is (>= (count vastaus) 2))
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
      (let [kiintiotiedot (assoc-in kiintiot [0 ::kiintio/id] (hae-kiintio-nimella "Joku kiintiö Vantaalla"))
            params (assoc params ::kiintio/tallennettavat-kiintiot kiintiotiedot)]


        (is (thrown? SecurityException
                     (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :tallenna-kiintiot
                                     +kayttaja-jvh+
                                     params)))))))