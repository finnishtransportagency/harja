(ns harja.palvelin.integraatiot.api.analytiikka-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.core.async :refer [<!! timeout]]
            [clj-time
             [core :as t]
             [format :as df]]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-jms]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.analytiikka :as api-analytiikka]
            [clojure.java.io :as io]))

(def kayttaja-yit "yit-rakennus")
(def kayttaja-analytiikka "analytiikka-testeri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-yit
    :api-analytiikka (component/using
                       (api-analytiikka/->Analytiikka)
                       [:http-palvelin :db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest hae-toteumat-test-onnistuu
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (u (str "INSERT INTO kayttaja (etunimi, sukunimi, kayttajanimi, organisaatio, \"analytiikka-oikeus\") VALUES
          ('etunimi','sukunimi', 'analytiikka-testeri', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), true)"))
        alkuaika (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                   (t/minus (t/now) (t/months 1)))
        loppuaika (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki")) (t/now))
        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))
        vastausdata (cheshire/decode (:body @vastaus))
        odotettu-vastaus (json/read-str (slurp (io/resource "api/examples/analytiikka-reittitoteumat-response.json")))]
    (is (= vastausdata odotettu-vastaus))))


(deftest hae-toteumat-test-ei-kayttoikeutta
  (let [alkuaika (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                   (t/minus (t/now) (t/months 1)))
        loppuaika (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki")) (t/now))]
    (is (thrown? Exception (cheshire/decode (:body (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-yit portti)))))))
