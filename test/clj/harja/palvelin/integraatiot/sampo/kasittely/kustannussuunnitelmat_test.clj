(ns harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuera]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(deftest tarkista-kokonaishintaisten-vuosisummien-muodostus
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        odotettu [{:alkupvm "2014-10-01T00:00:00.0", :loppupvm "2014-12-31T00:00:00.0", :summa 10500M}
                  {:alkupvm "2015-01-01T00:00:00.0", :loppupvm "2015-12-31T00:00:00.0", :summa 31510M}
                  {:alkupvm "2016-01-01T00:00:00.0", :loppupvm "2016-12-31T00:00:00.0", :summa 0}
                  {:alkupvm "2017-01-01T00:00:00.0", :loppupvm "2017-12-31T00:00:00.0", :summa 0}
                  {:alkupvm "2018-01-01T00:00:00.0", :loppupvm "2018-12-31T00:00:00.0", :summa 0}
                  {:alkupvm "2019-01-01T00:00:00.0", :loppupvm "2019-09-30T00:00:00.0", :summa 0}]
        maksuera (maksuera/hae-maksuera db 17)
        vuosittaiset-summat (tee-vuosittaiset-summat db 17 maksuera)]

    (is (= 6 (count vuosittaiset-summat)))
    (is (= odotettu vuosittaiset-summat))))

(deftest tarkista-yksikkohintaisten-vuosisummien-muodostus
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        odotettu [{:alkupvm "2014-10-01T00:00:00.0", :loppupvm "2014-12-31T00:00:00.0", :summa 300M}
                  {:alkupvm "2015-01-01T00:00:00.0", :loppupvm "2015-12-31T00:00:00.0", :summa 900M}
                  {:alkupvm "2016-01-01T00:00:00.0", :loppupvm "2016-12-31T00:00:00.0", :summa 0}
                  {:alkupvm "2017-01-01T00:00:00.0", :loppupvm "2017-12-31T00:00:00.0", :summa 0}
                  {:alkupvm "2018-01-01T00:00:00.0", :loppupvm "2018-12-31T00:00:00.0", :summa 0}
                  {:alkupvm "2019-01-01T00:00:00.0", :loppupvm "2019-09-30T00:00:00.0", :summa 0}]
        maksuera (maksuera/hae-maksuera db 18)
        vuosittaiset-summat (tee-vuosittaiset-summat db 18 maksuera)]

    (is (= 6 (count vuosittaiset-summat)))
    (is (= odotettu vuosittaiset-summat))))

