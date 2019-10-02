(ns harja.palvelin.integraatiot.api.yhteystiedot-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.yhteystiedot :as api-yhteystiedot]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.komponentit.fim :as fim]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]))

(def livi-jarjestelmakayttaja "livi")
(def urakoitsija-jarjestelmakayttaja "skanska")

(def fim-url "https://testi.oag/FIM")

(def fim-vastaus "<Members><member>
<AccountName>A000014</AccountName>
<FirstName>Erkki</FirstName>
<LastName>Elyläinen</LastName>
<DisplayName>Elyläinen erkki</DisplayName>
<Email>erkki@example.com</Email>
<MobilePhone>0982345</MobilePhone>
<Company>ELY</Company>
<Role>1242141-OULU2_ELY_Urakanvalvoja</Role>
</member>
<member>
<AccountName>LXTESTI</AccountName>
<FirstName>Ulla</FirstName>
<LastName>Urakoitsija</LastName>
<DisplayName>Ulla Urakoitsija YIT Rakennus Oy</DisplayName>
<Email>ulla@example.com</Email>
<MobilePhone>234234</MobilePhone>
<Company>YIT Rakennus Oy</Company>
<Role>1242141-OULU2_vastuuhenkilo</Role>
</member>
</Members>")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    livi-jarjestelmakayttaja
    :fim (component/using
           (fim/->FIM fim-url)
           [:db :integraatioloki])
    :api-yhteystiedot (component/using
                        (api-yhteystiedot/->Yhteystiedot)
                        [:http-palvelin :db :integraatioloki :fim])))

(use-fixtures :each jarjestelma-fixture)

(defn sisaltaa-roolin? [yhteyshenkilot rooli]
  (some #(= rooli (get-in % [:yhteyshenkilo :rooli])) yhteyshenkilot))

#_(deftest tarkista-yhteystietojen-haku
  (with-fake-http
    [(str "http://localhost:" portti "/api/urakat/yhteystiedot/1238") :allow
     fim-url fim-vastaus]
    (let [vastaus (api-tyokalut/get-kutsu "/api/urakat/yhteystiedot/1238" livi-jarjestelmakayttaja portti)
          odotettu-vastaus "{\"urakka\":{\"elynro\":12,\"alueurakkanro\":\"1238\",\"loppupvm\":\"2019-09-29T21:00:00Z\",\"nimi\":\"Oulun alueurakka 2014-2019\",\"sampoid\":\"1242141-OULU2\",\"alkupvm\":\"2014-09-30T21:00:00Z\",\"elynimi\":\"Pohjois-Pohjanmaa\",\"urakoitsija\":{\"nimi\":\"YIT Rakennus Oy\",\"ytunnus\":\"1565583-5\",\"katuosoite\":\"Panuntie 11, PL 36\",\"postinumero\":\"621  \"},\"yhteyshenkilot\":[{\"yhteyshenkilo\":{\"rooli\":\"ELY urakanvalvoja\",\"nimi\":\"Erkki Elyläinen\",\"puhelinnumero\":\"0982345\",\"email\":\"erkki@example.com\",\"organisaatio\":\"ELY\",\"vastuuhenkilo\":false,\"varahenkilo\":false}},{\"yhteyshenkilo\":{\"rooli\":\"Urakan vastuuhenkilö\",\"nimi\":\"Ulla Urakoitsija\",\"puhelinnumero\":\"234234\",\"email\":\"ulla@example.com\",\"organisaatio\":\"YIT Rakennus Oy\",\"vastuuhenkilo\":false,\"varahenkilo\":false}},{\"yhteyshenkilo\":{\"rooli\":\"Kunnossapitopäällikkö\",\"nimi\":\"Jouko Kasslin\",\"puhelinnumero\":\"046 248 7808\",\"email\":\"JoukoKasslin@gustr.com\",\"organisaatio\":\"YIT Rakennus Oy\",\"vastuuhenkilo\":false,\"varahenkilo\":false}},{\"yhteyshenkilo\":{\"rooli\":\"Sillanvalvoja\",\"nimi\":\"Tiina Frösén\",\"puhelinnumero\":\"042 869 5938\",\"email\":\"TiinaFrosen@teleworm.us\",\"organisaatio\":\"YIT Rakennus Oy\",\"vastuuhenkilo\":false,\"varahenkilo\":false}}]}}"
          data (walk/keywordize-keys (cheshire/decode (:body vastaus)))
          yhteyshenkilot (get-in data [:urakka :yhteyshenkilot])]
      (is (= 200 (:status vastaus)) "Haku onnistui validilla kutsulla")
      (is (= odotettu-vastaus (:body vastaus)))
      (is (= 4 (count yhteyshenkilot)))
      (is (sisaltaa-roolin? yhteyshenkilot "Sillanvalvoja"))
      (is (sisaltaa-roolin? yhteyshenkilot "Urakan vastuuhenkilö"))
      (is (sisaltaa-roolin? yhteyshenkilot "ELY urakanvalvoja"))
      (is (sisaltaa-roolin? yhteyshenkilot "Kunnossapitopäällikkö")))))

(deftest tarkista-oikeudet-rajapintaan
  (let [vastaus (api-tyokalut/get-kutsu "/api/urakat/yhteystiedot/1238" urakoitsija-jarjestelmakayttaja portti)]
    (is (= 403 (:status vastaus)) "Urakoitsijan järjestelmätunnuksella ei ole oikeutta palveluun")))


