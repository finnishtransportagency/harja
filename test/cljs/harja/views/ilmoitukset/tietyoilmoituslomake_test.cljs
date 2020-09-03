(ns harja.views.ilmoitukset.tietyoilmoituslomake-test
  (:require [tuck.core :refer [tuck]]
            [harja.pvm :as pvm]
            [cljs.test :as test :refer-macros [deftest is]]
            [reagent.core :as r]
            [clojure.string :as str]
            [harja.ui.grid :as g]
            [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu jvh-fixture]]
            [harja.testutils.shared-testutils :as u]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-tiedot]
            [harja.views.ilmoitukset.tietyoilmoituslomake :as tietyoilmoituslomake-view]
            [harja.domain.tietyoilmoitus :as t]
            #_[harja.views.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-view]
            )
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]
                   [cljs.core.async.macros :refer [go]]))

(test/use-fixtures :each u/komponentti-fixture fake-palvelut-fixture jvh-fixture)

(def mock-ilmoitus
  {:harja.domain.tietyoilmoitus/kaistajarjestelyt
   {:harja.domain.tietyoilmoitus/jarjestely "ajokaistaSuljettu"},
   :harja.domain.tietyoilmoitus/loppusijainnin-kuvaus
   "Jossain Kiimingissä",
   :harja.domain.tietyoilmoitus/viivastys-ruuhka-aikana 30,
   :harja.domain.tietyoilmoitus/kunnat "Oulu, Kiiminki",
   :harja.domain.tietyoilmoitus/tien-nimi "Kuusamontie",
   :harja.domain.tietyoilmoitus/ilmoittaja
   {:harja.domain.tietyoilmoitus/etunimi "Uuno",
    :harja.domain.tietyoilmoitus/sukunimi "Urakoitsija",
    :harja.domain.tietyoilmoitus/sahkoposti "yit_pk2@example.org",
    :harja.domain.tietyoilmoitus/matkapuhelin "43223123"},
   :harja.domain.tietyoilmoitus/tilaajayhteyshenkilo
   {:harja.domain.tietyoilmoitus/sukunimi "Toripolliisi",
    :harja.domain.tietyoilmoitus/matkapuhelin "0405127232",
    :harja.domain.tietyoilmoitus/sahkoposti
    "tero.toripolliisi@example.com",
    :harja.domain.tietyoilmoitus/etunimi "Tero"},
   :harja.domain.tietyoilmoitus/pysaytysten-loppu
   (pvm/->pvm-aika  "07.07.2017 07:07"),
   :harja.domain.tietyoilmoitus/tilaajan-nimi "Pohjois-Pohjanmaa",
   :harja.domain.tietyoilmoitus/vaikutussuunta "molemmat",
   :harja.domain.tietyoilmoitus/huomautukset ["avotuli"],
   :harja.domain.tietyoilmoitus/ajoittaiset-pysaytykset true,
   :harja.domain.tietyoilmoitus/tyoajat
   [{:harja.domain.tietyoilmoitus/alkuaika
     {:tunnit 8 :minuutit 0}
     :harja.domain.tietyoilmoitus/loppuaika
     {:tunnit 17 :minuutit 0}
     :harja.domain.tietyoilmoitus/paivat
     ["maanantai" "tiistai" "keskiviikko"]}
    {:harja.domain.tietyoilmoitus/alkuaika
     {:tunnit 7 :minuutit 0}
     :harja.domain.tietyoilmoitus/loppuaika
     {:tunnit 21 :minuutit 0}
     :harja.domain.tietyoilmoitus/paivat ["lauantai" "sunnuntai"]}],
   :harja.domain.tietyoilmoitus/nopeusrajoitukset
   [{:harja.domain.tietyoilmoitus/rajoitus "30",
     :harja.domain.tietyoilmoitus/matka 100}],
   :harja.domain.tietyoilmoitus/alku
   (pvm/->pvm-aika  "01.01.2017 01:01"),
   :harja.domain.tietyoilmoitus/tienpinnat
   [{:harja.domain.tietyoilmoitus/materiaali "paallystetty",
     :harja.domain.tietyoilmoitus/matka 100}],
   :harja.domain.tietyoilmoitus/tilaajayhteyshenkilo-id 1,
   :harja.domain.tietyoilmoitus/lisatietoja "Tämä on testi-ilmoitus",
   :harja.domain.tietyoilmoitus/luotu
   (pvm/->pvm-aika  "01.01.2017 01:01"),
   :harja.domain.tietyoilmoitus/loppu
   (pvm/->pvm-aika  "07.07.2017 07:07"),
   :harja.domain.tietyoilmoitus/liikenteenohjaaja "liikennevalot",
   :harja.domain.tietyoilmoitus/urakka-id 4,
   :harja.domain.tietyoilmoitus/ajoittain-suljettu-tie true,
   :harja.domain.tietyoilmoitus/alkusijainnin-kuvaus
   "Kuusamontien alussa",
   :harja.domain.tietyoilmoitus/urakoitsijayhteyshenkilo
   {:harja.domain.tietyoilmoitus/sahkoposti "yit_pk2@example.org",
    :harja.domain.tietyoilmoitus/sukunimi "Urakoitsija",
    :harja.domain.tietyoilmoitus/etunimi "Uuno",
    :harja.domain.tietyoilmoitus/matkapuhelin "43223123"},
   :harja.domain.tietyoilmoitus/tilaaja-id 9,
   :harja.domain.tietyoilmoitus/liikenteenohjaus
   "ohjataanVuorotellen",
   :harja.domain.tietyoilmoitus/kiertotien-mutkaisuus "loivatMutkat",
   :harja.domain.tietyoilmoitus/urakkatyyppi "hoito",
   :harja.domain.tietyoilmoitus/urakoitsijayhteyshenkilo-id 6,
   :harja.domain.tietyoilmoitus/viivastys-normaali-liikenteessa 15,
   :harja.domain.tietyoilmoitus/tyotyypit
   [{:harja.domain.tietyoilmoitus/tyyppi "Tienrakennus",
     :harja.domain.tietyoilmoitus/kuvaus "Rakennetaan tietä"}],
   :harja.domain.tietyoilmoitus/luoja 6,
   :harja.domain.tietyoilmoitus/urakoitsijan-nimi "YIT Rakennus Oy",
   :harja.domain.tietyoilmoitus/osoite
   {:harja.domain.tierekisteri/aosa 1,
    :harja.domain.tierekisteri/geometria nil,
    :harja.domain.tierekisteri/losa 5,
    :harja.domain.tierekisteri/tie 20,
    :harja.domain.tierekisteri/let 1,
    :harja.domain.tierekisteri/aet 1},
   :harja.domain.tietyoilmoitus/kohteen-aikataulu {:kohteen-alku (pvm/->pvm-aika  "01.01.2017 01:01")
                                                      :paallystys-valmis (pvm/->pvm-aika  "07.01.2017 01:01")}
   :harja.domain.tietyoilmoitus/urakan-nimi "Oulun alueurakka 2014-2019",
   :harja.domain.tietyoilmoitus/ilmoittaja-id 6,
   :harja.domain.tietyoilmoitus/ajoneuvorajoitukset
   {:harja.domain.tietyoilmoitus/max-korkeus 4M,
    :harja.domain.tietyoilmoitus/max-paino 4000M,
    :harja.domain.tietyoilmoitus/max-pituus 10M,
    :harja.domain.tietyoilmoitus/max-leveys 3M},
   :harja.domain.tietyoilmoitus/id 1,
   :harja.domain.tietyoilmoitus/kiertotienpinnat
   [{:harja.domain.tietyoilmoitus/materiaali "murske",
     :harja.domain.tietyoilmoitus/matka 100}],
   :harja.domain.tietyoilmoitus/pysaytysten-alku
   (pvm/->pvm-aika  "01.01.2017 01:01")})

(defn lomake-mock-komponentti [e! app]
  (let [valittu-ilmoitus (:valittu-ilmoitus app)
        kayttajan-urakat [5]]
    [tietyoilmoituslomake-view/lomake e! app false valittu-ilmoitus kayttajan-urakat]))


(defn query-selector [q]
  (js/document.querySelector q))

(defn kentan-label-loytyy [kentan-id]
  (some? (query-selector (clojure.string/replace "label[for=\"XX\"]" "XX" kentan-id))))


(deftest lomake-muodostuu
  (let [app (atom {:valittu-ilmoitus mock-ilmoitus})]
    (komponenttitesti
     [tuck app lomake-mock-komponentti]
     (is (= "Kuusamontie" (.-value (js/document.querySelector "label[for=tien-nimi] + input"))))
     (is (= "Tämä on testi-ilmoitus"
            (.-value (u/sel1 "textarea"))))

     "Muutetaan lisätietoja"
     (u/change "textarea" "Tämä on ilmoituksen testi!")
     (is (= (get-in @app [:valittu-ilmoitus ::t/lisatietoja]) "Tämä on ilmoituksen testi!")))))
