(ns harja.palvelin.integraatiot.api.ilmoitustoimenpiteiden-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]))

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :sonja (feikki-sonja)
    :tloik (component/using
            (luo-tloik-komponentti)
             [:db :sonja :integraatioloki])
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki :tloik])))

(use-fixtures :once jarjestelma-fixture)

(defn luo-testi-ilmoitus []
  (u "INSERT INTO ilmoitus (urakka, ilmoitusid, ilmoitettu, valitetty)
      VALUES ((SELECT id
      FROM urakka
      WHERE nimi = 'Oulun alueurakka 2014-2019'), 987654321, now(), (now() + interval '15 seconds'));"))


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


