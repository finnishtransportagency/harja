(ns harja.palvelin.integraatiot.api.varusteet-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.varusteet :as varusteet]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [clojure.java.io :as io]
            [cheshire.core :as cheshire]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]))

(def kayttaja "jvh")
(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :tierekisteri (component/using (tierekisteri/->Tierekisteri +testi-tierekisteri-url+ nil) [:db :integraatioloki :pois-kytketyt-ominaisuudet])
    :api-varusteet (component/using
                     (varusteet/->Varusteet)
                     [:http-palvelin :db :integraatioloki :tierekisteri :pois-kytketyt-ominaisuudet])))

(use-fixtures :once (compose-fixtures tietokanta-fixture
                                      jarjestelma-fixture))

(deftest tarkista-tietolajin-haku
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
        validi-kutsu "/api/varusteet/tietolaji?tunniste=tl506"
        tierekisteri-resurssi "/haetietolaji"]
    (with-fake-http
      [(str +testi-tierekisteri-url+ tierekisteri-resurssi) vastaus-xml
       (str "http://localhost:" portti validi-kutsu) :allow]
      (let [vastaus (api-tyokalut/get-kutsu [validi-kutsu] kayttaja portti)]
        (log/debug "Vastaus saatiin: " (pr-str vastaus))
        (is (= 200 (:status vastaus)) "Haku onnistui validilla kutsulla")))))

(deftest tarkista-tietolajin-virheellinen-haku
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
        virheellinen-kutsu "/api/varusteet/tietolaji?tunniste=666"
        tierekisteri-resurssi "/haetietolaji"
        oletettu-vastaus "Tietolajia ei voida hakea. Tuntematon tietolaji: 666"]
    (with-fake-http
      [(str +testi-tierekisteri-url+ tierekisteri-resurssi) vastaus-xml
       (str "http://localhost:" portti virheellinen-kutsu) :allow]
      (let [vastaus (api-tyokalut/get-kutsu [virheellinen-kutsu] kayttaja portti)]
        (is (= 400 (:status vastaus)) "Haku tuntemattomalla tietolajilla palauttaa virheen")
        (is (.contains (:body vastaus) oletettu-vastaus) "Vastaus sisältää oikean virheilmoitukset")))))

(deftest tarkista-tietueiden-haku
  (let [hae-tietolaji-vastaus (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
        vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietueet-response.xml"))
        validi-kutsu "/api/varusteet/haku?numero=3002&aet=2295&aosa=5&ajr=0&let=1&puoli=1&voimassaolopvm=2014-11-08&tilannepvm=2014-11-08&tietolajitunniste=tl506&losa=1"
        tierekisteri-resurssi "/haetietueet"]
    (with-fake-http
      [(str +testi-tierekisteri-url+ tierekisteri-resurssi) vastaus-xml
       (str "http://localhost:" portti validi-kutsu) :allow
       (str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-vastaus]
      (let [vastaus (api-tyokalut/get-kutsu [validi-kutsu] kayttaja portti)]
        (is (= 200 (:status vastaus)) "Haku onnistui validilla kutsulla")))))

(deftest tarkista-tietueiden-virheellinen-haku
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietueet-response.xml"))
        virheellinen-kutsu "/api/varusteet/haku"
        tierekisteri-resurssi "/haetietueet"
        oletettu-vastaus "Pakollista parametria: tietolajitunniste ei ole annettu"]
    (with-fake-http
      [(str +testi-tierekisteri-url+ tierekisteri-resurssi) vastaus-xml
       (str "http://localhost:" portti virheellinen-kutsu) :allow]
      (let [vastaus (api-tyokalut/get-kutsu [virheellinen-kutsu] kayttaja portti)]
        (is (= 400 (:status vastaus)) "Haku puutteellisilla parametreillä palauttaa virheen")
        (is (.contains (:body vastaus) oletettu-vastaus) "Vastaus sisältää oikean virheilmoitukset")))))

(deftest tarkista-tietueen-haku
  (let [hae-tietolaji-vastaus (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
        vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietue-response.xml"))
        validi-kutsu "/api/varusteet/varuste?tunniste=Livi956991&tietolajitunniste=tl506&tilannepvm=2014-11-08"
        tierekisteri-resurssi "/haetietue"]
    (with-fake-http
      [(str +testi-tierekisteri-url+ tierekisteri-resurssi) vastaus-xml
       (str "http://localhost:" portti validi-kutsu) :allow
       (str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-vastaus]
      (let [vastaus (api-tyokalut/get-kutsu [validi-kutsu] kayttaja portti)]
        (is (= 200 (:status vastaus)) "Haku onnistui validilla kutsulla")
        (is (= (count (get (json/read-str (:body vastaus)) "varusteet")) 1))))))

(deftest tarkista-tietueen-virheellinen-haku
  (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietue-response.xml"))
        virheellinen-kutsu "/api/varusteet/varuste"
        tierekisteri-resurssi "/haetietue"
        oletettu-vastaus "Pakollista parametria: tunniste ei ole annettu"]
    (with-fake-http
      [(str +testi-tierekisteri-url+ tierekisteri-resurssi) vastaus-xml
       (str "http://localhost:" portti virheellinen-kutsu) :allow]
      (let [vastaus (api-tyokalut/get-kutsu [virheellinen-kutsu] kayttaja portti)]
        (is (= 400 (:status vastaus)) "Haku puutteellisilla parametreillä palauttaa virheen")
        (is (.contains (:body vastaus) oletettu-vastaus) "Vastaus sisältää oikean virheilmoitukset")))))

(deftest tarkista-usean-tietueen-palautuminen
    (let [hae-tietolaji-vastaus (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
          vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietue-response.xml"))
          kutsu "/api/varusteet/varuste?tunniste=Livi956991&tietolajitunniste=tl506&tilannepvm=2014-11-08"]
      (with-fake-http
        [(str +testi-tierekisteri-url+ "/haetietue") vastaus-xml
         (str "http://localhost:" portti kutsu) :allow
         (str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-vastaus]
        (let [vastaus (api-tyokalut/get-kutsu [kutsu] kayttaja portti)
              vastauksen-data (cheshire/decode (:body vastaus))]
          (is (= 200 (:status vastaus)) "Haku onnistui")
          (is (= 1 (count (get vastauksen-data "varusteet"))) "Kutsu palautti oikein yhden tietueen")))))

(deftest tarkista-tietueen-lisaaminen
    (let [hae-tietolaji-vastaus (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
          vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
          kutsu "/api/varusteet/varuste"
          kutsu-data (slurp (io/resource "api/examples/varusteen-lisays-request.json"))]
      (with-fake-http
        [(str +testi-tierekisteri-url+ "/lisaatietue") vastaus-xml
         (str "http://localhost:" portti kutsu) :allow
         (str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-vastaus]
        (let [vastaus (api-tyokalut/post-kutsu [kutsu] kayttaja portti kutsu-data)]
          (is (= 200 (:status vastaus)) "Tietueen lisäys onnistui")
          (is (.contains (:body vastaus) "Uusi varuste lisätty onnistuneesti tunnisteella:"))))))

(deftest tarkista-tietueen-paivittaminen
    (let [hae-tietolaji-vastaus (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
          vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
          kutsu "/api/varusteet/varuste"
          kutsu-data (slurp (io/resource "api/examples/varusteen-paivitys-request.json"))]
      (with-fake-http
        [(str +testi-tierekisteri-url+ "/paivitatietue") vastaus-xml
         (str "http://localhost:" portti kutsu) :allow
         (str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-vastaus]
        (let [vastaus (api-tyokalut/put-kutsu [kutsu] kayttaja portti kutsu-data)]
          (is (= 200 (:status vastaus)) "Tietueen paivitys onnistui")))))

(deftest tarkista-tietueen-poistaminen
    (let [vastaus-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
          kutsu "/api/varusteet/varuste"
          kutsu-data (slurp (io/resource "api/examples/varusteen-poisto-request.json"))]
      (with-fake-http
        [(str +testi-tierekisteri-url+ "/poistatietue") vastaus-xml
         (str "http://localhost:" portti kutsu) :allow]
        (let [vastaus (api-tyokalut/delete-kutsu [kutsu] kayttaja portti kutsu-data)]
          (is (= 200 (:status vastaus)) "Tietueen poisto onnistui")))))
