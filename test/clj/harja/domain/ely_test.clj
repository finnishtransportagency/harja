(ns harja.domain.ely-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.domain.ely :as ely]))

(deftest elyjen-lyhenteet
  (is (= "UUD" (ely/elynumero->lyhenne 1)))
  (is (= "VAR" (ely/elynumero->lyhenne 2)))
  (is (= "KAS" (ely/elynumero->lyhenne 3)))
  (is (= "PIR" (ely/elynumero->lyhenne 4)))
  (is (= "POS" (ely/elynumero->lyhenne 8)))
  (is (= "KES" (ely/elynumero->lyhenne 9)))
  (is (= "EPO" (ely/elynumero->lyhenne 10)))
  (is (= "POP" (ely/elynumero->lyhenne 12)))
  (is (= "LAP" (ely/elynumero->lyhenne 14))))

(deftest elyjen-nimet
  (is (= "Uusimaa" (ely/elynumero->nimi 1)))
  (is (= "Varsinais-Suomi" (ely/elynumero->nimi 2)))
  (is (= "Kaakkois-Suomi" (ely/elynumero->nimi 3)))
  (is (= "Pirkanmaa" (ely/elynumero->nimi 4)))
  (is (= "Pohjois-Savo" (ely/elynumero->nimi 8)))
  (is (= "Keski-Suomi" (ely/elynumero->nimi 9)))
  (is (=  "Etelä-Pohjanmaa" (ely/elynumero->nimi 10)))
  (is (=  "Pohjois-Pohjanmaa" (ely/elynumero->nimi 12)))
  (is (=  "Lappi" (ely/elynumero->nimi 14))))
