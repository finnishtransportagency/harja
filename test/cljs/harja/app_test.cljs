(ns ^:figwheel-always harja.app-test
  (:require [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as ykshint-tyot]
            [cljs-time.core :as t]
            [cljs.test :as test :refer-macros [deftest is]])
  )


(enable-console-print!)

;; lisätään urakkaan vain testauksen kannalta tarvittavat kentät
(def +testi-urakka+
  {:alkupvm  (pvm/hoitokauden-alkupvm 2015)
   :loppupvm (pvm/hoitokauden-loppupvm 2020)})

(deftest hae-urakan-hoitokaudet []
         (let [hoitokaudet (s/hoitokaudet +testi-urakka+)
               viesti "hae-urakan-hoitokaudet"]
           (is (= 5 (count hoitokaudet)) viesti)
           (is (= 5 (count (into #{} (map #(:alkupvm %) hoitokaudet)))) viesti)
           (is (= 5 (count (into #{} (map #(:loppupvm %) hoitokaudet)))) viesti)
           (doseq [hk hoitokaudet]
             (is (< (:alkupvm hk) (:loppupvm hk)) viesti)
             (is (= 1 (t/day (:alkupvm hk))) viesti)
             (is (= 10 (t/month (:alkupvm hk))) viesti)
             (is (= 30 (t/day (:loppupvm hk))) viesti)
             (is (= 9 (t/month (:loppupvm hk))) viesti))))


(def +pilkottavat-tyo+
  [{:alkupvm       (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
    :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1350,
    :yksikkohinta  nil, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}])

(deftest pilko-hoitokausien-tyot []
         (let [tyo-avain (fn [rivi]
                           [(:alkupvm rivi) (:loppupvm rivi)])
               pilkotut (ykshint-tyot/pilko-hoitokausien-tyot +pilkottavat-tyo+)
               eka-rivi (first (filterv (fn [t]
                                          (= (:alkupvm t) (pvm/luo-pvm 2005 9 1)))
                                        pilkotut))
               toka-rivi (first (filterv (fn [t]
                                           (= (:alkupvm t) (pvm/luo-pvm 2006 0 1)))
                                         pilkotut))
               viesti "pilko-hoitokausien-tyot"]
           (is (= (count pilkotut) 2) viesti)
           (is (= (:maara eka-rivi) 1) viesti)
           (is (= (:maara toka-rivi) 3) viesti)))

 
(def +kannan-rivit+
  [{:alkupvm      (pvm/vuoden-eka-pvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
    :maara        19 :urakka 1, :tehtava 1350,
    :yksikkohinta nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
   {:alkupvm      (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/vuoden-viim-pvm 2006), :yksikko "km",
    :maara        1012 :urakka 1, :tehtava 1350,
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

(def +hoitokausien-tyorivit-samat+
  [[{:alkupvm       (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 40, :tehtava 1350,
     :yksikkohinta  10, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
    {:alkupvm       (pvm/hoitokauden-alkupvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2007), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 40, :tehtava 1350,
     :yksikkohinta  10, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}]

   [{:alkupvm       (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 40, :tehtava 1349,
     :yksikkohinta  10, :maara nil, :tehtavan_nimi "Jäätien hoito", :sopimus 2}
    {:alkupvm       (pvm/hoitokauden-alkupvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2007), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 40, :tehtava 1349,
     :yksikkohinta  10, :maara nil, :tehtavan_nimi "Jäätien hoito", :sopimus 2}]]
  )

(def +hoitokausien-tyorivit-erit+
  [[{:alkupvm       (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
     :maara-kkt-1-9 23 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1350,
     :yksikkohinta  nil, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
    {:alkupvm       (pvm/hoitokauden-alkupvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2007), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1350,
     :yksikkohinta  nil, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}]

   [{:alkupvm       (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1349,
     :yksikkohinta  nil, :maara nil, :tehtavan_nimi "Jäätien hoito", :sopimus 2}
    {:alkupvm       (pvm/hoitokauden-alkupvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2007), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1349,
     :yksikkohinta  nil, :maara nil, :tehtavan_nimi "Jäätien hoito", :sopimus 2}]]
  )
(def +testi-urakka-kaksi-vuotta+
  {:alkupvm  (pvm/hoitokauden-alkupvm 2005)
   :loppupvm (pvm/hoitokauden-loppupvm 2007)})

(deftest hoitokausien-sisalto-sama []
         (is true (s/hoitokausien-sisalto-sama? +hoitokausien-tyorivit-samat+ (s/hoitokaudet +testi-urakka-kaksi-vuotta+)))
         (is (not (s/hoitokausien-sisalto-sama? +hoitokausien-tyorivit-erit+ (s/hoitokaudet +testi-urakka-kaksi-vuotta+))))
         )


(def +monen-hoitokauden-tyorivit+
  [{:alkupvm       (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
    :maara-kkt-1-9 8 :maara-kkt-10-12 2, :urakka 1, :yhteensa 50.001, :tehtava 1350,
    :yksikkohinta  5, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
   {:alkupvm       (pvm/hoitokauden-alkupvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2007), :yksikko "km",
    :maara-kkt-1-9 8 :maara-kkt-10-12 2, :urakka 1, :yhteensa 50, :tehtava 1350,
    :yksikkohinta  5, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
   {:alkupvm       (pvm/hoitokauden-alkupvm 2007), :loppupvm (pvm/hoitokauden-loppupvm 2008), :yksikko "km",
    :maara-kkt-1-9 8 :maara-kkt-10-12 2, :urakka 1, :yhteensa 50, :tehtava 1350,
    :yksikkohinta  5, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
   ]
  )

(deftest toiden-kustannusten-summa []
         (is (= 150.001 (s/toiden-kustannusten-summa +monen-hoitokauden-tyorivit+))) "toiden-kustannusten-summa" )

















