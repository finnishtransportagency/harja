(ns harja.tyokalut.spec-apurit-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [harja.testi :refer :all]
    [harja.tyokalut.spec-apurit :refer [namespacefy poista-nil-avaimet]))

(deftest nil-arvojen-poisto-mapista-toimii
  (is (= (poista-nil-avaimet {:a "1" :b nil}) {:a "1"}))
  (is (= (poista-nil-avaimet {:a "1" :b "2"}) {:a "1" :b "2"}))
  (is (= (poista-nil-avaimet {:a "1" :b {:c "3"}}) {:a "1" :b {:c "3"}}))
  (is (= (poista-nil-avaimet {:a "1" :b {:c nil}}) {:a "1"}))
  (is (= (poista-nil-avaimet {:a nil :b {:c "3"}}) {:b {:c "3"}})))

(deftest namespacefy-toimii
  (let [yksitasoinen-map {:name "player1" :hp 1}
        kaksitasoinen-map {:name "player1"
                           :hp 1
                           :tasks {:id 666
                                   :time 5}
                           :points 7
                           :foobar nil}
        kolmitasoinen-map {:name "player1"
                           :hp 1
                           :tasks {:id 666
                                   :time 5
                                   :more-info {:description "Stupid task"
                                               :useless-keyword nil}}
                           :points 7
                           :foobar nil}]
    (is (= (namespacefy yksitasoinen-map {:ns :our.domain.player})
           {:our.domain.player/name "player1"
            :our.domain.player/hp 1}))

    (is (= (namespacefy yksitasoinen-map {:ns :our.domain.player :except #{:hp}})
           {:our.domain.player/name "player1"
            :hp 1}))

    (is (= (namespacefy yksitasoinen-map {:ns :our.domain.player :custom {:hp :our.domain.hp/hp}})
           {:our.domain.player/name "player1"
            :our.domain.hp/hp 1}))

    (is (= (namespacefy kaksitasoinen-map {:ns :our.domain.player
                                           :except #{:foobar}
                                           :custom {:points :our.domain.point/points}
                                           :inner {:tasks {:ns :our.domain.task}}})
           {:our.domain.player/name "player1"
            :our.domain.player/hp 1
            :our.domain.player/tasks {:our.domain.task/id 666
                                      :our.domain.task/time 5}
            :our.domain.point/points 7
            :foobar nil}))

    (is (= (namespacefy kolmitasoinen-map {:ns :our.domain.player
                                           :except #{:foobar}
                                           :custom {:points :our.domain.point/points}
                                           :inner {:tasks {:ns :our.domain.task
                                                           :inner {:more-info {:ns :our.domain.task
                                                                               :except #{:useless-keyword}}}}}})
           {:our.domain.player/name "player1"
            :our.domain.player/hp 1
            :our.domain.player/tasks {:our.domain.task/id 666
                                      :our.domain.task/time 5
                                      :our.domain.task/more-info {:our.domain.task/description "Stupid task"
                                                                  :useless-keyword nil}}
            :our.domain.point/points 7
            :foobar nil}))))