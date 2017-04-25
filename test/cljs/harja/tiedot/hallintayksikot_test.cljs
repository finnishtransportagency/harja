(ns harja.tiedot.hallintayksikot-test
  (:require [harja.tiedot.hallintayksikot :as hy]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.test :as test :refer-macros [deftest is async]]
            [clojure.test :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(t/use-fixtures :once harja.testutils/fake-palvelut-fixture)

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

(def fake-hy-haku (constantly [{:liikennemuoto :vesi :id 1} {:liikennemuoto :tie :id 2} {:liikennemuoto :tie :id 3}]))

(deftest foobar
  (async done
    (let [haku (harja.testutils/fake-palvelukutsu :hallintayksikot fake-hy-haku)
          haluttu {:vesi [{:liikennemuoto :vesi :id 1}] :tie [{:liikennemuoto :tie :id 2}
                                                              {:liikennemuoto :tie :id 3}]}
          alkutilanne @hy/haetut-hallintayksikot
          haun-tulos (hy/hae-hallintayksikot!)
          lopputilanne @hy/haetut-hallintayksikot]
      (is (not= alkutilanne lopputilanne))
      (is (not= alkutilanne haun-tulos))
      (is (= haun-tulos lopputilanne))

      (is (= haun-tulos haluttu))
      (is (= lopputilanne haluttu)))))

