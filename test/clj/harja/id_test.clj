(ns harja.id-test
  (:require [harja.id :as id]
            [clojure.test :refer [deftest is use-fixtures]]))

(deftest negatiivinen-id-ei-kelpaa
  (is (false? (id/id-olemassa? -1))))

(deftest nolla-ei-kelpaa
  (is (false? (id/id-olemassa? 0))))

(deftest nil-ei-kelpaa
  (is (false? (id/id-olemassa? nil))))

(deftest string-ei-kelpaa
  (is (false? (id/id-olemassa? "")))
  (is (false? (id/id-olemassa? "10")))
  (is (false? (id/id-olemassa? "asd"))))

(deftest yli-nolla-numerot-kelpaa
  (is (true? (id/id-olemassa? 10))))