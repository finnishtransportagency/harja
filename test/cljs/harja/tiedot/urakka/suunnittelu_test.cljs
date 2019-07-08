(ns harja.tiedot.urakka.suunnittelu-test
  (:require [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.suunnittelu.yksikkohintaiset-tyot :as ykshint-tyot]
            [harja.tiedot.urakka.suunnittelu.kokonaishintaiset-tyot :as kokhint-tyot]
            [cljs-time.core :as t]
            [cljs.test :as test :refer-macros [deftest is]]
            [harja.loki :refer [log]]
            [harja.pvm :refer [->pvm] :as pvm]
            [harja.ui.grid :as grid]))

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
  {:alkupvm (pvm/hoitokauden-alkupvm 2015)
   :loppupvm (pvm/hoitokauden-loppupvm 2020)
   :tyyppi :hoito})

(deftest hae-urakan-hoitokaudet []
                                (let [hoitokaudet (u/hoito-tai-sopimuskaudet +testi-urakka+)
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
  [{:alkupvm (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
    :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1350,
    :yksikkohinta nil, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}])


(def +kannan-rivit+
  [{:alkupvm (pvm/vuoden-eka-pvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
    :maara 19 :urakka 1, :tehtava 1350,
    :yksikkohinta nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
   {:alkupvm (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/vuoden-viim-pvm 2006), :yksikko "km",
    :maara 1012 :urakka 1, :tehtava 1350,
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
  [[{:alkupvm (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 40, :tehtava 1350,
     :yksikkohinta 10, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
    {:alkupvm (pvm/hoitokauden-alkupvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2007), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 40, :tehtava 1350,
     :yksikkohinta 10, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}]

   [{:alkupvm (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 40, :tehtava 1349,
     :yksikkohinta 10, :maara nil, :tehtavan_nimi "Jäätien hoito", :sopimus 2}
    {:alkupvm (pvm/hoitokauden-alkupvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2007), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 40, :tehtava 1349,
     :yksikkohinta 10, :maara nil, :tehtavan_nimi "Jäätien hoito", :sopimus 2}]]
  )

(def +hoitokausien-tyorivit-erit+
  [[{:alkupvm (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
     :maara-kkt-1-9 23 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1350,
     :yksikkohinta nil, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
    {:alkupvm (pvm/hoitokauden-alkupvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2007), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1350,
     :yksikkohinta nil, :maara nil, :tehtavan_nimi "Tien auraaminen", :sopimus 2}]

   [{:alkupvm (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1349,
     :yksikkohinta nil, :maara nil, :tehtavan_nimi "Jäätien hoito", :sopimus 2}
    {:alkupvm (pvm/hoitokauden-alkupvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2007), :yksikko "km",
     :maara-kkt-1-9 3 :maara-kkt-10-12 1, :urakka 1, :yhteensa 0, :tehtava 1349,
     :yksikkohinta nil, :maara nil, :tehtavan_nimi "Jäätien hoito", :sopimus 2}]]
  )
(def +testi-urakka-kaksi-vuotta+
  {:alkupvm (pvm/hoitokauden-alkupvm 2005)
   :loppupvm (pvm/hoitokauden-loppupvm 2007)
   :tyyppi :hoito})

(deftest hoitokausien-sisalto-sama []
                                   ;; PENDING: miten tämä toimii? jos muuta +hoitokausien-tyorivit-samat+ jotain kohtaa, en saa failaamaan
                                   (is (s/hoitokausien-sisalto-sama? +hoitokausien-tyorivit-samat+))
                                   (is (not (s/hoitokausien-sisalto-sama? +hoitokausien-tyorivit-erit+))))




(def +monen-hoitokauden-tyorivit+
  [{:alkupvm (pvm/hoitokauden-alkupvm 2005), :loppupvm (pvm/hoitokauden-loppupvm 2006), :yksikko "km",
    :maara-kkt-1-9 8 :maara-kkt-10-12 2, :urakka 1, :yhteensa 50.001, :tehtava 1350,
    :yksikkohinta 5, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
   {:alkupvm (pvm/hoitokauden-alkupvm 2006), :loppupvm (pvm/hoitokauden-loppupvm 2007), :yksikko "km",
    :maara-kkt-1-9 8 :maara-kkt-10-12 2, :urakka 1, :yhteensa 50, :tehtava 1350,
    :yksikkohinta 5, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
   {:alkupvm (pvm/hoitokauden-alkupvm 2007), :loppupvm (pvm/hoitokauden-loppupvm 2008), :yksikko "km",
    :maara-kkt-1-9 8 :maara-kkt-10-12 2, :urakka 1, :yhteensa 50, :tehtava 1350,
    :yksikkohinta 5, :tehtavan_nimi "Tien auraaminen", :sopimus 2}
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
                                             (let [kaudet (u/tulevat-hoitokaudet +testi-urakka-kaksi-vuotta+ [(pvm/hoitokauden-alkupvm 2006) (pvm/hoitokauden-loppupvm 2007)])
                                                   jaljelle-jaavat-rivit (flatten (u/jaljella-olevien-hoitokausien-rivit
                                                                                    +hoitokausien-tyorivit-erit+ kaudet))
                                                   viesti "jaljella-olevien-hoitokausien-rivit"]
                                               (is (= 4 (count (flatten +hoitokausien-tyorivit-erit+))) viesti)
                                               (is (= 2 (count jaljelle-jaavat-rivit)) viesti)))

(deftest tietojen-kopiointi-tuleville-hoitokausille []
                                                    (let [alkupvm (->pvm "01.10.2006")
                                                          loppupvm (->pvm "30.09.2007")
                                                          rivit [{:maara 66 :alkupvm alkupvm :loppupvm loppupvm}]

                                                          kopioidut (u/rivit-tulevillekin-kausille {:alkupvm (->pvm "01.10.2005")
                                                                                                    :loppupvm (->pvm "30.09.2010")
                                                                                                    :tyyppi :hoito}
                                                                                                   rivit
                                                                                                   [alkupvm loppupvm])]
                                                      (is (= 4 (count kopioidut)))
                                                      (is (pvm/sama-pvm? (->pvm "01.10.2006") (:alkupvm (first kopioidut))))
                                                      (is (pvm/sama-pvm? (->pvm "30.09.2010") (:loppupvm (last kopioidut))))
                                                      (is (every? #(= (:maara %) 66)))
                                                      ))

(deftest tietojen-kopiointi-tuleville-hoitokausille-kok-hint-tyot []
                                                                  (let [urakka {:alkupvm (pvm/hoitokauden-alkupvm 2005)
                                                                                :loppupvm (pvm/hoitokauden-loppupvm 2010)
                                                                                :tyyppi :hoito}
                                                                        alkupvm (pvm/hoitokauden-alkupvm 2006)
                                                                        loppupvm (pvm/hoitokauden-loppupvm 2007)
                                                                        rivit [{:maksupvm (->pvm "15.02.2006")
                                                                                :alkupvm alkupvm :loppupvm loppupvm ;;hoitokauden
                                                                                :kuukausi 2 :vuosi 2006
                                                                                :summa 62015.50}]
                                                                        kopioidut (u/rivit-tulevillekin-kausille-kok-hint-tyot urakka
                                                                                                                               rivit
                                                                                                                               [alkupvm loppupvm])]
                                                                    (is (= 4 (count kopioidut)))
                                                                    (is (= (->pvm "15.02.2007") (:maksupvm (first kopioidut))))
                                                                    (is (= (->pvm "15.02.2010") (:maksupvm (last kopioidut))))
                                                                    (is (every? #(= (:kuukausi %) 2) kopioidut))
                                                                    (is (every? #(= (:summa %) 62015.50) kopioidut))
                                                                    (is (= 248062 (s/toiden-kustannusten-summa kopioidut
                                                                                                               :summa)))))

(def +ryhmiteltava+ [{:alkupvm (pvm/hoitokauden-alkupvm 2005) :loppupvm (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2006)) :id 1}
                     ;; 2006-2007 jätetään välistä
                     {:alkupvm (pvm/hoitokauden-alkupvm 2007) :loppupvm (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2008)) :id 2}])

(deftest hoitokausittain-ryhmittely
  (let [r (u/ryhmittele-hoitokausittain +ryhmiteltava+)
        kaudet (sort-by first (keys r))]
    (is (= 2 (count kaudet)))
    (is (= (pvm/hoitokauden-alkupvm 2005) (ffirst kaudet)))
    (is (= (pvm/hoitokauden-alkupvm 2007) (first (second kaudet))))

    ;; rivi id:llä 1 on 2005-2006 hoitokauden ensimmäinen rivi
    (is (= 1 (-> r (get [(pvm/hoitokauden-alkupvm 2005) (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2006))]) first :id)))

    ;; 2006-2007 ei ole lainkaan ryhmää
    (is (nil? (-> r (get [(pvm/hoitokauden-alkupvm 2006) (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2007))]))))

    ;; rivi id:llä 2 on 2007-2008 hoitokauden ensimmäinen rivi
    (is (= 2 (-> r (get [(pvm/hoitokauden-alkupvm 2007) (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2008))]) first :id)))
    ))

(deftest hoitokausittain-ryhmittely-tyhjat-mukana
  (let [hoitokaudet (u/hoito-tai-sopimuskaudet {:alkupvm (pvm/hoitokauden-alkupvm 2005)
                                                :loppupvm (pvm/hoitokauden-loppupvm 2008)
                                                :tyyppi :hoito})
        r (u/ryhmittele-hoitokausittain +ryhmiteltava+ hoitokaudet)
        kaudet (sort-by first (keys r))]
    (is (= 3 (count kaudet)))
    (is (= (pvm/hoitokauden-alkupvm 2005) (ffirst kaudet)))
    (is (= (pvm/hoitokauden-alkupvm 2006) (first (second kaudet))))
    (is (= (pvm/hoitokauden-alkupvm 2007) (first (last kaudet))))

    ;; rivi id:llä 1 on 2005-2006 hoitokauden ensimmäinen rivi
    (is (= 1 (-> r (get [(pvm/hoitokauden-alkupvm 2005) (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2006))]) first :id)))

    ;; 2006-2007 on tyhjä ryhmä
    (is (= [] (-> r (get [(pvm/hoitokauden-alkupvm 2006) (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2007))]))))

    ;; rivi id:llä 2 on 2007-2008 hoitokauden ensimmäinen rivi
    (is (= 2 (-> r (get [(pvm/hoitokauden-alkupvm 2007) (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2008))]) first :id)))

    ))

(deftest varoita-ylikirjoituksesta-jos-muuttunut
  (is (s/varoita-ylikirjoituksesta? (u/ryhmittele-hoitokausittain
                                      [(hk-rivi 2007 {:maara 50})
                                       (hk-rivi 2008 {:maara 66})])

                                    (hk 2007))))

(deftest ala-varoita-ylikirjoituksesta-jos-vain-tyhjia
  (is (not (s/varoita-ylikirjoituksesta?
             (u/ryhmittele-hoitokausittain
               [(hk-rivi 2007 {:maara 50})]
               [(hk 2007) (hk 2008) (hk 2009)])
             (hk 2007)))))

(deftest ala-varoita-ylikirjoituksesta-jos-samoja
  (is (not (s/varoita-ylikirjoituksesta?
             (u/ryhmittele-hoitokausittain
               [(hk-rivi 2007 {:maara 40})
                (hk-rivi 2008 {:maara 50})
                (hk-rivi 2009 {:maara 50})])
             (hk 2008)))))


(deftest aseta-hoitokausi-testi-1 []
                                  (let [hoitokaudet (u/hoito-tai-sopimuskaudet {:tyyppi :hoito :alkupvm (pvm/hoitokauden-alkupvm 2012) :loppupvm (pvm/hoitokauden-loppupvm 2015)})
                                        rivi {:vuosi 2013
                                              :kuukausi 6}]
                                    (is (= (pvm/hoitokauden-alkupvm 2012) (:alkupvm (kokhint-tyot/aseta-hoitokausi hoitokaudet rivi))))
                                    (is (= (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2013)) (:loppupvm (kokhint-tyot/aseta-hoitokausi hoitokaudet rivi))))))

(deftest aseta-hoitokausi-testi-2 []
                                  (let [hoitokaudet (u/hoito-tai-sopimuskaudet {:tyyppi :hoito :alkupvm (pvm/hoitokauden-alkupvm 2012) :loppupvm (pvm/hoitokauden-loppupvm 2015)})
                                        rivi {:vuosi 2013
                                              :kuukausi 11}]
                                    (is (= (pvm/hoitokauden-alkupvm 2013) (:alkupvm (kokhint-tyot/aseta-hoitokausi hoitokaudet rivi))))
                                    (is (= (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2014)) (:loppupvm (kokhint-tyot/aseta-hoitokausi hoitokaudet rivi))))))
(deftest aseta-hoitokausi-testi-3 []
                                  (let [hoitokaudet (u/hoito-tai-sopimuskaudet {:tyyppi :teiden-hoito :alkupvm (pvm/hoitokauden-alkupvm 2019) :loppupvm (pvm/hoitokauden-loppupvm 2024)})
                                        rivi {:vuosi 2021
                                              :kuukausi 3}]
                                    (is (= (pvm/hoitokauden-alkupvm 2021) (:alkupvm (kokhint-tyot/aseta-hoitokausi hoitokaudet rivi))))
                                    (is (= (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm 2022)) (:loppupvm (kokhint-tyot/aseta-hoitokausi hoitokaudet rivi))))))
(deftest foo (is (= 1 1)))

(deftest yllapitourakan-sopimuskaudet-monta-vuotta
  (let [kaudet (u/hoito-tai-sopimuskaudet {:tyyppi :tiemerkinta
                                           :alkupvm (pvm/->pvm "1.10.2007")
                                           :loppupvm (pvm/->pvm "30.9.2012")})]
    (is (= 6 (count kaudet)) "kuusi sopimuskautta")))

(deftest yllapitourakan-sopimuskaudet-yksi-vuosi
  (let [kaudet (u/hoito-tai-sopimuskaudet {:tyyppi :valaistus
                                           :alkupvm (pvm/->pvm "7.7.2012")
                                           :loppupvm (pvm/->pvm "1.8.2012")})]
    (is (= 1 (count kaudet)) "1 sopimuskausi")
    (let [[alku loppu] (first kaudet)]
      (is (pvm/sama-pvm? alku (pvm/->pvm "7.7.2012")))
      (is (pvm/sama-pvm? loppu (pvm/paivan-lopussa (pvm/->pvm "1.8.2012")))))))

(deftest yllapitourakan-sopimuskaudet-kaksi-vuotta
  (let [kaudet (u/hoito-tai-sopimuskaudet {:tyyppi :paallystys
                                           :alkupvm (pvm/->pvm "1.11.2014")
                                           :loppupvm (pvm/->pvm "8.4.2015")})]
    (is (= 2 (count kaudet)))
    (let [[alku loppu] (first kaudet)]
      (is (pvm/sama-pvm? alku (pvm/->pvm "1.11.2014")))
      (is (pvm/sama-pvm? loppu (pvm/paivan-lopussa (pvm/->pvm "31.12.2014")))))
    (let [[alku loppu] (second kaudet)]
      (is (pvm/sama-pvm? alku (pvm/->pvm "1.1.2015")))
      (is (pvm/sama-pvm? loppu (pvm/paivan-lopussa (pvm/->pvm "8.4.2015")))))))


(def +prosessoitavat-rivit+
  [{:loppupvm (pvm/->pvm "31.12.2005"), :yksikko "tiekm", :tehtava 1369, :urakka 1, :yksikkohinta 5, :maara 10,
    :id 33, :tehtavan_nimi "K2", :sopimus 2, :alkupvm (pvm/->pvm "1.10.2005"), :tehtavan_id 1369}
   {:loppupvm (pvm/->pvm "30.09.2006"), :yksikko "tiekm", :tehtava 1369, :urakka 1, :yksikkohinta 5, :maara 20,
    :id 34, :tehtavan_nimi "K2", :sopimus 2, :alkupvm (pvm/->pvm "1.1.2006"), :tehtavan_id 1369}

   {:loppupvm (pvm/->pvm "31.12.2005"), :yksikko "tiekm", :tehtava 1369, :urakka 1, :yksikkohinta 10, :maara 30,
    :id 31, :tehtavan_nimi "K2", :sopimus 1, :alkupvm (pvm/->pvm "1.10.2005"), :tehtavan_id 1369}
   {:loppupvm (pvm/->pvm "30.09.2006"), :yksikko "tiekm", :tehtava 1369, :urakka 1, :yksikkohinta 10, :maara 40,
    :id 32, :tehtavan_nimi "K2", :sopimus 1, :alkupvm (pvm/->pvm "1.1.2006"), :tehtavan_id 1369}])

(deftest prosessoi-ykshint-tyorivit
  (let [tulos (s/prosessoi-tyorivit {:tyyppi :hoito} +prosessoitavat-rivit+)
        sopimus-2-tyorivi (first (filter #(= 2 (:sopimus %)) tulos))
        sopimus-1-tyorivi (first (filter #(= 1 (:sopimus %)) tulos))
        viesti "yksikköhintaisten töiden prosessoi työrivit"]

    ;; työrivi sopimukselle 2
    (is (= 1 (:urakka sopimus-2-tyorivi)) viesti)
    (is (= 2 (:sopimus sopimus-2-tyorivi)) viesti)
    (is (= 1369 (:tehtava sopimus-2-tyorivi)) viesti)

    (is (= "tiekm" (:yksikko sopimus-2-tyorivi)) viesti)
    (is (= 5 (:yksikkohinta sopimus-2-tyorivi)) viesti)

    (is (= 20 (:maara-kkt-1-9 sopimus-2-tyorivi)))
    (is (= 10 (:maara-kkt-10-12 sopimus-2-tyorivi)) viesti)
    (is (= 30 (:maara sopimus-2-tyorivi)) viesti)

    (is (= 100 (:yhteensa-kkt-1-9 sopimus-2-tyorivi)) viesti)
    (is (= 50 (:yhteensa-kkt-10-12 sopimus-2-tyorivi)) viesti)
    (is (= 150 (:yhteensa sopimus-2-tyorivi)) viesti)

    ;; työrivi sopimukselle 1
    (is (= 1 (:urakka sopimus-1-tyorivi)) viesti)
    (is (= 1 (:sopimus sopimus-1-tyorivi)) viesti)
    (is (= 1369 (:tehtava sopimus-1-tyorivi)) viesti)

    (is (= "tiekm" (:yksikko sopimus-1-tyorivi)) viesti)
    (is (= 10 (:yksikkohinta sopimus-1-tyorivi)) viesti)

    (is (= 40 (:maara-kkt-1-9 sopimus-1-tyorivi)) viesti)
    (is (= 30 (:maara-kkt-10-12 sopimus-1-tyorivi)) viesti)
    (is (= 70 (:maara sopimus-1-tyorivi)) viesti)

    (is (= 400 (:yhteensa-kkt-1-9 sopimus-1-tyorivi)) viesti)
    (is (= 300 (:yhteensa-kkt-10-12 sopimus-1-tyorivi)) viesti)
    (is (= 700 (:yhteensa sopimus-1-tyorivi)) viesti)))

(deftest vesivaylaurakan-hoitokaudet
  (is (= [[(pvm/->pvm "1.8.2015") (pvm/paivan-lopussa (pvm/->pvm "31.7.2016"))]
          [(pvm/->pvm "1.8.2016") (pvm/paivan-lopussa (pvm/->pvm "31.7.2017"))]
          [(pvm/->pvm "1.8.2017") (pvm/paivan-lopussa (pvm/->pvm "31.7.2018"))]]
         (u/hoito-tai-sopimuskaudet {:alkupvm (pvm/->pvm "1.8.2015")
                                     :loppupvm (pvm/->pvm "31.7.2018")
                                     :tyyppi :vesivayla-hoito}))))
