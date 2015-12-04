(ns harja.palvelin.integraatiot.api.ilmoitustoimenpiteiden-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.jms :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.tyokalut.xml :as xml]))

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki :klusterin-tapahtumat])
    :sonja (feikki-sonja)
    :tloik (component/using
             (->Tloik +tloik-ilmoitusviestijono+
                      +tloik-ilmoituskuittausjono+
                      +tloik-ilmoitustoimenpideviestijono+
                      +tloik-ilmoitustoimenpidekuittausjono+)
             [:db :sonja :integraatioloki :klusterin-tapahtumat])))

(use-fixtures :once jarjestelma-fixture)

(defn luo-testi-ilmoitus []
  (u "INSERT INTO ilmoitus (urakka, ilmoitusid, ilmoitettu)
      VALUES ((SELECT id
      FROM urakka
      WHERE nimi = 'Oulun alueurakka 2014-2019'), 987654321, now());"))

(defn poista-testi-ilmoitus []
  (u "DELETE FROM ilmoitus WHERE ilmoitusid = 987654321;"))

(defn hae-testi-ilmoituksen-toimenpiteiden-maara []
  (ffirst (q "SELECT count(id)
              FROM ilmoitustoimenpide
              WHERE ilmoitus =
              (SELECT id
               FROM ilmoitus
               WHERE ilmoitusid = 987654321);")))

(deftest tarkista-ilmoitustoimenpiteen-kirjaus
  (let [viestit (atom [])]
    (luo-testi-ilmoitus)
    (sonja/kuuntele (:sonja jarjestelma) +tloik-ilmoitustoimenpideviestijono+ #(swap! viestit conj (.getText %)))
    (let [vastaus (api-tyokalut/put-kutsu ["/api/ilmoitukset/987654321/"] kayttaja portti (slurp "test/resurssit/api/ilmoitustoimenpide.json"))]
      (println "-----> VASTAUS: " vastaus)
      (is 200 (:status vastaus)) "Viestin lähetys API:n onnistui.")

    (is (= 1 (hae-testi-ilmoituksen-toimenpiteiden-maara)) "Ilmoitustoimenpide löytyy tietokannasta.")

    (odota #(= 1 (count @viestit)) "Ilmoitustoimenpideviesti lähetettiin Sonjan jonoon." 10000)
    (is (xml/validoi "xsd/tloik/" "harja-tloik.xsd" @viestit) "Lähetetty ilmoitustoimenpide XML on valid.i")

    (poista-testi-ilmoitus)))
