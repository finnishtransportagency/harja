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
                        :vv-alukset (component/using
                                      (vv-alukset/->Alukset)
                                      [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-urakoitsijan-alukset
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        urakoitsija-id (hae-helsingin-vesivaylaurakan-urakoitsija)
        args {::alus/urakoitsija-id urakoitsija-id
              ::urakka/id urakka-id}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-urakoitsijan-alukset +kayttaja-jvh+
                              args)]

    (is (s/valid? ::alus/hae-urakoitsijan-alukset-kysely args))
    (is (s/valid? ::alus/hae-urakoitsijan-alukset-vastaus tulos))

    (is (some #(= (::alus/nimi %) "Rohmu") tulos))))

(deftest hae-urakoitsijan-alukset-ilman-oikeutta
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        urakoitsija-id (hae-helsingin-vesivaylaurakan-urakoitsija)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-urakoitsijan-alukset +kayttaja-ulle+
                                           {::alus/urakoitsija-id urakoitsija-id
                                            ::urakka/id urakka-id})))))

(deftest tallenna-urakoitsijan-alukset
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        urakoitsija-id (hae-helsingin-vesivaylaurakan-urakoitsija)
        alus-mmsit (set (map :mmsi (q-map "SELECT mmsi FROM vv_alus")))
        alukset-kaytossa (set (map ::mmsi (q-map "SELECT alus FROM vv_alus_urakka WHERE urakka = " urakka-id ";")))
        vapaat-alukset (filter (comp not alukset-kaytossa) alus-mmsit)
        uudet-alukset [{::alus/mmsi (first vapaat-alukset)
                        ::alus/kaytossa-urakassa? false
                        ::alus/lisatiedot "Hassu alus"
                        ::alus/urakan-aluksen-kayton-lisatiedot "Tämä teksti ei tallennu, koska liitetä urakkaan"}
                       {::alus/mmsi (second vapaat-alukset)
                        ::alus/kaytossa-urakassa? true
                        ::alus/lisatiedot "Hieno alus tämäkin"
                        ::alus/urakan-aluksen-kayton-lisatiedot "Kerrassaan upea alus, otetaan urakkaan heti!"}]
        urakan-alukset-ennen (ffirst (q "SELECT COUNT(*) FROM vv_alus_urakka;"))
        args {::alus/urakoitsija-id urakoitsija-id
              ::urakka/id urakka-id
              ::alus/tallennettavat-alukset uudet-alukset}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakoitsijan-alukset +kayttaja-jvh+
                                args)
        urakan-alukset-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_alus_urakka;"))]


    (is (s/valid? ::alus/tallenna-urakoitsijan-alukset-kysely args))
    (is (s/valid? ::alus/hae-urakoitsijan-alukset-vastaus vastaus))

    (is (= (+ urakan-alukset-ennen 1)
           urakan-alukset-jalkeen)
        "Aluslinkkejä tuli yksi lisää (vain yksi alus oli merkattu kuuluvaksi urakkaan)")

    (is (not-any? #(= (::alus/urakan-aluksen-kayton-lisatiedot %) "Tämä teksti ei tallennu, koska liitetä urakkaan") vastaus))
    (is (some #(= (::alus/urakan-aluksen-kayton-lisatiedot %) "Kerrassaan upea alus, otetaan urakkaan heti!") vastaus))))

(deftest tallenna-urakoitsijan-alukset-ilman-oikeutta
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        urakoitsija-id (hae-helsingin-vesivaylaurakan-urakoitsija)
        uudet-alukset []
        args {::alus/urakoitsija-id urakoitsija-id
              ::urakka/id urakka-id
              ::alus/tallennettavat-alukset uudet-alukset}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-urakoitsijan-alukset +kayttaja-ulle+
                                           args)))))

(deftest tallenna-urakoitsijan-alukset-vaaraan-urakkaan
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        urakoitsija-id (hae-helsingin-vesivaylaurakan-urakoitsija)
        uudet-alukset []
        args {::alus/urakoitsija-id urakoitsija-id
              ::urakka/id urakka-id
              ::alus/tallennettavat-alukset uudet-alukset}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-urakoitsijan-alukset +kayttaja-jvh+
                                           args)))))

(deftest tallenna-eri-urakoitsijan-alukset
  (let [urakka-id (hae-oulun-alueurakan-2005-2012-id)
        urakoitsija-id (hae-oulun-alueurakan-2005-2012-urakoitsija)
        alus-mmsit (set (map :mmsi (q-map "SELECT mmsi FROM vv_alus WHERE urakoitsija != " urakoitsija-id ";")))
        uudet-alukset [{::alus/mmsi (first alus-mmsit)
                        ::alus/kaytossa-urakassa? true
                        ::alus/lisatiedot "HAXOROITU ALUS"
                        ::alus/urakan-aluksen-kayton-lisatiedot "hupsis"}]
        args {::alus/urakoitsija-id urakoitsija-id
              ::urakka/id urakka-id
              ::alus/tallennettavat-alukset uudet-alukset}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-urakoitsijan-alukset +kayttaja-jvh+
                                             args)))))

(deftest hae-alusten-reitit
  (let [args {:alukset nil :alku nil :loppu nil}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-alusten-reitit +kayttaja-jvh+
                              args)]

    (is (s/valid? ::alus/hae-alusten-reitit-kysely args))
    (is (s/valid? ::alus/hae-alusten-reitit-vastaus tulos))

    (is (every?
          (fn [t]
            (and (every? some? (vals t))
                 (= #{::alus/sijainti ::alus/alus-mmsi} (into #{} (keys t)))))
          tulos)))

  (let [args {:alukset #{230111580} :alku nil :loppu nil}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-alusten-reitit +kayttaja-jvh+
                              args)]

    (is (s/valid? ::alus/hae-alusten-reitit-kysely args))
    (is (s/valid? ::alus/hae-alusten-reitit-vastaus tulos))

    (is (= 1 (count tulos)))
    (is (every?
          (fn [t]
            (and (every? some? (vals t))
                 (= #{::alus/sijainti ::alus/alus-mmsi} (into #{} (keys t)))))
          tulos))))

(deftest hae-alusten-reitit-pisteineen
  (let [args {:alukset nil :alku nil :loppu nil}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-alusten-reitit-pisteineen +kayttaja-jvh+
                              args)]

    (is (s/valid? ::alus/hae-alusten-reitit-pisteineen-kysely args))
    (is (s/valid? ::alus/hae-alusten-reitit-pisteineen-vastaus tulos))

    (is (every?
          (fn [t]
            (and (every? some? (vals t))
                 (= #{::alus/sijainti ::alus/alus-mmsi ::alus/pisteet} (into #{} (keys t)))))
          tulos))
    (is (every?
          (fn [pisteet]
            (and
              (not-empty pisteet)
              (every?
                (fn [piste]
                  (and (every? some? (vals piste))
                       (= #{::alus/aika ::alus/sijainti} (into #{} (keys piste)))))
                pisteet)))
          (map ::alus/pisteet tulos))))

  (let [args {:alukset #{230111580} :alku nil :loppu nil}
        tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-alusten-reitit-pisteineen +kayttaja-jvh+
                              args)]

    (is (s/valid? ::alus/hae-alusten-reitit-pisteineen-kysely args))
    (is (s/valid? ::alus/hae-alusten-reitit-pisteineen-vastaus tulos))

    (is (= 1 (count tulos)))
    (is (every?
          (fn [t]
            (and (every? some? (vals t))
                 (= #{::alus/sijainti ::alus/alus-mmsi ::alus/pisteet} (into #{} (keys t)))))
          tulos))
    (is (every?
          (fn [pisteet]
            (and
              (not-empty pisteet)
              (every?
                (fn [piste]
                  (and (every? some? (vals piste))
                       (= #{::alus/aika ::alus/sijainti} (into #{} (keys piste)))))
                pisteet)))
          (map ::alus/pisteet tulos)))))
