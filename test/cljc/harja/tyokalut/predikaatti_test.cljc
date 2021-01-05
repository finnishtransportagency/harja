(ns harja.tyokalut.predikaatti-test
  (:require [harja.tyokalut.predikaatti :as pred]
            #?@(:clj  [[clojure.test :refer [deftest is]]
                       [clojure.core.async :as async]]
                :cljs [[cljs.test :refer-macros [deftest is async use-fixtures]]
                       [cljs.core.async :as async]])))

(deftest chan-predikaatit-toimii
  (let [kanava (async/chan)]
    (is (pred/chan? kanava) "chan? ei tunnistanut kanavaa oikeaksi")
    (is (false? (pred/chan? :foo)) "chan? ei tunnistanut avainta vääräksi")
    (is (false? (pred/chan-closed? kanava)) "chan-clsed? ei tunnistanut avointa kanavaa oikein")
    (async/close! kanava)
    (is (pred/chan-closed? kanava) "chan-clsed? ei tunnistanut suljettua kanavaa oikein")
    #?(:clj (is (thrown? AssertionError (pred/chan-closed? :foo))))))
