(ns harja.palvelin.integraatiot.yha.paikkauskohteen-lahetyssanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-lahetyssanoma :as paikkauskohteen-lahetyssanoma])
  (:use [slingshot.slingshot :only [try+]]))

(def sanoma-oikein "{\"urakka\":{\"nimi\":\"Oulun alueurakka 2014-2019\",\"harja-id\":4},\"paikkauskohteet\":[{\"paikkauskohde\":{\"nimi\":\"Testikohde\",\"harja-id\":1,\"paikkaukset\":[{\"paikkaus\":{\"leveys\":1.3,\"sijainti\":{\"tie\":20,\"aosa\":1,\"aet\":1,\"losa\":1,\"let\":100},\"loppuaika\":\"2020-03-08T08:28:43Z\",\"alkuaika\":\"2020-02-28T08:28:43Z\",\"kivi-ja-sideaineet\":[{\"kivi-ja-sideaine\":{\"pitoisuus\":3.2,\"lisa-aineet\":\"Lisäaineet\",\"esiintyma\":\"Testikivi\",\"sideainetyyppi\":\"20/30\",\"muotoarvo\":\"Muotoarvo\",\"km-arvo\":\"1\"}},{\"kivi-ja-sideaine\":{\"pitoisuus\":3.2,\"lisa-aineet\":\"Lisäaineet\",\"esiintyma\":\"Testikivi\",\"sideainetyyppi\":\"35/50\",\"muotoarvo\":\"Muotoarvo2\",\"km-arvo\":\"1\"}}],\"massamenekki\":2,\"kuulamylly\":\"AN7\",\"raekoko\":1,\"tyomenetelma\":\"massapintaus\",\"id\":1,\"massatyyppi\":\"asfalttibetoni\"}},{\"paikkaus\":{\"leveys\":1.4,\"sijainti\":{\"tie\":20,\"aosa\":1,\"aet\":50,\"losa\":1,\"let\":150},\"loppuaika\":\"2020-03-13T08:28:43Z\",\"alkuaika\":\"2020-03-03T08:28:43Z\",\"kivi-ja-sideaineet\":[{\"kivi-ja-sideaine\":{\"pitoisuus\":3.3,\"lisa-aineet\":\"Lisäaineet\",\"esiintyma\":\"Testikivi\",\"sideainetyyppi\":\"20/30\",\"muotoarvo\":\"Muotoarvo\",\"km-arvo\":\"1\"}}],\"massamenekki\":3,\"kuulamylly\":\"AN7\",\"raekoko\":1,\"tyomenetelma\":\"massapintaus\",\"id\":2,\"massatyyppi\":\"asfalttibetoni\"}},{\"paikkaus\":{\"leveys\":1.2,\"sijainti\":{\"tie\":20,\"aosa\":3,\"aet\":1,\"losa\":3,\"let\":200},\"loppuaika\":\"2020-03-18T08:28:43Z\",\"alkuaika\":\"2020-03-08T08:28:43Z\",\"kivi-ja-sideaineet\":[{\"kivi-ja-sideaine\":{\"pitoisuus\":3.1,\"lisa-aineet\":\"Lisäaineet\",\"esiintyma\":\"Testikivi\",\"sideainetyyppi\":\"20/30\",\"muotoarvo\":\"Muotoarvo\",\"km-arvo\":\"1\"}}],\"massamenekki\":4,\"kuulamylly\":\"AN7\",\"raekoko\":1,\"tyomenetelma\":\"massapintaus\",\"id\":3,\"massatyyppi\":\"asfalttibetoni\"}},{\"paikkaus\":{\"leveys\":1.3,\"sijainti\":{\"tie\":20,\"aosa\":1,\"aet\":50,\"losa\":1,\"let\":150},\"loppuaika\":\"2020-03-07T08:28:43Z\",\"alkuaika\":\"2020-02-26T08:28:43Z\",\"kivi-ja-sideaineet\":[{\"kivi-ja-sideaine\":{\"pitoisuus\":3.2,\"lisa-aineet\":\"Lisäaineet\",\"esiintyma\":\"Testikivi\",\"sideainetyyppi\":\"20/30\",\"muotoarvo\":\"Muotoarvo\",\"km-arvo\":\"1\"}}],\"massamenekki\":2,\"kuulamylly\":\"AN7\",\"raekoko\":1,\"tyomenetelma\":\"massapintaus\",\"id\":4,\"massatyyppi\":\"asfalttibetoni\"}},{\"paikkaus\":{\"leveys\":1.3,\"sijainti\":{\"tie\":20,\"aosa\":3,\"aet\":100,\"losa\":3,\"let\":250},\"loppuaika\":\"2020-03-07T08:28:43Z\",\"alkuaika\":\"2020-02-26T08:28:43Z\",\"kivi-ja-sideaineet\":[{\"kivi-ja-sideaine\":{\"pitoisuus\":3.2,\"lisa-aineet\":\"Lisäaineet\",\"esiintyma\":\"Testikivi\",\"sideainetyyppi\":\"20/30\",\"muotoarvo\":\"Muotoarvo\",\"km-arvo\":\"1\"}}],\"massamenekki\":2,\"kuulamylly\":\"AN7\",\"raekoko\":1,\"tyomenetelma\":\"massapintaus\",\"id\":5,\"massatyyppi\":\"asfalttibetoni\"}}]}}]}")

(deftest tarkista-lahetyssanoman-muodostus
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        sanoma (paikkauskohteen-lahetyssanoma/muodosta db (hae-oulun-alueurakan-2014-2019-id) 1)
        sanoma-avaimilla (walk/keywordize-keys (cheshire/decode sanoma))]
    (is (= sanoma sanoma-oikein) "Oikeanlainen sanoma palautuu.")
    (is (= 5 (-> (first (:paikkauskohteet sanoma-avaimilla))
                                            (:paikkauskohde)
                                            (:paikkaukset)
                                            count)) "Poistettu paikkaus ei ole palautunut.")))

(deftest ei-voi-lahettaa-vaaran-urakan-kohteita
  (let [db (tietokanta/luo-tietokanta testitietokanta)] ;; kohde 1 on oulun urakan kohde]
    (is (thrown? Exception (paikkauskohteen-lahetyssanoma/muodosta db (hae-rovaniemen-maanteiden-hoitourakan-id) 1))
        "Sanoman muodostaminen epäonnistuu, kun paikkauskohde ei kuulu urakkaan.")))

