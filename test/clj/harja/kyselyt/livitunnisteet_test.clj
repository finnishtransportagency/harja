(ns harja.kyselyt.livitunnisteet-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.livitunnisteet :as livitunnisteet]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]))

(use-fixtures :once tietokantakomponentti-fixture)

(deftest tarkista-livitunnisteen-muodostaminen
  (let [tunniste (livitunnisteet/hae-seuraava-livitunniste (:db jarjestelma))]
    (is (= 20 (.length tunniste)) "Tunnisteen täytyy olla tasan 20 merkkiä pitkä")
    (is (= "HARJ" (.substring tunniste 0 4)) "Tunnisteen täytyy alkaa etuliitteellä HARJ")))
