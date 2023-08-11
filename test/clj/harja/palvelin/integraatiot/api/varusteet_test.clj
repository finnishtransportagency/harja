(ns harja.palvelin.integraatiot.api.varusteet-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.varusteet :as varusteet]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [clojure.java.io :as io]
            [org.httpkit.fake :refer [with-fake-http]]))

(def kayttaja "jvh")
(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :tierekisteri (component/using (tierekisteri/->Tierekisteri +testi-tierekisteri-url+ nil) [:db :integraatioloki])
    :api-varusteet (component/using
                     (varusteet/->Varusteet)
                     [:http-palvelin :db :integraatioloki :tierekisteri])))

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
        ;(log/debug "Vastaus saatiin: " (pr-str vastaus))
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
