(ns harja.domain.vesivaylat.hinnoittelu-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.domain.vesivaylat.hinnoittelu :as h]))

(deftest hinnoittelu-idlla
  (is (= (h/hinnoittelu-idlla [{::h/id 1} {::h/id 3 :foo :bar} {::h/id 2}] 3)
         {::h/id 3 :foo :bar})))