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
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/materiaalit")] kayttaja-analytiikka portti)
        _ (println "vastaus: " (pr-str (:body vastaus)))]
    (is (= 200 (:status vastaus)))))

(deftest hae-materiaalit-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        materiaalit-kannasta (q-map (str "SELECT id FROM materiaalikoodi"))
        materiaaliluokat-kannasta (q-map (str "SELECT id FROM materiaaliluokka"))
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/materiaalit")] kayttaja-analytiikka portti)
        _ (println "vastaus: " (pr-str (:body vastaus)))

        encoodattu-body (cheshire/decode (:body vastaus) true)
        _ (println "encoodattu-body: " encoodattu-body)]
    (is (= 200 (:status vastaus)))
    (is (= (count materiaalit-kannasta) (count (:materiaalikoodit encoodattu-body))))
    (is (= (count materiaaliluokat-kannasta) (count (:materiaaliluokat encoodattu-body))))))

(deftest hae-tehtavat-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/tehtavat")] kayttaja-analytiikka portti)
        _ (println "vastaus: " (pr-str (:body vastaus)))]
    (is (= 200 (:status vastaus)))))

(deftest hae-urakat-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/urakat")] kayttaja-analytiikka portti)
        _ (println "vastaus: " (pr-str (:body vastaus)))]
    (is (= 200 (:status vastaus)))))

(deftest hae-organisaatiot-yksinkertainen-onnistuu-test
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (luo-valiaikainen-kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/organisaatiot")] kayttaja-analytiikka portti)
        _ (println "vastaus: " (pr-str (:body vastaus)))]
    (is (= 200 (:status vastaus)))))
