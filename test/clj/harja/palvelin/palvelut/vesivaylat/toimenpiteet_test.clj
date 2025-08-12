(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet-test
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
            [harja.domain.urakka :as u]
            [harja.domain.urakka :as u]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet :as vv-toimenpiteet]
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
                        :vv-toimenpiteet (component/using
                                           (vv-toimenpiteet/->Toimenpiteet)
                                           [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def tp-komponenttien-tilat-referenssidata
  [{::toi/komponentti-id -2139967596,
    ::toi/tilakoodi "1022540401",
    ::toi/toimenpide-id 1}
   {::toi/komponentti-id -567765567,
    ::toi/tilakoodi "1022540402",
    ::toi/toimenpide-id 1}])

(deftest kok-hint-toimenpiteiden-haku
  (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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

    (is (>= (count vastaus) 2))

    (is (every? #(integer? (::toi/id %)) vastaus))
    (is (every? #(or (and (string? (::toi/lisatieto %))
                          (>= (count (::toi/lisatieto %)) 1))
                     (nil? (::toi/lisatieto %))) vastaus))
    (is (every? #(keyword? (::toi/tyolaji %)) vastaus))
    (is (every? #(keyword? (::toi/tyoluokka %)) vastaus))
    (is (every? #(keyword? (::toi/toimenpide %)) vastaus))
    (is (some #(not (empty? (::toi/liitteet %))) vastaus))
    (is (some #(>= (count (::toi/liitteet %)) 2) vastaus))
    (is (some #(number? (::toi/reimari-henkilo-lkm %)) vastaus))
    (is (not-any? #(str/includes? (str/lower-case (:nimi %)) "poistettu")
                  (mapcat ::toi/liitteet vastaus)))
    (is (every? #(nil? (::toi/liite-linkit %)) vastaus))
    (is (some #(> (count (get-in % [::toi/komponentit])) 0) vastaus))))

(deftest yks-hint-toimenpiteiden-haku
  (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id
                       :alku (c/to-date (t/date-time 2017 1 1))
                       :loppu (c/to-date (t/date-time 2018 1 1))}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-yksikkohintaiset-toimenpiteet +kayttaja-jvh+
                  kysely-params)]

    (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
    (is (s/valid? ::toi/hae-vesivayilien-yksikkohintaiset-toimenpiteet-vastaus vastaus))

    (is (>= (count vastaus) 2))

    (is (some #(not (empty? (get-in % [::toi/oma-hinnoittelu ::h/hinnat]))) vastaus))
    (is (some #(not (empty? (get-in % [::toi/oma-hinnoittelu ::h/tyot]))) vastaus))
    (is (some #(not (empty? (::toi/oma-hinnoittelu %))) vastaus))
    (is (some #(integer? (::toi/hintaryhma-id %)) vastaus))

    (is (every? #(integer? (::toi/id %)) vastaus))
    (is (every? #(or (and (string? (::toi/lisatieto %))
                       (>= (count (::toi/lisatieto %)) 1))
                   (nil? (::toi/lisatieto %))) vastaus))
    (is (every? #(or (nil? (::toi/tyolaji %)) (keyword? (::toi/tyolaji %))) vastaus))
    (is (every? #(or (nil? (::toi/tyoluokka %)) (keyword? (::toi/tyoluokka %))) vastaus))
    (is (every? #(or (nil? (::toi/toimenpide %)) (keyword? (::toi/toimenpide %))) vastaus))
    (is (some #(not (empty? (::toi/liitteet %))) vastaus))
    (is (some #(>= (count (::toi/liitteet %)) 1) vastaus))
    (is (some #(number? (::toi/reimari-henkilo-lkm %)) vastaus))
    (is (not-any? #(str/includes? (str/lower-case (:nimi %)) "poistettu")
          (mapcat ::toi/liitteet vastaus)))
    (is (every? #(nil? (::toi/liite-linkit %)) vastaus))
    (is (some #(> (count (get-in % [::toi/komponentit])) 0) vastaus))))


(deftest toimenpiteiden-haku-toimii-urakkafiltterilla
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
  (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
  (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vaylanro (hae-vayla-hietarasaari)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/vaylanro vaylanro}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]

      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (>= (count vastaus) 2))
      (is (every? #(= (get-in % [::toi/vayla ::va/vaylanro]) vaylanro) vastaus))))

  (testing "Väyläfiltterissä oleva väylä pitää olla samaa tyyppiä kuin väylätyyppi-filtterissä,
            muuten ei palaudu mitään"
    ;; Käytännössä tällaista tilannetta ei pitäisi tulla, UI:lta voidaan valita vain
    ;; annetun väylätyypin mukaisia väyliä filtteriksi.
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vaylanro (hae-vayla-hietarasaari) ;; Tyyppiä kauppamerenkulku
          vaylatyyppi :muu
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/vaylanro vaylanro
                         ::va/vaylatyyppi vaylatyyppi}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (= (count vastaus) 0))))


  (testing "Väyläfiltteri suodattaa toimenpiteet"
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/vaylanro -1}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (= (count vastaus) 0) "Ei toimenpiteitä höpöväylällä"))))

(deftest toimenpiteiden-haku-toimii-vaylatyyppifiltterilla
  (testing "Väylätyyppifiltteri löytää toimenpiteet"
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vaylatyyppi :kauppamerenkulku
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::va/vaylatyyppi vaylatyyppi}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (>= (count vastaus) 4))
      (is (every? #(= (get-in % [::toi/vayla ::va/tyyppi]) :kauppamerenkulku) vastaus))))

  (testing "Väylätyyppifiltteri suodattaa toimenpiteet"
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          vaylatyyppi :muu
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::va/vaylatyyppi vaylatyyppi}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (= (count vastaus) 0) "Ei toimenpiteitä tällä väylätyypillä"))))

(deftest toimenpiteiden-haku-toimii-turvalaiteifiltterilla
  (testing "Virheellinen turvalaitefiltteri ei löydä mitään"
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/vaylatyyppi :kauppamerenkulku
                         ::toi/turvalaitenro "-1"}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (= (count vastaus) 0)))))

(deftest toimenpiteiden-haku-toimii-vikailmoitusfiltterilla
  (testing ":vikailmoitukset? true, vain vikailmoitukselliset palautuu"
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         :vikailmoitukset? true}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (= (count vastaus) 1))
      (is (every? #(true? (::toi/vikakorjauksia? %)) vastaus))))

  (testing ":vikailmoitukset? false, palautuu vikailmoitukselliset ja -ilmoituksettomat"
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         :vikailmoitukset? false}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]

      (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
      (is (>= (count vastaus) 3))))

  (testing ":vikailmoitukset? nil, palautuu vikailmoitukselliset ja -ilmoituksettomat"
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-tyolaji "1022541802"}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (>= (count vastaus) 4))
      (is (every? #(= (::toi/tyolaji %) :poijut) vastaus)))

    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-tyolaji "1022541807"}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (= (count vastaus) 0))))

  (testing "Työluokalla suodatus toimii"
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-tyoluokat #{"1022541905"}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (>= (count vastaus) 3))
      (is (every? #(= (::toi/tyoluokka %) :valo-ja-energialaitteet) vastaus)))

    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-tyoluokat #{"1022541920"}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (= (count vastaus) 0))))

  (testing "Toimenpiteellä suodatus toimii"
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-toimenpidetyypit #{"1022542001"}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (>= (count vastaus) 3))
      (is (every? #(= (::toi/toimenpide %) :valo-ja-energialaitetyot) vastaus)))

    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
          sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
          kysely-params {::toi/urakka-id urakka-id
                         ::toi/sopimus-id sopimus-id
                         ::toi/reimari-toimenpidetyypit #{"1022542046"}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                  kysely-params)]
      (is (= (count vastaus) 0))))

  (testing "Työlajilla, työluokka ja & toimenpide toimivat yhdessä"
    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
      (is (>= (count vastaus) 3))

      (is (every? #(= (::toi/tyolaji %) :poijut) vastaus))
      (is (every? #(= (::toi/tyoluokka %) :valo-ja-energialaitteet) vastaus))
      (is (every? #(= (::toi/toimenpide %) :valo-ja-energialaitetyot) vastaus)))

    (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
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
  (let [urakka-id (hae-urakan-id-nimella "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL")
        sopimus-id (hae-helsingin-vesivaylaurakan-paasopimuksen-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id}]
    (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                             :hae-kokonaishintaiset-toimenpiteet +kayttaja-ulle+
                             kysely-params)))))
