(ns harja.palvelin.palvelut.vesivaylat.turvalaitteet-test
    (:require [clojure.test :refer :all]
              [com.stuartsierra.component :as component]
              [harja
               [pvm :as pvm]
               [testi :refer :all]]
              [harja.palvelin.komponentit.tietokanta :as tietokanta]
              [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
              [harja.tyokalut.functor :refer [fmap]]
              [taoensso.timbre :as log]
              [clojure.string :as str]
              [harja.palvelin.palvelut.vesivaylat.toimenpiteet.apurit :as apurit]
              [harja.palvelin.palvelut.vesivaylat.turvalaitteet :as vv-turvalaitteet]
              [clojure.spec.alpha :as s]
              [clj-time.core :as t]
              [clj-time.coerce :as c]

              [harja.domain.vesivaylat.turvalaite :as tu]
              [harja.domain.muokkaustiedot :as m]))

(defn jarjestelma-fixture [testit]
    (alter-var-root #'jarjestelma
                    (fn [_]
                        (component/start
                            (component/system-map
                                :db (tietokanta/luo-tietokanta testitietokanta)
                                :http-palvelin (testi-http-palvelin)
                                :vv-turvalaitteet (component/using
                                                     (vv-turvalaitteet/->Turvalaitteet)
                                                     [:db :http-palvelin])))))

    (testit)
    (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                        jarjestelma-fixture
                        urakkatieto-fixture))

(deftest hae-kartalle-turvalaitenumerolla
  (let [params {:turvalaitenumerot #{"1234"}}
          tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-turvalaitteet-kartalle +kayttaja-jvh+
                                params)]
      (is (s/valid? ::tu/hae-turvalaitteet-kartalle-kysely params))
      (is (s/valid? ::tu/hae-turvalaitteet-kartalle-vastaus tulos))

      (is (= 1 (count tulos)))
      (is (true? (contains? (first tulos) ::tu/koordinaatit)))))

(deftest hae-kartalle-vaylanumerolla
  (let [params {:vaylanumerot #{66662}}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-turvalaitteet-kartalle +kayttaja-jvh+
                              params)]
    (is (s/valid? ::tu/hae-turvalaitteet-kartalle-kysely params))
    (is (s/valid? ::tu/hae-turvalaitteet-kartalle-vastaus tulos))

    (is (= 4 (count tulos)))
    (is (every? #(contains? % ::tu/koordinaatit) tulos))))

(deftest hae-kartalle-tyhjilla-tiedoilla
  (let [params {}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-turvalaitteet-kartalle +kayttaja-jvh+
                              params)]
    (is (s/valid? ::tu/hae-turvalaitteet-kartalle-kysely params))
    (is (s/valid? ::tu/hae-turvalaitteet-kartalle-vastaus tulos))

    (is (= 0 (count tulos)))))
