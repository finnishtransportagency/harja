(ns harja.domain.urakan-tyotunnit-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [clj-time.core :as t]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit]))

(deftest urakan-vuosikolmannekset
  (let [kolmannekset (urakan-tyotunnit/urakan-vuosikolmannekset (t/date-time 2015 1) (t/date-time 2017 10))
        kolmannekset-2017 (kolmannekset 2017)]
    (is (= [2015 2016 2017] (into [] (keys kolmannekset))) "Vuodet on rakennettu oikein")
    (is (every? #(= [1 2 3] (keys (second %))) kolmannekset) "Jokaisella vuodella on kaikki vuosikolmannekset")
    (is (= (t/date-time 2017 1 1) (:alku (kolmannekset-2017 1))) "2017 Ensimmäisen vuosikolmanneksen alku on oikea")
    (is (= (t/date-time 2017 4 30) (:loppu (kolmannekset-2017 1))) "2017 Ensimmäisen vuosikolmanneksen loppu on oikea")
    (is (= (t/date-time 2017 5 1) (:alku (kolmannekset-2017 2))) "2017 Toisen vuosikolmanneksen alku on oikea")
    (is (= (t/date-time 2017 8 31) (:loppu (kolmannekset-2017 2))) "2017 Toisen vuosikolmanneksen loppu on oikea")
    (is (= (t/date-time 2017 9 1) (:alku (kolmannekset-2017 3))) "2017 Kolmannen vuosikolmanneksen alku on oikea")
    (is (= (t/date-time 2017 12 31) (:loppu (kolmannekset-2017 3))) "2017 Kolmannen vuosikolmanneksen loppu on oikea")))