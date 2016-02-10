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
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as xml-zip]
            [clojure.data.zip :as zip]))

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :sonja (feikki-sonja)
    :tloik (component/using
            (luo-tloik-komponentti)
             [:db :sonja :integraatioloki :klusterin-tapahtumat])
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki :klusterin-tapahtumat :tloik])))

(use-fixtures :once jarjestelma-fixture)

(defn luo-testi-ilmoitus []
  (u "INSERT INTO ilmoitus (urakka, ilmoitusid, ilmoitettu)
      VALUES ((SELECT id
      FROM urakka
      WHERE nimi = 'Oulun alueurakka 2014-2019'), 987654321, now());"))


(defn poista-testi-ilmoitustoimenpide []
  (u "DELETE FROM ilmoitustoimenpide WHERE ilmoitus IN (SELECT id FROM ilmoitus WHERE ilmoitusid = 987654321)"))

(defn poista-testi-ilmoitus []
  (u "DELETE FROM ilmoitus WHERE ilmoitusid = 987654321;"))

(defn hae-testi-ilmoituksen-toimenpiteiden-maara []
  (ffirst (q "SELECT count(id)
              FROM ilmoitustoimenpide
              WHERE ilmoitus in
              (SELECT id
               FROM ilmoitus
               WHERE ilmoitusid = 987654321);")))

(deftest tarkista-ilmoitustoimenpiteen-kirjaus
  (let [viestit (atom [])]
    (luo-testi-ilmoitus)
    (sonja/kuuntele (:sonja jarjestelma) +tloik-ilmoitustoimenpideviestijono+ #(swap! viestit conj (.getText %)))

    (let [vastaus (api-tyokalut/put-kutsu ["/api/ilmoitukset/987654321/"] kayttaja portti (slurp "test/resurssit/api/ilmoitustoimenpide.json"))]
      (is (= 200 (:status vastaus))) "Viestin lähetys API:n onnistui.")

    (is (= 1 (hae-testi-ilmoituksen-toimenpiteiden-maara)) "Ilmoitustoimenpide löytyy tietokannasta.")

    (odota #(= 1 (count @viestit)) "Ilmoitustoimenpideviesti lähetettiin Sonjan jonoon." 10000)

    (is (xml/validoi "xsd/tloik/" "harja-tloik.xsd" (first @viestit)) "Lähetetty ilmoitustoimenpide XML on validi")
    (let [data (xml/lue (first @viestit))]
      (is (= "987654321" (xml-zip/xml1-> data :ilmoitusId xml-zip/text)) "Toimenpide tehtiin oikeaan ilmoitukseen")
      (is (= "vastaanotto" (xml-zip/xml1-> data :tyyppi xml-zip/text)) "Toimenpide tyyppi on oikea"))

    (poista-testi-ilmoitustoimenpide)
    (poista-testi-ilmoitus)))
