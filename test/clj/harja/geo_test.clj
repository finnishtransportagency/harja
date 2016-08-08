(ns harja.geo-test
  (:require [clojure.test :refer :all]
            [harja.geo :as geo]))

(deftest extent
  (let [validi? (fn [[xmin ymin xmax ymax :as arr]]
                       (and (= 4 (count arr))
                            (= 4 (count (filter number? arr)))
                            (> xmax xmin)
                            (> ymax ymin)))
        geot [{:type   :line
               :points [[20 20] [10 10] [30 30]]}
              {:type  :multiline
               :lines [{:points [[20 20] [10 10] [30 30]]}
                       {:points [[100 100] [200 200]]}]}
              {:type        :polygon
               :coordinates [[20 20] [10 10] [30 30]]}
              {:type     :multipolygon
               :polygons [{:coordinates [[20 20] [10 10] [30 30]]}
                          {:coordinates [[100 100] [200 200]]}]}
              {:type :point
               :coordinates [20 20]}
              {:type        :multipoint
               :coordinates [[20 20] [30 30]]}
              {:type :icon
               :coordinates [20 20]}
              {:type :circle
               :coordinates [20 20]}
              {:type   :viiva
               :points [[20 20] [10 10] [30 30]]}
              {:type  :moniviiva
               :lines [{:points [[20 20] [10 10] [30 30]]}
                       {:points [[100 100] [200 200]]}]}
              {:type :merkki
               :coordinates [20 20]}]]
    (is (every? validi? (map geo/extent geot)))))