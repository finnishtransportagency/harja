(ns harja.kyselyt.koodistot-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clojure.data :refer [diff]]
            [harja.kyselyt.koodistot :as koodistot]))

(use-fixtures :each tietokantakomponentti-fixture)

(deftest palauta-koodi-kun-loytyy
  (is (= "tienrakennetoimenpide/trtp28" (koodistot/konversio (:db jarjestelma) "v/at" 32))))

(deftest palauta-nil-kun-ei-loyty
  (is (thrown-with-msg? AssertionError #"Harja koodi 320 ei voi konvertoida taulukossa v/at"
                        (koodistot/konversio (:db jarjestelma) "v/at" 320))))
