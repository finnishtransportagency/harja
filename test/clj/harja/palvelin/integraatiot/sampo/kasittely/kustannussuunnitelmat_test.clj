(ns harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat :refer :all]
            [clj-time.core :as time]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuera]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

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

(deftest tarkista-kokonaishintaisten-vuosisummien-muodostus
  (let [db (apply tietokanta/luo-tietokanta testitietokanta)
        odotettu [{:alkupvm "2014-10-01T00:00:00.0", :loppupvm "2014-12-31T02:00:00.0", :summa 10500M} {:alkupvm "2015-01-01T02:00:00.0", :loppupvm "2015-12-31T02:00:00.0", :summa 31510M} {:alkupvm "2016-01-01T02:00:00.0", :loppupvm "2016-12-31T02:00:00.0", :summa 0} {:alkupvm "2017-01-01T02:00:00.0", :loppupvm "2017-12-31T02:00:00.0", :summa 0} {:alkupvm "2018-01-01T02:00:00.0", :loppupvm "2018-12-31T02:00:00.0", :summa 0} {:alkupvm "2019-01-01T02:00:00.0", :loppupvm "2019-09-30T00:00:00.0", :summa 0}]
        maksuera (maksuera/hae-maksuera db 17)
        vuosittaiset-summat (tee-vuosittaiset-summat db 17 maksuera)]
    (clojure.pprint/pprint vuosittaiset-summat)
    (is (= 6 (count vuosittaiset-summat)))
    (is (= odotettu vuosittaiset-summat))))


