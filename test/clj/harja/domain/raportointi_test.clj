(ns harja.domain.raportointi-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.domain.raportointi :as r]))

(deftest formatoi-rapottielementti?
  (is (true? (r/formatoi-solu? [:solu {:fmt? true}])))
  (is (true? (r/formatoi-solu? [:solu {:jotain :muuta}])))
  (is (false? (r/formatoi-solu? [:solu {:fmt? false}])))

  (is (true? (r/formatoi-solu? 5))))

(deftest solun-oletustyyli-excel
  (let [vain-lihavointi (r/solun-oletustyyli-excel true false false false false false)]
    (is (true? (:bold (:font vain-lihavointi))))
    (is (nil? (:color (:font vain-lihavointi))))
    (is (nil? (:background vain-lihavointi)))
    (is (nil? (:border-left vain-lihavointi))))
  (let [korosta (r/solun-oletustyyli-excel false true false false false false)]
    (is (nil? (:bold (:font korosta))))
    (is (= :white (:color (:font korosta))))
    (is (= :dark_blue (:background korosta)))
    (is (= :thin (:border-left korosta))))
  (let [korosta-hennosti (r/solun-oletustyyli-excel false false true false false false)]
    (is (nil? (:bold (:font korosta-hennosti))))
    (is (= :black (:color (:font korosta-hennosti))))
    (is (= :pale_blue (:background korosta-hennosti)))
    (is (nil? (:border-left korosta-hennosti))))
  (let [varoitus (r/solun-oletustyyli-excel false false false false true false)]
    (is (nil? (:bold (:font varoitus))))
    (is (= :black (:color (:font varoitus))))
    (is (= :rose (:background varoitus)))
    (is (nil? (:border-left varoitus))))
  (let [varoitus-yli-korostuksen (r/solun-oletustyyli-excel false true false false true false)]
    (is (nil? (:bold (:font varoitus-yli-korostuksen))))
    (is (= :black (:color (:font varoitus-yli-korostuksen))))
    (is (= :rose (:background varoitus-yli-korostuksen)))
    (is (nil? (:border-left varoitus-yli-korostuksen))))
  (let [huomio-ja-lihavointi (r/solun-oletustyyli-excel true false false false false true)]
    (is (true? (:bold (:font huomio-ja-lihavointi))))
    (is (= :black (:color (:font huomio-ja-lihavointi))))
    (is (= :yellow (:background huomio-ja-lihavointi)))
    (is (nil? (:border-left huomio-ja-lihavointi)))))
