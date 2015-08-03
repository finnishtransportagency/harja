(ns harja.kyselyt.konversio-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.testi :refer :all]
            [clojure.data :refer [diff]]))

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
    (is (= (harja.kyselyt.konversio/sarakkeet-vektoriin mankeloitava
                                                        {:juttu :jutut :homma :hommat})
           haluttu))
    (is (nil? only-in-a))
    (is (nil? only-in-b))
    (is (= (count in-both) (count haluttu)))))
