(ns harja.domain.tierekisteri.varusteet-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.domain.tierekisteri.varusteet :as varusteet]
            [clj-time.core :as t]))


(deftest tarkista-tietolajin-koodien-voimassaolo
  (with-redefs [t/now #(t/date-time 2017 6 8 12)]
    (is (varusteet/tietolajin-koodi-voimassa?
          {:voimassaolo {:alkupvm (t/date-time 2016 1 1)
                         :loppupvm (t/date-time 2018 1 1)}}))

    (is (not (varusteet/tietolajin-koodi-voimassa?
               {:voimassaolo {:alkupvm (t/date-time 2016 1 1)
                              :loppupvm (t/date-time 2016 12 12)}})))

    (is (not (varusteet/tietolajin-koodi-voimassa?
               {:voimassaolo {:alkupvm (t/date-time 2017 9 9)
                              :loppupvm (t/date-time 2018 1 1)}})))))
