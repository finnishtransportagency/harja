(ns harja.hoitokaudet-test
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing test-var)])
  (:require [cljs.test :refer-macros [deftest is]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu] :as s))



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
  {:alkupvm (pvm/luo-pvm +hoitokauden-vuosi+ +hoitokauden-alkukk-indeksi+ +hoitokauden-alkupv-indeksi+)
   :loppupvm (pvm/luo-pvm (inc +hoitokauden-vuosi+) +hoitokauden-loppukk-indeksi+ +hoitokauden-loppupv-indeksi+)})
)

(deftest hae-urakan-hoitokaudet []
  (log "hae-urakan-hoitokaudet-testi" (s/hoitokaudet +testi-urakka+))
  (is 5 (count (s/hoitokaudet +testi-urakka+)))
  )
