(ns harja.palvelin.integraatiot.yha.paikkauskohteen-poistosanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-poistosanoma :as paikkauskohteen-poistosanoma])
  (:use [slingshot.slingshot :only [try+]]))

(use-fixtures :once tietokantakomponentti-fixture)

(def kohde-id
  (ffirst (q (str "SELECT id FROM paikkauskohde WHERE lisatiedot = 'Oulun testipaikkauskohde';"))))

(def sanoma-oikein (str "{\"poistettavat-paikkauskohteet\":[" kohde-id"]}"))

(deftest tarkista-poistosanoman-muodostus
  (let [db (:db jarjestelma)
        _ (u (str "UPDATE paikkauskohde SET poistettu = TRUE where id = " kohde-id ";"))
        sanoma (paikkauskohteen-poistosanoma/muodosta db (hae-oulun-alueurakan-2014-2019-id) kohde-id)
        sanoma-avaimilla (walk/keywordize-keys (cheshire/decode sanoma))
        _ (u (str "UPDATE paikkauskohde SET poistettu = FALSE where id = " kohde-id ";"))]
    (is (= sanoma-oikein sanoma) "Oikeanlainen sanoma palautuu.")
    (is (= 1 (count (:poistettavat-paikkauskohteet sanoma-avaimilla))) "Poistettavia paikkauskohteita on oikea määrä.")))

(deftest ei-voi-poistaa-vaaran-urakan-kohteita
  (let [db (:db jarjestelma)] ;; kohde 1 on oulun urakan kohde]
    (is (thrown? Exception (paikkauskohteen-poistosanoma/muodosta db (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)") 1))
        "Sanoman muodostaminen epäonnistuu, kun paikkauskohde ei kuulu urakkaan.")))

