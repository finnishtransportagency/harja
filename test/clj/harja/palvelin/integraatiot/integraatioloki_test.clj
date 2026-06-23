(ns harja.palvelin.integraatiot.integraatioloki-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.data.json :as json]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki siivoa-henkilotiedot-viestista] :as integraatioloki]))

(def +testiviesti+ {:suunta "ulos" :sisaltotyyppi "application/xml" :siirtotyyppi "jms" :sisalto "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" :otsikko nil :parametrit nil})

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :integraatioloki (component/using (->Integraatioloki nil) [:db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(defn poista-testitapahtuma [tapahtuma-id]
  (u "DELETE FROM integraatioviesti WHERE integraatiotapahtuma = " tapahtuma-id ";")
  (u "DELETE FROM integraatiotapahtuma WHERE id = " tapahtuma-id ";"))

(deftest tarkista-integraation-aloituksen-kirjaaminen
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio (:integraatioloki jarjestelma) "sampo-api" "sisaanluku" nil nil)]
    (is tapahtuma-id "Tapahtumalle palautettiin id.")
    (is (first (first (q "SELECT exists(SELECT id FROM integraatiotapahtuma WHERE id = " tapahtuma-id ");")))
        "Tietokannasta löytyy integraatiotapahtuma integraation aloituksen jälkeen.")
    (poista-testitapahtuma tapahtuma-id)))

(deftest tarkista-onnistuneen-integraation-kirjaaminen
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio (:integraatioloki jarjestelma) "sampo-api" "sisaanluku" nil nil)]
    (integraatioloki/kirjaa-onnistunut-integraatio (:integraatioloki jarjestelma) nil nil tapahtuma-id nil)
    (is (first (first (q "SELECT exists(SELECT id FROM integraatiotapahtuma WHERE id = " tapahtuma-id " AND onnistunut is true AND paattynyt is not null);")))
        "Tietokannasta löytyy integraatiotapahtuma joka on merkitty onnistuneeksi.")
    (poista-testitapahtuma tapahtuma-id)))

(deftest tarkista-viestin-kirjaaminen
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio (:integraatioloki jarjestelma) "sampo-api" "sisaanluku" nil +testiviesti+)]
    (is (= 1 (count (q "SELECT id FROM integraatioviesti WHERE integraatiotapahtuma = " tapahtuma-id ";"))))
    (poista-testitapahtuma tapahtuma-id)))
;; =============================================================================
;; siivoa-henkilotiedot-viestista -funktion testit
;; =============================================================================

(def +miam-jarjestelma+ "miam")
(def +miam-integraatio+ "hae-kayttajan-roolit")

(def +miam-vastaus-yksi-kayttaja+
  "{\"Table1\": [{\"CompanyID\": \"1234567-8\", \"Company\": \"Testi Oy\", \"UserName\": \"testi123\", \"Name\": \"Testi Käyttäjä\", \"Role\": \"1234567-8_Paakayttaja\", \"StartDate\": \"9.4.2024 13:01:03\", \"EndDate\": \"31.3.2029 0:00:00\", \"Agreementname\": \"_Organisaatio peruste Testi Oy\", \"Appname\": \"HARJA\", \"email\": \"testi@example.com\"}]}")

(def +miam-vastaus-useita-kayttajia+
  "{\"Table1\": [{\"CompanyID\": \"1234567-8\", \"Name\": \"Käyttäjä 1\", \"email\": \"kayttaja1@example.com\", \"Role\": \"Rooli1\"}, {\"CompanyID\": \"9876543-2\", \"Name\": \"Käyttäjä 2\", \"email\": \"kayttaja2@example.com\", \"Role\": \"Rooli2\"}]}")

(deftest siivoa-henkilotiedot-viestista-poistaa-name-ja-email-kentat
  (testing "Poistaa Name ja email kentät MIAM-vastauksesta"
    (let [siivottu (siivoa-henkilotiedot-viestista +miam-vastaus-yksi-kayttaja+ +miam-jarjestelma+ +miam-integraatio+)
          parsed (json/read-str siivottu :key-fn keyword)
          table1 (first (:Table1 parsed))]
      (is (some? siivottu) "Siivottu viesti ei saa olla nil")
      (is (nil? (:Name table1)) "Name-kenttä on poistettu")
      (is (nil? (:email table1)) "email-kenttä on poistettu")
      (is (= "1234567-8" (:CompanyID table1)) "Muut kentät säilyvät")
      (is (= "Testi Oy" (:Company table1)) "Company-kenttä säilyy")
      (is (= "testi123" (:UserName table1)) "UserName-kenttä säilyy")
      (is (= "1234567-8_Paakayttaja" (:Role table1)) "Role-kenttä säilyy")))

  (testing "Poistaa Name ja email kentät kaikilta käyttäjiltä"
    (let [siivottu (siivoa-henkilotiedot-viestista +miam-vastaus-useita-kayttajia+ +miam-jarjestelma+ +miam-integraatio+)
          parsed (json/read-str siivottu :key-fn keyword)
          table1 (:Table1 parsed)]
      (is (= 2 (count table1)) "Molemmat käyttäjät säilyvät")
      (doseq [kayttaja table1]
        (is (nil? (:Name kayttaja)) "Name-kenttä on poistettu")
        (is (nil? (:email kayttaja)) "email-kenttä on poistettu")))))

(deftest siivoa-henkilotiedot-viestista-ei-muokkaa-muita-jarjestelmia-tai-integraatioita
  (testing "Muiden järjestelmien ja integraatioiden viestejä ei muokata"
    (let [viesti +miam-vastaus-yksi-kayttaja+]
      ;; Eri järjestelmät
      (doseq [jarjestelma ["sampo-api" "turi" "labyrintti" "tloik" nil ""]]
        (is (= viesti (siivoa-henkilotiedot-viestista viesti jarjestelma +miam-integraatio+))
          (str "Järjestelmän '" jarjestelma "' viesti palautetaan muuttumattomana")))
      ;; Eri integraatiot
      (doseq [integraatio ["jokin-muu" "hae-sopimukset" nil ""]]
        (is (= viesti (siivoa-henkilotiedot-viestista viesti +miam-jarjestelma+ integraatio))
          (str "Integraation '" integraatio "' viesti palautetaan muuttumattomana"))))))

(deftest siivoa-henkilotiedot-viestista-virhetilanteet
  (testing "Nil-parametrit eivät aiheuta NullPointerExceptionia"
    (is (nil? (siivoa-henkilotiedot-viestista nil +miam-jarjestelma+ +miam-integraatio+))
      "Nil-viesti palautetaan nil:nä")
    (is (nil? (siivoa-henkilotiedot-viestista nil nil nil))
      "Kaikki parametrit nil palauttaa nil"))

  (testing "Invalidi tai tyhjä JSON palauttaa alkuperäisen viestin"
    (is (= "" (siivoa-henkilotiedot-viestista "" +miam-jarjestelma+ +miam-integraatio+))
      "Tyhjä string palautetaan sellaisenaan")
    (let [invalidi "tämä ei ole json"]
      (is (= invalidi (siivoa-henkilotiedot-viestista invalidi +miam-jarjestelma+ +miam-integraatio+))
        "Invalidi JSON palautetaan sellaisenaan")))

  (testing "Erikoistapaukset Table1-kentässä käsitellään oikein"
    ;; JSON ilman Table1
    (let [json-ilman "{\"other\": \"data\"}"
          tulos (siivoa-henkilotiedot-viestista json-ilman +miam-jarjestelma+ +miam-integraatio+)]
      (is (= "data" (:other (json/read-str tulos :key-fn keyword)))))
    ;; Tyhjä Table1
    (let [tyhja "{\"Table1\": []}"
          tulos (siivoa-henkilotiedot-viestista tyhja +miam-jarjestelma+ +miam-integraatio+)]
      (is (= [] (:Table1 (json/read-str tulos :key-fn keyword)))))
    ;; Table1 ei ole lista (null tai objekti)
    (doseq [erikois ["{\"Table1\": null}" "{\"Table1\": {\"Name\": \"test\"}}"]]
      (let [tulos (siivoa-henkilotiedot-viestista erikois +miam-jarjestelma+ +miam-integraatio+)]
        (is (string? tulos) "Tulos on merkkijono")))))
