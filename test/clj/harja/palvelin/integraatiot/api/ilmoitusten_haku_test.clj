(ns harja.palvelin.integraatiot.api.ilmoitusten-haku-test
  (:require [ajax.json :as json]
            [clojure.test :refer [deftest is use-fixtures testing]]
            [clojure.core.async :refer [<!! timeout]]
            [clj-time
             [core :as t]
             [format :as df]]
            [harja.kyselyt.konversio :as konv]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-jms]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [harja.pvm :as pvm])
  (:import (java.net URLEncoder)
           (java.text SimpleDateFormat)
           (java.util TimeZone)
           (java.util Date)))

(def kayttaja "yit-rakennus")

(def +kuittausjono+ "tloik-ilmoituskuittausjono")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki])
    :itmf (feikki-jms "itmf")
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :itmf :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)
;; Apufunktiot

(deftest hae-muuttuneet-ilmoitukset
  (u (str "UPDATE ilmoitus SET muokattu = NOW() + INTERVAL '1 hour'
           WHERE urakka = 4 AND id IN (SELECT id FROM ilmoitus WHERE urakka = 4 LIMIT 1)"))

  (let [nyt (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/4/ilmoitukset?muuttunutJalkeen=" (URLEncoder/encode nyt)]
                                        kayttaja portti)
        kaikkien-ilmoitusten-maara-suoraan-kannasta (ffirst (q (str "SELECT count(*) FROM ilmoitus
                                                                     WHERE urakka = 4;")))]
    (is (= 200 (:status vastaus)))

    (let [vastausdata (cheshire/decode (:body vastaus))
          ilmoitukset (get vastausdata "ilmoitukset")
          ilmoituksia (count ilmoitukset)]
      (is (> kaikkien-ilmoitusten-maara-suoraan-kannasta ilmoituksia))
      (is (< 0 ilmoituksia)))))

(defn- poista-ilmoista-turhat
  "Palauttaa ilmoitukset yksinkertaistettuna.
  {'ilmoitukset'
  [{'ilmoitus' {'kuittaukset' ['ja yksinkertaistetut kuittaukset tähän']
                'valitetty-urakkaan' <timestamp>
                'ilmoitusid' <ilmoitusid>}}]}"
  [ilmoitukset]
  (let [ilmoitukset-listana (get ilmoitukset "ilmoitukset")
        ;; Siivotaan yksittäisistä ilmoituksista pois kaikki mitä ei tarvita
        siivotut-ilmoitukset (mapv (fn [i]
                                     (let [kuittaukset (get-in i ["ilmoitus" "kuittaukset"])
                                           siivotut-kuittaukset (mapv (fn [k]
                                                                        (-> {}
                                                                          (assoc "kuittaustyyppi" (get-in k ["kuittaus" "kuittaustyyppi"])
                                                                                 "kanava" (get-in k ["kuittaus" "kanava"])
                                                                                 "kuitattu" (get-in k ["kuittaus" "kuitattu"])))) kuittaukset)]
                                       (-> {}
                                         (assoc-in ["ilmoitus" "kuittaukset"] siivotut-kuittaukset)
                                         (assoc-in ["ilmoitus" "valitetty-urakkaan"] (get i "valitetty-urakkaan"))
                                         (assoc-in ["ilmoitus" "ilmoitusid"] (get i "ilmoitusid"))))) ilmoitukset-listana)]
    {"ilmoitukset" siivotut-ilmoitukset}))

(deftest hae-ilmoitukset-ytunnuksella-onnistuu
  (let [kuukausi-sitten (nykyhetki-iso8061-formaatissa-menneisyyteen 30)
        huomenna (nykyhetki-iso8061-formaatissa-tulevaisuuteen 1)
        ;; kovakoodataan odotettu määrä, jos esim ajan myötä muuttuu nollaan, saattaa testi menettää tehoansa
        ;; jos muutat ilmoitusten testidataa näiden osalta, saattaa kovakoodattu numero muuttua
        odotettu-ilmoitusten-maara 13
        yit-y-tunnus "1565583-5"
        yit-y-tunnuksen-ilmot-kannassa (q-map
                                     "select o.ytunnus, count(i.*) as ilmoituksien_lukumaara from ilmoitus i
                                       join urakka u on i.urakka = u.id
                                       join organisaatio o ON o.id = u.urakoitsija
                                      WHERE o.ytunnus = '1565583-5' AND
                                        ((u.loppupvm + interval '36 hour') >= NOW()) OR
                                        (u.loppupvm IS NULL AND u.alkupvm <= NOW())
                                GROUP BY o.ytunnus;")
        _ (anna-lukuoikeus kayttaja)
        yit-vastaus (api-tyokalut/get-kutsu [(str "/api/ilmoitukset/" yit-y-tunnus "/" kuukausi-sitten "/" huomenna)]
                  kayttaja portti)
        yit-ilmot (get-in (cheshire/decode (:body yit-vastaus)) ["ilmoitukset"])

        _ (anna-lukuoikeus skanska-kayttaja)]
    (is (= 200 (:status yit-vastaus)))
    (is (str/includes? (:body yit-vastaus) "Ilmoittaja"))
    (is (str/includes? (:body yit-vastaus) "Rovanieminen"))
    (is (str/includes? (:body yit-vastaus) "Sillalla on lunta. Liikaa."))
    (is (= odotettu-ilmoitusten-maara (count yit-ilmot) (:ilmoituksien_lukumaara (first
                                                                                   (filter #(= (:ytunnus %) yit-y-tunnus)
                                                                                     yit-y-tunnuksen-ilmot-kannassa))))
      "Oikea määrä ilmoituksia palautuu.")))

(deftest hae-ilmoitukset-ytunnuksella-onnistuu-ilman-loppuaikaa
  (let [alkuaika "2022-01-01T00:00:00+03"
        y-tunnus "1565583-5"
        _ (anna-lukuoikeus kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/ilmoitukset/" y-tunnus "/" alkuaika)]
                  kayttaja portti)]
    (is (= 200 (:status vastaus)))))

(defn luo-ilmoitus [ilmoitusid urakka-id db-timestamp]
  (let [sql-str (format "INSERT INTO ilmoitus (urakka, ilmoitusid, ilmoitettu, valitetty, \"valitetty-urakkaan\")
      VALUES (%s, %s, '%s', '%s', '%s');" urakka-id ilmoitusid db-timestamp db-timestamp db-timestamp)
        _ (u sql-str)
        ilmoituksen-id (ffirst (q (format "SELECT id FROM ilmoitus WHERE urakka = %s order by id desc limit 1;" urakka-id)))]
    ilmoituksen-id))

(defn luo-kuittaus [ilmoituksen-id ilmoitusid kuittaustyyppi db-timestamp] ;kuittaustyyppi= lopetus, aloitus, vastaanotto
  (let [sql-str (format "INSERT INTO ilmoitustoimenpide (ilmoitus, ilmoitusid, kuittaustyyppi, kuitattu, suunta) VALUES
  (%s, %s, '%s' , '%s', 'sisaan'::viestisuunta);" ilmoituksen-id ilmoitusid kuittaustyyppi db-timestamp)]
    (u sql-str)))

(defn str-timestamp->json-ajaksi
  "Anna timestamp str muodossa: 'yyyy-MM-dd HH:mm:ss.SSS' palauttaa utc ajassa -> 'yyyy-MM-dd HH:mm:ssZ'"
  [aika-str]
  (let [dateformat (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS")
        _ (.setTimeZone dateformat (TimeZone/getTimeZone "Europe/Helsinki"))
        konvertoitu (.parse dateformat aika-str)
        iso-basic (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssZ") konvertoitu)
        json-aika (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss'Z'") (pvm/iso8601-basic->suomen-aika iso-basic))]
    json-aika))

(deftest hae-ilmoitukset-ytunnuksella-onnistuu-vaikka-haetaan-vain-kuittauksen-ajankohdasta
  (let [;; Luo uusi ilmoitus ja pari kuittausta
        y-tunnus "1565583-5"
        ilmoitusid (rand-int 92333123)
        ensimmainen-urakka-ytunnuksella (:id (first (q-map (format "select u.id as id
        from urakka u join organisaatio o on u.urakoitsija = o.id AND o.ytunnus = '%s'
        and u.loppupvm > NOW()
        and u.tyyppi = 'teiden-hoito'" y-tunnus))))

        db-timestamp-60min-sitten (nykyhetki-psql-timestamp-formaatissa-menneisyyteen-minuutteja 60)
        haku-timestamp-45min-sitten (nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja 45)
        haku-timestamp-51min-sitten (nykyhetki-iso8061-formaatissa-menneisyyteen-minuutteja 51)
        ilmoituksen-id (luo-ilmoitus ilmoitusid ensimmainen-urakka-ytunnuksella db-timestamp-60min-sitten)
        ;; Luo ilmoitukselle 3 kuittausta, jotka alkavat vähän eri aikaan, jotta voidaan testissä varmistua, että hakemalla vain kuittausajankohtaa, kaikki kuittaukset kuitenkin tulevat
        vastaanotto-kuitattu (nykyhetki-psql-timestamp-formaatissa-menneisyyteen-minuutteja 49)
        aloitus-kuitattu (nykyhetki-psql-timestamp-formaatissa-menneisyyteen-minuutteja 48)
        lopetus-kuitattu (nykyhetki-psql-timestamp-formaatissa-menneisyyteen-minuutteja 10)
        _ (luo-kuittaus ilmoituksen-id ilmoitusid "vastaanotto" vastaanotto-kuitattu)
        _ (luo-kuittaus ilmoituksen-id ilmoitusid "aloitus" aloitus-kuitattu)
        _ (luo-kuittaus ilmoituksen-id ilmoitusid "lopetus" lopetus-kuitattu)
        _ (anna-lukuoikeus kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/ilmoitukset/" y-tunnus "/" haku-timestamp-51min-sitten "/" haku-timestamp-45min-sitten)]
                  kayttaja portti)
        siivotut-ilmoitukset (poista-ilmoista-turhat (cheshire/decode (:body vastaus)))
        kuittaukset (get-in siivotut-ilmoitukset ["ilmoitukset" 0 "ilmoitus" "kuittaukset"])
        kuittausten-maara (count kuittaukset)]
    (is (= 200 (:status vastaus)))
    (is (= 3 kuittausten-maara))
    ;; Tarkista kuittauksen kuittausaika
    (is (= (str-timestamp->json-ajaksi vastaanotto-kuitattu) (get (first kuittaukset) "kuitattu")))
    (is (= (str-timestamp->json-ajaksi aloitus-kuitattu) (get (second kuittaukset) "kuitattu")))
    (is (= (str-timestamp->json-ajaksi lopetus-kuitattu) (get (nth kuittaukset 2) "kuitattu")))))

(deftest hae-ilmoitukset-ytunnuksella-epaonnistuu-ei-kayttoikeutta
  (let [alkuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        loppuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        y-tunnus "1234567-8"
        _ (anna-lukuoikeus kayttaja)
        vastaus (api-tyokalut/get-kutsu [(str "/api/ilmoitukset/" y-tunnus "/" alkuaika "/" loppuaika)]
                  kayttaja portti)
        odotettu-vastaus-json "{\"virheet\":[{\"virhe\":{\"koodi\":\"kayttajalla-puutteelliset-oikeudet\",\"viesti\":\"Käyttäjällä: yit-rakennus ei ole oikeuksia organisaatioon: 1234567-8\"}}]}"]
    (is (= odotettu-vastaus-json (:body vastaus)))))

(deftest hae-ilmoitukset-ytunnuksella-epaonnistuu-vaarat-hakuparametrit
  (testing "Alkuaika on väärässä muodossa "
    (let [alkuaika (.format (SimpleDateFormat. "YY-MM-d'T'HH:mm:ssX") (Date.))
          loppuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
          y-tunnus "1565583-5"
          _ (anna-lukuoikeus kayttaja)
          vastaus (api-tyokalut/get-kutsu [(str "/api/ilmoitukset/" y-tunnus "/" alkuaika "/" loppuaika)]
                    kayttaja portti)]
      (is (= 400 (:status vastaus)))
      (is (str/includes? (:body vastaus) "puutteelliset-parametrit"))))
  (testing "Loppuaika on väärässä muodossa "
    (let [alkuaika (.format (SimpleDateFormat. "yyy-MM-d'T'HH:mm:ssX") (Date.))
          loppuaika (.format (SimpleDateFormat. "-MM-dd'T'HH:mm:ssX") (Date.))
          y-tunnus "1565583-5"
          vastaus (api-tyokalut/get-kutsu [(str "/api/ilmoitukset/" y-tunnus "/" alkuaika "/" loppuaika)]
                    kayttaja portti)]
      (is (= 400 (:status vastaus)))
      (is (str/includes? (:body vastaus) "Loppuaika väärässä muodossa")))))


