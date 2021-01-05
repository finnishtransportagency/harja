(ns harja.domain.kohteiden-validointi-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [clojure.set :as clj-set]
            [harja.testi :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.domain.yllapitokohde :as yllapitokohteet]))

(defn tasmaa-poikkeus [{:keys [type virheet]} tyyppi koodi viesti]
  (and
    (= tyyppi type)
    (some (fn [virhe] (and (= koodi (:koodi virhe)) (.contains (:viesti virhe) viesti)))
          virheet)))

(deftest jarjesta-yllapitokohteet
  (let [yllapitokohteet [{:tunniste {:id 4}
                          :sijainti {:tie 20
                                     :numero 20
                                     :ajorata 2
                                     :ajr 2
                                     :kaista 1
                                     :aosa 3
                                     :aet 1
                                     :losa 4
                                     :let 100}}
                         {:tunniste {:id 1}
                          :sijainti {:tie 20
                                     :numero 20
                                     :ajorata 1
                                     :ajr 1
                                     :kaista 1
                                     :aosa 1
                                     :aet 1
                                     :losa 3
                                     :let 1}}
                         {:tunniste {:id 3}
                          :sijainti {:tie 20
                                     :numero 20
                                     :ajorata 2
                                     :ajr 2
                                     :kaista 1
                                     :aosa 1
                                     :aet 1
                                     :losa 3
                                     :let 1}}
                         {:tunniste {:id 2}
                          :sijainti {:tie 20
                                     :numero 20
                                     :ajorata 1
                                     :ajr 1
                                     :kaista 1
                                     :aosa 3
                                     :aet 1
                                     :losa 4
                                     :let 100}}]
        jarjestetyt (sort-by (comp yllapitokohteet/yllapitokohteen-jarjestys :sijainti) yllapitokohteet)]
    (let [hae-tunniste (fn [n] (get-in (nth jarjestetyt n) [:tunniste :id]))]
      (is (= 1 (hae-tunniste 0)))
      (is (= 2 (hae-tunniste 1)))
      (is (= 3 (hae-tunniste 2)))
      (is (= 4 (hae-tunniste 3))))))

(s/def ::paalupiste-paaluvalin-sisalla
  (s/with-gen (s/cat :paalupiste ::yllapitokohteet/tr-paalupiste
                     :paaluvali ::yllapitokohteet/tr-paaluvali)
              #(gen/fmap (fn [{tr-paaluvali-alkuosa :tr-alkuosa
                               tr-paaluvali-alkuetaisyys :tr-alkuetaisyys
                               tr-paaluvali-loppuosa :tr-loppuosa
                               tr-paaluvali-loppuetaisyys :tr-loppuetaisyys :as tr-paaluvali}]
                           (let [tr-piste-osa (rand-nth (range tr-paaluvali-alkuosa
                                                               (inc tr-paaluvali-loppuosa)))
                                 tr-piste-etaisyys (cond
                                                     (= tr-piste-osa tr-paaluvali-alkuosa tr-paaluvali-loppuosa) (rand-nth (range tr-paaluvali-alkuetaisyys
                                                                                                                                  (inc tr-paaluvali-loppuetaisyys)))
                                                     (= tr-piste-osa tr-paaluvali-alkuosa) (rand-nth (range tr-paaluvali-alkuetaisyys
                                                                                                            (+ tr-paaluvali-alkuetaisyys 1000)))
                                                     (= tr-piste-osa tr-paaluvali-loppuosa) (rand-nth (range (inc tr-paaluvali-loppuetaisyys)))
                                                     :else (rand-int 10000))]
                             [{:tr-numero (:tr-numero tr-paaluvali)
                               :tr-alkuosa tr-piste-osa
                               :tr-alkuetaisyys tr-piste-etaisyys}
                              tr-paaluvali]))
                         (s/gen ::yllapitokohteet/tr-paaluvali))))

(s/def ::paaluvali-paaluvalin-sisalla
  (s/with-gen (s/cat :paaluvali-1 ::yllapitokohteet/tr-paaluvali
                     :paaluvali-2 ::yllapitokohteet/tr-paaluvali
                     :kokonaan? boolean?)
              #(gen/fmap (fn [[{alkuosa-1 :tr-alkuosa
                                alkuetaisyys-1 :tr-alkuetaisyys
                                loppuosa-1 :tr-loppuosa
                                loppuetaisyys-1 :tr-loppuetaisyys :as paaluvali-1}
                               kokonaan?]]
                           (try
                             (let [;; range funktio palauttaa tyhjän listan, jos molemmat argumentit on sama.
                                   ;; Halutaan kummiskin tämä sama arvo listassa.
                                   range-not-empty (fn this
                                                     ([x] (this 0 x))
                                                     ([x1 x2]
                                                      (if (= x1 x2)
                                                        (list x1)
                                                        (range x1 x2))))
                                   alkuosa-2 (if kokonaan?
                                               (rand-nth (range-not-empty alkuosa-1 (inc loppuosa-1)))
                                               (rand-nth (range-not-empty (inc loppuosa-1))))
                                   ;; Jos alla oleva ehto täyttyy, niin paaluvalia-2 ei saa mitenkään
                                   ;; päälekkäin ykkösen kanssa. Siksipä nostetaan osan yksi loppuetaisyyttä vähän.
                                   loppuetaisyys-1 (if (and (= loppuosa-1 alkuosa-2)
                                                            (= loppuetaisyys-1 0))
                                                     (inc loppuetaisyys-1)
                                                     loppuetaisyys-1)
                                   loppuosa-2 (if kokonaan?
                                                (rand-nth (range-not-empty alkuosa-1 (inc loppuosa-1)))
                                                (rand-nth (range-not-empty alkuosa-1 (+ loppuosa-1 100))))
                                   alkuetaisyys-2 (cond
                                                    (= alkuosa-1 loppuosa-1 alkuosa-2) (if kokonaan?
                                                                                         ;; Tarkoittaa, että myös loppuosa-2 on sama
                                                                                         (rand-nth (range-not-empty alkuetaisyys-1 loppuetaisyys-1))
                                                                                         (rand-nth (range-not-empty loppuetaisyys-1)))
                                                    (= alkuosa-1 alkuosa-2) (if kokonaan?
                                                                              (rand-nth (range-not-empty alkuetaisyys-1 (+ alkuetaisyys-1 1000)))
                                                                              (rand-nth (range-not-empty (+ alkuetaisyys-1 1000))))
                                                    (= loppuosa-1 alkuosa-2) (rand-nth (range-not-empty loppuetaisyys-1))
                                                    :else (rand-int 10000))
                                   loppuetaisyys-2 (cond
                                                     (= alkuosa-1 loppuosa-1 alkuosa-2) (if kokonaan?
                                                                                          ;; Tarkoittaa, että myös loppuosa-2 on sama
                                                                                          (rand-nth (range-not-empty alkuetaisyys-2 (inc loppuetaisyys-1)))
                                                                                          (rand-nth (range-not-empty (max alkuetaisyys-2 alkuetaisyys-1) (+ loppuetaisyys-1 1000))))
                                                     (and (= loppuosa-1 loppuosa-2)
                                                          (not= alkuosa-1 loppuosa-1)) (if kokonaan?
                                                                                         (rand-nth (range-not-empty (inc loppuetaisyys-1)))
                                                                                         (rand-nth (range-not-empty (+ loppuetaisyys-1 1000))))
                                                     ;; jos kokonaan? on true, niin tänne ei tulla, koska myös alkuosa-2 on tosi
                                                     (= alkuosa-1 loppuosa-2) (rand-nth (range-not-empty alkuetaisyys-1 (+ alkuetaisyys-1 1000)))
                                                     :else (rand-int 10000))]
                               [(assoc paaluvali-1 :tr-loppuetaisyys loppuetaisyys-1)
                                {:tr-numero (:tr-numero paaluvali-1)
                                 :tr-alkuosa alkuosa-2
                                 :tr-alkuetaisyys alkuetaisyys-2
                                 :tr-loppuosa loppuosa-2
                                 :tr-loppuetaisyys loppuetaisyys-2}
                                kokonaan?])
                             (catch Throwable t
                               (println "Errori nakattu argumenteilla: " paaluvali-1 " ja " kokonaan?)
                               (throw t))))
                         (gen/tuple (s/gen ::yllapitokohteet/tr-paaluvali)
                                    (gen/boolean)))))

(def oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 1})
(def oikea-tr-vali {:tr-numero 22 :tr-ajorata 1 :tr-kaista 12 :tr-alkuosa 1 :tr-alkuetaisyys 5000 :tr-loppuosa 1 :tr-loppuetaisyys 5200})

(def vaara-tr-paaluvali {:tr-numero 22 :tr-alkuosa 6 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 1})
(def vaara-tr-vali {:tr-numero 22 :tr-ajorata 0 :tr-kaista 11 :tr-alkuosa 5 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 100})

(def tr-tieto [{:tr-numero 22
                :tr-osa 6
                :pituudet {:pituus 10000
                           :ajoradat [{:osiot [{:pituus 10000
                                                :kaistat [{:tr-kaista 1 :pituus 10000 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}]
                                       :tr-ajorata 0}]
                           :tr-alkuetaisyys 0}}
               {:tr-numero 22
                :tr-osa 5
                :pituudet {:pituus 10000
                           :ajoradat [{:osiot [{:pituus 10000
                                                :kaistat [{:tr-kaista 1 :pituus 10000 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}]
                                       :tr-ajorata 0}]
                           :tr-alkuetaisyys 0}}
               {:tr-numero 22
                :tr-osa 4
                :pituudet {:pituus 14000
                           :ajoradat [{:osiot [{:pituus 14000
                                                :kaistat [{:tr-kaista 11 :pituus 14000 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}
                                               {:pituus 500
                                                :kaistat [{:tr-kaista 12 :pituus 500 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}]
                                       :tr-ajorata 1}
                                      {:osiot [{:pituus 14000
                                                :kaistat [{:tr-kaista 21 :pituus 14000 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}
                                               {:pituus 500
                                                :kaistat [{:tr-kaista 22 :pituus 500 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}]
                                       :tr-ajorata 2}]
                           :tr-alkuetaisyys 0}}
               {:tr-numero 22
                :tr-osa 3
                :pituudet {:pituus 13000
                           :ajoradat [{:osiot [{:pituus 13000
                                                :kaistat [{:tr-kaista 1 :pituus 13000 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}]
                                       :tr-ajorata 0}]
                           :tr-alkuetaisyys 0}}
               {:tr-numero 22
                :tr-osa 1
                :pituudet {:pituus 10000
                           :ajoradat [{:osiot [{:pituus 100
                                                :kaistat [{:tr-kaista 1 :pituus 100 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}
                                               {:pituus 1000
                                                :kaistat [{:tr-kaista 1 :pituus 1000 :tr-alkuetaisyys 4000}]
                                                :tr-alkuetaisyys 4000}
                                               {:pituus 4400
                                                :kaistat [{:tr-kaista 1 :pituus 4400 :tr-alkuetaisyys 5600}]
                                                :tr-alkuetaisyys 5600}]
                                       :tr-ajorata 0}
                                      {:osiot [{:pituus 3900
                                                :kaistat [{:tr-kaista 12 :pituus 3900 :tr-alkuetaisyys 100}
                                                          {:tr-kaista 11 :pituus 3900 :tr-alkuetaisyys 100}]
                                                :tr-alkuetaisyys 100}
                                               {:pituus 600
                                                :kaistat [{:tr-kaista 11 :pituus 600 :tr-alkuetaisyys 5000}
                                                          {:tr-kaista 12 :pituus 600 :tr-alkuetaisyys 5000}]
                                                :tr-alkuetaisyys 5000}]
                                       :tr-ajorata 1}
                                      {:osiot [{:pituus 3900
                                                :kaistat [{:tr-kaista 22 :pituus 3900 :tr-alkuetaisyys 100}
                                                          {:tr-kaista 21 :pituus 3900 :tr-alkuetaisyys 100}]
                                                :tr-alkuetaisyys 100}
                                               {:pituus 600
                                                :kaistat [{:tr-kaista 21 :pituus 600 :tr-alkuetaisyys 5000}
                                                          {:tr-kaista 22 :pituus 600 :tr-alkuetaisyys 5000}]
                                                :tr-alkuetaisyys 5000}]
                                       :tr-ajorata 2}]
                           :tr-alkuetaisyys 0}}])

(deftest validoi-tr-valin-muoto
  (testing "tr oikeanmuotoisuus testaus funktiot"
    (let [testaa-fns (fn [tr-paaluavli sama?]
                       (if sama?
                         (do
                           (is (yllapitokohteet/losa=aosa? oikea-tr-vali))
                           (is (yllapitokohteet/let=aet? oikea-tr-paaluvali)))
                         (do
                           (is (yllapitokohteet/losa>aosa? oikea-tr-paaluvali))
                           (is (yllapitokohteet/let>aet? oikea-tr-vali)))))]

      (is (not (yllapitokohteet/losa=aosa? oikea-tr-paaluvali)))
      (is (not (yllapitokohteet/let=aet? oikea-tr-vali)))
      (is (not (yllapitokohteet/losa>aosa? oikea-tr-vali)))
      (is (not (yllapitokohteet/let>aet? oikea-tr-paaluvali)))
      (testaa-fns oikea-tr-paaluvali false)
      (testaa-fns oikea-tr-paaluvali true)
      (doseq [tr (gen/sample (s/gen ::yllapitokohteet/tr-paaluvali))]
        (if (= (:tr-alkuosa tr) (:tr-loppuosa tr))
          (testaa-fns tr true)
          (testaa-fns tr false)))))

  (testing "tr paalupiste tr paaluvalin sisalla?"
    (is (yllapitokohteet/tr-paalupiste-tr-paaluvalin-sisalla? {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 1}
                                                              oikea-tr-paaluvali))
    (is (yllapitokohteet/tr-paalupiste-tr-paaluvalin-sisalla? {:tr-numero 22 :tr-alkuosa 5 :tr-alkuetaisyys 1}
                                                              oikea-tr-paaluvali))
    (is (yllapitokohteet/tr-paalupiste-tr-paaluvalin-sisalla? {:tr-numero 22 :tr-alkuosa 4 :tr-alkuetaisyys 100}
                                                              oikea-tr-paaluvali))
    (is (not (yllapitokohteet/tr-paalupiste-tr-paaluvalin-sisalla? {:tr-numero 22 :tr-alkuosa 5 :tr-alkuetaisyys 2}
                                                                   oikea-tr-paaluvali)))
    (doseq [[tr-paalupiste tr-paaluvali] (gen/sample (s/gen ::paalupiste-paaluvalin-sisalla))]
      (is (yllapitokohteet/tr-paalupiste-tr-paaluvalin-sisalla? tr-paalupiste tr-paaluvali))))

  (testing "tr paaluvali tr paaluvalin sisalla?"
    (is (yllapitokohteet/tr-paaluvali-tr-paaluvalin-sisalla? oikea-tr-paaluvali
                                                             {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 100
                                                              :tr-loppuosa 1 :tr-loppuetaisyys 200}))
    (is (yllapitokohteet/tr-paaluvali-tr-paaluvalin-sisalla? oikea-tr-paaluvali
                                                             {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 100
                                                              :tr-loppuosa 3 :tr-loppuetaisyys 200}))
    (is (yllapitokohteet/tr-paaluvali-tr-paaluvalin-sisalla? oikea-tr-paaluvali
                                                             {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 100
                                                              :tr-loppuosa 5 :tr-loppuetaisyys 200}
                                                             false))
    (is (not (yllapitokohteet/tr-paaluvali-tr-paaluvalin-sisalla? oikea-tr-paaluvali
                                                                  {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 100
                                                                   :tr-loppuosa 5 :tr-loppuetaisyys 200}
                                                                  true)))
    (is (yllapitokohteet/tr-paaluvali-tr-paaluvalin-sisalla? oikea-tr-paaluvali
                                                             {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0
                                                              :tr-loppuosa 1 :tr-loppuetaisyys 200}
                                                             false))
    (is (not (yllapitokohteet/tr-paaluvali-tr-paaluvalin-sisalla? oikea-tr-paaluvali
                                                                  {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0
                                                                   :tr-loppuosa 1 :tr-loppuetaisyys 200}
                                                                  true)))
    (doseq [[tr-paaluvali-1 tr-paaluvali-2 kokonaan?] (gen/sample (s/gen ::paaluvali-paaluvalin-sisalla))]
      (is (yllapitokohteet/tr-paaluvali-tr-paaluvalin-sisalla? tr-paaluvali-1 tr-paaluvali-2 kokonaan?)))))

(deftest validoi-tr-valin-oikeellisuus
  (testing "tr paalupiste tr tiedon mukainen"
    (is (yllapitokohteet/tr-paalupiste-tr-tiedon-mukainen? (dissoc oikea-tr-paaluvali :tr-loppuosa :tr-loppuetaisyys) (last tr-tieto)))
    (is (yllapitokohteet/tr-paalupiste-tr-tiedon-mukainen? (-> oikea-tr-paaluvali
                                                               (dissoc :tr-alkuosa :tr-alkuetaisyys)
                                                               (clj-set/rename-keys {:tr-loppuosa :tr-alkuosa
                                                                                     :tr-loppuetaisyys :tr-alkuetaisyys}))
                                                           (first (filter #(= (:tr-osa %) (:tr-loppuosa oikea-tr-paaluvali))
                                                                          tr-tieto)))))
  (testing "tr piste tr tiedon mukainen"
    (is (yllapitokohteet/tr-piste-tr-tiedon-mukainen? (dissoc oikea-tr-vali :tr-loppuosa :tr-loppuetaisyys) (last tr-tieto)))
    (is (yllapitokohteet/tr-piste-tr-tiedon-mukainen? (-> oikea-tr-vali
                                                          (dissoc :tr-alkuosa :tr-alkuetaisyys)
                                                          (clj-set/rename-keys {:tr-loppuosa :tr-alkuosa
                                                                                :tr-loppuetaisyys :tr-alkuetaisyys}))
                                                      (last tr-tieto))))
  (testing "validoi paikka"
    (is (nil? (yllapitokohteet/validoi-paikka oikea-tr-paaluvali tr-tieto)))))

(deftest validoi-paakohteet-paallekkain
  (testing "tr vaalit paallekkain"
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 1}))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 100 :tr-loppuosa 5 :tr-loppuetaisyys 1}))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 3 :tr-alkuetaisyys 1 :tr-loppuosa 6 :tr-loppuetaisyys 1}))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0 :tr-loppuosa 6 :tr-loppuetaisyys 1}))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0 :tr-loppuosa 3 :tr-loppuetaisyys 1}))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 1 :tr-loppuosa 3 :tr-loppuetaisyys 1}))
    (is (not (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0 :tr-loppuosa 1 :tr-loppuetaisyys 1})))
    (is (not (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 5 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 100})))))

(deftest validoi-alikohteet-paallekkain
  (testing "tr valit paallekkain"
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali oikea-tr-vali true))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 1} true))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 100 :tr-loppuosa 5 :tr-loppuetaisyys 1} true))
    (is (not (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 3 :tr-alkuetaisyys 1 :tr-loppuosa 6 :tr-loppuetaisyys 1} true)))
    (is (not (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0 :tr-loppuosa 6 :tr-loppuetaisyys 1} true)))
    (is (not (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0 :tr-loppuosa 3 :tr-loppuetaisyys 1} true)))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 1 :tr-loppuosa 3 :tr-loppuetaisyys 1} true))
    (is (not (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0 :tr-loppuosa 1 :tr-loppuetaisyys 1} true)))
    (is (not (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 5 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 100} true)))))

(deftest validoi-paakohde
  ;; oikea kohde validoituu oikein
  (is (nil? (yllapitokohteet/validoi-kohde oikea-tr-paaluvali tr-tieto)))
  ;; nil kohde aiheuttaa ongelmia
  (is (-> (yllapitokohteet/validoi-kohde nil tr-tieto) :muoto ::s/problems first :pred (= 'clojure.core/map?)))
  ;; Kun alkuetäisyys on osan ulkopuolella, tulee ongelmia
  (is-> (-> (assoc oikea-tr-paaluvali :tr-alkuetaisyys 100000)
            (yllapitokohteet/validoi-kohde tr-tieto)
            :validoitu-paikka)
        #(-> % :kohteen-tiedot count (= 1)))
  ;; Testataan, että jokaisella kohteen paaluvälikentällä tulee olla arvo
  (is-> (-> (assoc oikea-tr-paaluvali :tr-numero nil :tr-alkuosa nil :tr-alkuetaisyys nil
                                      :tr-loppuosa nil :tr-loppuetaisyys nil)
            (yllapitokohteet/validoi-kohde tr-tieto)
            :muoto
            ::s/problems)
        #(-> % count (= 5)) "Ei ole viittä ongelmaa"
        (fn [tulos] (->> tulos (filter #(= (:path %) [:tr-numero])) first)) "tr-numero validointi puuttuu"
        (fn [tulos] (->> tulos (filter #(= (:path %) [:tr-alkuosa])) first)) "tr-alkuosa validointi puuttuu"
        (fn [tulos] (->> tulos (filter #(= (:path %) [:tr-alkuetaisyys])) first)) "tr-alkuetaisyys validointi puuttuu"
        (fn [tulos] (->> tulos (filter #(= (:path %) [:tr-loppuosa])) first)) "tr-loppuosa validointi puuttuu"
        (fn [tulos] (->> tulos (filter #(= (:path %) [:tr-loppuetaisyys])) first)) "tr-loppuetaisyys validointi puuttuu"))

(deftest validoi-alikohde
  (let [toiset-alikohteet [{:tr-numero 22 :tr-ajorata 1 :tr-kaista 11 :tr-alkuosa 1 :tr-alkuetaisyys 5000 :tr-loppuosa 1 :tr-loppuetaisyys 5200}
                           {:tr-numero 22 :tr-ajorata 0 :tr-kaista 1 :tr-alkuosa 3 :tr-alkuetaisyys 1 :tr-loppuosa 3 :tr-loppuetaisyys 100}]]
    (is (nil? (yllapitokohteet/validoi-alikohde oikea-tr-paaluvali oikea-tr-vali toiset-alikohteet tr-tieto)))))

(deftest kohde-tiedon-mukainen
  (testing "Pääkohde"
    ;; Testataan, että osan vaihtaminen onnistuu
    (is (nil? (yllapitokohteet/kohde-tiedon-mukainen {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 5000
                                                      :tr-loppuosa 6 :tr-loppuetaisyys 5000}
                                                     tr-tieto
                                                     true)))
    ;; Tiedoista puuttuvan osan käyttäminen ei onnistu
    (let [virhetiedot (yllapitokohteet/kohde-tiedon-mukainen {:tr-numero 22 :tr-alkuosa 2 :tr-alkuetaisyys 5000
                                                              :tr-loppuosa 6 :tr-loppuetaisyys 5000}
                                                             tr-tieto
                                                             true)]
      ;; Kohde-tiedon-mukainen palauttaa virheelliseksi todeltun kohteen tiedot
      (is (= (:kohde virhetiedot) {:tr-numero 22, :tr-alkuosa 2, :tr-alkuetaisyys 5000, :tr-loppuosa 6, :tr-loppuetaisyys 5000}))))

      (testing "Alikohde"
    ;; Testataan, että osan vaihtaminen onnistuu, kun ajorata ja kaista pysyy samana
    (is (nil? (yllapitokohteet/kohde-tiedon-mukainen {:tr-numero 22 :tr-ajorata 0 :tr-kaista 1
                                                      :tr-alkuosa 5 :tr-alkuetaisyys 5000
                                                      :tr-loppuosa 6 :tr-loppuetaisyys 5000}
                                                     tr-tieto
                                                     false)))
    ;; Osan vaihtaminen ei onnistu, kun ajorata ja kaista tiedot vaihtuvat
    (is (not (nil? (yllapitokohteet/kohde-tiedon-mukainen {:tr-numero 22 :tr-ajorata 0 :tr-kaista 1
                                                           :tr-alkuosa 4 :tr-alkuetaisyys 5000
                                                           :tr-loppuosa 6 :tr-loppuetaisyys 5000}
                                                          tr-tieto
                                                          false))))))

(deftest validoi-kaikki
  (let [tr-osoite {:tr-numero 22 :tr-alkuosa 3 :tr-alkuetaisyys 0 :tr-loppuosa 6 :tr-loppuetaisyys 10000}
        muiden-kohteiden-tiedot [{:tr-numero 1337
                                  :tr-osa 1
                                  :pituudet {:pituus 300
                                             :ajoradat [{:osiot [{:pituus 300
                                                                  :kaistat [{:tr-kaista 1 :pituus 300 :tr-alkuetaisyys 0}]
                                                                  :tr-alkuetaisyys 0}]
                                                         :tr-ajorata 0}]
                                             :tr-alkuetaisyys 0}}
                                 {:tr-numero 7331
                                  :tr-osa 2
                                  :pituudet {:pituus 400
                                             :ajoradat [{:osiot [{:pituus 400
                                                                  :kaistat [{:tr-kaista 1 :pituus 400 :tr-alkuetaisyys 100}]
                                                                  :tr-alkuetaisyys 100}]
                                                         :tr-ajorata 0}]
                                             :tr-alkuetaisyys 100}}]
        kohteiden-tiedot (concat tr-tieto
                                 [{:tr-numero 20
                                   :tr-osa 2
                                   :pituudet {:pituus 10000
                                              :ajoradat [{:osiot [{:pituus 10000
                                                                   :kaistat [{:tr-kaista 1 :pituus 10000 :tr-alkuetaisyys 0}]
                                                                   :tr-alkuetaisyys 0}]
                                                          :tr-ajorata 0}]
                                              :tr-alkuetaisyys 0}}])
        muiden-kohteiden-verrattavat-kohteet [[{:tr-numero 1337 :tr-ajorata 0 :tr-kaista 1
                                                :tr-alkuosa 1 :tr-alkuetaisyys 200
                                                :tr-loppuosa 1 :tr-loppuetaisyys 300}]
                                              [{:tr-numero 7331 :tr-ajorata 0 :tr-kaista 1
                                                :tr-alkuosa 2 :tr-alkuetaisyys 200
                                                :tr-loppuosa 2 :tr-loppuetaisyys 300}]]
        muutkohteet [{:tr-numero 1337 :tr-ajorata 0 :tr-kaista 1 :tr-alkuosa 1 :tr-alkuetaisyys 100 :tr-loppuosa 1 :tr-loppuetaisyys 200}
                     {:tr-numero 7331 :tr-ajorata 0 :tr-kaista 1 :tr-alkuosa 2 :tr-alkuetaisyys 100 :tr-loppuosa 2 :tr-loppuetaisyys 200}]
        vuosi 2019
        kohteen-alikohteet [(assoc tr-osoite :tr-ajorata 0 :tr-kaista 1 :tr-loppuosa 3 :tr-loppuetaisyys 1000)
                            (assoc tr-osoite :tr-ajorata 0 :tr-kaista 1 :tr-loppuosa 3 :tr-alkuetaisyys 1000 :tr-loppuetaisyys 2000)
                            (assoc tr-osoite :tr-ajorata 1 :tr-kaista 11 :tr-alkuosa 4 :tr-alkuetaisyys 1000 :tr-loppuosa 4 :tr-loppuetaisyys 2000)]
        urakan-muiden-kohteiden-alikohteet [{:tr-numero 22 :tr-ajorata 1 :tr-kaista 11 :tr-alkuosa 1 :tr-alkuetaisyys 100 :tr-loppuosa 1 :tr-loppuetaisyys 500}
                                            ;; Eri kohdteella tehdään samalle paaluvälille, mutta eri kohtaan
                                            (assoc tr-osoite :tr-ajorata 1 :tr-kaista 12 :tr-alkuosa 4 :tr-alkuetaisyys 100 :tr-loppuosa 4 :tr-loppuetaisyys 500)]
        muiden-urakoiden-alikohteet [{:tr-numero 20 :tr-ajorata 0 :tr-kaista 1 :tr-alkuosa 2 :tr-alkuetaisyys 100 :tr-loppuosa 2 :tr-loppuetaisyys 500}
                                     ; Eri urakka voi myös tehdä samalle paaluvälille päällystystä. Eri kohtaan tosin.
                                     (assoc tr-osoite :tr-ajorata 2 :tr-kaista 21 :tr-alkuosa 4 :tr-alkuetaisyys 100 :tr-loppuosa 4 :tr-loppuetaisyys 500)]
        alustatoimet [(assoc tr-osoite :tr-ajorata 1 :tr-kaista 11 :tr-alkuosa 4 :tr-alkuetaisyys 1000 :tr-loppuosa 4 :tr-loppuetaisyys 2000)]]
    (testing "validoi-kaikki toimii"
      (is (empty? (yllapitokohteet/validoi-kaikki tr-osoite kohteiden-tiedot muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                                                  vuosi kohteen-alikohteet muutkohteet alustatoimet urakan-muiden-kohteiden-alikohteet muiden-urakoiden-alikohteet))))
    (testing "Epätäydelliset kohteen tiedot"
      (is-> (yllapitokohteet/validoi-kaikki tr-osoite (remove #(and (= (:tr-numero %) 22)
                                                                    (= (:tr-osa %) 3)) kohteiden-tiedot) muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                                            vuosi kohteen-alikohteet muutkohteet alustatoimet urakan-muiden-kohteiden-alikohteet muiden-urakoiden-alikohteet)
            (fn [virheviestit]
              (= (into #{} (keys virheviestit))
                 #{:paakohde :alikohde :alustatoimenpide})) "Virheviesti ei näy kaikilla osa-alueilla"
            #(->> % vals flatten (mapcat vals) flatten distinct (= ["Tiellä 22 ei ole osaa 3"]))))
    (testing "Muut verrattavat kohteet päällekkäin"
      (is-> (yllapitokohteet/validoi-kaikki tr-osoite kohteiden-tiedot muiden-kohteiden-tiedot (assoc-in muiden-kohteiden-verrattavat-kohteet [0 0 :tr-alkuetaisyys] 100)
                                            vuosi kohteen-alikohteet muutkohteet alustatoimet urakan-muiden-kohteiden-alikohteet muiden-urakoiden-alikohteet)
            (fn [virheviestit]
              (= (into #{} (keys virheviestit))
                 #{:muukohde})) "Virheviesti ei näy kaikilla osa-alueilla"
            #(->> % vals flatten (mapcat vals) flatten distinct (= ["Kohteenosa on päällekkäin toisen osan kanssa"]))))
    (testing "validoi-kaikki huomauttaa kohdeosien päällekkkyydestä"
      ;; Kohteen omissa alikohteissa vikaa
      (is-> (yllapitokohteet/validoi-kaikki tr-osoite kohteiden-tiedot muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                                            vuosi (-> kohteen-alikohteet
                                                      (assoc-in [0 :tr-ajorata] 1)
                                                      (assoc-in [1 :nimi] "Foo-kohde")
                                                      (conj (assoc tr-osoite :tr-ajorata 0 :tr-kaista 1 :tr-loppuosa 3 :tr-alkuetaisyys 1500 :tr-loppuetaisyys 2000)))
                                            muutkohteet alustatoimet urakan-muiden-kohteiden-alikohteet muiden-urakoiden-alikohteet)
            (fn [virheviestit]
              (= (into #{} (keys virheviestit))
                 #{:alikohde})) "Virheviesti ei näy kaikilla osa-alueilla"
            #(= 1 0)                                        ; petar, tämä pitäisi feilaa!
            #(->> % vals flatten (mapcat vals) flatten distinct (= ["Tien 22 osalla 3 ei ole ajorataa 1"
                                                                    "Kohteenosa on päällekkäin toisen osan kanssa"
                                                                    "Kohteenosa on päällekkäin osan \"Foo-kohde\" kanssa"]))
            ;; Kohteen alikohteissa vikaa (100% päällekkäin) - petar
            (fn [virheviestit]
              (println "petar viherviesti" (pr-str virheviestit))
              (= (into #{} (keys virheviestit))
                 #{:alikohde})) "Virheviesti ei näy kaikilla osa-alueilla petar blabla "
            #(->> % vals flatten (mapcat vals) flatten distinct (= ["Tien 22 osalla 3 ei ole ajorataa 1 blabla"
                                                                    "Kohteenosa on päällekkäin toisen osan kanssa"
                                                                    "Kohteenosa on päällekkäin osan \"Foo-kohde\" kanssa"])))
      ;; Kohteen oma alikohde merkattu usealle osalle, eikä kaikilla osilla ole tarvittavaa ajorataa ja kaistaa
      (is-> (yllapitokohteet/validoi-kaikki (assoc tr-osoite :tr-alkuosa 1) kohteiden-tiedot muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                                            vuosi [(assoc tr-osoite :tr-ajorata 0 :tr-kaista 1 :tr-alkuosa 1 :tr-loppuosa 5 :tr-loppuetaisyys 1000)]
                                            muutkohteet [] urakan-muiden-kohteiden-alikohteet muiden-urakoiden-alikohteet)
            (fn [virheviestit]
              (= (into #{} (keys virheviestit))
                 #{:alikohde})) "Virheviesti ei näy kaikilla osa-alueilla"
            #(->> % vals flatten (mapcat vals) flatten distinct (= ["Ajorata 0 ei päätä osaa 1"
                                                                    "Tien 22 osalla 4 ei ole ajorataa 0"]))))
    (testing "validoi-kaikki huomauttaa kohteen kohdeosien ja saman urakan toisen kohteen kohdeosien päällekkyydestä"
      (is-> (yllapitokohteet/validoi-kaikki tr-osoite kohteiden-tiedot muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                                            vuosi kohteen-alikohteet muutkohteet alustatoimet (conj urakan-muiden-kohteiden-alikohteet
                                                                                                    (assoc (second kohteen-alikohteet)
                                                                                                      :paakohteen-nimi "Foo-kohde")) muiden-urakoiden-alikohteet)
            (fn [virheviestit]
              (= (into #{} (keys virheviestit))
                 #{:alikohde})) "Virheviesti ei näy kaikilla osa-alueilla"
            #(->> % vals flatten (mapcat vals) flatten distinct (= ["Kohteenosa (22, 0, 1, 3, 1000, 3, 2000) on päällekkäin kohteen \"Foo-kohde\" kohdeosan kanssa"]))))
    (testing "validoi-kaikki huomauttaa kohteen kohdeosien ja toisen urakan kohteen kohdeosien päällekkyydestä"
      (is-> (yllapitokohteet/validoi-kaikki tr-osoite kohteiden-tiedot muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                                            vuosi kohteen-alikohteet muutkohteet alustatoimet urakan-muiden-kohteiden-alikohteet (conj muiden-urakoiden-alikohteet
                                                                                                                                       (assoc (second kohteen-alikohteet)
                                                                                                                                         :urakka "Foo-urakka")))
            (fn [virheviestit]
              (= (into #{} (keys virheviestit))
                 #{:alikohde})) "Virheviesti ei näy kaikilla osa-alueilla"
            #(->> % vals flatten (mapcat vals) flatten distinct (= ["Kohteenosa (22, 0, 1, 3, 1000, 3, 2000) on päällekkäin toisen urakan kohdeosan kanssa"]))))
    (testing "validoi-kaikki huomauttaa virheellisistä alikohteista"
      (is-> (yllapitokohteet/validoi-kaikki tr-osoite kohteiden-tiedot muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                                           vuosi kohteen-alikohteet muutkohteet (conj alustatoimet
                                                                                      (update (first alustatoimet)
                                                                                              :tr-alkuetaisyys dec)
                                                                                      (assoc tr-osoite :tr-ajorata 0 :tr-kaista 1 :tr-loppuosa 3 :tr-alkuetaisyys 1000 :tr-loppuetaisyys 3000)) urakan-muiden-kohteiden-alikohteet muiden-urakoiden-alikohteet)
            (fn [virheviestit]
              (= (into #{} (keys virheviestit))
                 #{:alustatoimenpide})) "Virheviesti ei näy kaikilla osa-alueilla"
            #(->> % vals flatten (mapcat vals) flatten distinct (= ["Alustatoimenpide ei ole minkään alikohteen sisällä"
                                                                    "Alustatoimenpide on päällekkäin toisen osan kanssa"]))))))
