(ns harja.palvelin.integraatiot.api.sanomat.tielupa-sanoma-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.integraatiot.api.sanomat.tielupa-sanoma :as tielupa-sanoma]
            [harja.testi :refer :all]))

(deftest perustiedot
  (let [data {:perustiedot {:kohteen-postinumero 90900
                            :kohteen-postitoimipaikka "Kiiminki"
                            :voimassaolon-alkupvm "2020-09-22T12:00:00+02:00"
                            :kohteen-lahiosoite "Tie 123"
                            :kunta "Kiiminki"
                            :paatoksen-diaarinumero "123456789"
                            :saapumispvm "2020-09-22T12:00:00+02:00"
                            :otsikko "Lupa tehdä töitä"
                            :ely "Pohjois-Pohjanmaa"
                            :liite-url "https://liite.tilu.fi/1234.pdf"
                            :katselmus-url "https://tilu.fi/1234"
                            :tunniste {:id 1234}
                            :tien-nimi "Kuusamontie"
                            :myontamispvm "2020-09-22T12:00:00+02:00"
                            :alueurakka "Oulu"
                            :tyyppi "tietyolupa"
                            :voimassaolon-loppupvm "2020-09-22T12:00:00+02:00"}}
        odotettu {:harja.domain.tielupa/kohde-postitoimipaikka "Kiiminki"
                  :harja.domain.tielupa/voimassaolon-alkupvm #inst "2020-09-22T10:00:00.000-00:00"
                  :harja.domain.tielupa/kunta "Kiiminki"
                  :harja.domain.tielupa/kohde-lahiosoite "Tie 123"
                  :harja.domain.tielupa/paatoksen-diaarinumero "123456789"
                  :harja.domain.tielupa/hakija-postinumero 90900
                  :harja.domain.tielupa/otsikko "Lupa tehdä töitä"
                  :harja.domain.tielupa/urakoiden-nimet ["Oulu"]
                  :harja.domain.tielupa/kohde-postinumero 90900
                  :harja.domain.tielupa/ulkoinen-tunniste 1234
                  :harja.domain.tielupa/saapumispvm #inst "2020-09-22T10:00:00.000-00:00"
                  :harja.domain.tielupa/liite-url "https://liite.tilu.fi/1234.pdf"
                  :harja.domain.tielupa/katselmus-url "https://tilu.fi/1234"
                  :harja.domain.tielupa/voimassaolon-loppupvm #inst "2020-09-22T10:00:00.000-00:00"
                  :harja.domain.tielupa/myontamispvm #inst "2020-09-22T10:00:00.000-00:00"
                  :harja.domain.tielupa/tyyppi :tietyolupa
                  :harja.domain.tielupa/tien-nimi "Kuusamontie"}]
    (is (= (tielupa-sanoma/perustiedot data)) odotettu)))

(deftest sijainnit
  (let [data [{:sijainti
               {:ajorata 0
                :aosa 1
                :numero 20
                :let 300
                :puoli 0
                :losa 1
                :aet 1
                :sijoitus "oikea"
                :kaista 1}}
              {:sijainti
               {:ajorata 1
                :aosa 364
                :numero 4
                :let 666
                :puoli 0
                :losa 364
                :aet 444
                :sijoitus "oikea"
                :kaista 1}}]
        odotettu {:harja.domain.tielupa/sijainnit
                  [{:harja.domain.tielupa/tie 20
                    :harja.domain.tielupa/aosa 1
                    :harja.domain.tielupa/aet 1
                    :harja.domain.tielupa/losa 1
                    :harja.domain.tielupa/let 300
                    :harja.domain.tielupa/ajorata 0
                    :harja.domain.tielupa/kaista 1
                    :harja.domain.tielupa/puoli 0}
                   {:harja.domain.tielupa/tie 4
                    :harja.domain.tielupa/aosa 364
                    :harja.domain.tielupa/aet 444
                    :harja.domain.tielupa/losa 364
                    :harja.domain.tielupa/let 666
                    :harja.domain.tielupa/ajorata 1
                    :harja.domain.tielupa/kaista 1
                    :harja.domain.tielupa/puoli 0}]}]
    (is (= (tielupa-sanoma/sijainnit data) odotettu))))

(deftest hakijan-tiedot
  (let [data {:nimi "Henna Hakija"
              :postiosoite "Liitintie 1"
              :postinumero "90900"
              :puhelinnumero "987-7889087"
              :sahkopostiosoite "henna.hakija@example.com"
              :tyyppi "kotitalous"}
        odotettu {:harja.domain.tielupa/hakija-nimi "Henna Hakija"
                  :harja.domain.tielupa/hakija-postinosoite "Liitintie 1"
                  :harja.domain.tielupa/hakija-postinumero "90900"
                  :harja.domain.tielupa/hakija-puhelinnumero "987-7889087"
                  :harja.domain.tielupa/hakija-sahkopostiosoite
                  "henna.hakija@example.com"
                  :harja.domain.tielupa/hakija-tyyppi "kotitalous"
                  :harja.domain.tielupa/hakija-maakoodi nil
                  :harja.domain.tielupa/hakija-osasto nil}]
    (is (= (tielupa-sanoma/hakijan-tiedot data) odotettu))))

(deftest urakoitsijan-tiedot
  (let [data {:nimi "Puulaaki Oy"
              :yhteyshenkilo "Yrjänä Yhteyshenkilo"
              :puhelinnumero "987-7889087"
              :sahkopostiosoite "yrjana.yhteyshenkilo@example.com"}
        odotettu {:harja.domain.tielupa/urakoitsija-nimi "Puulaaki Oy"
                  :harja.domain.tielupa/urakoitsija-yhteyshenkilo
                  "Yrjänä Yhteyshenkilo"
                  :harja.domain.tielupa/urakoitsija-puhelinnumero "987-7889087"
                  :harja.domain.tielupa/urakoitsija-sahkopostiosoite
                  "yrjana.yhteyshenkilo@example.com"}]
    (is (= (tielupa-sanoma/urakoitsijan-tiedot data) odotettu))))

(deftest liikenneohjaajan-tiedot
  (let [data {:nimi "Liikenneohjaus Oy"
              :yhteyshenkilo "Lilli Liikenteenohjaaja"
              :puhelinnumero "987-7889087"
              :sahkopostiosoite "lilli.liikenteenohjaaja@example.com"}
        odotettu {:harja.domain.tielupa/liikenneohjaajan-nimi "Liikenneohjaus Oy"
                  :harja.domain.tielupa/liikenneohjaajan-yhteyshenkilo "Lilli Liikenteenohjaaja"
                  :harja.domain.tielupa/liikenneohjaajan-puhelinnumero "987-7889087"
                  :harja.domain.tielupa/liikenneohjaajan-sahkopostiosoite "lilli.liikenteenohjaaja@example.com"}]
    (is (= (tielupa-sanoma/liikenneohjaajan-tiedot data) odotettu))))

(deftest tienpitoviranomaisen-tiedot
  (let [data {:yhteyshenkilo "Teijo Tienpitäjä"
              :puhelinnumero "987-7889087"
              :sahkopostiosoite "teijo.tienpitaja@example.com"}
        odotettu {:harja.domain.tielupa/tienpitoviranomainen-yhteyshenkilo "Teijo Tienpitäjä"
                  :harja.domain.tielupa/tienpitoviranomainen-puhelinnumero "987-7889087"
                  :harja.domain.tielupa/tienpitoviranomainen-sahkopostiosoite "teijo.tienpitaja@example.com"
                  :harja.domain.tielupa/tienpitoviranomainen-kasittelija nil
                  :harja.domain.tielupa/tienpitoviranomainen-lupapaallikko nil}]
    (is (= (tielupa-sanoma/tienpitoviranomaisen-tiedot data) odotettu))))

(deftest valmistumisilmoitus
  (let [data {:vaaditaan true
              :palautettu true
              :valmistumisilmoitus "Työt valmistuneet 22.9.2017"}
        odotettu {:harja.domain.tielupa/valmistumisilmoitus
                  "Työt valmistuneet 22.9.2017"
                  :harja.domain.tielupa/valmistumisilmoitus-palautettu true
                  :harja.domain.tielupa/valmistumisilmoitus-vaaditaan true}]
    (is (= (tielupa-sanoma/valmistumisilmoitus data) odotettu))))

(deftest johto-ja-kaapelilupa
  (let [data {:maakaapelia-yhteensa 44.3
              :ilmakaapelia-yhteensa 10.3
              :tienylityksia 1
              :silta-asennuksia 2
              :kaapeliasennukset
              [{:kaapeliasennus
                {:laite "04 kV maakaapeli"
                 :asennustyyppi "Tien varressa"
                 :kommentit "Vedetään uutta kaapelia"
                 :sijainti
                 {:numero 20 :aet 2631 :aosa 6 :ajorata 0 :kaista 1 :puoli 0}
                 :maakaapelia-metreissa 44.3
                 :ilmakaapelia-metreissa 10.3
                 :nopeusrajoitus 30
                 :liikennemaara 10.2}}]}
        odotettu {:harja.domain.tielupa/johtolupa-ilmakaapelia-yhteensa 10.3M
                  :harja.domain.tielupa/johtolupa-maakaapelia-yhteensa 44.3M
                  :harja.domain.tielupa/johtolupa-silta-asennuksia 2
                  :harja.domain.tielupa/johtolupa-tienalituksia nil
                  :harja.domain.tielupa/johtolupa-tienylityksia 1
                  :harja.domain.tielupa/kaapeliasennukset [{:harja.domain.tielupa/aet 2631
                                                            :harja.domain.tielupa/ajorata 0
                                                            :harja.domain.tielupa/aosa 6
                                                            :harja.domain.tielupa/asennustyyppi "Tien varressa"
                                                            :harja.domain.tielupa/ilmakaapelia-metreissa 10.3M
                                                            :harja.domain.tielupa/kaista 1
                                                            :harja.domain.tielupa/kommentit "Vedetään uutta kaapelia"
                                                            :harja.domain.tielupa/laite "04 kV maakaapeli"
                                                            :harja.domain.tielupa/let nil
                                                            :harja.domain.tielupa/liikennemaara 10.2M
                                                            :harja.domain.tielupa/losa nil
                                                            :harja.domain.tielupa/maakaapelia-metreissa 44.3M
                                                            :harja.domain.tielupa/nopeusrajoitus 30
                                                            :harja.domain.tielupa/puoli 0
                                                            :harja.domain.tielupa/tie 20}]}]
    (is (= (tielupa-sanoma/johto-ja-kaapelilupa data) odotettu))))

(deftest liittymalupa
  (let [data {:myonnetty-kauttotarkoitus "lomakiintiesto"
              :tarkoituksen-kuvaus "Kulku kesämökille"
              :sijainnin-kuvaus "Kiimingin keskustasta 10 km pohjoiseen."
              :nykyisen-liittyman-paivays "2011-09-22T12:00:00+02:00"
              :liittymaohje
              {:rummun-etaisyys-metreissa 4
               :odotustila-metreissa 4
               :rummun-halkaisija-millimetreissa 1
               :liikennemerkit "Tieviitta"
               :nakemapisteen-etaisyys 6
               :rumpu true
               :lisaohjeet ""
               :leveys-metreissa 4
               :liittymakaari 12}
              :haettu-kayttotarkoitus {:lomakiinteistolle-kulkuun true}
              :arvioitu-kokonaisliikenne 22
              :arvioitu-kuorma-autoliikenne 5
              :kyla "Kiiminki"
              :muut-kulkuyhteydet ""
              :valmistumisen-takaraja "2018-01-01T00:00:00+02:00"
              :kiinteisto-rn "12344"
              :nykyisen-liittyman-numero 123
              :liittyman-siirto true
              :tilapainen false}
        odotettu {:harja.domain.tielupa/liittymalupa-arvioitu-kokonaisliikenne 22
                  :harja.domain.tielupa/liittymalupa-arvioitu-kuorma-autoliikenne 5
                  :harja.domain.tielupa/liittymalupa-haettu-kayttotarkoitus "lomakiinteistolle-kulku"
                  :harja.domain.tielupa/liittymalupa-kiinteisto-rn "12344"
                  :harja.domain.tielupa/liittymalupa-kyla "Kiiminki"
                  :harja.domain.tielupa/liittymalupa-liittyman-siirto true
                  :harja.domain.tielupa/liittymalupa-liittymaohje-leveys-metreissa 4M
                  :harja.domain.tielupa/liittymalupa-liittymaohje-liikennemerkit "Tieviitta"
                  :harja.domain.tielupa/liittymalupa-liittymaohje-liittymakaari 12M
                  :harja.domain.tielupa/liittymalupa-liittymaohje-liittymisnakema nil
                  :harja.domain.tielupa/liittymalupa-liittymaohje-lisaohjeet ""
                  :harja.domain.tielupa/liittymalupa-liittymaohje-nakemapisteen-etaisyys 6M
                  :harja.domain.tielupa/liittymalupa-liittymaohje-odotustila-metreissa 4M
                  :harja.domain.tielupa/liittymalupa-liittymaohje-rummun-etaisyys-metreissa 4M
                  :harja.domain.tielupa/liittymalupa-liittymaohje-rummun-halkaisija-millimetreissa 1M
                  :harja.domain.tielupa/liittymalupa-liittymaohje-rumpu true
                  :harja.domain.tielupa/liittymalupa-muut-kulkuyhteydet ""
                  :harja.domain.tielupa/liittymalupa-myonnetty-kayttotarkoitus "lomakiintiesto"
                  :harja.domain.tielupa/liittymalupa-nykyisen-liittyman-numero 123
                  :harja.domain.tielupa/liittymalupa-nykyisen-liittyman-paivays #inst "2011-09-22T10:00:00.000-00:00"
                  :harja.domain.tielupa/liittymalupa-sijainnin-kuvaus "Kiimingin keskustasta 10 km pohjoiseen."
                  :harja.domain.tielupa/liittymalupa-tarkoituksen-kuvaus "Kulku kesämökille"
                  :harja.domain.tielupa/liittymalupa-tilapainen false
                  :harja.domain.tielupa/liittymalupa-valmistumisen-takaraja #inst "2017-12-31T22:00:00.000-00:00"}]
    (is (= (tielupa-sanoma/liittymalupa data) odotettu))))

(deftest mainoslupa
  (let [data {:sijainnin-kuvaus "Kiimingin keskustasta 10 km pohjoiseen."
              :tiedoksi-elykeskukselle true
              :asemakaava-alueella true
              :suoja-alueen-leveys 1.2M
              :mainokset
              [{:mainos
                {:sijainti
                 {:numero 20
                  :aet 2631
                  :aosa 6
                  :ajorata 0
                  :kaista 1
                  :puoli 0
                  :sijoitus "oikea"}}}]}
        odotettu {:harja.domain.tielupa/mainoslupa-mainostettava-asia nil
                  :harja.domain.tielupa/mainoslupa-sijainnin-kuvaus
                  "Kiimingin keskustasta 10 km pohjoiseen."
                  :harja.domain.tielupa/mainoslupa-korvaava-paatos nil
                  :harja.domain.tielupa/mainoslupa-tiedoksi-elykeskukselle true
                  :harja.domain.tielupa/mainoslupa-asemakaava-alueella true
                  :harja.domain.tielupa/mainoslupa-suoja-alueen-leveys 1.2M
                  :harja.domain.tielupa/mainokset
                  [{:harja.domain.tielupa/tie 20
                    :harja.domain.tielupa/aosa 6
                    :harja.domain.tielupa/aet 2631
                    :harja.domain.tielupa/losa nil
                    :harja.domain.tielupa/let nil
                    :harja.domain.tielupa/ajorata 0
                    :harja.domain.tielupa/kaista 1
                    :harja.domain.tielupa/puoli 0}]}]
    (is (= (tielupa-sanoma/mainoslupa data) odotettu))))

(deftest mainosilmoitus
  (let [data {:sijainnin-kuvaus "Kiimingin keskustasta 10 km pohjoiseen."
              :tiedoksi-elykeskukselle true
              :mainostettava-asia "Hyvä hitutinteri"
              :asemakaava-alueella true
              :suoja-alueen-leveys 1.2M
              :mainokset
              [{:mainos
                {:sijainti
                 {:numero 20
                  :aet 2631
                  :aosa 6
                  :ajorata 0
                  :kaista 1
                  :puoli 0
                  :sijoitus "oikea"}}}]}
        odotettu {:harja.domain.tielupa/mainoslupa-sijainnin-kuvaus "Kiimingin keskustasta 10 km pohjoiseen."
                  :harja.domain.tielupa/mainoslupa-korvaava-paatos nil
                  :harja.domain.tielupa/mainoslupa-tiedoksi-elykeskukselle true
                  :harja.domain.tielupa/mainoslupa-asemakaava-alueella true
                  :harja.domain.tielupa/mainoslupa-suoja-alueen-leveys 1.2M
                  :harja.domain.tielupa/mainoslupa-mainostettava-asia "Hyvä hitutinteri"
                  :harja.domain.tielupa/mainokset
                  [{:harja.domain.tielupa/tie 20
                    :harja.domain.tielupa/aosa 6
                    :harja.domain.tielupa/aet 2631
                    :harja.domain.tielupa/losa nil
                    :harja.domain.tielupa/let nil
                    :harja.domain.tielupa/ajorata 0
                    :harja.domain.tielupa/kaista 1
                    :harja.domain.tielupa/puoli 0}]}]
    (is (= (tielupa-sanoma/mainoslupa data) odotettu))))

(deftest opastelupa
  (let [data {:ennakkomerkki true
              :palvelukohteen-osoiteviitta true
              :alkuperainen-lupanro 123
              :osoiteviitta true
              :kohteen-nimi "Koitelinkosken lomamökeille viitan pystyttäminen"
              :nykyinen-opastus ""
              :lisatiedot ""
              :kohteen-url-osoite "http://example.com"
              :opasteen-teksti "Koitelinkosken lomamökit"
              :opasteet
              [{:opaste
                {:kuvaus "Opastustaulu"
                 :tulostenumero 123
                 :sijainti
                 {:numero 20
                  :aet 2631
                  :aosa 6
                  :ajorata 0
                  :kaista 1
                  :puoli 0
                  :sijoitus "oikea"}}}]
              :alkuperaisen-luvan-loppupvm "2017-09-22T12:00:00+02:00"
              :alkuperaisen-luvan-alkupvm "2012-09-22T12:00:00+02:00"
              :jatkolupa false
              :osoiteviitan-tunnus "123"}
        odotettu {:harja.domain.tielupa/opastelupa-palvelukohteen-osoiteviitta true
                  :harja.domain.tielupa/opastelupa-osoiteviitan-tunnus "123"
                  :harja.domain.tielupa/opastelupa-alkuperainen-lupanro 123
                  :harja.domain.tielupa/opastelupa-lisatiedot ""
                  :harja.domain.tielupa/opastelupa-kohteen-nimi "Koitelinkosken lomamökeille viitan pystyttäminen"
                  :harja.domain.tielupa/opastelupa-osoiteviitta true
                  :harja.domain.tielupa/opastelupa-ennakkomerkki true
                  :harja.domain.tielupa/opastelupa-alkuperaisen-luvan-loppupvm #inst "2017-09-22T10:00:00.000-00:00"
                  :harja.domain.tielupa/opastelupa-kohteen-url-osoite "http://example.com"
                  :harja.domain.tielupa/opastelupa-opasteen-teksti "Koitelinkosken lomamökit"
                  :harja.domain.tielupa/opastelupa-alkuperaisen-luvan-alkupvm #inst "2012-09-22T10:00:00.000-00:00"
                  :harja.domain.tielupa/opastelupa-nykyinen-opastus ""
                  :harja.domain.tielupa/opastelupa-jatkolupa false
                  :harja.domain.tielupa/opastelupa-palvelukohteen-opastaulu nil
                  :harja.domain.tielupa/opasteet [{:harja.domain.tielupa/aet 2631
                                                   :harja.domain.tielupa/ajorata 0
                                                   :harja.domain.tielupa/aosa 6
                                                   :harja.domain.tielupa/kaista 1
                                                   :harja.domain.tielupa/kuvaus "Opastustaulu"
                                                   :harja.domain.tielupa/let nil
                                                   :harja.domain.tielupa/losa nil
                                                   :harja.domain.tielupa/puoli 0
                                                   :harja.domain.tielupa/tie 20
                                                   :harja.domain.tielupa/tulostenumero 123}]}]
    (is (= (tielupa-sanoma/opastelupa data) odotettu))))

(deftest suoja-aluerakentamislupa
  (let [data {:rakennettava-asia "Aitta"
              :lisatiedot "Komia aiatta"
              :esitetty-etaisyys-tien-keskilinjaan 48
              :vahimmaisetaisyys-tien-keskilinjasta 48
              :suoja-alueen-leveys 4
              :sijoitus {:nakema-alue true}
              :kiinteisto-rn "12344"}
        odotettu {:harja.domain.tielupa/suoja-aluerakentamislupa-esitetty-etaisyys-tien-keskilinjaan 48M
                  :harja.domain.tielupa/suoja-aluerakentamislupa-kiinteisto-rn "12344"
                  :harja.domain.tielupa/suoja-aluerakentamislupa-lisatiedot "Komia aiatta"
                  :harja.domain.tielupa/suoja-aluerakentamislupa-rakennettava-asia "Aitta"
                  :harja.domain.tielupa/suoja-aluerakentamislupa-sijoitus "nakemisalue"
                  :harja.domain.tielupa/suoja-aluerakentamislupa-suoja-alueen-leveys 4M
                  :harja.domain.tielupa/suoja-aluerakentamislupa-vahimmaisetaisyys-tien-keskilinjasta 48M}]
    (is (= (tielupa-sanoma/suoja-aluerakentamislupa data) odotettu))))

(deftest tilapainen-myyntilupa
  (let [data {:aihe "Hyviä marjoja myytävänä"
              :alueen-nimi "Kiimingin keskusta"
              :aikaisempi-myyntilupa "Lupa 123"
              :opastusmerkit "Kyltti tien poskessa"}
        odotettu {:harja.domain.tielupa/myyntilupa-aihe "Hyviä marjoja myytävänä"
                  :harja.domain.tielupa/myyntilupa-alueen-nimi "Kiimingin keskusta"
                  :harja.domain.tielupa/myyntilupa-aikaisempi-myyntilupa "Lupa 123"
                  :harja.domain.tielupa/myyntilupa-opastusmerkit "Kyltti tien poskessa"}]
    (is (= (tielupa-sanoma/tilapainen-myyntilupa data) odotettu))))

(deftest tilapaiset-liikennemerkkijarjestelyt
  (let [data {:aihe "Nopeusrajoitusten laskeminen festivaalien ajaksi"
              :sijainnin-kuvaus "Kiimingin keskustasta 10 km pohjoiseen."
              :tapahtuman-tiedot "Kiiminki kolisee"
              :nopeusrajoituksen-syy "Paljon ihmisiä tien läheisyydessä liikenteessä"
              :lisatiedot-nopeusrajoituksesta ""
              :muut-liikennemerkit ""
              :jarjestelyt
              [{:jarjestely
                {:liikennemerkki "Nopeusrajoitusmerkki"
                 :alkuperainen-nopeusrajoitus "60"
                 :alennettu-nopeusrajoitus "30"
                 :nopeusrajoituksen-pituus "250"
                 :sijainti
                 {:numero 20
                  :aet 2631
                  :aosa 6
                  :ajorata 0
                  :kaista 1
                  :puoli 0
                  :sijoitus "oikea"}}}]}
        odotettu {:harja.domain.tielupa/liikennemerkkijarjestely-aihe "Nopeusrajoitusten laskeminen festivaalien ajaksi"
                  :harja.domain.tielupa/liikennemerkkijarjestely-sijainnin-kuvaus "Kiimingin keskustasta 10 km pohjoiseen."
                  :harja.domain.tielupa/liikennemerkkijarjestely-tapahtuman-tiedot "Kiiminki kolisee"
                  :harja.domain.tielupa/liikennemerkkijarjestely-nopeusrajoituksen-syy "Paljon ihmisiä tien läheisyydessä liikenteessä"
                  :harja.domain.tielupa/liikennemerkkijarjestely-lisatiedot-nopeusrajoituksesta ""
                  :harja.domain.tielupa/liikennemerkkijarjestely-muut-liikennemerkit ""
                  :harja.domain.tielupa/liikennemerkkijarjestelyt
                  [{:harja.domain.tielupa/ajorata 0
                    :harja.domain.tielupa/alkuperainen-nopeusrajoitus "60"
                    :harja.domain.tielupa/tie 20
                    :harja.domain.tielupa/aosa 6
                    :harja.domain.tielupa/let nil
                    :harja.domain.tielupa/puoli 0
                    :harja.domain.tielupa/aet 2631
                    :harja.domain.tielupa/losa nil
                    :harja.domain.tielupa/alennettu-nopeusrajoitus "30"
                    :harja.domain.tielupa/nopeusrajoituksen-pituus "250"
                    :harja.domain.tielupa/kaista 1
                    :harja.domain.tielupa/liikennemerkki "Nopeusrajoitusmerkki"}]}]
    (is (= (tielupa-sanoma/tilapaiset-liikennemerkkijarjestelyt data) odotettu))))

(deftest vesihuoltolupa
  (let [data {:tienylityksia 2,
              :tienalituksia 5,
              :silta-asennuksia 0,
              :johtoasennukset
              [{:johtoasennus
                {:laite "Vesijohto", :asennustyyppi "", :kommentit ""}}]}
        odotettu {:harja.domain.tielupa/vesihuoltolupa-tienylityksia 2,
                  :harja.domain.tielupa/vesihuoltolupa-tienalituksia 5,
                  :harja.domain.tielupa/vesihuoltolupa-silta-asennuksia 0,
                  :harja.domain.tielupa/johtoasennukset
                  [{:harja.domain.tielupa/ajorata nil,
                    :harja.domain.tielupa/tie nil,
                    :harja.domain.tielupa/aosa nil,
                    :harja.domain.tielupa/let nil,
                    :harja.domain.tielupa/laite "Vesijohto",
                    :harja.domain.tielupa/kommentit "",
                    :harja.domain.tielupa/puoli nil,
                    :harja.domain.tielupa/aet nil,
                    :harja.domain.tielupa/losa nil,
                    :harja.domain.tielupa/asennustyyppi "",
                    :harja.domain.tielupa/kaista nil}]}]

    (is (= (tielupa-sanoma/vesihuoltolupa data) odotettu))))


(deftest mainoslupa-ilman-sijaintia
  (let [data {:sijainnin-kuvaus "Sijainti jota ei ole ilmoitettu tr-osoitteella."
              :tiedoksi-elykeskukselle true
              :asemakaava-alueella true
              :suoja-alueen-leveys 1.2M
              :mainokset
              [{:mainos
                {:sijainti {}}}]}
        odotettu {:harja.domain.tielupa/mainoslupa-mainostettava-asia nil
                  :harja.domain.tielupa/mainoslupa-sijainnin-kuvaus
                  "Sijainti jota ei ole ilmoitettu tr-osoitteella."
                  :harja.domain.tielupa/mainoslupa-korvaava-paatos nil
                  :harja.domain.tielupa/mainoslupa-tiedoksi-elykeskukselle true
                  :harja.domain.tielupa/mainoslupa-asemakaava-alueella true
                  :harja.domain.tielupa/mainoslupa-suoja-alueen-leveys 1.2M
                  :harja.domain.tielupa/mainokset
                  [{:harja.domain.tielupa/tie nil
                    :harja.domain.tielupa/aosa nil
                    :harja.domain.tielupa/aet nil
                    :harja.domain.tielupa/losa nil
                    :harja.domain.tielupa/let nil
                    :harja.domain.tielupa/ajorata nil
                    :harja.domain.tielupa/kaista nil
                    :harja.domain.tielupa/puoli nil}]}]
    (is (= (tielupa-sanoma/mainoslupa data) odotettu))))
