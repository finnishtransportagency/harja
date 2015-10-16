(ns harja.palvelin.integraatiot.api.varusteet-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.varusteet :as varusteet]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [clojure.java.io :as io]
            [cheshire.core :as cheshire])
  (:use org.httpkit.fake))

(def kayttaja "jvh")
(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :tierekisteri (component/using (tierekisteri/->Tierekisteri +testi-tierekisteri-url+) [:db :integraatioloki])
    :api-varusteet (component/using
                     (varusteet/->Varusteet)
                     [:http-palvelin :db :integraatioloki :tierekisteri])))

(use-fixtures :once (compose-fixtures tietokanta-fixture
                                      jarjestelma-fixture))

(defn tarkista-validi-ja-virheelinen-kutsu [vastaus-resurssi tierekisteri-resurssi validi-kutsu virheellinen-kutsu oletettu-vastaus]
  (let [vastaus-xml (slurp (io/resource vastaus-resurssi))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ tierekisteri-resurssi) vastaus-xml
       (str "http://localhost:" portti validi-kutsu) :allow
       (str "http://localhost:" portti virheellinen-kutsu) :allow]
      (let [vastaus (api-tyokalut/get-kutsu [validi-kutsu] kayttaja portti)]
        (is (= 200 (:status vastaus)) "Haku onnistui validilla kutsulla"))
      (let [vastaus (api-tyokalut/get-kutsu [virheellinen-kutsu] kayttaja portti)]
        (is (= 400 (:status vastaus)) "Haku puutteellisilla parametreillä palauttaa virheen")
        (println (:body vastaus))
        (is (.contains (:body vastaus) oletettu-vastaus) "Vastaus sisältää oikean virheilmoitukset")))))

(deftest tarkista-tietolajin-haku
  (let [vastaus-xml "xsd/tierekisteri/examples/hae-tietolaji-response.xml"
        validi-kutsu "/api/varusteet/tietolaji?tunniste=tl506"
        virheellinen-kutsu "/api/varusteet/tietolaji"
        tierekisteri-resurssi "/haetietolajit"
        oletettu-vastaus "Tietolajia ei voi hakea ilman tunnistetta. (URL-parametri: tunniste)"]
    (tarkista-validi-ja-virheelinen-kutsu vastaus-xml tierekisteri-resurssi validi-kutsu virheellinen-kutsu oletettu-vastaus)))

(deftest tarkista-tietueiden-haku
  (let [vastaus-xml "xsd/tierekisteri/examples/hae-tietueet-response.xml"
        validi-kutsu "/api/varusteet/varusteet?numero=3002&aet=2295&aosa=5&ajr=0&let=1&puoli=1&voimassaolopvm=2014-11-08&tietolajitunniste=tl506&losa=1"
        virheellinen-kutsu "/api/varusteet/varusteet"
        tierekisteri-resurssi "/haetietueet"
        oletettu-vastaus "Tietueita ei voi hakea ilman tietolajitunnistetta (URL-parametri: tietolajitunniste)"]
    (tarkista-validi-ja-virheelinen-kutsu vastaus-xml tierekisteri-resurssi validi-kutsu virheellinen-kutsu oletettu-vastaus)))

(deftest tarkista-tietueen-haku
  (let [vastaus-xml "xsd/tierekisteri/examples/hae-tietue-response.xml"
        validi-kutsu "/api/varusteet/varuste?tunniste=Livi956991&tietolajitunniste=tl506"
        virheellinen-kutsu "/api/varusteet/varuste"
        tierekisteri-resurssi "/haetietue"
        oletettu-vastaus "Tietuetta ei voi hakea ilman livi-tunnistetta (URL-parametri: tunniste)"]
    (tarkista-validi-ja-virheelinen-kutsu vastaus-xml tierekisteri-resurssi validi-kutsu virheellinen-kutsu oletettu-vastaus)))

(deftest tarkista-usean-tietuen-palautuminen
  (let [vastaus-xml "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<ns2:vastaus xmlns:ns2=\"http://www.solita.fi/harja/tierekisteri/vastaus\">\n    <ns2:status>OK</ns2:status>\n    <ns2:tietueet>\n        <ns2:tietue>\n            <tunniste>1245rgfsd</tunniste>\n            <alkupvm>2015-03-03+02:00</alkupvm>\n            <loppupvm>2015-03-03+02:00</loppupvm>\n            <karttapvm>2015-03-03+02:00</karttapvm>\n            <piiri>1</piiri>\n            <kuntoluokka>1</kuntoluokka>\n            <urakka>100</urakka>\n            <sijainti>\n                <koordinaatit>\n                    <x>0</x>\n                    <y>0</y>\n                    <z>0</z>\n                </koordinaatit>\n                <linkki>\n                    <id>1</id>\n                    <marvo>10</marvo>\n                </linkki>\n                <tie>\n                    <numero>1</numero>\n                    <aet>1</aet>\n                    <aosa>1</aosa>\n                    <let>1</let>\n                    <losa>1</losa>\n                    <ajr>1</ajr>\n                    <puoli>1</puoli>\n                </tie>\n            </sijainti>\n            <tietolaji>\n                <tietolajitunniste>tl506</tietolajitunniste>\n                <arvot>9987 2 2 0 1 0 1 1 Testiliikennemerkki Omistaja O K 123456789 40</arvot>\n            </tietolaji>\n        </ns2:tietue>\n        <ns2:tietue>\n            <tunniste>1245rgfsd</tunniste>\n            <alkupvm>2015-03-03+02:00</alkupvm>\n            <loppupvm>2015-03-03+02:00</loppupvm>\n            <karttapvm>2015-03-03+02:00</karttapvm>\n            <piiri>1</piiri>\n            <kuntoluokka>1</kuntoluokka>\n            <urakka>100</urakka>\n            <sijainti>\n                <koordinaatit>\n                    <x>0</x>\n                    <y>0</y>\n                    <z>0</z>\n                </koordinaatit>\n                <linkki>\n                    <id>1</id>\n                    <marvo>10</marvo>\n                </linkki>\n                <tie>\n                    <numero>1</numero>\n                    <aet>1</aet>\n                    <aosa>1</aosa>\n                    <let>1</let>\n                    <losa>1</losa>\n                    <ajr>1</ajr>\n                    <puoli>1</puoli>\n                </tie>\n            </sijainti>\n            <tietolaji>\n                <tietolajitunniste>tl506</tietolajitunniste>\n                <arvot>9987 2 2 0 1 0 1 1 Testiliikennemerkki Omistaja O K 123456789 40</arvot>\n            </tietolaji>\n        </ns2:tietue>\n    </ns2:tietueet>\n</ns2:vastaus>\n"
        kutsu "/api/varusteet/varuste?tunniste=Livi956991&tietolajitunniste=tl506"]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietue") vastaus-xml
       (str "http://localhost:" portti kutsu) :allow]
      (let [vastaus (api-tyokalut/get-kutsu [kutsu] kayttaja portti)
            vastauksen-data (cheshire/decode (:body vastaus))]
        (is (= 200 (:status vastaus)) "Haku onnistui")
        (is (= 2 (count (get vastauksen-data "varusteet"))) "Kutsu palautti oikein 2 varustetta")))))

(deftest tarkista-tietueen-lisaaminen
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/examples/ok-vastaus-response.xml"))
        kutsu "/api/varusteet/varuste"
        kutsu-data (slurp (io/resource "api/examples/varusteen-lisays-request.json"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/lisaatietue") vastaus-xml
       (str "http://localhost:" portti kutsu) :allow]
      (let [vastaus (api-tyokalut/post-kutsu [kutsu] kayttaja portti kutsu-data)]
        (is (= 200 (:status vastaus)) "Tietueen lisäys onnistui")
        (is (.contains (:body vastaus) "Uusi varuste lisätty onnistuneesti tunnisteella:"))))))

(deftest tarkista-tietueen-paivittaminen
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/examples/ok-vastaus-response.xml"))
        kutsu "/api/varusteet/varuste"
        kutsu-data (slurp (io/resource "api/examples/varusteen-paivitys-request.json"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/paivitatietue") vastaus-xml
       (str "http://localhost:" portti kutsu) :allow]
      (let [vastaus (api-tyokalut/put-kutsu [kutsu] kayttaja portti kutsu-data)]
        (is (= 200 (:status vastaus)) "Tietueen paivitys onnistui")))))

(deftest tarkista-tietueen-poistaminen
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/examples/ok-vastaus-response.xml"))
        kutsu "/api/varusteet/varuste"
        kutsu-data (slurp (io/resource "api/examples/varusteen-poisto-request.json"))]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/poistatietue") vastaus-xml
       (str "http://localhost:" portti kutsu) :allow]
      (let [vastaus (api-tyokalut/delete-kutsu [kutsu] kayttaja portti kutsu-data)]
        (is (= 200 (:status vastaus)) "Tietueen poisto onnistui")))))