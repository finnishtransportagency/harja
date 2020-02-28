(ns harja.palvelin.integraatiot.yha.paikkauskohteen-poistosanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-poistosanoma :as paikkauskohteen-poistosanoma])
  (:use [slingshot.slingshot :only [try+]]))

(def sanoma-oikein "{\"poistettavat-paikkauskohteet\":1,\"poistettavat-paikkaukset\":[1,2,3,4,5,6]}")

(deftest tarkista-poistosanoman-muodostus
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        _ (u "UPDATE paikkaus SET poistettu = TRUE where \"paikkauskohde-id\" = 1;")
        sanoma (paikkauskohteen-poistosanoma/muodosta db (hae-oulun-alueurakan-2014-2019-id) 1)
        sanoma-avaimilla (walk/keywordize-keys (cheshire/decode sanoma))]
    (is (= sanoma sanoma-oikein) "Oikeanlainen sanoma palautuu.")
    (println "sanoma "  sanoma-avaimilla)
    (println "sanoma " (:poistettavat-paikkaukset sanoma-avaimilla))
    (is (= 6 (-> (first (:paikkauskohteet sanoma-avaimilla))
                                            (:paikkauskohde)
                                            (:paikkaukset)
                                            count)) "Poistettu paikkaus ei ole palautunut.")))

(deftest ei-voi-poistaa-vaaran-urakan-kohteita
  (let [db (tietokanta/luo-tietokanta testitietokanta)] ;; kohde 1 on oulun urakan kohde]
    (is (thrown? Exception (paikkauskohteen-poistosanoma/muodosta db (hae-rovaniemen-maanteiden-hoitourakan-id) 1))
        "Sanoman muodostaminen epäonnistuu, kun paikkauskohde ei kuulu urakkaan.")))

