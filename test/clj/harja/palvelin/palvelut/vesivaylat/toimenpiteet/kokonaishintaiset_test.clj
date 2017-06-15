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
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.apurit :as apurit]
            [harja.domain.toteuma :as tot]
            [harja.domain.urakka :as u]
            [harja.domain.urakka :as u]
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
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id
                       :alku (c/to-date (t/date-time 2017 1 1))
                       :loppu (c/to-date (t/date-time 2018 1 1))}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                kysely-params)]
    (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
    (is (s/valid? ::toi/hae-vesivayilien-kokonaishintaiset-toimenpiteet-vastaus vastaus))
    (is (>= (count vastaus) 4))))

(deftest toimenpiteiden-haku-toimii-urakkafiltterilla
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                kysely-params)]
    (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
    (is (= (count vastaus) 0)
        "Ei toimenpiteitä Muhoksen urakassa")))

(deftest toimenpiteiden-haku-toimii-sopimusfiltterilla
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-sivusopimuksen-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                kysely-params)]
    (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
    (is (= (count vastaus) 0)
        "Ei toimenpiteitä sivusopimuksella")))

(deftest toimenpiteiden-haku-toimii-aikafiltterilla
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id
                       :alku (c/to-date (t/date-time 2016 1 1))
                       :loppu (c/to-date (t/date-time 2017 1 1))}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                kysely-params)]
    (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
    (is (= (count vastaus) 0)
        "Ei toimenpiteitä tällä aikavälillä")))

(deftest toimenpiteiden-haku-toimii-vaylafiltterilla
  (testing "Väyläfiltteri löytää toimenpiteet"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vayla-id (hae-vayla-hietarasaari)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/vayla-id vayla-id}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (>= (count vastaus) 4))))

  (testing "Väyläfiltterissä oleva väylä pitää olla samaa tyyppiä kuin väylätyyppi-filtterissä"
    ;; Käytännössä tällaista tilannetta ei pitäisi tulla, UI:lta voidaan valita vain
    ;; annetun väylätyypin mukaisia väyliä filtteriksi.
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vayla-id (hae-vayla-hietarasaari) ;; Tyyppiä kauppamerenkulku
          vaylatyyppi :muu
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/vayla-id vayla-id
                         ::va/vaylatyyppi vaylatyyppi}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (= (count vastaus) 0))))

  (testing "Väyläfiltteri suodattaa toimenpiteet"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/vayla-id -1}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (= (count vastaus) 0)
          "Ei toimenpiteitä tällä väylällä"))))

(deftest toimenpiteiden-haku-toimii-vaylatyyppifiltterilla
  (testing "Väylätyyppifiltteri löytää toimenpiteet"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vaylatyyppi :kauppamerenkulku
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::va/vaylatyyppi vaylatyyppi}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (>= (count vastaus) 4))))

  (testing "Väylätyyppifiltteri suodattaa toimenpiteet"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vaylatyyppi :muu
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::va/vaylatyyppi vaylatyyppi}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (= (count vastaus) 0)
          "Ei toimenpiteitä tällä väylätyypillä"))))

(deftest toimenpiteiden-haku-toimii-vikailmoitusfiltterilla
  (testing "Vikailmoituksellit löytyy"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         :vikailmoitukset? true}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (= (count vastaus) 1))))

  (testing "Vikailmoituksettomat löytyy"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         :vikailmoitukset? false}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (>= (count vastaus) 3))))

  (testing "Vikailmoituksettomat löytyy"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         :vikailmoitukset? nil}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (>= (count vastaus) 3)))))

(deftest toimenpiteiden-haku-toimii-toimenpidefilttereilla
  (testing "Työlajilla suodatus toimii"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-tyolaji "1022541802"}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (>= (count vastaus) 4)))

    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-tyolaji "1022541807"}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (= (count vastaus) 0))))

  (testing "Työluokalla suodatus toimii"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-tyoluokat #{"1022541905"}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (>= (count vastaus) 4)))

    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-tyoluokat #{"1022541920"}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (= (count vastaus) 0))))

  (testing "Toimenpiteellä suodatus toimii"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-toimenpidetyypit #{"1022542001"}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (>= (count vastaus) 4)))

    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-toimenpidetyypit #{"1022542046"}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (= (count vastaus) 0))))

  (testing "Työlajilla, työluokka ja & toimenpide toimivat yhdessä"
    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-tyolaji "1022541802"
                         ::toi/reimari-toimenpidetyypit #{"1022542001"}
                         ::toi/reimari-tyoluokat #{"1022541905"}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (>= (count vastaus) 4)))

    (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-tyolaji "1022541807"
                         ::toi/reimari-toimenpidetyypit #{"1022542046"}
                         ::toi/reimari-tyoluokat #{"1022541920"}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (= (count vastaus) 0)))))

(deftest toimenpiteiden-haku-ei-toimi-ilman-oikeuksia
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id}]
    (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-kokonaishintaiset-toimenpiteet +kayttaja-ulle+
                                           kysely-params)))))

(deftest yksikkohintaisiin-siirto
  (let [kokonaishintaiset-toimenpide-idt (apurit/hae-kokonaishintaiset-toimenpide-idt)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/idt kokonaishintaiset-toimenpide-idt}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :siirra-toimenpiteet-yksikkohintaisiin +kayttaja-jvh+
                                kysely-params)
        nykyiset-kokonaishintaiset-toimenpide-idt (apurit/hae-kokonaishintaiset-toimenpide-idt)
        siirrettyjen-uudet-tyypit (apurit/hae-toimenpiteiden-tyyppi kokonaishintaiset-toimenpide-idt)]
    (is (s/valid? ::toi/siirra-toimenpiteet-yksikkohintaisiin-kysely kysely-params))
    (is (s/valid? ::toi/siirra-toimenpiteet-yksikkohintaisiin-vastaus vastaus))

    (is (= vastaus kokonaishintaiset-toimenpide-idt) "Vastauksena siirrettyjen id:t")
    (is (empty? nykyiset-kokonaishintaiset-toimenpide-idt) "Kaikki siirrettiin")
    (is (every? #(= % "yksikkohintainen") siirrettyjen-uudet-tyypit) "Uudet tyypit on oikein")))