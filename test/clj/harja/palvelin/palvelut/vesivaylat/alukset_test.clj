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
  (let [kaikkien-alusten-lkm-kannassa (ffirst (q "SELECT COUNT(*) FROM vv_alus"))
        args {}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-kaikki-alukset +kayttaja-jvh+
                              args)]

    (is (s/valid? ::alus/hae-kaikki-alukset-kysely args))
    (is (s/valid? ::alus/hae-kaikki-alukset-vastaus tulos))

    (is (some #(= (::alus/nimi %) "Rohmu") tulos))
    (is (= (count tulos) kaikkien-alusten-lkm-kannassa))))

(deftest hae-kaikki-alukset-ilman-oikeutta
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :hae-kaikki-alukset +kayttaja-ulle+
                                         {}))))

(deftest hae-urakan-alukset
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        urakan-alusten-lkm-kannassa (ffirst (q "SELECT COUNT(*) FROM vv_alus_urakka WHERE urakka = " urakka-id ";"))
        args {::urakka/id urakka-id}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-urakan-alukset +kayttaja-jvh+
                              args)]

    (is (s/valid? ::alus/hae-urakan-alukset-kysely args))
    (is (s/valid? ::alus/hae-urakan-alukset-vastaus tulos))

    (is (some #(= (::alus/nimi %) "Rohmu") tulos))
    (is (= (count tulos) urakan-alusten-lkm-kannassa))))

(deftest hae-urakan-alukset-ilman-oikeutta
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-urakan-alukset +kayttaja-ulle+
                                           {::urakka/id urakka-id})))))

(deftest hae-urakoitsijan-alukset
  (let [urakoitsija-id (hae-helsingin-vesivaylaurakan-urakoitsija)
        urakoitsijan-urakat (hae-urakoitsijan-urakka-idt urakoitsija-id)
        urakoitsijan-alusten-lkm-kannassa (ffirst (q "SELECT COUNT(*) FROM vv_alus_urakka
                                                      WHERE urakka IN (" (str/join "," urakoitsijan-urakat) ");"))
        args {::organisaatio/id urakoitsija-id}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-urakoitsijan-alukset +kayttaja-jvh+
                              args)]

    (is (s/valid? ::alus/hae-urakoitsijan-alukset-kysely args))
    (is (s/valid? ::alus/hae-urakoitsijan-alukset-vastaus tulos))

    (is (some #(= (::alus/nimi %) "Rohmu") tulos))
    (is (= (count tulos) urakoitsijan-alusten-lkm-kannassa))))

(deftest hae-urakoitsijan-alukset-ilman-oikeutta
  (let [urakoitsija-id (hae-helsingin-vesivaylaurakan-urakoitsija)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-urakoitsijan-alukset +kayttaja-ulle+
                                           {::organisaatio/id urakoitsija-id})))))

(deftest tallenna-urakan-alukset
  (let [alukset []
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        args {::urakka/id urakka-id
              ::alus/urakan-tallennettavat-alukset alukset}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-alukset +kayttaja-ulle+
                                args)]

    (is (s/valid? ::alus/tallenna-urakan-alukset-kysely args))
    (is (s/valid? ::alus/hae-kaikki-alukset-vastaus vastaus))))