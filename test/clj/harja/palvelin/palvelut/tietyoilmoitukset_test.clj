(ns harja.palvelin.palvelut.tietyoilmoitukset-test
  (:require [clojure.test :refer :all]
            [harja.domain.tieliikenneilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tietyoilmoitukset :as tietyoilmoitukset]
            [harja.palvelin.palvelut.tietyoilmoitukset.pdf :as tietyoilmoitukset-pdf]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [taoensso.timbre :as log]
            [harja.domain.tietyoilmoitus :as t]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.muokkaustiedot :as m]
            [specql.core :refer [fetch]]
            [harja.kyselyt.tietyoilmoitukset :as q]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.fim :as fim]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [specql.core :refer [insert!]]
            [clojure.string :as str]
            [harja.kyselyt.tietyoilmoitukset :as q-tietyoilmoitukset])
  (:use org.httpkit.fake)
  (:import (org.apache.pdfbox.text PDFTextStripper)
           (org.apache.pdfbox.pdmodel PDDocument)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :http-palvelin (testi-http-palvelin)
                        :sonja (feikki-sonja)
                        :sonja-sahkoposti (component/using
                                            (sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :tietyoilmoitukset (component/using
                                             (tietyoilmoitukset/->Tietyoilmoitukset)
                                             [:http-palvelin :db :fim :sonja-sahkoposti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

#_(deftest hae-ilmoituksia
  (let [parametrit {:alkuaika (pvm/luo-pvm 2016 1 1)
                    :loppuaika (pvm/luo-pvm 2017 3 1)
                    :urakka nil
                    :sijainti nil
                    :vain-kayttajan-luomat nil}
        tietyoilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-tietyoilmoitukset
                                          +kayttaja-jvh+
                                          parametrit)]
    (is (= 1 (count tietyoilmoitukset)) "Ilmoituksia on palautunut oikea määrä")
    (is (= 1 (count (::t/tyovaiheet (first tietyoilmoitukset)))) "Ilmoituksella on työvaiheita oikea määrä")))

(def mock-ilmoitus
  {::t/kaistajarjestelyt {::t/jarjestely "ajokaistaSuljettu"}
   ::t/loppusijainnin-kuvaus "Jossain Kiimingissä"
   ::t/viivastys-ruuhka-aikana 30
   ::t/kunnat "Oulu, Kiiminki"
   ::t/tien-nimi "Kuusamontie"
   ::t/ilmoittaja {::t/etunimi "Uuno",
                   ::t/sukunimi "Urakoitsija",
                   ::t/sahkoposti "yit_pk2@example.org",
                   ::t/matkapuhelin "43223123"}
   ::t/tilaajayhteyshenkilo {::t/sukunimi "Toripolliisi",
                             ::t/matkapuhelin "0405127232",
                             ::t/sahkoposti "tero.toripolliisi@example.com",
                             ::t/etunimi "Tero"}
   ::t/pysaytysten-loppu #inst "2017-07-07T07:07:07.000000000-00:00"
   ::t/tilaajan-nimi "Pohjois-Pohjanmaa",
   ::t/vaikutussuunta "molemmat",
   ::t/huomautukset ["avotuli"],
   ::t/ajoittaiset-pysaytykset true,
   ::t/tyoajat [{::t/alkuaika
                 (java.time.LocalTime/of 8 0)
                 ::t/loppuaika
                 (java.time.LocalTime/of 17 0)
                 ::t/paivat
                 ["maanantai" "tiistai" "keskiviikko"]}
                {::t/alkuaika
                 (java.time.LocalTime/of 7 0)
                 ::t/loppuaika
                 (java.time.LocalTime/of 21 0)
                 ::t/paivat ["lauantai" "sunnuntai"]}]
   ::t/nopeusrajoitukset [{::t/rajoitus "30",
                           ::t/matka 100}]
   ::t/alku #inst "2017-01-01T01:01:01.000000000-00:00"
   ::t/tienpinnat [{::t/materiaali "paallystetty",
                    ::t/matka 100}]
   ::t/tilaajayhteyshenkilo-id 1,
   ::t/lisatietoja "Tämä on testi-ilmoitus",
   ::t/loppu #inst "2017-07-07T07:07:07.000000000-00:00"
   ::t/liikenteenohjaaja "liikennevalot"
   ::t/urakka-id 4,
   ::t/ajoittain-suljettu-tie true,
   ::t/alkusijainnin-kuvaus "Kuusamontien alussa",
   ::t/urakoitsijayhteyshenkilo {::t/sahkoposti "yit_pk2@example.org",
                                 ::t/sukunimi "Urakoitsija",
                                 ::t/etunimi "Uuno",
                                 ::t/matkapuhelin "43223123"}
   ::t/tilaaja-id 9,
   ::t/liikenteenohjaus "ohjataanVuorotellen",
   ::t/kiertotien-mutkaisuus "loivatMutkat",
   ::t/urakkatyyppi "hoito",
   ::t/urakoitsijayhteyshenkilo-id 6,
   ::t/viivastys-normaali-liikenteessa 15,
   ::t/tyotyypit [{::t/tyyppi "Tienrakennus",
                   ::t/kuvaus "Rakennetaan tietä"}]
   ::m/luoja-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi='jvh'")),
   ::t/urakoitsijan-nimi "YIT Rakennus Oy",
   ::t/osoite {::tr/aosa 1,
               ::tr/losa 5,
               ::tr/tie 20,
               ::tr/let 1,
               ::tr/aet 1}
   ::t/urakan-nimi "Oulun alueurakka 2014-2019",
   ::t/ilmoittaja-id 6,
   ::t/ajoneuvorajoitukset {::t/max-korkeus 4.0,
                            ::t/max-paino 4000.0,
                            ::t/max-pituus 10.0,
                            ::t/max-leveys 3.0}
   ::t/kiertotienpinnat [{::t/materiaali "murske",
                          ::t/matka 100}]
   ::t/pysaytysten-alku #inst "2017-01-01T01:01:01.000000000-00:00"})

(deftest tallenna-ilmoitus
  (let [ilm-ennen mock-ilmoitus
        db (:db jarjestelma)
        lkm-ennen (count (fetch db ::t/ilmoitus #{::t/id} {}))
        ilm-tallennettu (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :tallenna-tietyoilmoitus +kayttaja-jvh+
                                        {:ilmoitus ilm-ennen
                                         :sahkopostitiedot {}})
        lkm-jalkeen (count (fetch db ::t/ilmoitus #{::t/id} {}))]
    (is (some? (::t/id ilm-tallennettu)))
    (is (= lkm-jalkeen (inc lkm-ennen)))

    (tarkista-map-arvot
      ilm-ennen
      (-> ilm-tallennettu
          (dissoc ::t/id)
          ;; FIXME: specql ei pitäisi palauttaa nil geometriaa
          (update ::t/osoite dissoc ::tr/geometria)))
    (let [ilm-haettu (first (fetch db ::t/ilmoitus q/kaikki-ilmoituksen-kentat
                                   {::t/id (::t/id ilm-tallennettu)}))]
      (tarkista-map-arvot
        (assoc-in ilm-haettu [::t/osoite ::tr/geometria] nil)
        ilm-tallennettu))))

(deftest hae-yllapitokohteen-tiedot-tietyoilmoitukselle
  (with-fake-http
    [+testi-fim+ (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))]
    (let [yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
          paallystysurakka-id (hae-muhoksen-paallystysurakan-id)
          tiemerkintaurakka-id (hae-oulun-tiemerkintaurakan-id)
          vastaus-paallystysurakka (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :hae-yllapitokohteen-tiedot-tietyoilmoitukselle
                                                   +kayttaja-jvh+
                                                   {:yllapitokohde-id yllapitokohde-id
                                                    :valittu-urakka-id paallystysurakka-id})
          vastaus-tiemerkintaurakka (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :hae-yllapitokohteen-tiedot-tietyoilmoitukselle
                                                    +kayttaja-jvh+
                                                    {:yllapitokohde-id yllapitokohde-id
                                                     :valittu-urakka-id tiemerkintaurakka-id})]
      (is (s/valid? ::t/hae-yllapitokohteen-tiedot-tietyoilmoitukselle-vastaus vastaus-paallystysurakka))
      (is (s/valid? ::t/hae-yllapitokohteen-tiedot-tietyoilmoitukselle-vastaus vastaus-tiemerkintaurakka)))))

(deftest hae-tietyoilmoitus
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tietyoilmoitus
                                +kayttaja-jvh+
                                1)]

    (is (s/valid? ::t/ilmoitus vastaus))))


(def tulostettava-tti
  {::t/kaistajarjestelyt {::t/jarjestely "ajokaistaSuljettu"},
   ::t/loppusijainnin-kuvaus "Ylihärmä, Hakola",
   ::t/pituus 2686.000000000225,
   ::t/viivastys-ruuhka-aikana 15,
   ::t/kunnat "Kauhava"
   ::t/tien-nimi "Sairaalantie / Hakola"
   ::t/ilmoittaja {::t/sahkoposti "max.syöttöpaine@example.com",
                   ::t/sukunimi "Syöttöpaine",
                   ::t/etunimi "Max"
                   ::t/matkapuhelin "1234567890"}
   ::t/tilaajayhteyshenkilo {::t/matkapuhelin "1122334455"
                             ::t/etunimi "Foo"
                             ::t/sukunimi "Barsky"}
   ::t/tilaajan-nimi "Etelä-Pohjanmaa"
   ::t/vaikutussuunta "molemmat"
   ::t/tyoajat [{::t/alkuaika (java.time.LocalTime/of 6 0)
                 ::t/loppuaika (java.time.LocalTime/of 22 0)
                 ::t/paivat ["tiistai" "maanantai" "perjantai" "keskiviikko" "torstai"]}]
   ::t/nopeusrajoitukset [{::t/rajoitus "30"
                           ::t/matka 600}]
   :harja.domain.muokkaustiedot/luotu #inst "2017-05-11T17:19:14.959000000-00:00"
   ::t/alku #inst "2017-05-14T21:00:00.000000000-00:00"
   ::t/tienpinnat [{::t/materiaali "paallystetty"}]
   ::t/loppu #inst "2017-05-15T21:00:00.000000000-00:00"
   ::t/liikenteenohjaaja "liikennevalot"
   ::t/urakka-id 212,
   :harja.domain.muokkaustiedot/luoja-id 863483,
   ::t/alkusijainnin-kuvaus "Ylihärmä, Hakola"
   ::t/urakoitsijayhteyshenkilo {::t/etunimi "Barbara",
                                 ::t/sukunimi "Jenkins",
                                 ::t/matkapuhelin "666666666"}
   ::t/liikenteenohjaus "ohjataanVuorotellen"
   ::t/viivastys-normaali-liikenteessa 2,
   ::t/tyotyypit [{::t/tyyppi "Jyrsintä-/stabilointityö"}
                  {::t/tyyppi "Päällystystyö"}]
   ::t/urakoitsijan-nimi "Testiurakoitsija Oy"
   ::t/osoite {:harja.domain.tierekisteri/losa 1
               :harja.domain.tierekisteri/let 2686
               :harja.domain.tierekisteri/aosa 1
               :harja.domain.tierekisteri/aet 0
               :harja.domain.tierekisteri/geometria (org.postgis.PGgeometry. "MULTILINESTRING((288948.7829999998 7011181.2949,288969.7690000003 7011177.4529,288977.62200000044 7011175.306899998,288987.51099999994 7011170.602899998,289008.78000000026 7011156.7009,289032.0310000004 7011131.8719,289034.2829999998 7011129.501899999,289045.8289999999 7011119.1219,289047.34800000023 7011117.8259,289054.0389999999 7011112.116900001,289061.15299999993 7011105.994899999,289070.51300000027 7011098.789900001,289085.5480000004 7011089.122900002,289093.5939999996 7011084.543900002,289106.88100000005 7011079.006900001,289120.5480000004 7011074.696899999,289132.2209999999 7011069.797899999,289157.54700000025 7011061.2249,289188.0329999998 7011051.692899998,289215.7620000001 7011042.102899998,289231.3849999998 7011036.7228999995,289245.24700000044 7011031.339899998,289262.63900000043 7011025.688900001,289272.87399999984 7011022.868900001,289283.2889999999 7011022.0009,289305.7860000003 7011022.486900002,289322.3509999998 7011024.515900001,289339.9129999997 7011027.558899999,289355.09499999974 7011029.7029,289372.2889999999 7011031.7379,289389.99899999984 7011032.1439,289396.16199999955 7011032.412900001,289408.8169999998 7011030.152899999,289422.19600000046 7011027.481899999,289437.88100000005 7011023.051899999,289442.16199999955 7011021.809900001,289455.93099999987 7011018.080899999,289469.20600000024 7011014.706900001,289472.2410000004 7011013.936900001,289485.96999999974 7011009.999899998,289504.43599999975 7011004.431899998,289519.26499999966 7010997.3389,289526.9550000001 7010992.784899998,289533.56099999975 7010988.6679,289536.1370000001 7010986.8739,289542.70799999963 7010981.056899998,289545.23199999984 7010978.278900001,289551.92899999954 7010969.221900001,289557.4759999998 7010960.067899998,289557.8080000002 7010959.379900001,289560.09300000034 7010954.5909,289562.87200000044 7010946.6329,289562.9749999996 7010938.0469,289560.9060000004 7010926.433899999,289556.73900000006 7010909.4329,289554.875 7010897.9989,289554.5750000002 7010894.979899999,289554.21999999974 7010884.387899999,289554.18599999975 7010883.082899999,289554.4000000004 7010870.7798999995,289556.4349999996 7010857.802900001,289559.14499999955 7010844.343899999,289564.12200000044 7010829.6589,289568.61899999995 7010819.1369,289574.0190000003 7010810.3539,289577.31099999975 7010805.937899999,289582.95999999996 7010799.056899998,289592.1610000003 7010788.7819,289602.6799999997 7010779.0918999985,289617.7549999999 7010767.6679,289634.73699999973 7010757.2599,289640.3099999996 7010753.960900001,289658.2620000001 7010742.766899999,289680.0089999996 7010728.194899999,289700.62200000044 7010713.513900001,289719.4179999996 7010697.9509,289737.7750000004 7010683.2819,289746.98199999984 7010676.289900001,289750.7520000003 7010673.560899999,289767.74899999984 7010662.245900001,289782.2599999998 7010654.7798999995,289785.48599999957 7010653.2359,289791.79700000025 7010650.3189,289801.95999999996 7010646.220899999,289818.7949999999 7010639.368900001,289829.5389999999 7010633.1789,289838.4450000003 7010626.9529,289853.23500000034 7010615.448899999,289866.5379999997 7010605.227899998,289887.57100000046 7010587.5579,289898.42200000025 7010578.672899999,289907.71499999985 7010571.453899998,289927.1579999998 7010557.370900001,289938.97200000007 7010549.684900001,289947.58499999996 7010544.984900001,289953.83100000024 7010541.709899999,289961.90199999977 7010538.2619,289967.375 7010536.093899999,289978.85699999984 7010530.937899999,289991.52300000004 7010525.835900001,290002.5530000003 7010522.402899999,290013.19799999986 7010518.9048999995,290026.074 7010515.486900002,290036.1210000003 7010512.545899998,290060.6730000004 7010506.670899998,290076.81599999964 7010502.1488999985,290090.76400000043 7010498.619899999,290100.41000000015 7010495.152899999,290110.9500000002 7010490.812899999,290124.15500000026 7010485.7709,290141.6900000004 7010479.126899999,290152.58100000024 7010473.776900001,290162.6749999998 7010469.911899999,290172.4740000004 7010465.766899999,290187.8300000001 7010458.946899999,290208.28699999955 7010450.056899998,290226.41500000004 7010442.777899999,290242.93400000036 7010435.629900001,290256.6500000004 7010429.7269,290273.7000000002 7010422.3299,290285.9139999999 7010415.168900002,290298.4709999999 7010404.3849,290299.95600000024 7010402.8039,290308.3700000001 7010392.149900001,290312.9620000003 7010384.545899998,290316.6799999997 7010376.048900001,290320.5889999997 7010366.315900002,290322.72200000007 7010356.332899999,290323.8770000003 7010346.9969,290323.48000000045 7010334.868900001,290322.11099999957 7010321.157900002,290318.4910000004 7010306.8299,290311.06400000025 7010291.164900001,290302.93400000036 7010277.5229,290293.70799999963 7010265.604899999,290283.2089999998 7010254.729899999,290274.15500000026 7010245.7269,290269.52300000004 7010241.1259,290268.1200000001 7010239.775899999,290256.1900000004 7010229.7919000015,290250.6339999996 7010224.3649,290237.67200000025 7010213.337900002,290227.69299999997 7010204.287900001,290218.5460000001 7010197.1329,290198.1679999996 7010177.8959,290185.426 7010166.616900001,290182.41000000015 7010163.657900002,290168.1370000001 7010150.1369,290148.3870000001 7010133.6459,290144.91899999976 7010130.991900001,290125.3669999996 7010113.4048999995,290124.7309999997 7010112.8259,290108.60500000045 7010101.440900002,290101.2479999997 7010097.008900002,290094.97300000023 7010093.4329,290077.6030000001 7010083.934900001,290060.65500000026 7010075.573899999,290039.52699999977 7010063.969900001,290025.26800000016 7010055.5978999995,290015.6639999999 7010048.611900002,290006.3169999998 7010042.3849,289995.6299999999 7010032.820900001,289987.28500000015 7010024.333900001,289984.8339999998 7010021.540899999,289976.20999999996 7010012.322900001,289965.70600000024 7010001.2929,289955.12399999984 7009990.1899,289948.4610000001 7009983.4109000005,289944.7960000001 7009979.844900001,289944.4400000004 7009979.510899998,289937.2709999997 7009972.944899999,289915.25299999956 7009952.915899999,289904.0120000001 7009942.831900001,289891.68599999975 7009931.528900001,289882.801 7009921.959899999,289881.2000000002 7009920.141899999,289871.93900000025 7009909.5759,289861.64499999955 7009896.4639,289852.3169999998 7009884.812899999,289847.4079999998 7009877.970899999,289837.5429999996 7009861.619899999,289828.3269999996 7009847.3609,289820.86199999973 7009829.1488999985,289815.2240000004 7009807.014899999,289810.51099999994 7009788.9969,289804.99700000044 7009769.9059000015,289802.29399999976 7009760.289900001,289799.0020000003 7009748.8939,289792.6540000001 7009723.907900002,289789.6229999997 7009714.812899999,289785.23000000045 7009700.413899999,289773.642 7009662.2049,289764.65699999966 7009639.058899999,289760.45299999975 7009631.956900001,289757.45023308636 7009627.676007431))")
               :harja.domain.tierekisteri/tie 17790}
   ::t/urakan-nimi "piällystys & paikkaus <3"
   ::t/ajoneuvorajoitukset {::t/max-leveys 4.0}
   ::t/paailmoitus nil})

(defn pdf-tuloste-sisaltaa-tekstia [pdf tekstit]
  (testing "PDF avattavissa ja sisältää tekstiä"
    (let [text (.getText (PDFTextStripper.)
                         (PDDocument/load (java.io.ByteArrayInputStream. pdf)))]

      (testing "PDF teksti sisältää ilmoituksen tekstejä"
        (doseq [teksti tekstit]
          (is (str/includes? text teksti)))))))

(defn pdf-tulostus-toimii [tti]
  (let [xsl-fo (tietyoilmoitukset-pdf/tietyoilmoitus-pdf tti)]
    (is (vector? xsl-fo))
    (let [pdf (luo-pdf-bytes xsl-fo)]
      (is (> (count pdf) 9000) "Luotu PDF on järkevän kokoinen")

      (with-open [out (io/output-stream "testi.pdf")]
        (.write out pdf))

      pdf)))

(deftest hardkoodattu-tti->pdf
  (let [pdf (pdf-tulostus-toimii tulostettava-tti)]
    (pdf-tuloste-sisaltaa-tekstia pdf ["Max Syöttöpaine"
                                       "piällystys & paikkaus <3"
                                       "17790"
                                       "Ylihärmä, Hakola"
                                       "11.05.2017"
                                       "2686,0 m"
                                       "Työaika maanantai, tiistai, keskiviikko, torstai, perjantai: 06:00 – 22:00"])))

(deftest testikannan-tti->pdf
  (let [tti-idt (map :id (q-map "SELECT id FROM tietyoilmoitus"))]
    (doseq [tti-id tti-idt]
      (let [pdf (pdf-tulostus-toimii (first (fetch (:db jarjestelma)
                                          ::t/ilmoitus+pituus
                                          q-tietyoilmoitukset/ilmoitus-pdf-kentat
                                          {::t/id tti-id})))]
        (pdf-tuloste-sisaltaa-tekstia pdf ["Tämä on testi-ilmoitus"])))))

(deftest hae-ilmoituksen-sahkopostilahetykset-test
  (let [db (:db jarjestelma)
        ilmoitus-ja-urakka-id (first
                                (q "SELECT ti.id, ti.\"urakka-id\"
                                    FROM tietyoilmoitus ti
                                    JOIN tietyoilmoituksen_email_lahetys tiel ON tiel.tietyoilmoitus=ti.id
                                    WHERE tiel.id IS NOT NULL;"))
        kysely-parametrit {::t/id (first ilmoitus-ja-urakka-id) ::t/urakka-id (second ilmoitus-ja-urakka-id)}
        sahkopostit (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-ilmoituksen-sahkopostitiedot +kayttaja-jvh+
                                        kysely-parametrit)]
    (is (= (count sahkopostit) 2))))

;; TODO Lisää testit:
;; :hae-urakan-tiedot-tietyoilmoitukselle
