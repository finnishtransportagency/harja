(ns harja.palvelin.integraatiot.velho.varusteet-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-time.coerce :refer [to-date-time]]
            [ring.util.codec :refer [form-decode]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]
            [harja.palvelin.integraatiot.velho.yhteiset-test :as yhteiset-test]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.test :refer :all])
  (:import (org.postgis PGgeometry)))

(def kayttaja "jvh")

(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")
(def +velho-token-url+ "http://localhost:1234/token")

(def +velho-api-juuri+ "http://localhost:1234")

(def +velho-urakka-oid-url+ (str +velho-api-juuri+ "/hallintorekisteri/api/v1/tunnisteet/urakka/maanteiden-hoitourakka"))
(def +velho-urakka-kohde-url+ (str +velho-api-juuri+ "hallintorekisteri/api/v1/kohteet"))

(def +velho-toimenpiteet-oid-url+ (re-pattern (str +velho-api-juuri+ "/toimenpiderekisteri/api/v1/tunnisteet/[^/]+/[^/]+")))
(def +velho-toimenpiteet-kohde-url+ (re-pattern (str +velho-api-juuri+ "/toimenpiderekisteri/api/v1/historia/kohteet")))

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :velho-integraatio (component/using
                         (velho-integraatio/->Velho {:paallystetoteuma-url +velho-paallystystoteumat-url+
                                                     :token-url +velho-token-url+
                                                     :kayttajatunnus "abc-123"
                                                     :salasana "blabla"
                                                     :varuste-api-juuri-url +velho-api-juuri+
                                                     :varuste-urakka-oid-url +velho-urakka-oid-url+
                                                     :varuste-urakka-kohteet-url +velho-urakka-kohde-url+
                                                     :varuste-toimenpiteet-oid-url +velho-toimenpiteet-oid-url+
                                                     :varuste-toimenpiteet-kohteet-url +velho-toimenpiteet-kohde-url+
                                                     :varuste-client-id "feffefef"
                                                     :varuste-client-secret "puppua"})
                         [:db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

































































