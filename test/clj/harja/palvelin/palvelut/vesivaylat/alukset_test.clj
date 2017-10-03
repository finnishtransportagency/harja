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

            [harja.domain.vesivaylat.alus :as alus]
            [harja.domain.urakka :as urakka]
            [harja.domain.organisaatio :as organisaatio]
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
  (let [args {}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-kaikki-alukset +kayttaja-jvh+
                              args)]

    (is (s/valid? ::alus/hae-kaikki-alukset-kysely args))
    (is (s/valid? ::alus/hae-kaikki-alukset-vastaus tulos))

    (is (some? tulos))))

(deftest hae-urakan-alukset
  (let [args {::urakka/id (hae-helsingin-vesivaylaurakan-id)}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-urakan-alukset +kayttaja-jvh+
                              args)]

    (is (s/valid? ::alus/hae-urakan-alukset-kysely args))
    (is (s/valid? ::alus/hae-urakan-alukset-vastaus tulos))

    (is (some? tulos))))

(deftest hae-urakoitsijan-alukset
  (let [args {::organisaatio/id (hae-helsingin-vesivaylaurakan-urakoitsija)}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-urakoitsijan-alukset +kayttaja-jvh+
                              {})]

    (is (s/valid? ::alus/hae-urakoitsijan-alukset-kysely args))
    (is (s/valid? ::alus/hae-urakoitsijan-alukset-vastaus tulos))))
