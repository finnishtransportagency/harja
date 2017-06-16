(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.yksikkohintaiset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.toimenpide :as toi]
            [harja.domain.urakka :as u]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.apurit :as apurit]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.palvelin.palvelut.vesivaylat.toimenpiteet.yksikkohintaiset :as yks]
            [clojure.spec.alpha :as s]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :vv-yksikkohintaiset (component/using
                                                (yks/->YksikkohintaisetToimenpiteet)
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
                                :hae-yksikkohintaiset-toimenpiteet +kayttaja-jvh+
                                kysely-params)]
    (is (s/valid? ::toi/hae-vesivaylien-toimenpiteet-kysely kysely-params))
    (is (s/valid? ::toi/hae-vesivayilien-yksikkohintaiset-toimenpiteet-vastaus vastaus))
    (is (>= (count vastaus) 4))))

(deftest kokonaishintaisiin-siirto
  (let [yksikkohintaiset-toimenpide-idt (apurit/hae-yksikkohintaiset-toimenpide-idt)
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        kysely-params {::toi/urakka-id urakka-id
                       ::toi/idt yksikkohintaiset-toimenpide-idt}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :siirra-toimenpiteet-kokonaishintaisiin +kayttaja-jvh+
                                kysely-params)
        nykyiset-kokonaishintaiset-toimenpide-idt (apurit/hae-yksikkohintaiset-toimenpide-idt)
        siirrettyjen-uudet-tyypit (apurit/hae-toimenpiteiden-tyyppi yksikkohintaiset-toimenpide-idt)]
    (is (s/valid? ::toi/siirra-toimenpiteet-kokonaishintaisiin-kysely kysely-params))
    (is (s/valid? ::toi/siirra-toimenpiteet-kokonaishintaisiin-vastaus vastaus))

    (is (= vastaus yksikkohintaiset-toimenpide-idt) "Vastauksena siirrettyjen id:t")
    (is (empty? nykyiset-kokonaishintaiset-toimenpide-idt) "Kaikki siirrettiin")
    (is (every? #(= % "kokonaishintainen") siirrettyjen-uudet-tyypit) "Uudet tyypit on oikein")))