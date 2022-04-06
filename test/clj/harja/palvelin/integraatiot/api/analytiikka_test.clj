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

(deftest hae-toteumat-test-yksinkertainen-onnistuu
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (u (str "INSERT INTO kayttaja (etunimi, sukunimi, kayttajanimi, organisaatio, \"analytiikka-oikeus\") VALUES
          ('etunimi','sukunimi', 'analytiikka-testeri', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), true)"))
        ;; Aseta tiukka hakuväli, josta löytyy vain vähän toteumia
        alkuaika "2004-01-01T00:00:00"
        loppuaika "2004-12-31T23:59:59"
        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))
        odotettu-vastaus "{\"reittitoteumat\":[{\"reitti\":[{\"reittipiste\":{\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"tehtavat\":[]},\"materiaalit\":[]}],\"toteuma\":{\"alkanut\":\"2004-10-19T21:00:00Z\",\"tehtavat\":[{\"id\":6,\"maara\":{\"maara\":123,\"yksikko\":\"m2\"}},{\"id\":5,\"maara\":{\"maara\":28,\"yksikko\":\"m2\"}}],\"muutostiedot\":{\"luotu\":\"2004-10-19T07:23:54Z\",\"luoja\":9},\"paattynyt\":\"2006-09-29T21:00:00Z\",\"suorittaja\":{\"nimi\":\"Pekan Kone OY\",\"ytunnus\":\"4715514-4\"},\"materiaalit\":[{\"materiaali\":\"Hiekoitushiekka\",\"maara\":{\"maara\":25,\"yksikko\":\"t\"}},{\"materiaali\":\"Hiekoitushiekka\",\"maara\":{\"maara\":25,\"yksikko\":\"t\"}}],\"sopimus\":{\"id\":1},\"toteumatyyppi\":\"yksikkohintainen\",\"tunniste\":{\"id\":9},\"lisatieto\":\"Automaattisesti lisätty fastroi toteuma\",\"tiesijainti\":{}}}]}"]
    (is (= (:body @vastaus) odotettu-vastaus))))

(deftest hae-toteumat-test-reitillinen-onnistuu
  (let [; Luo väliaikainen käyttäjä, jolla on oikeudet analytiikkarajapintaan
        _ (u (str "INSERT INTO kayttaja (etunimi, sukunimi, kayttajanimi, organisaatio, \"analytiikka-oikeus\") VALUES
          ('etunimi','sukunimi', 'analytiikka-testeri', (SELECT id FROM organisaatio WHERE nimi = 'Liikennevirasto'), true)"))
        alkuaika "2005-01-01T00:00:00"
        loppuaika "2005-12-31T23:59:59"
        vastaus (future (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-analytiikka portti))
        odotettu-vastaus "{\"reittitoteumat\":[{\"reitti\":[{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:00\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"498919\",\"y\":\"7247099\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:01\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499271\",\"y\":\"7248395\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:02\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499399\",\"y\":\"7249019\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:03\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499820\",\"y\":\"7249885\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:04\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"498519\",\"y\":\"7247299\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:05\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499371\",\"y\":\"7248595\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:06\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499499\",\"y\":\"7249319\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:07\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499520\",\"y\":\"7249685\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:00\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"498919\",\"y\":\"7247099\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:01\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499271\",\"y\":\"7248395\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:02\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499399\",\"y\":\"7249019\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:03\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499820\",\"y\":\"7249885\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:04\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"498519\",\"y\":\"7247299\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:05\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499371\",\"y\":\"7248595\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:06\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499499\",\"y\":\"7249319\"},\"tehtavat\":[]},\"materiaalit\":[]},{\"reittipiste\":{\"aika\":\"2005-10-10T00:10:07\",\"materiaalit\":[],\"koodinaatit\":{\"x\":\"499520\",\"y\":\"7249685\"},\"tehtavat\":[]},\"materiaalit\":[]}],\"toteuma\":{\"alkanut\":\"2005-09-30T21:00:00Z\",\"tehtavat\":[{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}},{\"id\":1,\"maara\":{\"maara\":10,\"yksikko\":\"m2\"}}],\"muutostiedot\":{\"luotu\":\"2022-03-30T08:31:16Z\",\"luoja\":2},\"paattynyt\":\"2005-10-01T21:00:00Z\",\"suorittaja\":{\"nimi\":\"Seppo Suorittaja\",\"ytunnus\":\"4153724-6\"},\"materiaalit\":[{\"materiaali\":\"Talvisuolaliuos NaCl\",\"maara\":{\"maara\":7,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos NaCl\",\"maara\":{\"maara\":7,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos NaCl\",\"maara\":{\"maara\":7,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos NaCl\",\"maara\":{\"maara\":7,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos NaCl\",\"maara\":{\"maara\":7,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos NaCl\",\"maara\":{\"maara\":7,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos NaCl\",\"maara\":{\"maara\":7,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos NaCl\",\"maara\":{\"maara\":7,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos CaCl2\",\"maara\":{\"maara\":4,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos CaCl2\",\"maara\":{\"maara\":4,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos CaCl2\",\"maara\":{\"maara\":4,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos CaCl2\",\"maara\":{\"maara\":4,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos CaCl2\",\"maara\":{\"maara\":4,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos CaCl2\",\"maara\":{\"maara\":4,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos CaCl2\",\"maara\":{\"maara\":4,\"yksikko\":\"t\"}},{\"materiaali\":\"Talvisuolaliuos CaCl2\",\"maara\":{\"maara\":4,\"yksikko\":\"t\"}}],\"sopimus\":{\"id\":1},\"toteumatyyppi\":\"yksikkohintainen\",\"tunniste\":{\"id\":1},\"lisatieto\":\"Reitillinen yksikköhintainen toteuma 1\",\"tiesijainti\":{}}},{\"reitti\":[{\"reittipiste\":{\"tehtavat\":[]},\"materiaalit\":[]}],\"toteuma\":{\"alkanut\":\"2005-09-30T21:00:00Z\",\"tehtavat\":[{\"id\":2,\"maara\":{\"maara\":7,\"yksikko\":\"m2\"}}],\"muutostiedot\":{\"luotu\":\"2022-03-30T08:31:16Z\",\"luoja\":9},\"paattynyt\":\"2005-10-01T21:00:00Z\",\"suorittaja\":{\"nimi\":\"Seppo Suorittaja\",\"ytunnus\":\"4153724-6\"},\"materiaalit\":[{\"materiaali\":\"Erityisalueet NaCl\",\"maara\":{\"maara\":3,\"yksikko\":\"t\"}}],\"sopimus\":{\"id\":1},\"toteumatyyppi\":\"yksikkohintainen\",\"tunniste\":{\"id\":2},\"lisatieto\":\"Reitillinen yksikköhintainen toteuma 2\",\"tiesijainti\":{}}},{\"reitti\":[{\"reittipiste\":{\"tehtavat\":[]},\"materiaalit\":[]}],\"toteuma\":{\"alkanut\":\"2005-09-30T21:00:00Z\",\"tehtavat\":[{\"id\":3,\"maara\":{\"maara\":15,\"yksikko\":\"m2\"}}],\"muutostiedot\":{\"luotu\":\"2022-03-30T08:31:16Z\",\"luoja\":9},\"paattynyt\":\"2005-10-01T21:00:00Z\",\"suorittaja\":{\"nimi\":\"Antti Ahertaja\",\"ytunnus\":\"1524792-1\"},\"materiaalit\":[{\"materiaali\":\"Erityisalueet NaCl-liuos\",\"maara\":{\"maara\":9,\"yksikko\":\"t\"}}],\"sopimus\":{\"id\":1},\"toteumatyyppi\":\"yksikkohintainen\",\"tunniste\":{\"id\":3},\"lisatieto\":\"Yksikköhintainen toteuma 1\",\"tiesijainti\":{}}}]}"]
    (is (= (:body @vastaus) odotettu-vastaus))))


(deftest hae-toteumat-test-ei-kayttoikeutta
  (let [alkuaika (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                   (t/minus (t/now) (t/months 1)))
        loppuaika (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki")) (t/now))]
    (is (thrown? Exception (cheshire/decode (:body (api-tyokalut/get-kutsu [(str "/api/analytiikka/toteumat/" alkuaika "/" loppuaika)] kayttaja-yit portti)))))))
