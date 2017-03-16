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

(defn absolute [x] (Math/abs x))

(deftest extent-laajentaminen-prosentilla
  (testing "Apufunktiot"
    (let [extent [2 2 12 12]]
      (is (= 0.0 (#'geo/kasvata-vasemmalle extent 0.2)))
      (is (= 0.0 (#'geo/kasvata-alaspain extent 0.2)))
      (is (= 14.0 (#'geo/kasvata-oikealle extent 0.2)))
      (is (= 14.0 (#'geo/kasvata-ylospain extent 0.2)))))

  (testing "Laajentaminen prosentilla"
    (let [extent [2 2 12 12]]
      (is (= [0.0 0.0 14.0 14.0] (geo/laajenna-extent-prosentilla extent [0.2 0.2 0.2 0.2])))
      (is (= [2 2 14.0 12] (geo/laajenna-extent-prosentilla extent [0 0 0.2 0])))))

  (testing "Oletuksena muut suunnat kasvaa saman verran, paitsi ylöspäin aina enemmän."
    (let [extent [2 2 12 12]
          [minx miny maxx maxy :as muutokset] (map - (geo/laajenna-extent-prosentilla extent) extent)]
      (is (= (absolute (int minx)) (absolute (int maxx))) "Oletuksena extentin pitäis laajeta vasemmalle ja oikealle saman verran")
      (is (< (absolute miny) (absolute maxy)) "Oletuksena extentin pitäis kasvaa ylöspäin enemmän kuin alaspäin"))))