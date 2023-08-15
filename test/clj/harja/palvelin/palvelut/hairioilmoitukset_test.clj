(ns harja.palvelin.palvelut.hairioilmoitukset-test
  (:require [clojure.test :refer :all]
            [harja.domain.tieliikenneilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.hairioilmoitukset :as hairioilmoitukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.set :as set])
  (:import (harja.domain.roolit EiOikeutta))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hairioilmoitukset (component/using
                                             (hairioilmoitukset/->Hairioilmoitukset)
                                             [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest kaikki-saavat-hakea-tuoreimman-hairioilmoituksen
  (let [vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :hae-voimassaoleva-hairioilmoitus
                  +kayttaja-tero+
                  {})]
  (is (map? vastaus))
  (is (= (first (keys vastaus)) :hairioilmoitus))))

(deftest ajastetut-hairioilmoitukset-toimii
  (testing "Päättyvä häiriöilmoitus näkyy"
    (let [_tee-paattyva-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :aseta-hairioilmoitus
                                         +kayttaja-jvh+
                                         {::hairio/viesti "test 1"
                                          ::hairio/loppuaika (pvm/dateksi (t/from-now (t/days 1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus ::hairio/viesti]) "test 1")))))

(deftest paattynyt-hairio-ilmoitus-ei-nay
  (testing "Päättynyt häiriöilmoitus ei näy"
    (let [_tee-paattynyt-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :aseta-hairioilmoitus
                                          +kayttaja-jvh+
                                          {::hairio/viesti "test 2"
                                           ::hairio/loppuaika (pvm/dateksi (t/from-now (t/days -1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus]) nil)))))

(deftest tuleva-hairio-ei-nay
  (testing "Tuleva häiriöilmoitus ei näy"
    (let [_tee-alkava-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :aseta-hairioilmoitus
                                       +kayttaja-jvh+
                                       {::hairio/viesti "test 3"
                                        ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days 1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus]) nil)))))

(deftest ajastettuna-alkanut-hairio-nakyy
  (testing "Ajastettuna alkava häiriöilmoitus näkyy"
    (let [_tee-alkanut-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :aseta-hairioilmoitus
                                        +kayttaja-jvh+
                                        {::hairio/viesti "test 4"
                                         ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days -1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus ::hairio/viesti]) "test 4")))))

(deftest hairion-pois-paalta-laittaminen
    (let [_tee-alkanut-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :aseta-hairioilmoitus
                                        +kayttaja-jvh+
                                        {::hairio/viesti "test 1"
                                         ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days -1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-hairioilmoitukset
                    +kayttaja-jvh+
                    {})
          _laita_pois_paalta (kutsu-palvelua
                               (:http-palvelin jarjestelma)
                               :aseta-hairioilmoitus-pois
                               +kayttaja-jvh+
                               {::hairio/id (::hairio/id (first vastaus))})
          poiston-jalkeen (kutsu-palvelua
                            (:http-palvelin jarjestelma)
                            :hae-hairioilmoitukset
                            +kayttaja-jvh+
                            {})]
      (is (is (::hairio/voimassa? (first vastaus))))
      (is (= (::hairio/voimassa? (first poiston-jalkeen)) false))))

(deftest hairion-pois-paalta-laittaminen-ei-toimi-ilman-oikeuksia
  (try+
    (let [_tee-alkanut-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :aseta-hairioilmoitus
                                        +kayttaja-jvh+
                                        {::hairio/viesti "test 1"
                                         ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days -1)))})
          ilmoitukset (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-hairioilmoitukset
                    +kayttaja-jvh+
                    {})
          _ (kutsu-palvelua (:http-palvelin jarjestelma)
              :aseta-hairioilmoitus-pois
              +kayttaja-tero+
              {::hairio/id (::hairio/id (first ilmoitukset))})])
    (is false "Nyt on joku paha oikeusongelma")
    (catch EiOikeutta e
      (is e))))
