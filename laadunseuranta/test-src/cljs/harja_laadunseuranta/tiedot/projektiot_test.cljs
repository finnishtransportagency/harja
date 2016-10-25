(ns ^:figwheel-load harja-laadunseuranta.tiedot.projektiot-test
  (:require [cljs.test :as t :refer-macros [deftest is testing run-tests]]
            [harja-laadunseuranta.tiedot.projektiot :as p]))

(enable-console-print!)

(deftest test-coordinate-conversion
  (testing "koordinaattien järjestys ja mittakaava oikein"
    (is (= [405698.9876087785 7209946.446847636] (js->clj (p/wgs84->etrsfin [25 65]))))))

(deftest testaa-tilegridin-luonti
  (testing "tilegrid luodaan oikein"
    (is (= (p/tilegrid 16)
           {:origin [-548576 8388608]
            :resolutions [8192 4096 2048 1024 512 256 128 64 32 16 8 4 2 1 0.5 0.25]
            :matrixIds [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15]}))))
