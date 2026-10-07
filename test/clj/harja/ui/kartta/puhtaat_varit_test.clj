(ns harja.ui.kartta.puhtaat-varit-test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.ui.kartta.varit.puhtaat :as varit])
  (:import [java.awt Color]))

(deftest kaikki-backend-varit-ovat-java-awt-color
  ;; Laadunseurannassa (tarkastusten piirto) jos yritetään käsitellä HEX / string värejä, 
  ;; tämä aiheuttaa resource exhaustion ja prosessorin säikeet alkavat kaatumaan
  (testing "Kaikki karttavärit ovat backendilla java.awt.Color-instansseja"
    (doseq [vari varit/kaikki]
      (is (instance? Color vari)
        (str
          "Väri ei ollut java.awt.Color: "
          (pr-str vari)
          ", tyyppi: "
          (type vari))))))
