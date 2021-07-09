(ns harja.palvelin.integraatiot.yha.paikkauskohteen-lahetyssanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-lahetyssanoma :as paikkauskohteen-lahetyssanoma]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [try+]]))

(use-fixtures :once tietokantakomponentti-fixture)

(deftest tarkista-lahetyssanoman-muodostus
  (let [db (:db jarjestelma)
        sanoma-json (paikkauskohteen-lahetyssanoma/muodosta db (hae-oulun-alueurakan-2014-2019-id) 1)
        sanoma (walk/keywordize-keys
                 (cheshire/decode sanoma-json))
        eka-paikkaus (-> (first (:paikkauskohteet sanoma))
                         :paikkauskohde
                         :paikkaukset
                         first
                         :paikkaus)]
    (is (= "Oulun alueurakka 2014-2019" (get-in sanoma [:urakka :nimi])) "Urakan tiedot palautuvat.")
    (is (= 1
           (get-in eka-paikkaus [:sijainti :ajorata])) "Ajorata läsnä")
    (is (= {:ajourat [{:ajoura 1}]
            :ajouravalit [{:ajouravali 1}]
            :keskisaumat []
            :reunat [{:reuna 1}]}
           (get-in eka-paikkaus [:sijainti :tienkohdat])) "Tienkohdat läsnä")
    (is (= 5 (-> (first (:paikkauskohteet sanoma))
                 (:paikkauskohde)
                 (:paikkaukset)
                 count)) "Poistettu paikkaus ei ole palautunut, voimassa olevat ovat.")))

(deftest ei-voi-lahettaa-vaaran-urakan-kohteita
  (let [db (:db jarjestelma)]     ;; kohde 1 on oulun urakan kohde]
    (is (thrown? Exception (paikkauskohteen-lahetyssanoma/muodosta db (hae-rovaniemen-maanteiden-hoitourakan-id) 1))
        "Sanoman muodostaminen epäonnistuu, kun paikkauskohde ei kuulu urakkaan.")))

