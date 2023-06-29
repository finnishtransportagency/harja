(ns harja.kyselyt.koodistot-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clojure.data :refer [diff]]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]
            [harja.kyselyt.koodistot :as koodistot]))

(use-fixtures :each tietokantakomponentti-fixture)

(deftest palauta-koodi-kun-loytyy
  (is (= "tienrakennetoimenpide/trtp28" (koodistot/konversio (:db jarjestelma) velho-yhteiset/lokita-ja-tallenna-hakuvirhe "v/at" 32))))

(deftest palauta-nil-kun-ei-loyty
  (is (thrown-with-msg? AssertionError #"Harja koodi '320' ei voi konvertoida taulukossa v/at. Vaatii uuden koodin lisäämistä kantaan tai selvittelyä Velholaisten kanssa!"
        (koodistot/konversio (:db jarjestelma) velho-yhteiset/lokita-ja-tallenna-hakuvirhe "v/at" 320))))