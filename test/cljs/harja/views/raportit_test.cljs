(ns harja.views.raportit-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]

    [harja.pvm :refer [->pvm] :as pvm]
    [harja.domain.paikkaus.minipot :as minipot]
    [harja.loki :refer [log]]
    [harja.domain.paallystys.pot :as pot]
    [harja.views.raportit :as raportit]))


(deftest materiaalisarakkeiden-muodostus-toimii
  (let [materiaalitoteumat [{:urakka_nimi "Pirkanmaan raivausurakka 2130" :materiaali_nimi "NACL" :kokonaismaara 1}
                            {:urakka_nimi "Oulun alueurakka 2005-2010" :materiaali_nimi "NACL2" :kokonaismaara 2}
                            {:urakka_nimi "Oulun alueurakka 2005-2010" :materiaali_nimi "NACL" :kokonaismaara 1}]
        sarakkeet (raportit/muodosta-materiaalisarakkeet materiaalitoteumat)]
    (is (true? (vector? sarakkeet)))
    (is (= (count sarakkeet) 2))
    (is (= (:nimi (first sarakkeet)) :NACL))
    (is (= (:nimi (second sarakkeet)) :NACL2))))

(deftest materiaaliraportin-rivien-muodostus-toimii
  (let [materiaalitoteumat [{:urakka_nimi "Oulun alueurakka 2005-2010" :materiaali_nimi "NACL2" :kokonaismaara 2}
                            {:urakka_nimi "Oulun alueurakka 2005-2010" :materiaali_nimi "NACL" :kokonaismaara 1}
                            {:urakka_nimi "Pirkanmaan raivausurakka 2130" :materiaali_nimi "NACL" :kokonaismaara 1}]
        rivit (raportit/muodosta-materiaaliraportin-rivit materiaalitoteumat)]
    (is (true? (vector? rivit)))
    (is (= (count rivit) 2))
    (is (= (:NACL (first rivit)) 1))
    (is (= (:NACL2 (first rivit)) 2))
    (is (= (:NACL (second rivit)) 1))
    (is (= (:NACL2 (second rivit)) 0))))
