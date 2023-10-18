(ns harja.palvelin.integraatiot.velho.varusteet-test
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
            [harja.palvelin.integraatiot.velho.yhteiset-test :as yhteiset-test]
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

(def +velho-nimikkeisto-url+ (re-pattern (str +velho-api-juuri+ "/metatietopalvelu/api/v2/metatiedot/kohdeluokka/[^/]+/[^/]+")))
(def +tienvarsikalusteet-nimikkeisto-url+ (str +velho-api-juuri+ "/metatietopalvelu/api/v2/metatiedot/kohdeluokka/varusteet/tienvarsikalusteet"))

(def +velho-varusteet-hakurajapinta-url+ (re-pattern (str +velho-api-juuri+ "/hakupalvelu/api/v1/haku/kohdeluokat")))

(def +urakan-velho-oid+ "urakan-velho-oid")

(def +tienvarsikaluste-oid+ "1.2.345.678.9.0.12.345.678901234")

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

(defn testidatan-lisays-fixture [testit]
  (i "INSERT INTO velho_nimikkeisto (versio, tyyppi_avain, kohdeluokka, nimiavaruus, nimi, otsikko) VALUES (1, 'tienvarsikalustetyyppi', 'tienvarsikalusteet', 'varusteet', 'tvkttest', 'Testikalustetyyppi'), (1, 'kuntoluokka', '', 'kohdeluokka', 'kltest', 'Testikuntoluokka'), (1, 'varustetoimenpide', '', 'varustetoimenpide', 'vtptest', 'Testivarustetoimenpide')")
  (testit)
  (u "DELETE FROM velho_nimikkeisto WHERE nimi ILIKE '%test'"))

(use-fixtures :each
  jarjestelma-fixture
  testidatan-lisays-fixture)

(deftest tuo-velho-nimikkeisto-test
  (testing "Velhon nimikkeistön tuonti onnistuu"
    (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                     +tienvarsikalusteet-nimikkeisto-url+ (slurp "test/resurssit/velho/varusteet/metatietopalvelu/kohdeluokka.json")
                     +velho-nimikkeisto-url+ "{}"]
      (u "DELETE FROM velho_nimikkeisto")
      (varusteet/tuo-velho-nimikkeisto (:velho-integraatio jarjestelma))

      (let [nimikkeisto (q-map "SELECT versio, tyyppi_avain, kohdeluokka, nimiavaruus, nimi, otsikko FROM velho_nimikkeisto")
            odotettu-nimikkeisto [{:versio 1 :tyyppi_avain "tienvarsikalustetyyppi" :kohdeluokka "tienvarsikalusteet" :nimiavaruus "varusteet" :nimi "tvkt1234" :otsikko "Eka kalustetyyppi"}
                                  {:versio 1 :tyyppi_avain "tienvarsikalustetyyppi" :kohdeluokka "tienvarsikalusteet" :nimiavaruus "varusteet" :nimi "tvkt4321" :otsikko "Toka kalustetyyppi"}
                                  {:versio 1 :tyyppi_avain "kuntoluokka" :kohdeluokka "" :nimiavaruus "kuntoluokka" :nimi "kl1234" :otsikko "Eka otsikko"}
                                  {:versio 1 :tyyppi_avain "kuntoluokka" :kohdeluokka "" :nimiavaruus "kuntoluokka" :nimi "kl4321" :otsikko "Toka otsikko"}
                                  {:versio 1 :tyyppi_avain "varustetoimenpide" :kohdeluokka "" :nimiavaruus "varustetoimenpide" :nimi "vtp1234" :otsikko "Eka varustetoimenpide"}
                                  {:versio 1 :tyyppi_avain "varustetoimenpide" :kohdeluokka "" :nimiavaruus "varustetoimenpide" :nimi "vtp4321" :otsikko "Toka varustetoimenpide"}]]
        (is (= nimikkeisto odotettu-nimikkeisto)) "Mock-datasta ei saatu luettua nimikkeistöä oikein!")))

  (testing "Velhon nimikkeistön tuonti epäonnistuu oikein"
    (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                     +tienvarsikalusteet-nimikkeisto-url+ :deny
                     +velho-nimikkeisto-url+ "{}"]
      (is (thrown? Error
            (varusteet/tuo-velho-nimikkeisto (:velho-integraatio jarjestelma)))))))

(def odotettu-varuste
  {:alkupvm #inst "2022-10-15T00:00:00.000000000-00:00"
   :kohdeluokka "tienvarsikalusteet"
   :kuntoluokka "Testikuntoluokka"
   :lisatieto nil
   :loppupvm nil
   :muokattu nil
   :muokkaaja "MUOKKAAJA"
   :sijainti (PGgeometry. "POINT(6839198.670452601 638694.7440636739)")
   :toimenpide "Lisätty"
   :tr-alkuetaisyys 101
   :tr-alkuosa 1
   :tr-loppuetaisyys nil
   :tr-loppuosa nil
   :tr-numero 1
   :tyyppi "Testikalustetyyppi"
   :ulkoinen-oid "1.2.345.678.9.0.12.345.678901234"})

(deftest hae-varusteen-historia-test
  (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                   (str +velho-api-juuri+ "/varusterekisteri/api/v1/historia/kohde/" +tienvarsikaluste-oid+) (slurp "test/resurssit/velho/varusteet/varusteen-historia.json")]
    (let [vastaus (varusteet/hae-varusteen-historia (:velho-integraatio jarjestelma) {:ulkoinen-oid +tienvarsikaluste-oid+
                                                                                      :kohdeluokka "tienvarsikalusteet"})]
      (is (= 2 (count vastaus)))
      (is (apply = (map #(dissoc % :alkupvm :loppupvm :tr-alkuetaisyys) vastaus)))
      (is (= odotettu-varuste (second vastaus))))))

(deftest hae-urakan-varustetoteumat-test
  (with-fake-http [{:url +velho-token-url+ :method :post} yhteiset-test/fake-token-palvelin
                   +velho-varusteet-hakurajapinta-url+ (slurp "test/resurssit/velho/varusteet/varusteiden-hakurajapinta-vastaus.json")]
    (with-redefs [harja.kyselyt.urakat/hae-urakan-velho-oid (constantly +urakan-velho-oid+)]
      (let [vastaus (varusteet/hae-urakan-varustetoteumat (:velho-integraatio jarjestelma) {:urakka-id 123
                                                                                            :hoitokauden-alkuvuosi 2020})]
        (is (= 1 (count (:toteumat vastaus))))
        (is (= odotettu-varuste (first (:toteumat vastaus))))))))
