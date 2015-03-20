(ns harja.app-test
  (:require [harja.asiakas.main :as app]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s]
            [cljs.test :as test])
  
  (:require-macros [cljs.test :refer [deftest is use-fixtures]]))


(enable-console-print!)

(deftest dummy-failing-test
  (is (= 1 2)))

(def +hoitokauden-alkukk-indeksi+ "9")
(def +hoitokauden-alkupv-indeksi+ "1")
(def +hoitokauden-loppukk-indeksi+ "8")
(def +hoitokauden-loppupv-indeksi+ "30")
(def +hoitokauden-eka-vuosi+ 
  2015)
(def +hoitokauden-vika-vuosi+ 
  2020)

;; lisätään urakkaan vain testauksen kannalta tarvittavat kentät
(def +testi-urakka+
  {:alkupvm (pvm/luo-pvm +hoitokauden-eka-vuosi+ +hoitokauden-alkukk-indeksi+ +hoitokauden-alkupv-indeksi+)
   :loppupvm (pvm/luo-pvm +hoitokauden-vika-vuosi+ +hoitokauden-loppukk-indeksi+ +hoitokauden-loppupv-indeksi+)})

(deftest hae-urakan-hoitokaudet []
  (log "hae-urakan-hoitokaudet-testi" (s/hoitokaudet +testi-urakka+))
  (is 5 (count (s/hoitokaudet +testi-urakka+)))
  )
