(ns harja.palvelin.integraatiot.api.yhteystiedot-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.yhteystiedot :as api-yhteystiedot]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.komponentit.fim :as fim]
            [cheshire.core :as cheshire]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [clj-time
             [coerce :as tc]
             [format :as df]]))

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
<Role>MHU-TESTI-LAP-ROV_ELY_Urakanvalvoja</Role>
</member>
<member>
<AccountName>LXTESTI</AccountName>
<FirstName>Ulla</FirstName>
<LastName>Urakoitsija</LastName>
<DisplayName>Ulla Urakoitsija YIT Rakennus Oy</DisplayName>
<Email>ulla@example.com</Email>
<MobilePhone>234234</MobilePhone>
<Company>YIT Rakennus Oy</Company>
<Role>MHU-TESTI-LAP-ROV_vastuuhenkilo</Role>
</member>
</Members>")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    livi-jarjestelmakayttaja
    :fim (component/using
           (fim/->FIM {:url fim-url})
           [:db :integraatioloki])
    :api-yhteystiedot (component/using
                        (api-yhteystiedot/->Yhteystiedot)
                        [:http-palvelin :db :integraatioloki :fim])))

(use-fixtures :each jarjestelma-fixture)

(defn sisaltaa-roolin? [yhteyshenkilot rooli]
  (some #(= rooli (get-in % [:yhteyshenkilo :rooli])) yhteyshenkilot))

(deftest tarkista-yhteystietojen-haku
  (with-fake-http
    [(str "http://localhost:" portti "/api/urakat/yhteystiedot/13371") :allow
     fim-url fim-vastaus]
    (let [;; Poistetaan oikeudet 
          _ (poista-kayttajan-api-oikeudet livi-jarjestelmakayttaja)
          ;; Näillä oikeuksilla pelkästään ei pitäisi lukea yhteystietoja 
          _ (anna-analytiikkaoikeus livi-jarjestelmakayttaja)
          _ (anna-tielupaoikeus livi-jarjestelmakayttaja)
          vastaus (api-tyokalut/get-kutsu "/api/urakat/yhteystiedot/13371" livi-jarjestelmakayttaja portti)

          ;; Käyttäjällä ei ole lukuoikeutta  
          _ (is (= 403 (:status vastaus)) "Käyttäjältä ei löydy luku oikeuksia")
          _ (is (str/includes? (:body vastaus) "Käyttäjätunnuksella puutteelliset oikeudet") "Virheviesti löytyy")
          
          ;; Annetaan oikeudet ja tehdään kutsu uudelleen
          _ (poista-kayttajan-api-oikeudet livi-jarjestelmakayttaja)
          _ (anna-lukuoikeus livi-jarjestelmakayttaja)
          vastaus (api-tyokalut/get-kutsu "/api/urakat/yhteystiedot/13371" livi-jarjestelmakayttaja portti)

          {:keys [alkupvm loppupvm]} (first (q-map "SELECT * FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)';"))
          formaatti "yyyy-MM-dd'T'HH:mm:ss"
          alkupvm (str (df/unparse (df/formatter formaatti) (tc/from-date alkupvm)) "Z")
          loppupvm (str (df/unparse (df/formatter formaatti) (tc/from-date loppupvm)) "Z")
          odotettu-vastaus (str "{\"urakka\":{\"elynro\":14,\"alueurakkanro\":\"13371\",\"loppupvm\":\"" loppupvm
                             "\",\"nimi\":\"Rovaniemen MHU testiurakka (1. hoitovuosi)\",\"sampoid\":\"MHU-TESTI-LAP-ROV\",\"alkupvm\":\"" alkupvm
                             "\",\"elynimi\":\"Lappi\",\"urakoitsija\":{\"nimi\":\"YIT Rakennus Oy\",\"ytunnus\":\"1565583-5\",\"katuosoite\":\"Panuntie 11, PL 36\",\"postinumero\":\"621  \"},"
                             "\"yhteyshenkilot\":[{\"yhteyshenkilo\":{\"rooli\":\"ELY urakanvalvoja\",\"nimi\":\"Erkki Elyläinen\",\"puhelinnumero\":\"0982345\",\"email\":\"erkki@example.com\",\"organisaatio\":\"ELY\",\"vastuuhenkilo\":false,\"varahenkilo\":false}},{\"yhteyshenkilo\":{\"rooli\":\"Urakan vastuuhenkilö\",\"nimi\":\"Ulla Urakoitsija\",\"puhelinnumero\":\"234234\",\"email\":\"ulla@example.com\",\"organisaatio\":\"YIT Rakennus Oy\",\"vastuuhenkilo\":false,\"varahenkilo\":false}},{\"yhteyshenkilo\":{\"rooli\":\"Kunnossapitopäällikkö\",\"nimi\":\"Åsa Linnasalo\",\"puhelinnumero\":\"044 261 2773\",\"email\":\"AsaLinnasalo@cuvox.de\",\"organisaatio\":\"YIT Rakennus Oy\",\"vastuuhenkilo\":false,\"varahenkilo\":false}},{\"yhteyshenkilo\":{\"rooli\":\"Sillanvalvoja\",\"nimi\":\"Vihtori Ollila\",\"puhelinnumero\":\"042 220 6892\",\"email\":\"VihtoriOllila@einrot.com\",\"organisaatio\":\"YIT Rakennus Oy\",\"vastuuhenkilo\":false,\"varahenkilo\":false}}]}}")
          data (walk/keywordize-keys (cheshire/decode (:body vastaus)))
          yhteyshenkilot (get-in data [:urakka :yhteyshenkilot])]
      (is (= 200 (:status vastaus)) "Haku onnistui validilla kutsulla")
      (is (= odotettu-vastaus (:body vastaus)))
      (is (= 4 (count yhteyshenkilot)))
      (is (sisaltaa-roolin? yhteyshenkilot "Sillanvalvoja"))
      (is (sisaltaa-roolin? yhteyshenkilot "Urakan vastuuhenkilö"))
      (is (sisaltaa-roolin? yhteyshenkilot "ELY urakanvalvoja"))
      (is (sisaltaa-roolin? yhteyshenkilot "Kunnossapitopäällikkö")))))

;; Varmista, ettei kanavaurakkaa löydetä
(deftest tarkista-ettei-kanavaurakkaa-loydy
  (let [kanavaurakat
        (q-map (str "select urakkanro from urakka
                     where loppupvm > 'NOW()'
                     AND tyyppi in ('vesivayla-hoito', 'vesivayla-ruoppaus', 'vesivayla-turvalaitteiden-korjaus', 'vesivayla-kanavien-hoito', 'vesivayla-kanavien-korjaus')
                     AND urakkanro is not null"))
        kanava-urakkanumero (:urakkanro (first kanavaurakat))]
    (with-fake-http
      [(str "http://localhost:" portti (str "/api/urakat/yhteystiedot/" kanava-urakkanumero)) :allow
       fim-url fim-vastaus]
      (let [_ (anna-lukuoikeus livi-jarjestelmakayttaja)
            vastaus (api-tyokalut/get-kutsu (str "/api/urakat/yhteystiedot/" kanava-urakkanumero) livi-jarjestelmakayttaja portti)
            odotettu-vastaus (format "{\"virheet\":[{\"virhe\":{\"koodi\":\"tuntematon-urakka\",\"viesti\":\"Urakkanumerolla: %s ei löydy voimassa olevaa urakkaa Harjassa.\"}}]}" kanava-urakkanumero)
            data (walk/keywordize-keys (cheshire/decode (:body vastaus)))]
        (is (= 400 (:status vastaus)) "Haku epäonnistui epävalidilla urakkanumerolla")
        (is (= odotettu-vastaus (:body vastaus)))))))

(deftest tarkista-oikeudet-rajapintaan
  (let [_ (anna-lukuoikeus urakoitsija-jarjestelmakayttaja)
        vastaus (api-tyokalut/get-kutsu "/api/urakat/yhteystiedot/13371" urakoitsija-jarjestelmakayttaja portti)]
    (is (= 403 (:status vastaus)) "Urakoitsijan järjestelmätunnuksella ei ole oikeutta palveluun")))


