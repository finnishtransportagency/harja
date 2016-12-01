(ns harja.tyokalut.merkkijono-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [harja.testi :refer :all]
    [harja.tyokalut.merkkijono :as merkkijono]))

(deftest tarkista-paivamaaran-validointi
  (is (merkkijono/iso-8601-paivamaara? "2016-11-11"))
  (is (not (merkkijono/iso-8601-paivamaara? "asdf")))
  (is (not (merkkijono/iso-8601-paivamaara? "2016-22-22")))
  (is (not (merkkijono/iso-8601-paivamaara? "11-22-2016"))))

