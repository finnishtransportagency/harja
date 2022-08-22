(ns harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuera]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(use-fixtures :once tietokantakomponentti-fixture)

;; Jos summa on 0 euroa, summaksi asetetaan 1 euro. Sampo-järjestelmän vaatimus.
(deftest tarkista-kokonaishintaisten-vuosisummien-muodostus
  (let [db (:db jarjestelma)
        odotettu [{:alkupvm "2014-01-01T00:00:00.0", :loppupvm "2014-12-31T00:00:00.0", :summa 10500M}
                  {:alkupvm "2015-01-01T00:00:00.0", :loppupvm "2015-12-31T00:00:00.0", :summa 31510M}
                  {:alkupvm "2016-01-01T00:00:00.0", :loppupvm "2016-12-31T00:00:00.0", :summa 1}
                  {:alkupvm "2017-01-01T00:00:00.0", :loppupvm "2017-12-31T00:00:00.0", :summa 1}
                  {:alkupvm "2018-01-01T00:00:00.0", :loppupvm "2018-12-31T00:00:00.0", :summa 1}
                  {:alkupvm "2019-01-01T00:00:00.0", :loppupvm "2019-12-31T00:00:00.0", :summa 1}]
        numero (ffirst (q "SELECT numero
                           FROM maksuera
                           WHERE nimi = 'Oulu Talvihoito TP ME 2014-2019' AND
                                 tyyppi = 'kokonaishintainen';"))
        maksuera (hae-maksueran-tiedot db numero)
        vuosittaiset-summat (tee-vuosittaiset-summat db numero maksuera)]

    (is (= 6 (count vuosittaiset-summat)))
    (is (= odotettu vuosittaiset-summat))))

(deftest tarkista-muiden-maksuerien-vuosisummien-muodostus
  (let [db (:db jarjestelma)
        odotettu [{:alkupvm "2014-01-01T00:00:00.0"
                   :loppupvm "2014-12-31T00:00:00.0"
                   :summa 1}
                  {:alkupvm "2015-01-01T00:00:00.0"
                   :loppupvm "2015-12-31T00:00:00.0"
                   :summa 1}
                  {:alkupvm "2016-01-01T00:00:00.0"
                   :loppupvm "2016-12-31T00:00:00.0"
                   :summa 1}
                  {:alkupvm "2017-01-01T00:00:00.0"
                   :loppupvm "2017-12-31T00:00:00.0"
                   :summa 1}
                  {:alkupvm "2018-01-01T00:00:00.0"
                   :loppupvm "2018-12-31T00:00:00.0"
                   :summa 1}
                  {:alkupvm "2019-01-01T00:00:00.0"
                   :loppupvm "2019-12-31T00:00:00.0"
                   :summa 1}]
        numero (ffirst (q "SELECT numero
                           FROM maksuera
                           WHERE nimi = 'Kajaani Talvihoito TP ME 2014-2019' AND
                                 tyyppi = 'muu';"))
        maksuera (hae-maksueran-tiedot db numero)
        vuosittaiset-summat (tee-vuosittaiset-summat db numero maksuera)]

    (is (= 6 (count vuosittaiset-summat)))
    (is (= odotettu vuosittaiset-summat))))

(deftest tarkista-lkp-tilinnumeron-paattely
  (is (= "43020000" (valitse-lpk-tilinumero 1 "23104")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 23104 perusteella")
  (is (= "43020000" (valitse-lpk-tilinumero 1 "23116")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 23116 perusteella")
  (is (= "43020000" (valitse-lpk-tilinumero 1 "23124")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 23124 perusteella")
  (is (= "43020000" (valitse-lpk-tilinumero 1 "20107")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 20107 perusteella")
  (is (= "43020000" (valitse-lpk-tilinumero 1 "20112")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 20112 perusteella")
  (is (= "43020000" (valitse-lpk-tilinumero 1 "20143")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 20143 perusteella")
  (is (= "43020000" (valitse-lpk-tilinumero 1 "20179")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 20179 perusteella")
  (is (= "12980010" (valitse-lpk-tilinumero 1 "20106")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 20106 perusteella")
  (is (= "12980010" (valitse-lpk-tilinumero 1 "20135")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 20135 perusteella")
  (is (= "12980010" (valitse-lpk-tilinumero 1 "20183")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 20183 perusteella")
  (is (= "12980010" (valitse-lpk-tilinumero 1 "14109")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 14109 perusteella")
  (is (= "12980010" (valitse-lpk-tilinumero 1 "141217")) "Oikea LKP-tilinnumero valittu toimenpidekoodin 141217 perusteella")
  (is (thrown? RuntimeException (valitse-lpk-tilinumero 1 nil)) "Jos LKP-tuotenumeroa ei voida päätellä, täytyy aiheutua poikkeus")
  (is (thrown? RuntimeException (valitse-lpk-tilinumero 1 666)) "Jos LKP-tuotenumeroa ei voida päätellä, täytyy aiheutua poikkeus"))

