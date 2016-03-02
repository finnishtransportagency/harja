(ns harja.pvm-test
  "Harjan pvm-namespacen backendin testit"
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [harja.pvm :as pvm]
    [clj-time.core :as t]))

(deftest ennen?
  (is (false? (pvm/ennen? nil nil)))
  (is (false? (pvm/ennen? (t/now) nil)))
  (is (false? (pvm/ennen? nil (t/now))))
  (is (false? (pvm/ennen? (t/now) (t/now))))
  (is (false? (pvm/ennen? (t/plus (t/now) (t/hours 4))
                          (t/now))))
  (is (true? (pvm/ennen? (t/now)
                         (t/plus (t/now) (t/hours 4))))))

(deftest jalkeen?
  (is (false? (pvm/jalkeen? nil nil)))
  (is (false? (pvm/jalkeen? (t/now) nil)))
  (is (false? (pvm/jalkeen? nil (t/now))))
  (is (false? (pvm/jalkeen? (t/now) (t/now))))
  (is (false? (pvm/jalkeen? (t/now)
                            (t/plus (t/now) (t/hours 4)))))
  (is (true? (pvm/jalkeen? (t/plus (t/now) (t/hours 4))
                           (t/now)))))