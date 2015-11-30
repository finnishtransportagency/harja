(ns harja.palvelin.integraatiot.api.validointi.parametrien-validointi-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parmetrien-validointi]))

(deftest tarkista-puuttuvan-parametrin-kasittely
  (is (thrown? Exception (parmetrien-validointi/tarkista-parametrit {:x 1} {:x "X puuttuu" :y "Y puuttuu"})
               "Poikkeusta ei heitetty, kun parametriä ei annettu"))
  (is (thrown? Exception (parmetrien-validointi/tarkista-parametrit {:x 1 :y nil} {:x "X puuttuu" :y "Y puuttuu"})
               "Poikkeusta ei heitetty, kun parametrin arvo on tyhjä"))
  (is (nil? (parmetrien-validointi/tarkista-parametrit {:x 1 :y 2} {:x "X puuttuu" :y "Y puuttuu"}))))