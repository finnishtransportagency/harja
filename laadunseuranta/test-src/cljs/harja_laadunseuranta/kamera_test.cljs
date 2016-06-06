(ns harja-laadunseuranta.kamera-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja-laadunseuranta.kamera :as kamera]))

(deftest kuvan-muodostus
  (is (= {:data "foo"
          :mime-type "image/jpeg"}
         (kamera/tee-kuva "data:image/jpeg;base64,foo"))))
