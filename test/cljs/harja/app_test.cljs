(ns harja.app-test
  (:require [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s]
            [cljs.test :as test :refer-macros [deftest is]])
  )


(enable-console-print!)


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
  (is (= 6 (count (s/hoitokaudet +testi-urakka+)))
      "Normaalissa urakassa on 6 hoitokautta"))  
  
 
