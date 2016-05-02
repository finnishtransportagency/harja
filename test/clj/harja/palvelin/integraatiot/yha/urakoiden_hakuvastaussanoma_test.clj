(ns harja.palvelin.integraatiot.yha.urakoiden-hakuvastaussanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma :as hakuvastaussanoma]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all])
  (:use [slingshot.slingshot :only [try+]]))

(deftest tarkista-invalidi-xml
  (is (thrown? RuntimeException (hakuvastaussanoma/lue-sanoma +invalidi-urakoiden-hakuvastaus+))))

(deftest tarkista-tyhja-vastaus
  (let [vastaus (hakuvastaussanoma/lue-sanoma +urakoiden-tyhja-hakuvastaus+)]
    (is (contains? vastaus :urakat))
    (is (= 0 (count (:urakat vastaus))))))

(deftest tarkista-usean-urakan-hakuvastaus
  (let [vastaus (hakuvastaussanoma/lue-sanoma +usean-urakan-hakuvastaus+)]
    (is (= 2 (count (:urakat vastaus))))
    (is (= 3 (:yhaid (first (:urakat vastaus)))))
    (is (= 1 (count (:elyt (first (:urakat vastaus))))))
    (is (= "POP" (first (:elyt (first (:urakat vastaus))))))
    (is (= 1 (count (:vuodet (first (:urakat vastaus))))))
    (is (= 2016 (first (:vuodet (first (:urakat vastaus))))))
    (is (= 2 (count (:vuodet (second (:urakat vastaus))))))
    (is (= 2 (count (:elyt (second (:urakat vastaus))))))))