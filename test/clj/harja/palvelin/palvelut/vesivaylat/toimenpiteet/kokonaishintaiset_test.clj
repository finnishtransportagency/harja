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
            [harja.domain.vesivaylat.toimenpide :as to]
            [clojure.string :as str]
            [harja.domain.vesivaylat.vayla :as va]
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
        sopimus-id (hae-helsingin-vesivaylaurakan-sopimuksen-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kokonaishintaiset-toimenpiteet +kayttaja-jvh+
                                {::to/urakka-id urakka-id
                                 ::to/sopimus-id sopimus-id})]
    (is (>= (count vastaus) 1))
    (is (s/valid? ::to/hae-kokonaishintaiset-toimenpiteet vastaus))))