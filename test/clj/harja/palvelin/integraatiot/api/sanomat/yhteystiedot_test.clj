(ns harja.palvelin.integraatiot.api.sanomat.yhteystiedot-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.sanomat.yhteystiedot :as yhteystiedot]
            [harja.tyokalut.json-validointi :as json]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.tyokalut.spec-apurit :as spec-apurit]))

(def testiurakan-tiedot
  {:urakoitsija-ytunnus "1565583-5"
   :loppupvm #inst"2019-09-29T21:00:00.000-00:00"
   :urakoitsija-katuosoite "Panuntie 11 PL 36"
   :urakoitsija-postinumero "621  "
   :nimi "Oulun alueurakka 2014-2019"
   :sampoid "1242141-OULU2"
   :id 4
   :alkupvm #inst"2014-09-30T21:00:00.000-00:00"
   :elynimi "Pohjois-Pohjanmaa"
   :urakkanro "1238"
   :urakoitsija-nimi "YIT Rakennus Oy"
   :elynumero 12})

(def testi-fim-kayttajat
  [{:tunniste nil
    :kayttajatunnus "ASDF"
    :etunimi "Elli"
    :sukunimi "Elyvalvoja"
    :sahkoposti "elli@example.com"
    :puhelin "985484"
    :roolit ["ELY urakanvalvoja"]
    :organisaatio "Destia Oy"
    :vastuuhenkilo true}
   {:tunniste nil
    :kayttajatunnus "DFGDFE"
    :etunimi "Urho"
    :sukunimi "Urakoitsija"
    :sahkoposti "urho@example.com"
    :puhelin "80924098"
    :roolit ["Urakan vastuuhenkilö" "Urakan laadunvalvoja"]
    :organisaatio "ELY"
    :varahenkilo true}])

(def testi-harja-kayttajat
  [{:organisaatio_id 11
    :kayttajatunnus nil
    :sahkoposti nil
    :organisaatio_tyyppi "urakoitsija"
    :sukunimi "Sillanvalvoja"
    :organisaatio_lyhenne nil
    :rooli "Sillanvalvoja"
    :id 106
    :matkapuhelin nil
    :etunimi "Seija"
    :tyopuhelin nil
    :organisaatio_nimi "YIT Rakennus Oy"}])

(deftest tarkista-sanoman-muodostus
  (let [odotettu-data {:urakka {:alkupvm #inst "2014-09-30T21:00:00.000-00:00"
                                :alueurakkanro "1238"
                                :elynimi "Pohjois-Pohjanmaa"
                                :elynro 12
                                :loppupvm #inst "2019-09-29T21:00:00.000-00:00"
                                :nimi "Oulun alueurakka 2014-2019"
                                :sampoid "1242141-OULU2"
                                :urakoitsija {:katuosoite "Panuntie 11 PL 36"
                                              :nimi "YIT Rakennus Oy"
                                              :postinumero "621  "
                                              :ytunnus "1565583-5"}
                                :yhteyshenkilot [{:yhteyshenkilo {:email "elli@example.com"
                                                                  :nimi "Elli Elyvalvoja"
                                                                  :organisaatio "Destia Oy"
                                                                  :puhelinnumero "985484"
                                                                  :rooli "ELY urakanvalvoja"
                                                                  :varahenkilo false
                                                                  :vastuuhenkilo true}}
                                                 {:yhteyshenkilo {:email "urho@example.com"
                                                                  :nimi "Urho Urakoitsija"
                                                                  :organisaatio "ELY"
                                                                  :puhelinnumero "80924098"
                                                                  :rooli "Urakan vastuuhenkilö"
                                                                  :varahenkilo true
                                                                  :vastuuhenkilo false}}
                                                 {:yhteyshenkilo {:email nil
                                                                  :nimi "Seija Sillanvalvoja"
                                                                  :organisaatio "YIT Rakennus Oy"
                                                                  :puhelinnumero nil
                                                                  :rooli "Sillanvalvoja"
                                                                  :varahenkilo false
                                                                  :vastuuhenkilo false}}]}}
        data (yhteystiedot/urakan-yhteystiedot testiurakan-tiedot testi-fim-kayttajat testi-harja-kayttajat)]
    (is (= odotettu-data data) "Data on muodostettu odotetun mukaisesti")
    (json/validoi json-skeemat/+urakan-yhteystietojen-haku-vastaus+
                  (cheshire/encode (spec-apurit/poista-nil-avaimet data)))))
