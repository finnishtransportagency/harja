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
  (:import (java.util Date)))

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


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest kaikki-saavat-hakea-tuoreimman-hairioilmoituksen
  (let [vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :hae-tuorein-voimassaoleva-hairioilmoitus
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
                    :hae-tuorein-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus ::hairio/viesti]) "test 1"))))

  (testing "Päättynyt häiriöilmoitus ei näy"
    (let [_tee-paattynyt-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :aseta-hairioilmoitus
                                          +kayttaja-jvh+
                                          {::hairio/viesti "test 2"
                                           ::hairio/loppuaika (pvm/dateksi (t/from-now (t/days -1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-tuorein-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus]) nil))))

  (testing "Tuleva häiriöilmoitus ei näy"
    (let [_tee-alkava-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :aseta-hairioilmoitus
                                       +kayttaja-jvh+
                                       {::hairio/viesti "test 3"
                                        ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days 1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-tuorein-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus]) nil))))

  (testing "Ajastettuna alkava häiriöilmoitus näkyy"
    (let [_tee-alkanut-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :aseta-hairioilmoitus
                                        +kayttaja-jvh+
                                        {::hairio/viesti "test 4"
                                         ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days -1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-tuorein-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus ::hairio/viesti]) "test 4")))))
