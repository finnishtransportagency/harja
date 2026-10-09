(ns harja.pvm-test
  (:require [cljs.test :refer-macros [deftest is]]
            [harja.pvm :as pvm]))

(deftest pvm-parsiminen
  (is (pvm/sama-pvm? (pvm/->pvm "8.4.1981")
        (pvm/->pvm "08.04.1981"))))
