(ns harja.palvelin.palvelut.tietyoilmoitukset-test
  (:require [clojure.test :refer :all]
            [harja.domain.ilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tietyoilmoitukset :as tietyoilmoitukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.domain.tietyoilmoitukset :as t]
            [harja.domain.tierekisteri :as tr]
            [specql.core :refer [fetch]]
            [harja.kyselyt.tietyoilmoitukset :as q]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :tietyoilmoitukset (component/using
                                             (tietyoilmoitukset/->Tietyoilmoitukset)
                                             [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-ilmoituksia
  (let [parametrit {:alkuaika (pvm/luo-pvm 2016 1 1)
                    :loppuaika (pvm/luo-pvm 2017 3 1)
                    :urakka nil
                    :sijainti nil
                    :vain-kayttajan-luomat nil}
        _ (println "KUTSUTAAN")
        tietyoilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-tietyoilmoitukset
                                          +kayttaja-jvh+
                                          parametrit)]
    (println "kutsuttu")
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
   ::t/ajoittaiset-pysatykset true,
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
   ::t/luoja 6,
   ::t/urakoitsijan-nimi "YIT Rakennus Oy",
   ::t/osoite {::tr/aosa 1,
               ::tr/geometria nil,
               ::tr/losa 5,
               ::tr/tie 20,
               ::tr/let 1,
               ::tr/aet 1}
   ::t/urakan-nimi "Oulun alueurakka 2014-2019",
   ::t/ilmoittaja-id 6,
   ::t/ajoneuvorajoitukset {::t/max-korkeus 4M,
                            ::t/max-paino 4000M,
                            ::t/max-pituus 10M,
                            ::t/max-leveys 3M}
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
    (is (= ilm-ennen (dissoc ilm-tallennettu ::t/id)))
    (let [ilm-haettu (first (fetch db ::t/ilmoitus q/kaikki-ilmoituksen-kentat
                               {::t/id (::t/id ilm-tallennettu)}))]
      (tarkista-map-arvot
       (assoc-in ilm-haettu [::t/osoite ::tr/geometria] nil)
       ilm-tallennettu))))
