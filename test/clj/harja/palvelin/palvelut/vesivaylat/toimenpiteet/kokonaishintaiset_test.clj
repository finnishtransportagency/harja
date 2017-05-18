(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.kokonaishintaiset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.toimenpide :as toi]
            [clojure.string :as str]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.toteuma :as tot]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.kokonaishintaiset :as ko]
            [clojure.spec.alpha :as s]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :vv-kokonaishintaiset (component/using
                                                (ko/->KokonaishintaisetToimenpiteet)
                                                [:db :http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest toimenpiteiden-haku
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        kysely-params {::tot/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id
                       :alku (c/to-date (t/date-time 2017 1 1))
                       :loppu (c/to-date (t/date-time 2018 1 1))}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                kysely-params)]
    (is (s/valid? ::toi/hae-kokonaishintaiset-toimenpiteet-kysely kysely-params))
    (is (>= (count vastaus) 4))
    (is (s/valid? ::toi/hae-kokonaishintaiset-toimenpiteet-vastaus vastaus))))

(deftest toimenpiteiden-haku-toimii-urakkafiltterilla
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        kysely-params {::tot/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                kysely-params)]
    (is (= (count vastaus) 0)
        "Ei toimenpiteitä Muhoksen urakassa")))

(deftest toimenpiteiden-haku-toimii-sopimusfiltterilla
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-sivusopimuksen-id)
        kysely-params {::tot/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                kysely-params)]
    (is (= (count vastaus) 0)
        "Ei toimenpiteitä sivusopimuksella")))

(deftest toimenpiteiden-haku-toimii-aikafiltterilla
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        kysely-params {::tot/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id
                       :alku (c/to-date (t/date-time 2016 1 1))
                       :loppu (c/to-date (t/date-time 2017 1 1))}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                kysely-params)]
    (is (= (count vastaus) 0)
        "Ei toimenpiteitä tällä aikavälillä")))

(deftest toimenpiteiden-haku-toimii-vaylafiltterilla
  (testing "Väyläfiltteri löytää toimenpiteet"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vayla-id (hae-vayla-hietarasaari)
          kysely-params {::tot/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/vayla-id vayla-id}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (>= (count vastaus) 4))))

  (testing "Väyläfiltterissä oleva väylä pitää olla samaa tyyppiä kuin väylätyyppi-filtterissä"
    ;; Käytännössä tällaista tilannetta ei pitäisi tulla, UI:lta voidaan valita vain
    ;; annetun väylätyypin mukaisia väyliä filtteriksi.
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vayla-id (hae-vayla-hietarasaari) ;; Tyyppiä kauppamerenkulku
          vaylatyyppi :muu
          kysely-params {::tot/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/vayla-id vayla-id
                         ::va/vaylatyyppi vaylatyyppi}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (= (count vastaus) 0))))

  (testing "Väyläfiltteri suodattaa toimenpiteet"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::tot/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/vayla-id -1}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (= (count vastaus) 0)
          "Ei toimenpiteitä tällä väylällä"))))

(deftest toimenpiteiden-haku-toimii-vaylatyyppifiltterilla
  (testing "Väylätyyppifiltteri löytää toimenpiteet"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vaylatyyppi :kauppamerenkulku
          kysely-params {::tot/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::va/vaylatyyppi vaylatyyppi}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (>= (count vastaus) 4))))

  (testing "Väylätyyppifiltteri suodattaa toimenpiteet"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vaylatyyppi :muu
          kysely-params {::tot/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::va/vaylatyyppi vaylatyyppi}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (= (count vastaus) 0)
          "Ei toimenpiteitä tällä väylätyypillä"))))

(deftest toimenpiteiden-haku-ei-toimi-ilman-oikeuksia
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        kysely-params {::tot/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-kokonaishintaiset-toimenpiteet +kayttaja-ulle+
                                           kysely-params)))))