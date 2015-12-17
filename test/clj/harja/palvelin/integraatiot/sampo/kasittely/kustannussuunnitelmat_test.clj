(ns harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat :refer :all]
            [clj-time.core :as time]))

(deftest tarkista-vuosien-muodostus
  (let [vuodet (luo-vuodet (time/date-time 2015 1 1) (time/date-time 2015 12 31))]
    (is (= 1 (count vuodet)))

    (is (= (time/date-time 2015 1 1) (:alkupvm (first vuodet))))
    (is (= (time/date-time 2015 12 31) (:loppupvm (first vuodet)))))


  (let [vuodet (luo-vuodet (time/date-time 2015 10 1) (time/date-time 2016 9 30))]
    (is (= 2 (count vuodet)))

    (is (= (time/date-time 2015 10 1) (:alkupvm (first vuodet))))
    (is (= (time/date-time 2015 12 31) (:loppupvm (first vuodet))))

    (is (= (time/date-time 2016 1 1) (:alkupvm (second vuodet))))
    (is (= (time/date-time 2016 9 30) (:loppupvm (second vuodet)))))

  (let [vuodet (luo-vuodet (time/date-time 2015 10 1) (time/date-time 2018 9 30))]
    (is (= 4 (count vuodet)))

    (is (= (time/date-time 2015 10 1) (:alkupvm (first vuodet))))
    (is (= (time/date-time 2015 12 31) (:loppupvm (first vuodet))))

    (is (= (time/date-time 2016 1 1) (:alkupvm (second vuodet))))
    (is (= (time/date-time 2016 12 31) (:loppupvm (second vuodet))))

    (is (= (time/date-time 2017 1 1) (:alkupvm (nth vuodet 2))))
    (is (= (time/date-time 2017 12 31) (:loppupvm (nth vuodet 2))))

    (is (= (time/date-time 2018 1 1) (:alkupvm (last vuodet))))
    (is (= (time/date-time 2018 9 30) (:loppupvm (last vuodet))))))
