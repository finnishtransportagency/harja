(ns harja.fmt-test
  (:require
    [clojure.test :refer :all]
    [harja.fmt :as fmt]
    [taoensso.timbre :as log]))

(deftest kuvaile-aikavali-toimii
  (is (thrown? AssertionError (fmt/kuvaile-aikavali nil)))
  (is (thrown? AssertionError (fmt/kuvaile-aikavali -4)))
  (is (= (fmt/kuvaile-aikavali 0) ""))
  (is (= (fmt/kuvaile-aikavali 1) "1 päivä"))
  (is (= (fmt/kuvaile-aikavali 6) "6 päivää"))
  (is (= (fmt/kuvaile-aikavali 7) "1 viikko"))
  (is (= (fmt/kuvaile-aikavali 10) "1 viikko"))
  (is (= (fmt/kuvaile-aikavali 15) "2 viikkoa"))
  (is (= (fmt/kuvaile-aikavali 30) "1 kuukausi"))
  (is (= (fmt/kuvaile-aikavali 90) "3 kuukautta"))
  (is (= (fmt/kuvaile-aikavali 365) "1 vuosi"))
  (is (= (fmt/kuvaile-aikavali 850) "2 vuotta")))