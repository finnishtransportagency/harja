(ns harja.palvelin.integraatiot.api.analytiikka-materiaalit-test
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

(defn- luo-valiaikainen-kayttaja []
  (u (str "INSERT INTO kayttaja (etunimi, sukunimi, kayttajanimi, organisaatio, \"analytiikka-oikeus\") VALUES
          ('etunimi','sukunimi', 'analytiikka-testeri', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), true)")))

(deftest hae-materiaalit-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
       _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/materiaalit")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-materiaalit-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        materiaalit-kannasta (q-map (str "SELECT id FROM materiaalikoodi"))
        materiaaliluokat-kannasta (q-map (str "SELECT id FROM materiaaliluokka"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/materiaalit")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count materiaalit-kannasta) (count (:materiaalikoodit encoodattu-body))))
    (is (= (count materiaaliluokat-kannasta) (count (:materiaaliluokat encoodattu-body))))))

(deftest hae-tehtavat-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/tehtavat")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-tehtavat-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        tehtavat-kannasta (q-map
                            (str "SELECT t.id
                                    FROM toimenpidekoodi t
                                         LEFT JOIN toimenpidekoodi emo ON t.emo = emo.id AND t.taso = 4
                                   WHERE (t.poistettu IS FALSE and t.taso in (1,2,3) OR (t.taso = 4 AND emo.poistettu IS FALSE) OR (t.taso = 4 AND emo.poistettu IS TRUE AND t.poistettu IS FALSE))"))
        tehtavaryhmat-kannasta (q-map (str "SELECT id FROM tehtavaryhma"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/tehtavat")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count tehtavat-kannasta) (count (:tehtavat encoodattu-body))))
    (is (= (count tehtavaryhmat-kannasta) (count (:tehtavaryhmat encoodattu-body))))))

(deftest hae-urakat-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/urakat")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-urakat-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        urakat-kannasta (q-map (str "SELECT id FROM urakka"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/urakat")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count urakat-kannasta) (count (:urakat encoodattu-body))))))

(deftest hae-organisaatiot-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/organisaatiot")] kayttaja-analytiikka portti)]
    (is (= 200 (:status vastaus)))))

(deftest hae-organisaatiot-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        organisaatiot-kannasta (q-map (str "SELECT id FROM organisaatio"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/organisaatiot")] kayttaja-analytiikka portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count organisaatiot-kannasta) (count (:organisaatiot encoodattu-body))))))


(deftest varmista-toteumien-vaatimat-materiaalit-loytyy
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        ;; Aseta tiukka hakuväli, josta löytyy vain vähän toteumia
        alkuaika "2004-01-01T00:00:00+03"
        loppuaika "2004-12-31T21:00:00+03"
        toteumavastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti)
        toteuma-body (cheshire/decode (:body toteumavastaus) true)
        toteuma (:toteuma (first (:reittitoteumat toteuma-body)))
        toteuma-materiaalit (:materiaalit toteuma)

        materiaalivastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/materiaalit")] kayttaja-analytiikka portti)
        materiaalibody-body (cheshire/decode (:body materiaalivastaus) true)
        materiaalit (:materiaalikoodit materiaalibody-body)
        materiaalitiedot (reduce
                           (fn [tiedot tot-mat]
                             (let [;; Haetaan materiaalia vastaava id
                                   materiaali-id (some (fn [m]
                                                         (when (= (:nimi m) (:materiaali tot-mat))
                                                           (:id m))) materiaalit)]
                               {:id materiaali-id
                                :nimi (:materiaali tot-mat)}))
                           [] toteuma-materiaalit)
        ;; Me tiedetään, että kutsussa palautuu Hiekoitushiekkaa, niin varmistetaan, että sen id on oikein
        odotetut-materiaalitiedot {:id 5, :nimi "Hiekoitushiekka"}]
    (is (= 200 (:status toteumavastaus)))
    (is (= 200 (:status materiaalivastaus)))
    (is (= odotetut-materiaalitiedot materiaalitiedot))))
