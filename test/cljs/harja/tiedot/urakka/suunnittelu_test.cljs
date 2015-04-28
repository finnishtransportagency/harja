(ns harja.tiedot.urakka.suunnittelu-test
  (:require [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as ykshint-tyot]
            [harja.tiedot.urakka.kokonaishintaiset-tyot :as kokhint-tyot]
            [cljs-time.core :as t]
            [cljs.test :as test :refer-macros [deftest is]]
            [harja.loki :refer [log]]
            [harja.pvm :refer [->pvm] :as pvm]))

(defn hk-rivi
  "Apuri hoitokausirivin tekemiseksi. Asettaa alkupvm annetulle vuodelle ja loppupvm seuraavalle."
  [vuosi arvot]
  (merge arvot {:alkupvm (pvm/hoitokauden-alkupvm vuosi) :loppupvm (pvm/hoitokauden-loppupvm (inc vuosi))}))

(defn hk
  "Apuri joka antaa hoitokauden, joka alkaa annetun vuoden hoitokauden alkupvm:llä ja loppuu seuraavan
  vuoden loppupvm:llä. Palauttaa vektorin [alkupvm loppupvm]"
  [vuosi]
  [(pvm/hoitokauden-alkupvm vuosi) (pvm/hoitokauden-loppupvm (inc vuosi))])


;; lisätään urakkaan vain testauksen kannalta tarvittavat kentät
(def +testi-urakka+
  {:alkupvm  (pvm/hoitokauden-alkupvm 2015)
   :loppupvm (pvm/hoitokauden-loppupvm 2020)
   :tyyppi :hoito})

(deftest hae-urakan-hoitokaudet []
         (let [hoitokaudet (s/hoitokaudet +testi-urakka+)
               viesti "hae-urakan-hoitokaudet"]
           (is (= 5 (count hoitokaudet)) viesti)
           (is (= 5 (count (into #{} (map #(first %) hoitokaudet)))) viesti)
           (is (= 5 (count (into #{} (map #(second %) hoitokaudet)))) viesti)
           (doseq [hk hoitokaudet]
             (is (< (first hk) (second hk)) viesti)
             (is (= 1 (t/day (first hk))) viesti)
             (is (= 10 (t/month (first hk))) viesti)
             (is (= 30 (t/day (second hk))) viesti)
             (is (= 9 (t/month (second hk))) viesti))))

(def +pilkottavat-tyo+
  [{:alkupvm       (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
    :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1350,
    :yksikkohinta  nil, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}])


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
   :loppupvm (pvm/hoitokauden-loppupvm 2007)
   :tyyppi :hoito})

(deftest hoitokausien-sisalto-sama []
  ;; PENDING: miten tämä toimii? jos muuta +hoitokausien-tyorivit-samat+ jotain kohtaa, en saa failaamaan
  (is (s/hoitokausien-sisalto-sama? +hoitokausien-tyorivit-samat+))
  (is (not (s/hoitokausien-sisalto-sama? +hoitokausien-tyorivit-erit+))))




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

(deftest toiden-kustannusten-summa-yks-hint-tyot []
         (is (= 150.001 (s/toiden-kustannusten-summa +monen-hoitokauden-tyorivit+)) "toiden-kustannusten-summa-yks-hint-tyot"))

(def +monen-hoitokauden-tyorivit-kok-hint-tyot+
  [{:vuosi 2015 :kuukausi 3 :summa 1000}
   {:vuosi 2015 :kuukausi 4 :summa 200}
   {:vuosi 2015 :kuukausi 5 :summa 30}
   {:vuosi 2015 :kuukausi 6 :summa 5}])

(deftest toiden-kustannusten-summa-kok-hint-tyot []
         (is (= 1235 (s/toiden-kustannusten-summa +monen-hoitokauden-tyorivit-kok-hint-tyot+
                                                  :summa)) "toiden-kustannusten-summa-kok-hint-tyot"))

(deftest jaljella-olevien-hoitokausien-rivit []
  (let [kaudet (s/tulevat-hoitokaudet +testi-urakka-kaksi-vuotta+ [(pvm/hoitokauden-alkupvm 2006) (pvm/hoitokauden-loppupvm 2007)])
        jaljelle-jaavat-rivit (flatten (s/jaljella-olevien-hoitokausien-rivit
                      +hoitokausien-tyorivit-erit+ kaudet))
        viesti "jaljella-olevien-hoitokausien-rivit"]
    (is (= 4 (count (flatten +hoitokausien-tyorivit-erit+))) viesti)
    (is (= 2 (count jaljelle-jaavat-rivit)) viesti)))

(deftest tietojen-kopiointi-tuleville-hoitokausille []
  (let [alkupvm (->pvm "01.10.2006")
        loppupvm (->pvm "30.09.2007")
        rivit [{:maara 66 :alkupvm alkupvm :loppupvm loppupvm}]
        
        kopioidut (s/rivit-tulevillekin-kausille {:alkupvm (->pvm "01.10.2005")
                                                  :loppupvm (->pvm "30.09.2010")
                                                  :tyyppi :hoito}
                                                 rivit
                                                 [alkupvm loppupvm])]
    (is (= 4 (count kopioidut)))
    (is (= (->pvm "01.10.2006") (:alkupvm (first kopioidut))))
    (is (= (->pvm "30.09.2010") (:loppupvm (last kopioidut))))
    (is (every? #(= (:maara %) 66))) 
    ))

(deftest tietojen-kopiointi-tuleville-hoitokausille-kok-hint-tyot []
         (let [urakka {:alkupvm  (pvm/hoitokauden-alkupvm 2005)
                       :loppupvm (pvm/hoitokauden-loppupvm 2010)
                       :tyyppi :hoito}
               alkupvm (pvm/hoitokauden-alkupvm 2006)
               loppupvm (pvm/hoitokauden-loppupvm 2007)
               rivit [{:maksupvm (->pvm "15.02.2006")
                       :alkupvm  alkupvm :loppupvm loppupvm ;;hoitokauden
                       :kuukausi 2 :vuosi 2006
                       :summa    62015.50}]
               kopioidut (s/rivit-tulevillekin-kausille-kok-hint-tyot urakka
                                                                      rivit
                                                                      [alkupvm loppupvm])]
           (is (= 4 (count kopioidut)))
           (is (= (->pvm "15.02.2007") (:maksupvm (first kopioidut))))
           (is (= (->pvm "15.02.2010") (:maksupvm (last kopioidut))))
           (is (every? #(= (:kuukausi %) 2) kopioidut))
           (is (every? #(= (:summa %) 62015.50) kopioidut))
           (is (= 248062 (s/toiden-kustannusten-summa kopioidut
                                                 :summa)))))

(def +ryhmiteltava+ [{:alkupvm (pvm/hoitokauden-alkupvm 2005) :loppupvm (pvm/hoitokauden-loppupvm 2006) :id 1}
                     ;; 2006-2007 jätetään välistä
                     {:alkupvm (pvm/hoitokauden-alkupvm 2007) :loppupvm (pvm/hoitokauden-loppupvm 2008) :id 2}])

(deftest hoitokausittain-ryhmittely
  (let [r (s/ryhmittele-hoitokausittain +ryhmiteltava+)
        kaudet (sort-by first (keys r))]
    (is (= 2 (count kaudet)))
    (is (= (pvm/hoitokauden-alkupvm 2005) (ffirst kaudet)))
    (is (= (pvm/hoitokauden-alkupvm 2007) (first (second kaudet))))

    ;; rivi id:llä 1 on 2005-2006 hoitokauden ensimmäinen rivi
    (is (= 1 (-> r (get [(pvm/hoitokauden-alkupvm 2005) (pvm/hoitokauden-loppupvm 2006)]) first :id)))

    ;; 2006-2007 ei ole lainkaan ryhmää
    (is (nil? (-> r (get [(pvm/hoitokauden-alkupvm 2006) (pvm/hoitokauden-loppupvm 2007)]))))
        
    ;; rivi id:llä 2 on 2007-2008 hoitokauden ensimmäinen rivi
    (is (= 2 (-> r (get [(pvm/hoitokauden-alkupvm 2007) (pvm/hoitokauden-loppupvm 2008)]) first :id)))
    ))

(deftest hoitokausittain-ryhmittely-tyhjat-mukana
  (let [hoitokaudet (s/hoitokaudet {:alkupvm (pvm/hoitokauden-alkupvm 2005)
                                    :loppupvm (pvm/hoitokauden-loppupvm 2008)
                                    :tyyppi :hoito})
        r (s/ryhmittele-hoitokausittain +ryhmiteltava+ hoitokaudet)
        kaudet (sort-by first (keys r))]
    (is (= 3 (count kaudet)))
    (is (= (pvm/hoitokauden-alkupvm 2005) (ffirst kaudet)))
    (is (= (pvm/hoitokauden-alkupvm 2006) (first (second kaudet))))
    (is (= (pvm/hoitokauden-alkupvm 2007) (first (last kaudet))))

    ;; rivi id:llä 1 on 2005-2006 hoitokauden ensimmäinen rivi
    (is (= 1 (-> r (get [(pvm/hoitokauden-alkupvm 2005) (pvm/hoitokauden-loppupvm 2006)]) first :id)))

    ;; 2006-2007 on tyhjä ryhmä 
    (is (= [] (-> r (get [(pvm/hoitokauden-alkupvm 2006) (pvm/hoitokauden-loppupvm 2007)]))))
        
    ;; rivi id:llä 2 on 2007-2008 hoitokauden ensimmäinen rivi
    (is (= 2 (-> r (get [(pvm/hoitokauden-alkupvm 2007) (pvm/hoitokauden-loppupvm 2008)]) first :id)))
     
    ))

(deftest varoita-ylikirjoituksesta-jos-muuttunut
  (is (s/varoita-ylikirjoituksesta? (s/ryhmittele-hoitokausittain
                                     [(hk-rivi 2007 {:maara 50})
                                      (hk-rivi 2008 {:maara 66})])
                                    
                                    (hk 2007))))

(deftest ala-varoita-ylikirjoituksesta-jos-vain-tyhjia
  (is (not (s/varoita-ylikirjoituksesta?
            (s/ryhmittele-hoitokausittain
             [(hk-rivi 2007 {:maara 50})]
             [(hk 2007) (hk 2008) (hk 2009)])
            (hk 2007)))))

(deftest ala-varoita-ylikirjoituksesta-jos-samoja
  (is (not (s/varoita-ylikirjoituksesta?
            (s/ryhmittele-hoitokausittain
             [(hk-rivi 2007 {:maara 40})
              (hk-rivi 2008 {:maara 50})
              (hk-rivi 2009 {:maara 50})])
            (hk 2008)))))


(deftest aseta-hoitokausi-testi-1 []
  (let [hoitokaudet (s/hoitokaudet {:tyyppi :hoito :alkupvm (pvm/hoitokauden-alkupvm 2012) :loppupvm (pvm/hoitokauden-loppupvm 2015)}) 
        rivi {:vuosi 2013
              :kuukausi 6}]
    (is (= (pvm/hoitokauden-alkupvm 2012) (:alkupvm (kokhint-tyot/aseta-hoitokausi hoitokaudet rivi))))
    (is (= (pvm/hoitokauden-loppupvm 2013) (:loppupvm (kokhint-tyot/aseta-hoitokausi hoitokaudet rivi))))))
    
(deftest aseta-hoitokausi-testi-2 []
  (let [hoitokaudet (s/hoitokaudet {:tyyppi :hoito :alkupvm (pvm/hoitokauden-alkupvm 2012) :loppupvm (pvm/hoitokauden-loppupvm 2015)}) 
        rivi {:vuosi 2013
              :kuukausi 11}]
    (is (= (pvm/hoitokauden-alkupvm 2013) (:alkupvm (kokhint-tyot/aseta-hoitokausi hoitokaudet rivi))))
    (is (= (pvm/hoitokauden-loppupvm 2014) (:loppupvm (kokhint-tyot/aseta-hoitokausi hoitokaudet rivi))))))

(deftest yllapitourakan-sopimuskaudet-monta-vuotta
  (let [kaudet (s/hoitokaudet {:tyyppi :tiemerkinta
                               :alkupvm (pvm/->pvm "1.10.2007")
                               :loppupvm (pvm/->pvm "30.9.2012")})]
    (is (= 6 (count kaudet)) "kuusi sopimuskautta")))

(deftest yllapitourakan-sopimuskaudet-yksi-vuosi
  (let [kaudet (s/hoitokaudet {:tyyppi :valaistus
                               :alkupvm (pvm/->pvm "7.7.2012")
                               :loppupvm (pvm/->pvm "1.8.2012")})]
    (is (= 1 (count kaudet)) "1 sopimuskausi")
    (let [[alku loppu] (first kaudet)]
      (is (= alku (pvm/->pvm "7.7.2012")))
      (is (= loppu (pvm/->pvm "1.8.2012"))))))

(deftest yllapitourakan-sopimuskaudet-kaksi-vuotta
  (let [kaudet (s/hoitokaudet {:tyyppi :paallystys
                               :alkupvm (pvm/->pvm "1.11.2014")
                               :loppupvm (pvm/->pvm "8.4.2015")})]
    (is (= 2 (count kaudet)))
    (let [[alku loppu] (first kaudet)]
      (is (= alku (pvm/->pvm "1.11.2014")))
      (is (= loppu (pvm/->pvm "31.12.2014"))))
    (let [[alku loppu] (second kaudet)]
      (is (= alku (pvm/->pvm "1.1.2015")))
      (is (= loppu (pvm/->pvm "8.4.2015"))))))

      
