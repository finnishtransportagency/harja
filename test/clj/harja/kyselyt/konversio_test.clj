(ns harja.kyselyt.konversio-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clojure.data :refer [diff]]
            [harja.kyselyt.konversio :as konversio]))

(deftest sarakkeet-vektoriin-test
  (let [mankeloitava [{:id 1 :juttu {:id 1}}
                      {:id 1 :juttu {:id 2} :homma {:id 1}}
                      {:id 1 :juttu nil :homma {:id 2}}

                      {:id 2 :juttu {:id 3} :homma {:id 3}}
                      {:id 2 :juttu {:id 4} :homma {:id 3}}

                      {:id 3}

                      {:id 4 :juttu {:id nil} :homma {:id nil}}]
        haluttu [{:id 1, :jutut [{:id 2} {:id 1}], :hommat [{:id 2} {:id 1}]}
                 {:id 2, :jutut [{:id 4} {:id 3}], :hommat [{:id 3}]}
                 {:id 3, :jutut [], :hommat []}
                 {:id 4, :jutut [], :hommat []}]

        [only-in-a only-in-b in-both] (diff
                                        (harja.kyselyt.konversio/sarakkeet-vektoriin mankeloitava
                                                                                     {:juttu :jutut :homma :hommat})
                                        haluttu)]
    (is (= (konversio/sarakkeet-vektoriin mankeloitava
                                          {:juttu :jutut :homma :hommat})
           haluttu))
    (is (nil? only-in-a))
    (is (nil? only-in-b))
    (is (= (count in-both) (count haluttu)))))

(deftest tarkista-sekvenssin-muuttaminen-jdbc-arrayksi
  (is (= "{1,2,3}" (konversio/seq->array ["1" "2" "3"])) "Merkkijonosekvenssi muunnettin oikein")
  (is (= "{1,2,3}" (konversio/seq->array [:1 :2 :3])) "Keyword-sekvenssi muunnettin oikein")
  (is (= "{1,2,3}" (konversio/seq->array [1 2 3])) "Kokonaislukusekvenssi muunnettin oikein")
  (is (= "{1.1,2.2,3.3}" (konversio/seq->array [1.1 2.2 3.3])) "Desimaalilukusekvenssi muunnettin oikein")  )
