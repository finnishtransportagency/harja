(ns harja.views.ilmoitukset.tietyoilmoituslomake-test
  (:require [tuck.core :refer [tuck]]
            [harja.pvm :as pvm]
            [cljs.test :as t :refer-macros [deftest is]]
            [reagent.core :as r]
            [clojure.string :as str]
            [harja.ui.grid :as g]
            [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu jvh-fixture]]
            [harja.testutils.shared-testutils :as u]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-tiedot]
            [harja.views.ilmoitukset.tietyoilmoituslomake :as tietyoilmoituslomake-view]
            #_[harja.views.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-view]
            )
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]
                   [cljs.core.async.macros :refer [go]]))

(t/use-fixtures :each u/komponentti-fixture fake-palvelut-fixture jvh-fixture)

(def mock-ilmoitus {:harja.domain.tietyoilmoitukset/kaistajarjestelyt
               {:harja.domain.tietyoilmoitukset/jarjestely "ajokaistaSuljettu"},
               :harja.domain.tietyoilmoitukset/loppusijainnin-kuvaus
               "Jossain Kiimingissä",
               :harja.domain.tietyoilmoitukset/viivastys-ruuhka-aikana 30,
               :harja.domain.tietyoilmoitukset/kunnat "Oulu, Kiiminki",
               :harja.domain.tietyoilmoitukset/tien-nimi "Kuusamontie",
               :harja.domain.tietyoilmoitukset/ilmoittaja
               {:harja.domain.tietyoilmoitukset/etunimi "Uuno",
                :harja.domain.tietyoilmoitukset/sukunimi "Urakoitsija",
                :harja.domain.tietyoilmoitukset/sahkoposti "yit_pk2@example.org",
                :harja.domain.tietyoilmoitukset/matkapuhelin "43223123"},
               :harja.domain.tietyoilmoitukset/tilaajayhteyshenkilo
               {:harja.domain.tietyoilmoitukset/sukunimi "Toripolliisi",
                :harja.domain.tietyoilmoitukset/matkapuhelin "0405127232",
                :harja.domain.tietyoilmoitukset/sahkoposti
                "tero.toripolliisi@example.com",
                :harja.domain.tietyoilmoitukset/etunimi "Tero"},
               :harja.domain.tietyoilmoitukset/pysaytysten-loppu
                    (pvm/->pvm-aika  "07.07.2017 07:07"),
               :harja.domain.tietyoilmoitukset/tilaajan-nimi "Pohjois-Pohjanmaa",
               :harja.domain.tietyoilmoitukset/vaikutussuunta "molemmat",
               :harja.domain.tietyoilmoitukset/huomautukset ["avotuli"],
               :harja.domain.tietyoilmoitukset/ajoittaiset-pysatykset true,
               :harja.domain.tietyoilmoitukset/tyoajat
               [{:harja.domain.tietyoilmoitukset/alkuaika
                 {:tunnit 8 :minuutit 0}
                 :harja.domain.tietyoilmoitukset/loppuaika
                 {:tunnit 17 :minuutit 0}
                 :harja.domain.tietyoilmoitukset/paivat
                 ["maanantai" "tiistai" "keskiviikko"]}
                {:harja.domain.tietyoilmoitukset/alkuaika
                 {:tunnit 7 :minuutit 0}
                 :harja.domain.tietyoilmoitukset/loppuaika
                 {:tunnit 21 :minuutit 0}
                 :harja.domain.tietyoilmoitukset/paivat ["lauantai" "sunnuntai"]}],
               :harja.domain.tietyoilmoitukset/nopeusrajoitukset
               [{:harja.domain.tietyoilmoitukset/rajoitus "30",
                 :harja.domain.tietyoilmoitukset/matka 100}],
               :harja.domain.tietyoilmoitukset/alku
                    (pvm/->pvm-aika  "01.01.2017 01:01"),
               :harja.domain.tietyoilmoitukset/tienpinnat
               [{:harja.domain.tietyoilmoitukset/materiaali "paallystetty",
                 :harja.domain.tietyoilmoitukset/matka 100}],
               :harja.domain.tietyoilmoitukset/tilaajayhteyshenkilo-id 1,
               :harja.domain.tietyoilmoitukset/lisatietoja "Tämä on testi-ilmoitus",
               :harja.domain.tietyoilmoitukset/luotu
                    (pvm/->pvm-aika  "01.01.2017 01:01"),
               :harja.domain.tietyoilmoitukset/loppu
                    (pvm/->pvm-aika  "07.07.2017 07:07"),
               :harja.domain.tietyoilmoitukset/liikenteenohjaaja "liikennevalot",
               :harja.domain.tietyoilmoitukset/urakka-id 4,
               :harja.domain.tietyoilmoitukset/ajoittain-suljettu-tie true,
               :harja.domain.tietyoilmoitukset/alkusijainnin-kuvaus
               "Kuusamontien alussa",
               :harja.domain.tietyoilmoitukset/urakoitsijayhteyshenkilo
               {:harja.domain.tietyoilmoitukset/sahkoposti "yit_pk2@example.org",
                :harja.domain.tietyoilmoitukset/sukunimi "Urakoitsija",
                :harja.domain.tietyoilmoitukset/etunimi "Uuno",
                :harja.domain.tietyoilmoitukset/matkapuhelin "43223123"},
               :harja.domain.tietyoilmoitukset/tilaaja-id 9,
               :harja.domain.tietyoilmoitukset/liikenteenohjaus
               "ohjataanVuorotellen",
               :harja.domain.tietyoilmoitukset/tyovaiheet
               [{:harja.domain.tietyoilmoitukset/kaistajarjestelyt
                 {:harja.domain.tietyoilmoitukset/jarjestely "ajokaistaSuljettu"},
                 :harja.domain.tietyoilmoitukset/loppusijainnin-kuvaus
                 "Ylikiimingintien risteys",
                 :harja.domain.tietyoilmoitukset/viivastys-ruuhka-aikana 30,
                 :harja.domain.tietyoilmoitukset/kunnat "Oulu, Kiiminki",
                 :harja.domain.tietyoilmoitukset/tien-nimi "Kuusamontie",
                 :harja.domain.tietyoilmoitukset/ilmoittaja
                 {:harja.domain.tietyoilmoitukset/matkapuhelin "43223123",
                  :harja.domain.tietyoilmoitukset/etunimi "Uuno",
                  :harja.domain.tietyoilmoitukset/sukunimi "Urakoitsija",
                  :harja.domain.tietyoilmoitukset/sahkoposti "yit_pk2@example.org"},
                 :harja.domain.tietyoilmoitukset/tilaajayhteyshenkilo
                 {:harja.domain.tietyoilmoitukset/matkapuhelin "0405127232",
                  :harja.domain.tietyoilmoitukset/etunimi "Tero",
                  :harja.domain.tietyoilmoitukset/sahkoposti
                  "tero.toripolliisi@example.com",
                  :harja.domain.tietyoilmoitukset/sukunimi "Toripolliisi"},
                 :harja.domain.tietyoilmoitukset/pysaytysten-loppu
                 (pvm/->pvm-aika  "07.07.2017 07:07"),
                 :harja.domain.tietyoilmoitukset/tilaajan-nimi "Pohjois-Pohjanmaa",
                 :harja.domain.tietyoilmoitukset/vaikutussuunta "molemmat",
                 :harja.domain.tietyoilmoitukset/huomautukset ["avotuli"],
                 :harja.domain.tietyoilmoitukset/ajoittaiset-pysatykset true,
                 :harja.domain.tietyoilmoitukset/tyoajat
                 [{:harja.domain.tietyoilmoitukset/alkuaika
                   {:tunnit 6 :minuutit 0}
                   :harja.domain.tietyoilmoitukset/loppuaika
                   {:tunnit 18 :minuutit 15}
                   :harja.domain.tietyoilmoitukset/paivat
                   ["maanantai" "tiistai" "keskiviikko"]}
                  {:harja.domain.tietyoilmoitukset/alkuaika
                   {:tunnit 20 :minuutit 0}
                   :harja.domain.tietyoilmoitukset/loppuaika
                   {:tunnit 23 :minuutit 0}
                   :harja.domain.tietyoilmoitukset/paivat ["lauantai" "sunnuntai"]}],
                 :harja.domain.tietyoilmoitukset/nopeusrajoitukset
                 [{:harja.domain.tietyoilmoitukset/rajoitus "30",
                   :harja.domain.tietyoilmoitukset/matka 100}],
                 :harja.domain.tietyoilmoitukset/alku
                 (pvm/->pvm-aika  "01.06.2017 01:01"),
                 :harja.domain.tietyoilmoitukset/tienpinnat
                 [{:harja.domain.tietyoilmoitukset/materiaali "paallystetty",
                   :harja.domain.tietyoilmoitukset/matka 100}],
                 :harja.domain.tietyoilmoitukset/tilaajayhteyshenkilo-id 1,
                 :harja.domain.tietyoilmoitukset/lisatietoja
                 "Tämä on testi-ilmoitus",
                 :harja.domain.tietyoilmoitukset/luotu
                 (pvm/->pvm-aika  "01.01.2017 01:01"),
                 :harja.domain.tietyoilmoitukset/loppu
                 (pvm/->pvm-aika  "20.06.2017 07:07"),
                 :harja.domain.tietyoilmoitukset/liikenteenohjaaja "liikennevalot",
                 :harja.domain.tietyoilmoitukset/urakka-id 4,
                 :harja.domain.tietyoilmoitukset/ajoittain-suljettu-tie true,
                 :harja.domain.tietyoilmoitukset/alkusijainnin-kuvaus
                 "Vaalantien risteys",
                 :harja.domain.tietyoilmoitukset/urakoitsijayhteyshenkilo
                 {:harja.domain.tietyoilmoitukset/sahkoposti "yit_pk2@example.org",
                  :harja.domain.tietyoilmoitukset/etunimi "Uuno",
                  :harja.domain.tietyoilmoitukset/matkapuhelin "43223123",
                  :harja.domain.tietyoilmoitukset/sukunimi "Urakoitsija"},
                 :harja.domain.tietyoilmoitukset/tilaaja-id 9,
                 :harja.domain.tietyoilmoitukset/liikenteenohjaus
                 "ohjataanVuorotellen",
                 :harja.domain.tietyoilmoitukset/paatietyoilmoitus 1,
                 :harja.domain.tietyoilmoitukset/kiertotien-mutkaisuus
                 "loivatMutkat",
                 :harja.domain.tietyoilmoitukset/urakkatyyppi "hoito",
                 :harja.domain.tietyoilmoitukset/urakoitsijayhteyshenkilo-id 6,
                 :harja.domain.tietyoilmoitukset/viivastys-normaali-liikenteessa 15,
                 :harja.domain.tietyoilmoitukset/tyotyypit
                 [{:harja.domain.tietyoilmoitukset/tyyppi "Tienrakennus",
                   :harja.domain.tietyoilmoitukset/kuvaus "Rakennetaan tietä"}],
                 :harja.domain.tietyoilmoitukset/luoja 6,
                 :harja.domain.tietyoilmoitukset/urakoitsijan-nimi "YIT Rakennus Oy",
                 :harja.domain.tietyoilmoitukset/osoite
                 {:harja.domain.tierekisteri/aet 1,
                  :harja.domain.tierekisteri/geometria nil,
                  :harja.domain.tierekisteri/tie 20,
                  :harja.domain.tierekisteri/let 1,
                  :harja.domain.tierekisteri/aosa 3,
                  :harja.domain.tierekisteri/losa 4},
                 :harja.domain.tietyoilmoitukset/urakan-nimi
                 "Oulun alueurakka 2014-2019",
                 :harja.domain.tietyoilmoitukset/ilmoittaja-id 6,
                 :harja.domain.tietyoilmoitukset/ajoneuvorajoitukset
                 {:harja.domain.tietyoilmoitukset/max-leveys 3M,
                  :harja.domain.tietyoilmoitukset/max-korkeus 4M,
                  :harja.domain.tietyoilmoitukset/max-paino 4000M,
                  :harja.domain.tietyoilmoitukset/max-pituus 10M},
                 :harja.domain.tietyoilmoitukset/id 2,
                 :harja.domain.tietyoilmoitukset/kiertotienpinnat
                 [{:harja.domain.tietyoilmoitukset/materiaali "murske",
                   :harja.domain.tietyoilmoitukset/matka 100}],
                 :harja.domain.tietyoilmoitukset/pysaytysten-alku
                 (pvm/->pvm-aika  "01.01.2017 01:01")}],
               :harja.domain.tietyoilmoitukset/kiertotien-mutkaisuus "loivatMutkat",
               :harja.domain.tietyoilmoitukset/urakkatyyppi "hoito",
               :harja.domain.tietyoilmoitukset/urakoitsijayhteyshenkilo-id 6,
               :harja.domain.tietyoilmoitukset/viivastys-normaali-liikenteessa 15,
               :harja.domain.tietyoilmoitukset/tyotyypit
               [{:harja.domain.tietyoilmoitukset/tyyppi "Tienrakennus",
                 :harja.domain.tietyoilmoitukset/kuvaus "Rakennetaan tietä"}],
               :harja.domain.tietyoilmoitukset/luoja 6,
               :harja.domain.tietyoilmoitukset/urakoitsijan-nimi "YIT Rakennus Oy",
               :harja.domain.tietyoilmoitukset/osoite
               {:harja.domain.tierekisteri/aosa 1,
                :harja.domain.tierekisteri/geometria nil,
                :harja.domain.tierekisteri/losa 5,
                :harja.domain.tierekisteri/tie 20,
                :harja.domain.tierekisteri/let 1,
                :harja.domain.tierekisteri/aet 1},
               :harja.domain.tietyoilmoitukset/urakan-nimi "Oulun alueurakka 2014-2019",
               :harja.domain.tietyoilmoitukset/ilmoittaja-id 6,
               :harja.domain.tietyoilmoitukset/ajoneuvorajoitukset
               {:harja.domain.tietyoilmoitukset/max-korkeus 4M,
                :harja.domain.tietyoilmoitukset/max-paino 4000M,
                :harja.domain.tietyoilmoitukset/max-pituus 10M,
                :harja.domain.tietyoilmoitukset/max-leveys 3M},
               :harja.domain.tietyoilmoitukset/id 1,
               :harja.domain.tietyoilmoitukset/kiertotienpinnat
               [{:harja.domain.tietyoilmoitukset/materiaali "murske",
                 :harja.domain.tietyoilmoitukset/matka 100}],
               :harja.domain.tietyoilmoitukset/pysaytysten-alku
                    (pvm/->pvm-aika  "01.01.2017 01:01")}
  )

(defn lomake-mock-komponentti [e! app]
  (let [valittu-ilmoitus (:valittu-ilmoitus app)
        kayttajan-urakat [5]]
    [tietyoilmoituslomake-view/lomake e! false valittu-ilmoitus kayttajan-urakat]))


(defn query-selector [q]
  (js/document.querySelector q))

(defn kentan-label-loytyy [kentan-id]
  (some? (query-selector (clojure.string/replace "label[for=\"XX\"]" "XX" kentan-id))))

(deftest lomake-muodostuu
  (let [app (atom {:valittu-ilmoitus mock-ilmoitus})]
    (komponenttitesti
     [tuck app lomake-mock-komponentti]
     (is (= "Kuusamontie" (.-value (js/document.querySelector "label[for=tien-nimi] + input"))))
     #_(is (= "Tämä on testi-ilmoitus"
            (.-value (u/sel1 "label[for=lisatietoja] + input"))))
     )))
