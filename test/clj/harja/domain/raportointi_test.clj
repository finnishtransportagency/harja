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
  (let [vain-lihavointi (r/solun-oletustyyli-excel {:lihavoi? true})]
    (is (true? (:bold (:font vain-lihavointi))))
    (is (= "Open Sans" (:name (:font vain-lihavointi))))
    (is (= :black (:color (:font vain-lihavointi))))
    (is (nil? (:background vain-lihavointi)))
    (is (nil? (:border-left vain-lihavointi))))
  (let [korosta (r/solun-oletustyyli-excel {:korosta? true})]
    (is (nil? (:bold (:font korosta))))
    (is (= :white (:color (:font korosta))))
    (is (= "Open Sans" (:name (:font korosta))))
    (is (= :dark_blue (:background korosta)))
    (is (= :thin (:border-left korosta))))
  (let [korosta-hennosti (r/solun-oletustyyli-excel {:korosta-hennosti? true})]
    (is (nil? (:bold (:font korosta-hennosti))))
    (is (= :black (:color (:font korosta-hennosti))))
    (is (= "Open Sans" (:name (:font korosta-hennosti))))
    (is (= "#9AC7FC" (:background korosta-hennosti)))
    (is (nil? (:border-left korosta-hennosti))))
  (let [korosta-harmaa (r/solun-oletustyyli-excel {:korosta-harmaa? true})]
    (is (nil? (:bold (:font korosta-harmaa))))
    (is (= :black (:color (:font korosta-harmaa))))
    (is (= "Open Sans" (:name (:font korosta-harmaa))))
    (is (= :grey_25_percent (:background korosta-harmaa)))
    (is (nil? (:border-left korosta-harmaa))))
  (let [varoitus (r/solun-oletustyyli-excel {:varoitus? true})]
    (is (nil? (:bold (:font varoitus))))
    (is (= :black (:color (:font varoitus))))
    (is (= "Open Sans" (:name (:font varoitus))))
    (is (= :rose (:background varoitus)))
    (is (nil? (:border-left varoitus))))
  (let [varoitus-yli-korostuksen (r/solun-oletustyyli-excel {:korosta? true
                                                            :varoitus? true})]
    (is (nil? (:bold (:font varoitus-yli-korostuksen))))
    (is (= :black (:color (:font varoitus-yli-korostuksen))))
    (is (= "Open Sans" (:name (:font varoitus-yli-korostuksen))))
    (is (= :rose (:background varoitus-yli-korostuksen)))
    (is (nil? (:border-left varoitus-yli-korostuksen))))
  (let [huomio-ja-lihavointi (r/solun-oletustyyli-excel {:lihavoi? true
                                                         :huomio? true})]
    (is (true? (:bold (:font huomio-ja-lihavointi))))
    (is (= :black (:color (:font huomio-ja-lihavointi))))
    (is (= "Open Sans" (:name (:font huomio-ja-lihavointi))))
    (is (= :yellow (:background huomio-ja-lihavointi)))
    (is (nil? (:border-left huomio-ja-lihavointi))))
  (let [negatiivinen (r/solun-oletustyyli-excel {:negatiivinen? true})]
    (is (= "#F8D7D1" (:background negatiivinen)))
    (is (= "#B40A14" (:color (:font negatiivinen))))))
