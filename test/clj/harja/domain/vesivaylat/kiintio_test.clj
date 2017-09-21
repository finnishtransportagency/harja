(ns harja.domain.vesivaylat.kiintio-test
  (:require [clojure.test :refer :all]
            [harja.domain.vesivaylat.kiintio :as kiintio]))

(deftest kiintio-idlla
  (is (= {::kiintio/id 1
          :foo :bar}
         (kiintio/kiintio-idlla
           [{::kiintio/id 1
             :foo :bar}
            {::kiintio/id 2}]
           1))))

(deftest jarjesta-kiintiot
  (is (= [{::kiintio/nimi "a"
           ::kiintio/id 1}
          {::kiintio/nimi "b"
           ::kiintio/id 3}
          {::kiintio/nimi "d"
           ::kiintio/id 100}]
         (kiintio/jarjesta-kiintiot
           [{::kiintio/nimi "c"
             ::kiintio/id -1}
            {::kiintio/nimi "b"
             ::kiintio/id 3}
            {::kiintio/nimi "d"
             ::kiintio/id 100}
            {::kiintio/nimi "a"
             ::kiintio/id 1}
            {::kiintio/nimi "f"
             ::kiintio/id nil}]))))