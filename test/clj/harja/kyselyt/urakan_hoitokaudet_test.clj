(ns harja.kyselyt.urakan-hoitokaudet-test
  (:require
   [clj-time.core :as time]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [harja.testi :refer :all]
   [clj-time.coerce :as coerce]
   [taoensso.timbre :as log]))

(use-fixtures :each urakkatieto-fixture)


(defn luo-hoito-urakka []
  (u (str "INSERT INTO urakka (nimi, sampoid, tyyppi, alkupvm, loppupvm)
  VALUES ('HOITOKAUSITESTI', 'HOITOKAUSITESTI', 'hoito' :: urakkatyyppi, '2016-10-01', '2019-09-30');"))
  (ffirst (q (str "SELECT id FROM urakka WHERE nimi = 'HOITOKAUSITESTI'"))))

(defn luo-vuoden-sisalla-paattyva-urakka []
  (u (str "INSERT INTO urakka (nimi, sampoid, tyyppi, alkupvm, loppupvm)
  VALUES ('HOITOKAUSITESTI', 'HOITOKAUSITESTI', 'paallystys' :: urakkatyyppi, '2017-02-02', '2017-07-07');"))
  (ffirst (q (str "SELECT id FROM urakka WHERE nimi = 'HOITOKAUSITESTI'"))))

(defn luo-monivuotinen-urakka []
  (u (str "INSERT INTO urakka (nimi, sampoid, tyyppi, alkupvm, loppupvm)
  VALUES ('HOITOKAUSITESTI', 'HOITOKAUSITESTI', 'paallystys' :: urakkatyyppi, '2017-02-02', '2020-07-07');"))
  (ffirst (q (str "SELECT id FROM urakka WHERE nimi = 'HOITOKAUSITESTI'"))))

(deftest hae-tuntemattoman-urakan-hoitokaudet
  (let [hoitokaudet (q (str "SELECT * FROM urakan_hoitokaudet(49781243)"))]
    (is (empty? hoitokaudet))))

(deftest hae-hoitourakan-hoitokaudet
  (let [urakka-id (luo-hoito-urakka)]
    (testing "Hoitourakan hoitokaudet täytyy olla alkavan vuoden 1.10. ja seuraavan vuoden 30.9. välillä."
      (let [hoitokaudet (q (str "SELECT * FROM urakan_hoitokaudet(" urakka-id ")"))]
        (is (= 3 (count hoitokaudet)))
        (mapv (fn [hoitokausi]
                ;(log/debug "HOITOKAUSI: " hoitokausi)
                (let [alkupvm (paikallinen-aika (first hoitokausi))
                      loppupvm (paikallinen-aika (second hoitokausi))]
                  (is (= 1 (time/day alkupvm)))
                  (is (= 10 (time/month alkupvm)))
                  (is (= 30 (time/day loppupvm)))
                  (is (= 9 (time/month loppupvm)))))
              hoitokaudet)))))

(deftest hae-vuoden-sisalla-paattyvan-urakan-hoitokaudet
  (let [urakka-id (luo-vuoden-sisalla-paattyva-urakka)]
    (testing "Vuoden sisällä päättyvällä urakalla on vain yksi hoitokausi, joka on urakan alkupäivämäärästä loppupäivämäärän."
      (let [hoitokaudet (q (str "SELECT * FROM urakan_hoitokaudet(" urakka-id ")"))
            alkupvm (paikallinen-aika (first (first hoitokaudet)))
            loppupvm (paikallinen-aika (second (first hoitokaudet)))]
        (is (= 1 (count hoitokaudet)))
        (is (= 2 (time/day alkupvm)))
        (is (= 2 (time/month alkupvm)))
        (is (= 7 (time/day loppupvm)))
        (is (= 7 (time/month loppupvm)))))))

(deftest hae-monivuotisen-urakan-hoitokaudet
  (let [urakka-id (luo-monivuotinen-urakka)]
    (testing "Monivuotisen urakan hoitokaudet jaotellaan vuosittain."
      (let [hoitokaudet (q (str "SELECT * FROM urakan_hoitokaudet(" urakka-id ")"))
            ensimmainen-alkupvm (paikallinen-aika (first (first hoitokaudet)))
            ensimmainen-loppupvm (paikallinen-aika (second (first hoitokaudet)))
            toinen-alkupvm (paikallinen-aika (first (second hoitokaudet)))
            toinen-loppupvm (paikallinen-aika (second (second hoitokaudet)))
            kolmas-alkupvm (paikallinen-aika (first (nth hoitokaudet 2)))
            kolmas-loppupvm (paikallinen-aika (second (nth hoitokaudet 2)))
            neljas-alkupvm (paikallinen-aika (first (nth hoitokaudet 3)))
            neljas-loppupvm (paikallinen-aika (second (nth hoitokaudet 3)))]
        (is (= 4 (count hoitokaudet)))

        (is (= 2 (time/day ensimmainen-alkupvm)))
        (is (= 2 (time/month ensimmainen-alkupvm)))
        (is (= 31 (time/day ensimmainen-loppupvm)))
        (is (= 12 (time/month ensimmainen-loppupvm)))

        (is (= 1 (time/day toinen-alkupvm)))
        (is (= 1 (time/month toinen-alkupvm)))
        (is (= 31 (time/day toinen-loppupvm)))
        (is (= 12 (time/month toinen-loppupvm)))

        (is (= 1 (time/day kolmas-alkupvm)))
        (is (= 1 (time/month kolmas-alkupvm)))
        (is (= 31 (time/day kolmas-loppupvm)))
        (is (= 12 (time/month kolmas-loppupvm)))

        (is (= 1 (time/day neljas-alkupvm)))
        (is (= 1 (time/month neljas-alkupvm)))
        (is (= 7 (time/day neljas-loppupvm)))
        (is (= 7 (time/month neljas-loppupvm)))))))
