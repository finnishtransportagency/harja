(ns harja.tiedot.urakka.pot2.validoinnit-test
  (:require [cljs.test :as t :refer-macros [deftest is testing]]
            [harja.domain.pot2 :as pot2-domain]
            [harja.tiedot.urakka.pot2.validoinnit :as validoinnit]))

(deftest rem-tas-massamenekin-validointi
  (testing "REM-TAS:n yli 50 kg/m2 on virhe"
    (is (= validoinnit/massamenekin-ylarajan-virhe
           (validoinnit/validoi-rem-tas-massamenekki
             (inc pot2-domain/+massamenekin-maksimi+)
             {:toimenpide pot2-domain/+rem-tas-toimenpide+}
             nil))))
  (testing "50 kg/m2 asti arvo on sallittu"
    (is (nil? (validoinnit/validoi-rem-tas-massamenekki
                pot2-domain/+massamenekin-maksimi+
                {:toimenpide pot2-domain/+rem-tas-toimenpide+}
                nil))))
  (testing "Muut alustatoimenpiteet eivät saa REM-TAS-virhettä"
    (is (nil? (validoinnit/validoi-rem-tas-massamenekki
                51
                {:toimenpide pot2-domain/+rem-toimenpide+}
                nil)))))

(deftest rem-massamenekin-varoitus
  (testing "REM:n yli 50 kg/m2 on varoitus"
    (is (= validoinnit/massamenekin-ylarajan-virhe
           (validoinnit/varoita-rem-massamenekista
             51
             {:toimenpide pot2-domain/+rem-toimenpide+}
             nil))))
  (testing "Muut päällystekerroksen toimenpiteet eivät saa REM-varoitusta"
    (is (nil? (validoinnit/varoita-rem-massamenekista
                51
                {:toimenpide pot2-domain/+rem-tas-toimenpide+}
                nil)))))
