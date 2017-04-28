(ns harja.tiedot.hallintayksikot-test
  (:require [harja.tiedot.hallintayksikot :as hy]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.test :refer-macros [deftest is async use-fixtures]]
            [harja.testutils :as utils])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(use-fixtures :each utils/fake-palvelut-fixture)

(deftest vaylamuoto-hallintayksikolle
  (async done
    (go
      (is (= :bar (<! (hy/hallintayksikon-vaylamuoto* (atom {:foo [{:id 1} {:id 2}]
                                                             :bar [{:id 3}]})
                                                      3))))
      (is (not= :foo (<! (hy/hallintayksikon-vaylamuoto* (atom {:foo [{:id 1} {:id 2}]
                                                                :bar [{:id 3}]})
                                                         3))))
      (is (= :bar (<! (hy/hallintayksikon-vaylamuoto* (atom {:foo [{:id 1} {:id 2}]
                                                             :bar [{:id 3} {:id 4}]})
                                                      4))))
      (is (= :foo (<! (hy/hallintayksikon-vaylamuoto* (atom {:foo [{:id 1} {:id 2}]
                                                             :bar [{:id 3} {:id 4}]})
                                                      1))))
      (is (= nil (<! (hy/hallintayksikon-vaylamuoto* (atom {:foo [{:id 1} {:id 2}]
                                                             :bar [{:id 3} {:id 4}]})
                                                      5))))
      (is (= nil (<! (hy/hallintayksikon-vaylamuoto* (atom {:foo [{:id 1} {:id 2}]
                                                             :bar [{:id 3} {:id 4}]})
                                                      nil))))
      (is (= nil (<! (hy/hallintayksikon-vaylamuoto* (atom {:foo []
                                                             :bar []})
                                                      nil))))
      (is (= nil (<! (hy/hallintayksikon-vaylamuoto* (atom {})
                                                      nil))))
      (done))))

(def fake-hy-haku (constantly [{:liikennemuoto "V" :id 1} {:liikennemuoto "T" :id 2} {:liikennemuoto "T" :id 3}]))

(deftest hallintayksikoiden-haku
  (async done
    (go
      (let [haku (utils/fake-palvelukutsu :hallintayksikot fake-hy-haku)
           haluttu {:vesi [{:liikennemuoto :vesi :id 1 :type :hy}] :tie [{:liikennemuoto :tie :id 2 :type :hy}
                                                               {:liikennemuoto :tie :id 3 :type :hy}]}
           alkutilanne @hy/haetut-hallintayksikot
           haun-tulos (<! (hy/hae-hallintayksikot!))
           lopputilanne @hy/haetut-hallintayksikot]
       (is (not= alkutilanne lopputilanne))
       (is (not= alkutilanne haun-tulos))
       (is (= haun-tulos lopputilanne))

       (is (= haun-tulos haluttu))
       (is (= lopputilanne haluttu))

       (done)))))

