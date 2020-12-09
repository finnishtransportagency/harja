(ns harja.palvelin.palvelut.api-jarjestelmatunnukset-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.haku :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.api-jarjestelmatunnukset :as api-jarjestelmatunnukset])
  (:import (harja.domain.roolit EiOikeutta))
  (:use [slingshot.slingshot :only [try+ throw+]]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :api-jarjestelmatunnukset (component/using
                                                    (api-jarjestelmatunnukset/->APIJarjestelmatunnukset)
                                                    [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest jarjestelmatunnuksien-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-jarjestelmatunnukset +kayttaja-jvh+ nil)]
    (is (vector? vastaus))
    (is (= (count vastaus) 7))))

(deftest jarjestelmatunnuksien-haku-ei-toimi-ilman-oikeuksia
  (try+
    (let [_ (kutsu-palvelua (:http-palvelin jarjestelma)
                            :hae-jarjestelmatunnukset +kayttaja-tero+ nil)])
    (is false "Nyt on joku paha oikeusongelma")
    (catch EiOikeutta e
      (is e))))

(deftest jarjestelmatunnuksien-lisaoikeuksian-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-jarjestelmatunnuksen-lisaoikeudet +kayttaja-jvh+ nil)]
    (is (vector? vastaus))
    (is (= (count vastaus) 0))))

(deftest jarjestelmatunnuksien-lisaoikeuksian-haku-ei-toimi-ilman-oikeuksia
  (try+
    (let [_ (kutsu-palvelua (:http-palvelin jarjestelma)
                            :hae-jarjestelmatunnuksen-lisaoikeudet +kayttaja-tero+ nil)])
    (is false "Nyt on joku paha oikeusongelma")
    (catch EiOikeutta e
      (is e))))

(deftest urakoiden-haku-toimii
  (let [odotettu-maara (ffirst (q "SELECT COUNT(*) FROM urakka;"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakat-lisaoikeusvalintaan +kayttaja-jvh+ nil)]
    (is (vector? vastaus))
    (is (= (count vastaus) odotettu-maara))))

(deftest urakoiden-haku-ei-toimi-ilman-oikeuksia
  (try+
    (let [_ (kutsu-palvelua (:http-palvelin jarjestelma)
                            :hae-urakat-lisaoikeusvalintaan +kayttaja-tero+ nil)])
    (is false "Nyt on joku paha oikeusongelma")
    (catch EiOikeutta e
      (is e))))

(deftest jarjestelmatunnusten-tallennus-toimii
  (let [ennen-muutosta (q "SELECT id, kayttajanimi, kuvaus, organisaatio FROM kayttaja;")
        testitunnukset [{:id -1, :kayttajanimi "juha88",
                         :kuvaus "noni",
                         :organisaatio {:nimi "Liikennevirasto", :id 1}}
                        {:id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'destia'")),
                         :kayttajanimi "destia",
                         :kuvaus "testissä muutettu",
                         :organisaatio {:nimi "Liikennevirasto", :id 1}}]
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-jarjestelmatunnukset +kayttaja-jvh+ testitunnukset)
        muutoksen-jalkeen (q "SELECT id, kayttajanimi, kuvaus, organisaatio FROM kayttaja;")]

    (is (integer? (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'juha88'"))))
    (is (= (ffirst (q "SELECT kuvaus FROM kayttaja WHERE kayttajanimi = 'destia'"))
           "testissä muutettu"))
    (is (= (filterv #(and (not= (second %) "destia") (not= (second %) "juha88"))
                    ennen-muutosta)
           (filterv #(and (not= (second %) "destia") (not= (second %) "juha88"))
                    muutoksen-jalkeen))
        "Ei koskettu muihin käyttäjätunnuksiin")
    (u "DELETE FROM kayttaja WHERE kayttajanimi = 'juha88';")))

(deftest jarjestelmatunnusten-tallennus-ei-toimi-ilman-oikeuksia
  (let [ennen-muutosta (q "SELECT id, kayttajanimi, kuvaus, organisaatio FROM kayttaja;")]
    (try+
      (let [testitunnukset [{:id -1, :kayttajanimi "juha88",
                             :kuvaus "noni",
                             :organisaatio {:nimi "Liikennevirasto", :id 1}}
                            {:id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'destia'")),
                             :kayttajanimi "destia",
                             :kuvaus "testissä muutettu",
                             :organisaatio {:nimi "Liikennevirasto", :id 1}}]
            _ (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-jarjestelmatunnukset +kayttaja-tero+ testitunnukset)]
        (is false "Nyt on joku paha oikeusongelma"))
      (catch EiOikeutta e
        (is e)))
    (let [muutoksen-jalkeen (q "SELECT id, kayttajanimi, kuvaus, organisaatio FROM kayttaja;")]
      (is (= ennen-muutosta muutoksen-jalkeen)))))

(deftest jarjestelmatunnuksen-lisaoikeudet-toimii
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        kayttaja-id (:id +kayttaja-tero+)
        testioikeudet {:oikeudet [{:id -1
                                   :urakka-id urakka-id
                                   :kayttaja kayttaja-id}]
                       :kayttaja-id kayttaja-id}
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-jarjestelmatunnuksen-lisaoikeudet +kayttaja-jvh+ testioikeudet)
        uusi-id (ffirst (q "SELECT id FROM kayttajan_lisaoikeudet_urakkaan WHERE kayttaja = " kayttaja-id
                           " AND urakka = " urakka-id ";"))]

    (is (integer? uusi-id))

    (let [testioikeudet {:oikeudet [{:id uusi-id
                                     :urakka-id urakka-id
                                     :kayttaja kayttaja-id
                                     :poistettu true}]
                         :kayttaja-id kayttaja-id}
          _ (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-jarjestelmatunnuksen-lisaoikeudet +kayttaja-jvh+ testioikeudet)]

      (is (nil? (ffirst (q "SELECT id FROM kayttajan_lisaoikeudet_urakkaan WHERE kayttaja = " kayttaja-id
                               " AND urakka = " urakka-id ";")))))))

(deftest jarjestelmatunnuksen-lisaoikeudet-ei-toimi-ilman-oikeuksia
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        kayttaja-id (:id +kayttaja-tero+)
        testioikeudet [{:id -1
                        :urakka-id urakka-id
                        :kayttaja kayttaja-id}]]
    (try+
      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-jarjestelmatunnuksen-lisaoikeudet +kayttaja-tero+ testioikeudet)
      (is false "Nyt on joku paha oikeusongelma")
      (catch EiOikeutta e
        (is e)))
    (is (nil? (ffirst (q "SELECT id FROM kayttajan_lisaoikeudet_urakkaan WHERE kayttaja = " kayttaja-id
                         " AND urakka = " urakka-id ";"))))))