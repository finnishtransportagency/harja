(ns harja.domain.ely-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [harja.testi :refer :all]
            [harja.domain.ely :as ely]))

(deftest elyjen-lyhenteet
  (is (= "UUD" (ely/elynumero->lyhenne 1)))
  (is (= "VAR" (ely/elynumero->lyhenne 2)))
  (is (= "KAS" (ely/elynumero->lyhenne 3)))
  (is (= "PIR" (ely/elynumero->lyhenne 4)))
  (is (= "POS" (ely/elynumero->lyhenne 8)))
  (is (= "KES" (ely/elynumero->lyhenne 9)))
  (is (= "EPO" (ely/elynumero->lyhenne 10)))
  (is (= "POP" (ely/elynumero->lyhenne 12)))
  (is (= "LAP" (ely/elynumero->lyhenne 14))))

(deftest elyjen-nimet
  (is (= "Uusimaa" (ely/elynumero->nimi 1)))
  (is (= "Varsinais-Suomi" (ely/elynumero->nimi 2)))
  (is (= "Kaakkois-Suomi" (ely/elynumero->nimi 3)))
  (is (= "Pirkanmaa" (ely/elynumero->nimi 4)))
  (is (= "Pohjois-Savo" (ely/elynumero->nimi 8)))
  (is (= "Keski-Suomi" (ely/elynumero->nimi 9)))
  (is (=  "Etelä-Pohjanmaa" (ely/elynumero->nimi 10)))
  (is (=  "Pohjois-Pohjanmaa" (ely/elynumero->nimi 12)))
  (is (=  "Lappi" (ely/elynumero->nimi 14))))

;; EVK -testit

(deftest evknumerot-jarjestyksessa-toimii
  (testing "EVK-numeroita on 10 kappaletta"
    (is (= 10 (count ely/evknumerot-jarjestyksessa))))
  (testing "EVK-numerot ovat järjestyksessä 1-10"
    (is (= [380040 380041 380042 380043 380044 380045 380046 380047 380048 380049] ely/evknumerot-jarjestyksessa))))

(deftest evknumero-nimi-ja-numero-toimii
  (testing "Jokaiselle EVK-numerolle löytyy nimi"
    (doseq [n ely/evknumerot-jarjestyksessa]
      (is (some? (get ely/evknumero->nimi n))
          (str "EVK-numero " n " puuttuu evknumero->nimi mapista"))))
  (testing "Nimien lukumäärä vastaa EVK-numeroiden lukumäärää"
    (is (= (count ely/evknumerot-jarjestyksessa)
           (count ely/evknumero->nimi)))))

(deftest evknumero-lyhenne-mapping-on-kattava
  (testing "Jokaiselle EVK-numerolle löytyy lyhenne"
    (doseq [n ely/evknumerot-jarjestyksessa]
      (is (some? (get ely/evknumero->lyhenne n))
          (str "EVK-numero " n " puuttuu evknumero->lyhenne mapista"))))
  (testing "Lyhenteet ovat 3 merkkiä pitkiä ja isoja kirjaimia"
    (doseq [[_ lyhenne] ely/evknumero->lyhenne]
      (is (= 3 (count lyhenne))
          (str "Lyhenne '" lyhenne "' ei ole 3 merkkiä"))
      (is (= lyhenne (str/upper-case lyhenne))
          (str "Lyhenne '" lyhenne "' ei ole isoilla kirjaimilla"))))
  (testing "Lyhenteiden lukumäärä vastaa EVK-numeroiden lukumäärää"
    (is (= (count ely/evknumerot-jarjestyksessa)
           (count ely/evknumero->lyhenne)))))

(deftest tuntematon-numero-palauttaa-nil
  (testing "Olematon EVK-numero palauttaa nil"
    (is (nil? (get ely/evknumero->nimi 99)))
    (is (nil? (get ely/evknumero->lyhenne 0)))
    (is (nil? (get ely/evknumero->nimi -1))))
  (testing "Olematon ELY-numero palauttaa nil"
    (is (nil? (get ely/elynumero->nimi 99)))
    (is (nil? (get ely/elynumero->lyhenne 5)))))
