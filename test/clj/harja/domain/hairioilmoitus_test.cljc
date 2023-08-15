(ns harja.domain.hairioilmoitus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.pvm :as pvm]
            [harja.domain.hairioilmoitus :as hairio]))

(defn- hairio [id voimassa? alkuaika loppuaika]
  {::hairio/id id
   ::hairio/viesti "viesti"
   ::hairio/pvm (pvm/nyt)
   ::hairio/voimassa? voimassa?
   ::hairio/tyyppi :hairio
   ::hairio/alkuaika alkuaika
   ::hairio/loppuaika loppuaika})

(deftest voimassaoleva-hairio
  (let [hairiot [(hairio 1 true (pvm/dateksi (pvm/paivaa-sitten 2)) (pvm/dateksi (pvm/paivaa-sitten 1)))
                 (hairio 2 true (pvm/dateksi (pvm/paivaa-sitten 1)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)))
                 (hairio 3 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        voimassaoleva (hairio/voimassaoleva-hairio hairiot)]
    (is (= (::hairio/id voimassaoleva) 2))))

(deftest voimassaoleva-hairio-ei-yhtaan
  (let [hairiot [(hairio 1 false (pvm/dateksi (pvm/paivaa-sitten 2)) (pvm/dateksi (pvm/paivaa-sitten 1)))
                 (hairio 2 false (pvm/dateksi (pvm/paivaa-sitten 1)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)))
                 (hairio 3 false (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        voimassaoleva (hairio/voimassaoleva-hairio hairiot)]
    (is (nil? (::hairio/id voimassaoleva)))))

(deftest voimassaoleva-hairio-ei-yhtaan-2
  (let [hairiot [(hairio 1 true (pvm/dateksi (pvm/paivaa-sitten 2)) (pvm/dateksi (pvm/paivaa-sitten 1)))
                 (hairio 2 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        voimassaoleva (hairio/voimassaoleva-hairio hairiot)]
    (is (nil? (::hairio/id voimassaoleva)))))

(deftest voimassaoleva-hairio-alku-avoin-paattynyt
  (let [hairiot [(hairio 1 true nil (pvm/dateksi (pvm/paivaa-sitten 1)))
                 (hairio 2 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        voimassaoleva (hairio/voimassaoleva-hairio hairiot)]
    (is (nil? (::hairio/id voimassaoleva)))))

(deftest voimassaoleva-hairio-alku-avoin-voimassa
  (let [hairiot [(hairio 1 true nil (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)))
                 (hairio 2 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        voimassaoleva (hairio/voimassaoleva-hairio hairiot)]
    (is (= (::hairio/id voimassaoleva) 1))))

(deftest tulevat-hairiot
  (let [hairiot [(hairio 1 false (pvm/dateksi (pvm/paivaa-sitten 2)) (pvm/dateksi (pvm/paivaa-sitten 1)))
                 (hairio 2 true (pvm/dateksi (pvm/paivaa-sitten 1)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)))
                 (hairio 3 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))
                 (hairio 4 false (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        tulevat (hairio/tulevat-hairiot hairiot)]
    (is (some #(= (::hairio/id %) 3) tulevat))
    (is (= (count tulevat) 1))))

(deftest vanhat-hairiot
  (let [hairiot [(hairio 1 false (pvm/dateksi (pvm/paivaa-sitten 2)) (pvm/dateksi (pvm/paivaa-sitten 1)))
                 (hairio 2 true (pvm/dateksi (pvm/paivaa-sitten 1)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)))
                 (hairio 3 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))
                 (hairio 4 false (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        vanhat (hairio/vanhat-hairiot hairiot)]
    (is (some #(= (::hairio/id %) 1) vanhat))
    (is (some #(= (::hairio/id %) 4) vanhat))
    (is (= (count vanhat) 2))))

(deftest aikavalit-leikkaavat
  (let [vanhat [(hairio 1 true (pvm/dateksi (pvm/paivaa-sitten 2)) (pvm/dateksi (pvm/paivaa-sitten 1)))
                (hairio 2 true (pvm/dateksi (pvm/paivaa-sitten 1)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)))
                (hairio 3 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        uusi (hairio 4 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 1)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 40)))]
    (is (hairio/onko-paallekkainen (::hairio/alkuaika uusi) (::hairio/loppuaika uusi) vanhat))))

(deftest aikavalit-eivat-leikkaa
  (let [vanhat [(hairio 1 true (pvm/dateksi (pvm/paivaa-sitten 2)) (pvm/dateksi (pvm/paivaa-sitten 1)))
                (hairio 2 true (pvm/dateksi (pvm/paivaa-sitten 1)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)))
                (hairio 3 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        uusi (hairio 4 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 55)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 60)))]
    (is (not (hairio/onko-paallekkainen (::hairio/alkuaika uusi) (::hairio/loppuaika uusi) vanhat)))))

(deftest aikavalit-eivat-leikkaa-sivuavat
  (let [vanhat [(hairio 1 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        uusi (hairio 2 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 60)))]
    (is (not (hairio/onko-paallekkainen (::hairio/alkuaika uusi) (::hairio/loppuaika uusi) vanhat)))))

(deftest aikavali-leikkaus-uuden-alku-avoin-ei-leikkaa
  (let [vanhat [(hairio 1 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]]
    (is (not (hairio/onko-paallekkainen nil (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 4)) vanhat)))))

(deftest aikavali-leikkaus-uuden-alku-avoin-leikkaa
  (let [vanhat [(hairio 1 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]]
    (is (hairio/onko-paallekkainen nil (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 6)) vanhat))))

(deftest aikavali-leikkaus-uuden-loppu-avoin-ei-leikkaa
  (let [vanhat [(hairio 1 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 7)))]]
    (is (not (hairio/onko-paallekkainen (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 8)) nil vanhat)))))

(deftest aikavali-leikkaus-uuden-loppu-avoin-leikkaa
  (let [vanhat [(hairio 1 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 7)))]]
    (is (hairio/onko-paallekkainen (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 6)) nil vanhat))))

(deftest aikavali-leikkaus-vanhan-alku-avoin-leikkaa
  (let [vanhat [(hairio 1 true nil (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 1)))]]
    (is (not (hairio/onko-paallekkainen (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 2)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 4)) vanhat)))))

(deftest aikavali-leikkaus-vanhan-alku-avoin-ei-leikkaa
  (let [vanhat [(hairio 1 true nil (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 3)))]]
    (is (hairio/onko-paallekkainen (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 2)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 4)) vanhat))))

(deftest aikavali-leikkaus-vanhan-loppu-avoin-ei-leikkaa
  (let [vanhat [(hairio 1 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) nil)]]
    (is (not (hairio/onko-paallekkainen (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 2)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 4)) vanhat)))))

(deftest aikavali-leikkaus-vanhan-loppu-avoin-leikkaa
  (let [vanhat [(hairio 1 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 3)) nil)]]
    (is (hairio/onko-paallekkainen (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 2)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 4)) vanhat))))
