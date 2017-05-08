(ns harja.domain.raportointi-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.domain.raportointi :as r]))

(deftest formatoi-rapottielementti?
  (is (true? (r/formatoi-solu? [:solu {:fmt? true}])))
  (is (true? (r/formatoi-solu? [:solu {:jotain :muuta}])))
  (is (false? (r/formatoi-solu? [:solu {:fmt? false}])))

  (is (true? (r/formatoi-solu? 5))))