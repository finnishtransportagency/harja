(ns harja.palvelin.integraatiot.api.analytiikka-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.core.async :refer [<!! timeout]]
            [clj-time
             [core :as t]
             [format :as df]]
            [harja.pvm :as pvm]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-jms]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.analytiikka :as api-analytiikka]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.text SimpleDateFormat)
           (java.util Date)))

(def kayttaja-yit "yit-rakennus")
(def kayttaja-analytiikka "analytiikka-testeri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-yit
    :api-analytiikka (component/using
                       (api-analytiikka/->Analytiikka)
                       [:http-palvelin :db-replica :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn sisaltaa-perustiedot [vastaus]
  (is (str/includes? vastaus "tyokone"))
  (is (str/includes? vastaus "materiaalit"))
  (is (str/includes? vastaus "reittipiste"))
  (is (str/includes? vastaus "yksikko"))
  (is (str/includes? vastaus "tehtava"))
  (is (str/includes? vastaus "muutostiedot")))

(deftest hae-toteumat-test-yksinkertainen-onnistuu
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (u (str "INSERT INTO kayttaja (etunimi, sukunimi, kayttajanimi, organisaatio, \"analytiikka-oikeus\") VALUES
          ('etunimi','sukunimi', 'analytiikka-testeri', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), true)"))
        ;; Aseta tiukka hakuväli, josta löytyy vain vähän toteumia
        alkuaika "2004-01-01T00:00:00+03"
        loppuaika "2004-12-31T21:00:00+03"
        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))]
    (is (= 200 (:status @vastaus)))
    (sisaltaa-perustiedot (:body @vastaus))))

(deftest hae-toteumat-test-reitillinen-onnistuu
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (u (str "INSERT INTO kayttaja (etunimi, sukunimi, kayttajanimi, organisaatio, \"analytiikka-oikeus\") VALUES
          ('etunimi','sukunimi', 'analytiikka-testeri', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), true)"))
        alkuaika "2015-01-19T00:00:00+03"
        loppuaika "2015-01-19T21:00:00+03"
        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))]
    (is (= 200 (:status @vastaus)))
    (sisaltaa-perustiedot (:body @vastaus))))

(deftest hae-toteumat-test-ei-kayttoikeutta
  (let [kuukausi-sitten (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date. (- (.getTime (Date.)) (* 30 86400 1000))))
        nyt (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" kuukausi-sitten "/" nyt)] kayttaja-yit portti)]
    (is (= 403 (:status vastaus)))
    (is (str/includes? (:body vastaus) "virheet"))
    (is (str/includes? (:body vastaus) "koodi"))
    (is (str/includes? (:body vastaus) "tuntematon-kayttaja"))))

(deftest hae-toteumat-test-vaara-paivamaaraformaatti
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (u (str "INSERT INTO kayttaja (etunimi, sukunimi, kayttajanimi, organisaatio, \"analytiikka-oikeus\") VALUES
          ('etunimi','sukunimi', 'analytiikka-testeri', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), true)"))
        alkuaika "2005-01-01T00:00:00"
        loppuaika "2005-12-31T21:00:00+03"
        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))]
    (is (= 400 (:status @vastaus)))
    (is (str/includes? (:body @vastaus) "Alkuaika väärässä muodossa"))))

(deftest hae-toteumat-test-poistettu-onnistuu
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (u (str "INSERT INTO kayttaja (etunimi, sukunimi, kayttajanimi, organisaatio, \"analytiikka-oikeus\") VALUES
          ('etunimi','sukunimi', 'analytiikka-testeri', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), true)"))
        ;; Aseta tiukka hakuväli, josta löytyy vain vähän toteumia
        alkuaika "2004-01-01T00:00:00+03"
        loppuaika "2004-12-31T21:00:00+03"
        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))

        ;; Merkitään kaikki saman aikavälin toteumat poistetuiksi ja tehdään uusi haku
        _ (u (str "UPDATE toteuma SET poistettu = true WHERE alkanut >= '2004-01-01T00:00:00+03' AND alkanut <= '2004-12-31T21:00:00+03'"))
        poistetut (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))
        _ (u (str "UPDATE toteuma SET poistettu = false WHERE alkanut >= '2004-01-01T00:00:00+03' AND alkanut <= '2004-12-31T21:00:00+03'"))
        _ (Thread/sleep 3500)]
    (is (= 200 (:status @vastaus)))
    (is (= 200 (:status @poistetut)))
    (sisaltaa-perustiedot (:body @vastaus))
    (sisaltaa-perustiedot (:body @poistetut))
    (is (= @vastaus @poistetut))))
