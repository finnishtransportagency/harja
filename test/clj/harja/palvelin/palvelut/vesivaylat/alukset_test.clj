(ns harja.palvelin.palvelut.vesivaylat.alukset-test
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
            [harja.palvelin.palvelut.vesivaylat.alukset :as vv-alukset]
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
                        :pois-kytketyt-ominaisuudet testi-pois-kytketyt-ominaisuudet
                        :vv-alukset (component/using
                                      (vv-alukset/->Alukset)
                                      [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-kaikki-alukset
  (let [tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-kaikki-alukset +kayttaja-jvh+
                              {})]
    (is (some? tulos))
    ;; TODO
    ))

(deftest hae-urakan-alukset
  (let [tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-urakan-alukset +kayttaja-jvh+
                              {})]
    (is (some? tulos))
    ;; TODO
    ))

(deftest hae-urakoitsijan-alukset
  (let [tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-urakoitsijan-alukset +kayttaja-jvh+
                              {})]
    (is (some? tulos))
    ;; TODO
    ))
