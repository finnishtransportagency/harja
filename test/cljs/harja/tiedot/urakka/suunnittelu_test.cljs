(ns harja.tiedot.urakka.suunnittelu-test
  (:require [harja.tiedot.urakka.suunnittelu :as s]
            [cljs.test :as test :refer-macros [deftest is]]
            [harja.pvm :refer [->pvm] :as pvm]))

(deftest tietojen-kopiointi-tuleville-hoitokausille []
  (let [alkupvm (->pvm "01.10.2006")
        loppupvm (->pvm "30.09.2007")
        rivit [{:maara 66 :alkupvm alkupvm :loppupvm loppupvm}]
        
        kopioidut (s/rivit-tulevillekin-kausille {:alkupvm (->pvm "01.10.2005")
                                                  :loppupvm (->pvm "30.09.2010")}
                                                 rivit
                                                 [alkupvm loppupvm])]
    (.log js/console "KOPS: " kopioidut)
    (is (= 4 (count kopioidut)))
    (is (= (->pvm "01.10.2006") (:alkupvm (first kopioidut))))
    (is (= (->pvm "30.09.2010") (:loppupvm (last kopioidut))))
    (is (every? #(= (:maara %) 66)))
 
    ))
    
    
