(ns harja.palvelin.integraatiot.api.paivystajat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.paivystajatiedot :as api-paivystajatiedot]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]
            [harja.fmt :as fmt]))

(def kayttaja-yit "yit-rakennus")
(def kayttaja-jvh "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja-yit
                                           :api-paivystajatiedot
                                           (component/using
                                             (api-paivystajatiedot/->Paivystajatiedot)
                                             [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(defn hae-vapaa-yhteyshenkilo-ulkoinen-id []
  (let [id (rand-int 10000)
        vastaus (q (str "SELECT * FROM yhteyshenkilo WHERE ulkoinen_id = '" id "';"))]
    (if (empty? vastaus) id (recur))))

(deftest tallenna-paivystajatiedot
  (let [urakka-id (hae-oulun-alueurakan-2005-2010-id)
        urakoitsija-id (hae-oulun-alueurakan-2005-2010-urakoitsija)
        ulkoinen-id (hae-vapaa-yhteyshenkilo-ulkoinen-id)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/paivystajatiedot"] kayttaja-yit portti
                                                (-> "test/resurssit/api/kirjaa_paivystajatiedot.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__ETUNIMI__" "Päivi")
                                                    (.replace "__SUKUNIMI__" "Päivystäjä")
                                                    (.replace "__EMAIL__" "paivi.paivystaja@sahkoposti.com")
                                                    (.replace "__MATKAPUHELIN__" "04001234567")
                                                    (.replace "__TYOPUHELIN__" "04005555555")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [paivystaja-id (ffirst (q (str "SELECT id FROM yhteyshenkilo WHERE ulkoinen_id = '" (str ulkoinen-id) "';")))
          paivystaja (first (q (str "SELECT ulkoinen_id, etunimi, sukunimi, sahkoposti, matkapuhelin, tyopuhelin FROM yhteyshenkilo WHERE ulkoinen_id = '" (str ulkoinen-id) "';")))
          urakoitsija (ffirst (q (str "SELECT organisaatio FROM yhteyshenkilo WHERE ulkoinen_id = '" (str ulkoinen-id) "';")))
          paivystys (first (q (str "SELECT yhteyshenkilo, vastuuhenkilo, varahenkilo FROM paivystys WHERE yhteyshenkilo = " paivystaja-id)))]
      (is (= paivystaja [(str ulkoinen-id) "Päivi" "Päivystäjä" "paivi.paivystaja@sahkoposti.com" "04001234567" "04005555555"]))
      (is (= urakoitsija-id urakoitsija))
      (is (= paivystys [paivystaja-id true false]))

      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/paivystajatiedot"] kayttaja-yit portti
                                                      (-> "test/resurssit/api/kirjaa_paivystajatiedot.json"
                                                          slurp
                                                          (.replace "__ID__" (str ulkoinen-id))
                                                          (.replace "__ETUNIMI__" "Taneli")
                                                          (.replace "__SUKUNIMI__" "Tähystäjä")
                                                          (.replace "__EMAIL__" "taneli.tahystaja@gmail.com")
                                                          (.replace "__MATKAPUHELIN__" "05001234567")))]
        (is (= 200 (:status vastaus-paivitys)))
        (let [paivystaja (first (q (str "SELECT ulkoinen_id, etunimi, sukunimi, sahkoposti, matkapuhelin FROM yhteyshenkilo WHERE ulkoinen_id = '" (str ulkoinen-id) "';")))
              paivystys (first (q (str "SELECT yhteyshenkilo FROM paivystys WHERE yhteyshenkilo = " paivystaja-id)))]
          (is (= paivystaja [(str ulkoinen-id) "Taneli" "Tähystäjä" "taneli.tahystaja@gmail.com" "+3585001234567"]))
          (is (= paivystys [paivystaja-id]))

          (u (str "DELETE FROM yhteyshenkilo WHERE ulkoinen_id = '" (str ulkoinen-id) "';"))
          (u (str "DELETE FROM paivystys WHERE yhteyshenkilo = " paivystaja-id)))))))

(deftest hae-paivystajatiedot-urakan-idlla
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/" urakka-id "/paivystajatiedot"] kayttaja-yit portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count (:paivystajatiedot encoodattu-body)) 1))
    (is (= (count (:paivystykset (:urakka (first (:paivystajatiedot encoodattu-body))))) 3))))

(deftest testaa-puhelinnumeron-trimmaus
  (is (= (fmt/trimmaa-puhelinnumero "0400123123") (fmt/trimmaa-puhelinnumero "+358400123123")))
  (is (= (fmt/trimmaa-puhelinnumero "0400-123123") (fmt/trimmaa-puhelinnumero "+358400123123")))
  (is (= (fmt/trimmaa-puhelinnumero "0400 123 123") (fmt/trimmaa-puhelinnumero "+358400123123")))
  (is (= (fmt/trimmaa-puhelinnumero "+358400-123-123") (fmt/trimmaa-puhelinnumero "+358400123123")))
  (is (= (fmt/trimmaa-puhelinnumero "+358400-123-123") (fmt/trimmaa-puhelinnumero "+358400 123 123")))
  (is (= (fmt/trimmaa-puhelinnumero "+358400 123 123") (fmt/trimmaa-puhelinnumero "+358400123123")))
  (is (= (fmt/trimmaa-puhelinnumero "0400-123123") (fmt/trimmaa-puhelinnumero "0400 123 123")))
  (is (= (fmt/trimmaa-puhelinnumero "040-123123") (fmt/trimmaa-puhelinnumero "+35840123123")))
  (is (= (fmt/trimmaa-puhelinnumero "050 675 436") (fmt/trimmaa-puhelinnumero "+35850-675-436")))
  (is (= (fmt/trimmaa-puhelinnumero "0400-123-123") (fmt/trimmaa-puhelinnumero "0400 123 123")))
  (is (not= (fmt/trimmaa-puhelinnumero "+3580400-123-123") (fmt/trimmaa-puhelinnumero "+358400 123 123")))
  (is (not= (fmt/trimmaa-puhelinnumero "+358500-123-123") (fmt/trimmaa-puhelinnumero "+358400 123 123")))
  (is (not= (fmt/trimmaa-puhelinnumero "+0400-123-123") (fmt/trimmaa-puhelinnumero "358400 123 123"))))

(deftest hae-paivystajatiedot-puhelinnumerolla-aikavalilla
  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/puhelinnumerolla?alkaen=2029-01-30T12:00:00Z&paattyen=2030-01-30T12:00:00Z&puhelinnumero=0505555555"] kayttaja-jvh portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (log/debug (:body vastaus))
    (is (= (count (:paivystajatiedot encoodattu-body)) 0))))

(deftest hae-paivystajatiedot-puhelinnumerolla
  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/puhelinnumerolla?puhelinnumero=0505555555"] kayttaja-jvh portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (log/debug (:body vastaus))
    (is (= (count (:paivystajatiedot encoodattu-body)) 1))
    (is (= (count (:paivystykset (:urakka (first (:paivystajatiedot encoodattu-body))))) 1))))

(deftest esta-hae-paivystajatiedot-puhelinnumerolla-ilman-oikeuksia
  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/puhelinnumerolla?alkaen=2000-01-30T12:00:00Z&paattyen=2030-01-30T12:00:00Z&puhelinnumero=0505555555"] kayttaja-yit portti)]
    (is (= 400 (:status vastaus)))))

(deftest hae-paivystajatiedot-sijainnilla-kayttaen-lyhytta-aikavalia
  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/sijainnilla?urakkatyyppi=hoito&x=453271&y=7188395"] kayttaja-yit portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count (:paivystajatiedot encoodattu-body)) 1))
    (is (= (count (:paivystykset (:urakka (first (:paivystajatiedot encoodattu-body))))) 3))))

(deftest hae-paivystajatiedot-sijainnilla-kayttaen-pitkaa-aikavalia
  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/sijainnilla?urakkatyyppi=hoito&x=453271&y=7188395&alkaen=2029-01-30T12:00:00Z&paattyen=2030-01-30T12:00:00Z"] kayttaja-yit portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count (:paivystajatiedot encoodattu-body)) 0))))

(deftest hae-paivystajatiedot-sijainnilla
  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/sijainnilla?urakkatyyppi=hoito&x=453271&y=7188395"] kayttaja-yit portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (count (:paivystajatiedot encoodattu-body)) 1))
    (is (= (count (:paivystykset (:urakka (first (:paivystajatiedot encoodattu-body))))) 3))))

(deftest hae-tarkista-paivamaarakasittelyt
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/4/paivystajatiedot?paattyen=2016-09-30"] kayttaja-yit portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"puutteelliset-parametrit\",\"viesti\":\"Päivämäärävälillä ei voi hakea ilman alkupäivämäärää\"}}]}"
           (:body vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/4/paivystajatiedot?alkaen=2016-09-30"] kayttaja-yit portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"puutteelliset-parametrit\",\"viesti\":\"Päivämäärävälillä ei voi hakea ilman loppupäivämäärää\"}}]}"
           (:body vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/4/paivystajatiedot?alkaen=rikki&paattyen=2016-09-30"] kayttaja-yit portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-paivamaara\",\"viesti\":\"Päivämäärää: rikki ei voi parsia. Anna päivämäärä muodossa: YYYY-MM-DD.\"}}]}"
           (:body vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/4/paivystajatiedot?alkaen=2016-09-30&paattyen=rikki"] kayttaja-yit portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-paivamaara\",\"viesti\":\"Päivämäärää: rikki ei voi parsia. Anna päivämäärä muodossa: YYYY-MM-DD.\"}}]}"
           (:body vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/4/paivystajatiedot?alkaen=2016-09-30&paattyen=2016-01-02"] kayttaja-yit portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-paivamaara\",\"viesti\":\"Alkupäivämäärä: 2016-09-30 00:00:00.0 on päättymispäivämäärän: 2016-01-02 00:00:00.0 jälkeen.\"}}]}"
           (:body vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/sijainnilla?urakkatyyppi=hoito&x=453271&y=7188395&alkaen=rikki&paattyen=2016-09-30"] kayttaja-yit portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-paivamaara\",\"viesti\":\"Päivämäärää: rikki ei voi parsia. Anna päivämäärä muodossa: YYYY-MM-DD.\"}}]}"
           (:body vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/sijainnilla?urakkatyyppi=hoito&x=453271&y=7188395&alkaen=2016-09-30&paattyen=rikki"] kayttaja-yit portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-paivamaara\",\"viesti\":\"Päivämäärää: rikki ei voi parsia. Anna päivämäärä muodossa: YYYY-MM-DD.\"}}]}"
           (:body vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/sijainnilla?urakkatyyppi=hoito&x=453271&y=7188395&alkaen=2016-09-30&paattyen=2016-01-02"] kayttaja-yit portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-paivamaara\",\"viesti\":\"Alkupäivämäärä: 2016-09-30 00:00:00.0 on päättymispäivämäärän: 2016-01-02 00:00:00.0 jälkeen.\"}}]}"
           (:body vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/puhelinnumerolla?puhelinnumero=0505555555&alkaen=rikki&paattyen=2016-09-30"] kayttaja-jvh portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-paivamaara\",\"viesti\":\"Päivämäärää: rikki ei voi parsia. Anna päivämäärä muodossa: YYYY-MM-DD.\"}}]}"
           (:body vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/puhelinnumerolla?puhelinnumero=0505555555&alkaen=2016-09-30&paattyen=rikki"] kayttaja-jvh portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-paivamaara\",\"viesti\":\"Päivämäärää: rikki ei voi parsia. Anna päivämäärä muodossa: YYYY-MM-DD.\"}}]}"
           (:body vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/paivystajatiedot/haku/puhelinnumerolla?puhelinnumero=0505555555&alkaen=2016-09-30&paattyen=2016-01-02"] kayttaja-jvh portti)]
    (is (= 400 (:status vastaus)))
    (is (= "{\"virheet\":[{\"virhe\":{\"koodi\":\"virheellinen-paivamaara\",\"viesti\":\"Alkupäivämäärä: 2016-09-30 00:00:00.0 on päättymispäivämäärän: 2016-01-02 00:00:00.0 jälkeen.\"}}]}"
           (:body vastaus)))))

