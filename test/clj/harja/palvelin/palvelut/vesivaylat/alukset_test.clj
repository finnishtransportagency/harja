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
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        alus-mmsit (set (map :mmsi (q-map "SELECT mmsi FROM vv_alus")))
        alukset-kaytossa (set (map ::mmsi (q-map "SELECT alus FROM vv_alus_urakka WHERE urakka = " urakka-id ";")))
        vapaat-alukset (filter (comp not alukset-kaytossa) alus-mmsit)
        uudet-alukset [{::alus/mmsi (first vapaat-alukset)
                        ::alus/urakan-aluksen-kayton-lisatiedot "Hieno alus tässä urakassa"}
                       {::alus/mmsi (second vapaat-alukset)
                        ::alus/urakan-aluksen-kayton-lisatiedot "Kerrassaan upea alus, otetaan urakkaan heti!"}]
        urakan-alukset-ennen (ffirst (q "SELECT COUNT(*) FROM vv_alus_urakka;"))
        args {::urakka/id urakka-id
              ::alus/urakan-tallennettavat-alukset uudet-alukset}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-alukset +kayttaja-ulle+
                                args)
        urakan-alukset-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_alus_urakka;"))]


    (is (s/valid? ::alus/tallenna-urakan-alukset-kysely args))
    (is (s/valid? ::alus/hae-urakan-alukset-vastaus vastaus))

    (is (= (+ urakan-alukset-ennen (count uudet-alukset))
           urakan-alukset-jalkeen)
        "Aluslinkkejä tuli lisää oikea määrä")

    (is (some #(= (::alus/urakan-aluksen-kayton-lisatiedot %) "Hieno alus tässä urakassa") vastaus))
    (is (some #(= (::alus/urakan-aluksen-kayton-lisatiedot %) "Kerrassaan upea alus, otetaan urakkaan heti!") vastaus))))

(deftest tallenna-urakan-alukset-ilman-oikeutta
  (let [urakka-id (hae-helsingin-vesivaylaurakan-id)
        alus-mmsit (set (map :mmsi (q-map "SELECT mmsi FROM vv_alus")))
        alukset-kaytossa (set (map ::mmsi (q-map "SELECT alus FROM vv_alus_urakka WHERE urakka = " urakka-id ";")))
        vapaat-alukset (filter (comp not alukset-kaytossa) alus-mmsit)
        uudet-alukset [{::alus/mmsi (first vapaat-alukset)
                        ::alus/urakan-aluksen-kayton-lisatiedot "Hieno alus tässä urakassa"}
                       {::alus/mmsi (second vapaat-alukset)
                        ::alus/urakan-aluksen-kayton-lisatiedot "Kerrassaan upea alus, otetaan urakkaan heti!"}]
        args {::urakka/id urakka-id
              ::alus/urakan-tallennettavat-alukset uudet-alukset}]


    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-urakan-alukset +kayttaja-ulle+
                                           args)))))

(deftest hae-urakoitsijan-alukset-ilman-oikeutta
  (let [urakoitsija-id (hae-helsingin-vesivaylaurakan-urakoitsija)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-urakoitsijan-alukset +kayttaja-ulle+
                                           {::organisaatio/id urakoitsija-id})))))

(deftest tallenna-alukset
  (let [urakoitsija-id (hae-helsingin-vesivaylaurakan-urakoitsija)
        uudet-alukset [{::alus/mmsi 12235365
                        ::alus/nimi "Urpo"
                        ::alus/urakoitsija-id urakoitsija-id
                        ::alus/lisatiedot "Urpo on karhu"}
                       {::alus/mmsi 12235366
                        ::alus/nimi "Turpo"
                        ::alus/urakoitsija-id urakoitsija-id
                        ::alus/lisatiedot "Turpo on karhu"}
                       {::alus/mmsi 12235367
                        ::alus/nimi ""
                        ::alus/urakoitsija-id urakoitsija-id
                        ::alus/lisatiedot "Tää on varsinaista karhun elämää!"}]
        alukset-ennen (ffirst (q "SELECT COUNT(*) FROM vv_alus;"))
        args {::alus/tallennettavat-alukset uudet-alukset}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-alukset +kayttaja-jvh+
                                args)
        alukset-jalkeen (ffirst (q "SELECT COUNT(*) FROM vv_alus;"))]


    (is (s/valid? ::alus/tallenna-alukset-kysely args))
    (is (s/valid? ::alus/hae-kaikki-alukset-vastaus vastaus))

    (is (= (+ alukset-ennen (count uudet-alukset))
           alukset-jalkeen)
        "Alusksia tuli lisää oikea määrä")

    (is (some #(= (::alus/nimi %) "Urpo") vastaus))
    (is (some #(= (::alus/lisatiedot %) "Urpo on karhu") vastaus))

    (is (some #(= (::alus/nimi %) "Turpo") vastaus))
    (is (some #(= (::alus/lisatiedot %) "Turpo on karhu") vastaus))))

(deftest tallenna-alukset-ilman-oikeutta
  (let [urakoitsija-id (hae-helsingin-vesivaylaurakan-urakoitsija)
        uudet-alukset [{::alus/mmsi 12235365
                        ::alus/nimi "Yrmykyrmy"
                        ::alus/urakoitsija-id urakoitsija-id
                        ::alus/lisatiedot "Hakkeroitu alus"}]
        alukset-ennen (ffirst (q "SELECT COUNT(*) FROM vv_alus;"))
        args {::alus/tallennettavat-alukset uudet-alukset}]

    (is (thrown? Exception
                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-alukset +kayttaja-ulle+
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
