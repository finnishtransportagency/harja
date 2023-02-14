(ns harja.palvelin.integraatiot.api.ilmoitusten-haku-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [clojure.core.async :refer [<!! timeout]]
            [clj-time
             [core :as t]
             [format :as df]]
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

(defn odotettu-ilmoitus [ilmoitettu lahetetty]
  {"ilmoitettu" ilmoitettu
   "ilmoittaja" {"email" "matti.meikalainen@palvelu.fi"
                 "etunimi" "Matti"
                 "matkapuhelin" "08023394852"
                 "sukunimi" "Meikäläinen"}
   "ilmoitusid" 123456789
   "tunniste" "UV-1509-1a"
   "ilmoitustyyppi" "toimenpidepyynto"
   "lahettaja" {"email" "pekka.paivystaja@livi.fi"
                "etunimi" "Pekka"
                "sukunimi" "Päivystäjä"}
   "lisatieto" "Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti."
   "otsikko" "Korkeat vallit"
   "paikankuvaus" "Jossain kentällä."
   "selitteet" [{"selite" "tyomaajarjestelyihinLiittyvaIlmoitus"}
                {"selite" "kuoppiaTiessa"}
                {"selite" "kelikysely"}
                {"selite" "soratienKuntoHuono"}
                {"selite" "saveaTiella"}
                {"selite" "liikennettaVaarantavaEsteTiella"}
                {"selite" "irtokiviaTiella"}
                {"selite" "kevyenLiikenteenVaylaanLiittyvaIlmoitus"}
                {"selite" "raivausJaKorjaustoita"}
                {"selite" "auraustarve"}
                {"selite" "yliauraus"}
                {"selite" "kaivonKansiRikki"}
                {"selite" "kevyenLiikenteenVaylatOvatLiukkaita"}
                {"selite" "routaheitto"}
                {"selite" "avattavatPuomit"}
                {"selite" "tievalaistusVioittunutOnnettomuudessa"}
                {"selite" "muuKyselyTaiNeuvonta"}
                {"selite" "soratienTasaustarve"}
                {"selite" "tieTaiTienReunaOnPainunut"}
                {"selite" "siltaanLiittyvaIlmoitus"}
                {"selite" "polynsidontatarve"}
                {"selite" "liikennevalotEivatToimi"}
                {"selite" "kunnossapitoJaHoitotyo"}
                {"selite" "vettaTiella"}
                {"selite" "aurausvallitNakemaesteena"}
                {"selite" "ennakoivaVaroitus"}
                {"selite" "levahdysalueeseenLiittyvaIlmoitus"}
                {"selite" "sohjonPoisto"}
                {"selite" "liikennekeskusKuitannutLoppuneeksi"}
                {"selite" "muuToimenpidetarve"}
                {"selite" "hiekoitustarve"}
                {"selite" "tietOvatJaatymassa"}
                {"selite" "jaatavaaSadetta"}
                {"selite" "tienvarsilaitteisiinLiittyvaIlmoitus"}
                {"selite" "oljyaTiella"}
                {"selite" "sahkojohtoOnPudonnutTielle"}
                {"selite" "tieOnSortunut"}
                {"selite" "tievalaistusVioittunut"}
                {"selite" "testilahetys"}
                {"selite" "tievalaistuksenLamppujaPimeana"}
                {"selite" "virkaApupyynto"}
                {"selite" "tiemerkintoihinLiittyvaIlmoitus"}
                {"selite" "tulvavesiOnNoussutTielle"}
                {"selite" "niittotarve"}
                {"selite" "kuormaOnLevinnytTielle"}
                {"selite" "tieOnLiukas"}
                {"selite" "tiellaOnEste"}
                {"selite" "harjaustarve"}
                {"selite" "hoylaystarve"}
                {"selite" "tietyokysely"}
                {"selite" "paallystevaurio"}
                {"selite" "rikkoutunutAjoneuvoTiella"}
                {"selite" "mustaaJaataTiella"}
                {"selite" "kevyenLiikenteenVaylillaOnLunta"}
                {"selite" "hirviaitaVaurioitunut"}
                {"selite" "korvauskysely"}
                {"selite" "puitaOnKaatunutTielle"}
                {"selite" "rumpuunLiittyvaIlmoitus"}
                {"selite" "lasiaTiella"}
                {"selite" "liukkaudentorjuntatarve"}
                {"selite" "alikulkukaytavassaVetta"}
                {"selite" "kevyenliikenteenAlikulkukaytavassaVetta"}
                {"selite" "tievalaistuksenLamppuPimeana"}
                {"selite" "kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita"}
                {"selite" "kuoppa"}
                {"selite" "toimenpidekysely"}
                {"selite" "pysakkiinLiittyvaIlmoitus"}
                {"selite" "nakemaalueenRaivaustarve"}
                {"selite" "vesakonraivaustarve"}
                {"selite" "muuttuvatOpasteetEivatToimi"}
                {"selite" "tievalaistus"}
                {"selite" "vesiSyovyttanytTienReunaa"}
                {"selite" "raskasAjoneuvoJumissa"}
                {"selite" "myrskyvaurioita"}
                {"selite" "kaidevaurio"}
                {"selite" "liikennemerkkeihinLiittyvaIlmoitus"}
                {"selite" "siirrettavaAjoneuvo"}
                {"selite" "tielleOnVuotanutNestettaLiikkuvastaAjoneuvosta"}
                {"selite" "tapahtumaOhi"}
                {"selite" "kevyenLiikenteenVaylatOvatjaatymassa"}
                {"selite" "tietOvatjaisiaJamarkia"}]
   "sijainti" {"koordinaatit" {"x" 443199.0
                               "y" 7377324.0}}
   "tienumero" 79
   "yhteydenottopyynto" false})

;; Apufunktiot
(defn urakkaidt-ytunnuksella [ytunnus-str]
  (let [idt (q-map (format "(SELECT u.id as id, u.urakkanro as urakkanro
                             FROM urakka u
                                  JOIN organisaatio o ON o.id = u.urakoitsija AND o.ytunnus = '%s'
                                  -- Haetaan vain käynnissäolevista urakoista. Urakat ovat vastuussa tieliikenneilmoituksista
                                  -- 12 h urakan päättymisvuorokauden jälkeenkin.
                              WHERE (((u.loppupvm + interval '36 hour') >= NOW() AND (u.alkupvm + interval '36 hour') <= NOW()) OR
                                    (u.loppupvm IS NULL AND u.alkupvm <= NOW())))" ytunnus-str))]
    idt))

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
        y-tunnus "1565583-5"
        vastaus (api-tyokalut/get-kutsu [(str "/api/ilmoitukset/" y-tunnus "/" kuukausi-sitten "/"huomenna)]
                  kayttaja portti)]
    (is (= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Ilmoittaja"))
    (is (str/includes? (:body vastaus) "Rovanieminen"))
    (is (str/includes? (:body vastaus) "Sillalla on lunta. Liikaa."))))

(deftest hae-ilmoitukset-ytunnuksella-onnistuu-ilman-loppuaikaa
  (let [alkuaika "2022-01-01T00:00:00+03"
        y-tunnus "1565583-5"
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
(deftest hae-ilmoitukset-ytunnuksella-epaonnistuu-ei-kayttoikeutta
  (let [alkuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        loppuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        y-tunnus "1234567-8"
        vastaus (api-tyokalut/get-kutsu [(str "/api/ilmoitukset/" y-tunnus "/" alkuaika "/" loppuaika)]
                  kayttaja portti)
        odotettu-vastaus-json "{\"virheet\":[{\"virhe\":{\"koodi\":\"kayttajalla-puutteelliset-oikeudet\",\"viesti\":\"Käyttäjällä: yit-rakennus ei ole oikeuksia organisaatioon: 1234567-8\"}}]}"]
    (is (= odotettu-vastaus-json (:body vastaus)))))

(deftest hae-ilmoitukset-ytunnuksella-epaonnistuu-vaarat-hakuparametrit
  (testing "Alkuaika on väärässä muodossa "
    (let [alkuaika (.format (SimpleDateFormat. "YY-MM-d'T'HH:mm:ssX") (Date.))
          loppuaika (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
          y-tunnus "1565583-5"
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


