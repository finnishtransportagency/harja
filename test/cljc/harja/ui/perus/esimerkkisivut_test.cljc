(ns harja.ui.perus.esimerkkisivut-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [harja.ui.perus.esimerkkisivut :as esimerkkisivut]
            [harja.ui.perus.simulaattori :as simulaattori]))

(deftest esimerkkisivut-latautuvat
  (is (seq esimerkkisivut/esimerkkisivut)))

(deftest esimerkkisivujen-id-t-ovat-yksilollisia
  (let [idt (map :id esimerkkisivut/esimerkkisivut)]
    (is (= (count idt) (count (set idt))))))

(deftest jokaisella-esimerkkisivulla-on-kuvaus-ja-kelvollinen-puu
  (doseq [{:keys [id nimi kuvaus puu]} esimerkkisivut/esimerkkisivut]
    (testing (str id)
      (is (keyword? id))
      (is (not (str/blank? nimi)) "nimi puuttuu")
      (is (not (str/blank? kuvaus)) "kuvaus puuttuu")
      (is (seq puu) "puu puuttuu")
      (is (empty? (simulaattori/virheet puu))
          "esimerkkisivun puun pitää läpäistä simulaattorin validointi"))))

(deftest hae-palauttaa-esimerkkisivun-id-lla
  (is (= :listaussivu (:id (esimerkkisivut/hae :listaussivu))))
  (is (nil? (esimerkkisivut/hae :ei-ole))))
