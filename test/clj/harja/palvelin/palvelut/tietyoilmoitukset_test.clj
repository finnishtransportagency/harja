(ns harja.palvelin.palvelut.tietyoilmoitukset-test
  (:require [clojure.test :refer :all]
            [harja.domain.tieliikenneilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tietyoilmoitukset :as tietyoilmoitukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [taoensso.timbre :as log]
            [harja.domain.tietyoilmoitukset :as t]
            [harja.palvelin.palvelut.tietyoilmoitukset.pdf :as t-pdf]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.muokkaustiedot :as m]
            [specql.core :refer [fetch]]
            [harja.kyselyt.tietyoilmoitukset :as q]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.fim :as fim]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s])
  (:use org.httpkit.fake))

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
                        :tietyoilmoitukset (component/using
                                             (tietyoilmoitukset/->Tietyoilmoitukset)
                                             [:http-palvelin :db :fim])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-ilmoituksia
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
   ::m/luoja-id 2,
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
                                        ilm-ennen)
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
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-yllapitokohteen-tiedot-tietyoilmoitukselle
                                  +kayttaja-jvh+
                                  yllapitokohde-id)]

      (is (s/valid? ::t/hae-yllapitokohteen-tiedot-tietyoilmoitukselle-vastaus vastaus)))))

(deftest hae-tietyoilmoitus
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tietyoilmoitus
                                +kayttaja-jvh+
                                1)]

    (is (s/valid? ::t/ilmoitus vastaus))))

(defn poimi [predikaatti tiedot]
  (let [osuma (atom nil)
        edellinen (atom nil)]
    (clojure.walk/postwalk (fn [x]
                             (when (predikaatti x) (reset! osuma x)) x) tiedot)
    @osuma))

(deftest pdf-kentat
  (let [pdf-puu-str (-> mock-ilmoitus t-pdf/tietyoilmoitus-pdf str)]
    (is (sisaltaa "alkaa" "2017"))))



;; TODO Lisää testit:
;; :hae-urakan-tiedot-tietyoilmoitukselle
