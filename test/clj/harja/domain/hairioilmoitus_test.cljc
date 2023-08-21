(ns harja.domain.hairioilmoitus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.pvm :as pvm]
            [harja.domain.hairioilmoitus :as hairio]
            [clj-time.core :as t]))

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
    (is (hairio/onko-paallekkainen? (::hairio/alkuaika uusi) (::hairio/loppuaika uusi) vanhat))))

(deftest aikavalit-eivat-leikkaa
  (let [vanhat [(hairio 1 true (pvm/dateksi (pvm/paivaa-sitten 2)) (pvm/dateksi (pvm/paivaa-sitten 1)))
                (hairio 2 true (pvm/dateksi (pvm/paivaa-sitten 1)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)))
                (hairio 3 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 5)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 50)))]
        uusi (hairio 4 true (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 55)) (pvm/dateksi (pvm/pvm-plus-tuntia (pvm/nyt) 60)))]
    (is (not (hairio/onko-paallekkainen? (::hairio/alkuaika uusi) (::hairio/loppuaika uusi) vanhat)))))

(deftest aikavalit-leikkaavat-sivuaminen-sallittu
  (is (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
        (t/date-time 2023 8 15 8 0)
        (t/date-time 2023 8 15 8 30)
        (t/date-time 2023 8 15 8 29)
        (t/date-time 2023 8 15 9 0)))

  (is (false? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                (t/date-time 2023 8 15 8 0)
                (t/date-time 2023 8 15 8 30)
                (t/date-time 2023 8 15 8 31)
                (t/date-time 2023 8 15 9 0))))

  (is (false? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                (t/date-time 2023 8 15 8 0)
                (t/date-time 2023 8 15 8 30)
                (t/date-time 2023 8 15 8 30)
                (t/date-time 2023 8 15 9 0)))
    "Aikavälien sivuaminen sallittu")

  (is (false? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                nil
                (t/date-time 2023 8 15 8 30)
                (t/date-time 2023 8 15 8 30)
                (t/date-time 2023 8 15 9 0)))
    "Ekan aikavälin alku avoin, ok, sivuaa")

  (is (true? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
               nil
               (t/date-time 2023 8 15 8 31)
               (t/date-time 2023 8 15 8 30)
               (t/date-time 2023 8 15 9 0)))
    "Ekan aikavälin alku avoin, leikkaa")

  (is (true? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                (t/date-time 2023 8 15 8 0)
                nil
                (t/date-time 2023 8 15 8 30)
                (t/date-time 2023 8 15 9 0)))
    "Ekan aikavälin loppu avoin, leikkaa")

  (is (false? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
               (t/date-time 2023 8 15 9 0)
               nil
               (t/date-time 2023 8 15 8 30)
               (t/date-time 2023 8 15 9 0)))
    "Ekan aikavälin loppu avoin, ok")

  (is (false? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                (t/date-time 2023 8 15 8 0)
                (t/date-time 2023 8 15 8 30)
                nil
                (t/date-time 2023 8 15 8 0)))
    "Toisen aikavälin alku avoin, ei leikkaa")

  (is (true? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                (t/date-time 2023 8 15 8 0)
                (t/date-time 2023 8 15 8 30)
                nil
                (t/date-time 2023 8 15 8 1)))
    "Toisen aikavälin alku avoin, ei leikkaa")

  (is (false? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                (t/date-time 2023 8 15 8 0)
                (t/date-time 2023 8 15 8 30)
                (t/date-time 2023 8 15 8 31)
                nil))
    "Toisen aikavälin loppu avoin, ei leikkaa")

  (is (true? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                (t/date-time 2023 8 15 8 0)
                (t/date-time 2023 8 15 8 30)
                (t/date-time 2023 8 15 8 29)
                nil))
    "Toisen aikavälin loppu avoin, leikkaa")

  (is (true? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
               nil
               (t/date-time 2023 8 15 8 0)
               nil
               (t/date-time 2023 8 15 8 29)))
    "Ekan aikavälin alku avoin, tokan alku avoin")

  (is (true? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
               (t/date-time 2023 8 15 8 0)
               nil
               (t/date-time 2023 8 15 8 29)
               nil))
    "Ekan aikavälin loppu avoin, tokan loppu avoin")

  (is (false? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
               nil
               (t/date-time 2023 8 15 8 30)
               (t/date-time 2023 8 15 8 30)
                nil))
    "Ekan aikavälin alku avoin, tokan loppu avoin, ei leikkaa")

  (is (true? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                nil
                (t/date-time 2023 8 15 8 30)
                (t/date-time 2023 8 15 8 29)
                nil))
    "Ekan aikavälin alku avoin, tokan loppu avoin, leikkaa")

  (is (false? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                (t/date-time 2023 8 15 8 30)
                nil
                nil
                (t/date-time 2023 8 15 8 30)))
    "Ekan aikavälin loppu avoin, tokan alku avoin, ei leikkaa")

  (is (true? (hairio/aikavalit-leikkaavat-sivuaminen-sallittu?
                (t/date-time 2023 8 15 8 31)
                nil
                nil
                (t/date-time 2023 8 15 8 30)))
    "Ekan aikavälin loppu avoin, tokan alku avoin, ei leikkaa"))
