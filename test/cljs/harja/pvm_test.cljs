(ns harja.pvm-test
  (:require [harja.pvm :as pvm]
            [cljs.test :as test :refer-macros [deftest is]]))

(deftest pvm-parsiminen
  (is (pvm/sama-pvm? (pvm/->pvm "8.4.1981") (pvm/->pvm "08.04.1981"))))

(deftest edellisen-kkn-vastaava-pvm
         (is (=
               (pvm/edellisen-kkn-vastaava-pvm (pvm/->pvm "8.4.1981"))
               (pvm/->pvm "08.03.1981")))
         (is (=
               (pvm/edellisen-kkn-vastaava-pvm (pvm/->pvm "8.1.1981"))
               (pvm/->pvm "08.12.1980")))

         )


