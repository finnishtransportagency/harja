(ns harja.palvelin.integraatiot.api.ilmoitusten-haku-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.jms :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z])
  (:import (java.net URLEncoder)
           (java.text SimpleDateFormat)
           (java.util Date)))

(def kayttaja "jvh")

(def +kuittausjono+ "tloik-ilmoituskuittausjono")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki :klusterin-tapahtumat])
    :sonja (feikki-sonja)
    :tloik (component/using
            (luo-tloik-komponentti)
             [:db :sonja :integraatioloki :klusterin-tapahtumat])))

(use-fixtures :once jarjestelma-fixture)

(def odotettu-ilmoitus
  {"ilmoittaja"
                        {"sukunimi"           "Meikäläinen",
                         "etunimi"            "Matti",
                         "matkapuhelinnumero" "08023394852",
                         "tyopuhelinnumero"   nil,
                         "email"              "matti.meikalainen@palvelu.fi"},
   "ilmoitustyyppi"     "toimenpidepyynto",
   "otsikko"            "Korkeat vallit",
   "yhteydenottopyynto" false,
   "sijainti"           {"koordinaatit" {"x" 452935.0, "y" 7186873.0}},
   "lahettaja"
                        {"etunimi"            "Pekka",
                         "sukunimi"           "Päivystäjä",
                         "matkapuhelinnumero" nil,
                         "tyopuhelinnumero"   nil,
                         "email"              "pekka.paivystaja@livi.fi"},
   "ilmoitettu"         "2015-09-29T11:49:45Z",
   "ilmoitusid"         123456789,
   "selitteet"
                        [{"selite" "auraustarve"} {"selite" "aurausvallitNakemaesteena"}],
   "tienumero"          4,
   "lyhytselite"        "Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti.",
   "pitkaselite"        "Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti."})

(deftest kuuntele-urakan-ilmoituksia
  (let [vastaus (future (api-tyokalut/get-kutsu ["/api/urakat/4/ilmoitukset"] kayttaja portti))
        tloik-kuittaukset (atom [])]
    (sonja/laheta (:sonja jarjestelma) +tloik-ilmoitusviestijono+ +testi-ilmoitus-sanoma+)
    (sonja/kuuntele (:sonja jarjestelma) +kuittausjono+ #(swap! tloik-kuittaukset conj (.getText %)))

    (odota #(not (nil? @vastaus)) "Saatiin vastaus ilmoitushakuun." 10000)
    (is (= 200 (:status @vastaus)))

    (let [vastausdata (cheshire/decode (:body @vastaus))
          ilmoitus (get (first (get vastausdata "ilmoitukset")) "ilmoitus")]
      (is (= 1 (count (get vastausdata "ilmoitukset"))))
      (is (= odotettu-ilmoitus ilmoitus)))

    (odota #(= 1 (count @tloik-kuittaukset)) "Kuittaus on vastaanotettu." 10000)

    (let [xml (first @tloik-kuittaukset)
          data (xml/lue xml)]
      (is (= "valitetty" (z/xml1-> data :kuittaustyyppi z/text))))))

(deftest hae-muuttuneet-ilmoitukset
  (u (str "UPDATE ilmoitus SET muokattu = NOW() + INTERVAL '1 hour' WHERE urakka = 4 AND id IN (SELECT id FROM ilmoitus WHERE urakka = 4 LIMIT 1)"))

  (let [nyt (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/4/ilmoitukset?muuttunutJalkeen=" (URLEncoder/encode nyt)]
                                        kayttaja portti)
        kaikkien-ilmoitusten-maara-suoraan-kannasta (ffirst (q (str "SELECT count(*) FROM ilmoitus where urakka = 4;")))]
    (is (= 200 (:status vastaus)))

    (let [vastausdata (cheshire/decode (:body vastaus))
          ilmoitukset (get vastausdata "ilmoitukset")
          ilmoituksia (count ilmoitukset)]
      (is (> kaikkien-ilmoitusten-maara-suoraan-kannasta ilmoituksia))
      (is (< 0 ilmoituksia)))))
