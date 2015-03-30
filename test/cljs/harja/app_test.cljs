(ns ^:figwheel-always harja.app-test
  (:require [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as ykshint-tyot]
            [cljs-time.core :as t]
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
      "hae-urakan-hoitokaudet"))  

(def +pilkottavat-tyo+
  [{:alkupvm (pvm/luo-pvm 2005 10 1), :loppupvm (pvm/luo-pvm 2006 9 30), :yksikko "km",
      :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1350, 
      :yksikkohinta nil, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}])

(deftest pilko-hoitokausien-tyot []
  (let [tyo-avain (fn [rivi]
                    [(:alkupvm rivi) (:loppupvm rivi)])
        pilkotut (ykshint-tyot/pilko-hoitokausien-tyot +pilkottavat-tyo+)
        eka-rivi (first (filterv (fn [t]
                                   (= (:alkupvm t) (pvm/luo-js-pvm 2005 9 1)))
                                 pilkotut))
        toka-rivi (first (filterv (fn [t]
                                    (= (:alkupvm t) (pvm/luo-js-pvm 2006 0 1)))
                                  pilkotut))
        viesti "pilko-hoitokausien-tyot"]
    (is (= (count pilkotut) 2) viesti)
    (is (= (:maara eka-rivi) 1) viesti)
    (is (= (:maara toka-rivi) 3) viesti)))


(def +kannan-rivit+
  [{:alkupvm (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/vuoden-viim-pvm 2006), :yksikko "km",
      :maara 1012 :urakka 1, :tehtava 1350, 
      :yksikkohinta nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
   {:alkupvm (pvm/vuoden-eka-pvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
      :maara 19 :urakka 1, :tehtava 1350, 
      :yksikkohinta nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}])

(deftest kannan-rivit->tyorivi []
  (let [kasattu-rivi (ykshint-tyot/kannan-rivit->tyorivi +kannan-rivit+)
        viesti "kannan-rivit->tyorivi"]
    (is (= (:maara-kkt-10-12 kasattu-rivi) 1012) viesti)
    (is (= (:maara-kkt-1-9 kasattu-rivi) 19) viesti)
    (is (= (:urakka kasattu-rivi) 1) viesti)
    (is (= (:sopimus kasattu-rivi) 2) viesti)
    (is (= (:yksikko kasattu-rivi) "km") viesti)
    (is (= (:tehtavan_nimi kasattu-rivi) "Tien auraaminen") viesti)
    (is (pvm/sama-pvm? (:alkupvm kasattu-rivi) (pvm/hoitokauden-alkupvm 2005)) viesti)
    (is (pvm/sama-pvm? (:loppupvm kasattu-rivi) (pvm/hoitokauden-loppupvm 2006)) viesti))) 