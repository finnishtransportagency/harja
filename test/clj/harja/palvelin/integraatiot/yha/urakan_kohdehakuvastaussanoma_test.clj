(ns harja.palvelin.integraatiot.yha.urakan-kohdehakuvastaussanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.sanomat.urakan-kohdehakuvastaussanoma :as vastaussanoma]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all])
  (:use [slingshot.slingshot :only [try+]]))

(deftest tarkista-invalidi-xml
  (is (thrown? RuntimeException (vastaussanoma/lue-sanoma +invalidi-urakan-kohdehakuvastaus+))))

(deftest tarkista-tyhja-vastaus
  (let [vastaus (vastaussanoma/lue-sanoma +tyhja-urakan-kohteidenhakuvastaus+)]
    (is (contains? vastaus :kohteet))
    (is (= 0 (count (:kohteet vastaus))))))

(deftest tarkista-usean-urakan-hakuvastaus
  (let [vastaus (vastaussanoma/lue-sanoma +onnistunut-urakan-kohdehakuvastaus+)]
    (println vastaus)
    (is (= 1 (count (:kohteet vastaus))))
    (is (= 3 (:yhaid (first (:kohteet vastaus)))))
    (is (= 2 (count (:alikohteet (first (:kohteet vastaus))))))
    (is (= "A" (:tunnus (first (:alikohteet (first (:kohteet vastaus)))))))
    (is (= "B" (:tunnus (second (:alikohteet (first (:kohteet vastaus)))))))))