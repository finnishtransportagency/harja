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
            [clojure.spec.alpha :as s]))

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
                       ::toi/sopimus-id sopimus-id}
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
        "Ei vesiväyläjuttuja Muhoksen urakassa")))

(deftest toimenpiteiden-haku-toimii-sopimusfiltterilla
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        sopimus-id (hae-helsingin-vesivaylaurakan-sivusopimuksen-id)
        kysely-params {::tot/urakka-id urakka-id
                       ::toi/sopimus-id sopimus-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                kysely-params)]
    (is (= (count vastaus) 0)
        "Ei vesiväyläjuttuja sivusopimuksella")))