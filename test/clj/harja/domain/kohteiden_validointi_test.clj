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
                                       :tr-ajorata 0}
                                      {:osiot [{:pituus 10000
                                                :kaistat [{:tr-kaista 21 :pituus 10000 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}]
                                       :tr-ajorata 2}]

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

(def tr-tieto-esimerkki-pituudet {:pituus 400
                                  :ajoradat [{:osiot [{:pituus 100
                                                       :kaistat [{:tr-kaista 1 :pituus 100 :tr-alkuetaisyys 0}
                                                                 {:tr-kaista 2 :pituus 100 :tr-alkuetaisyys 0}]
                                                       :tr-alkuetaisyys 0}]
                                              :tr-ajorata 0}
                                             {:osiot [{:pituus 100
                                                       :kaistat [{:tr-kaista 1 :pituus 100 :tr-alkuetaisyys 100}
                                                                 {:tr-kaista 2 :pituus 100 :tr-alkuetaisyys 100}
                                                                 {:tr-kaista 3 :pituus 50 :tr-alkuetaisyys 150}]
                                                       :tr-alkuetaisyys 100}]
                                              :tr-ajorata 0}
                                             {:osiot [{:pituus 100
                                                       :kaistat [{:tr-kaista 1 :pituus 50 :tr-alkuetaisyys 200}
                                                                 {:tr-kaista 2 :pituus 100 :tr-alkuetaisyys 200}
                                                                 {:tr-kaista 3 :pituus 100 :tr-alkuetaisyys 200}]
                                                       :tr-alkuetaisyys 200}]
                                              :tr-ajorata 0}
                                             {:osiot [{:pituus 100
                                                       :kaistat [{:tr-kaista 1 :pituus 50 :tr-alkuetaisyys 350}
                                                                 {:tr-kaista 2 :pituus 100 :tr-alkuetaisyys 300}
                                                                 {:tr-kaista 3 :pituus 100 :tr-alkuetaisyys 300}]
                                                       :tr-alkuetaisyys 300}]
                                              :tr-ajorata 0}]
                                  :tr-alkuetaisyys 0})

(def tr-tieto-kaistat [{:tr-numero 555
                        :tr-osa 6
                        :pituudet tr-tieto-esimerkki-pituudet}
                       {:tr-numero 555
                        :tr-osa 8
                        :pituudet tr-tieto-esimerkki-pituudet}
                       {:tr-numero 555
                        :tr-osa 9
                        :pituudet tr-tieto-esimerkki-pituudet}])


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
  (let [tulos (-> (assoc oikea-tr-paaluvali :tr-alkuetaisyys 100000)
                  (yllapitokohteet/validoi-kohde tr-tieto)
                  :validoitu-paikka)]
        (is (= 5 (-> tulos :kohteen-tiedot count))))
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


(deftest loyda-kaikki-kaistat
  (testing "ei löydä mitään jos ei ole täysin sisällä"
    (is (= [] (yllapitokohteet/kaikki-kaistat {:tr-numero 22 :tr-ajorata 1
                                               :tr-alkuosa 1 :tr-alkuetaisyys 1 :tr-loppuosa 15 :tr-loppuetaisyys 1}
                                              tr-tieto)))
    (is (= [] (yllapitokohteet/kaikki-kaistat {:tr-numero 20 :tr-ajorata 1
                                               :tr-alkuosa 1 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 1}
                                              tr-tieto))))
  (testing "löydä kaikki kaistat yhden osan sisällä"
    (is (= [11 12] (yllapitokohteet/kaikki-kaistat {:tr-numero 22 :tr-ajorata 1
                                                    :tr-alkuosa 1 :tr-alkuetaisyys 100
                                                    :tr-loppuosa 1 :tr-loppuetaisyys 200}
                                                   tr-tieto)))
    (is (= [11 12] (yllapitokohteet/kaikki-kaistat {:tr-numero 22 :tr-ajorata 1
                                                    :tr-alkuosa 4 :tr-alkuetaisyys 0
                                                    :tr-loppuosa 4 :tr-loppuetaisyys 500}
                                                   tr-tieto)))
    (is (= [11] (yllapitokohteet/kaikki-kaistat {:tr-numero 22 :tr-ajorata 1
                                                 :tr-alkuosa 4 :tr-alkuetaisyys 100
                                                 :tr-loppuosa 4 :tr-loppuetaisyys 501}
                                                tr-tieto))))
  (testing "löydä kaikki kaistat muutamien osan sisällä"
    (is (= [1] (yllapitokohteet/kaikki-kaistat {:tr-numero 22 :tr-ajorata 0
                                                :tr-alkuosa 5 :tr-alkuetaisyys 100
                                                :tr-loppuosa 6 :tr-loppuetaisyys 500}
                                               tr-tieto))))
  (testing "löydä kaikki kaistat yhden osan sisällä, kunnolla löytää intersection"
    (is (= [1 2] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                  :tr-alkuosa 6 :tr-alkuetaisyys 10
                                                  :tr-loppuosa 6 :tr-loppuetaisyys 120}
                                                 tr-tieto-kaistat)))
    (is (= [1 2] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                  :tr-alkuosa 6 :tr-alkuetaisyys 10
                                                  :tr-loppuosa 6 :tr-loppuetaisyys 200}
                                                 tr-tieto-kaistat)))
    (is (= [1 2 3] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                    :tr-alkuosa 6 :tr-alkuetaisyys 150
                                                    :tr-loppuosa 6 :tr-loppuetaisyys 200}
                                                   tr-tieto-kaistat)))
    (is (= [1 2 3] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                    :tr-alkuosa 6 :tr-alkuetaisyys 150
                                                    :tr-loppuosa 6 :tr-loppuetaisyys 220}
                                                   tr-tieto-kaistat)))
    (is (= [1 2 3] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                    :tr-alkuosa 6 :tr-alkuetaisyys 150
                                                    :tr-loppuosa 6 :tr-loppuetaisyys 250}
                                                   tr-tieto-kaistat)))
    (is (= [2 3] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                  :tr-alkuosa 6 :tr-alkuetaisyys 150
                                                  :tr-loppuosa 6 :tr-loppuetaisyys 251}
                                                 tr-tieto-kaistat)))
    (is (= [2 3] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                  :tr-alkuosa 6 :tr-alkuetaisyys 150
                                                  :tr-loppuosa 6 :tr-loppuetaisyys 299}
                                                 tr-tieto-kaistat)))
    (is (= [2 3] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                  :tr-alkuosa 6 :tr-alkuetaisyys 150
                                                  :tr-loppuosa 6 :tr-loppuetaisyys 320}
                                                 tr-tieto-kaistat)))
    (is (= [2] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                :tr-alkuosa 6 :tr-alkuetaisyys 0
                                                :tr-loppuosa 6 :tr-loppuetaisyys 400}
                                               tr-tieto-kaistat))))
  (testing "löydä kaikki kaistat muutamien osien sisällä, kunnolla löytää intersection"
    (is (= [2] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                :tr-alkuosa 6 :tr-alkuetaisyys 10
                                                :tr-loppuosa 8 :tr-loppuetaisyys 120}
                                               tr-tieto-kaistat)))
    (is (= [1 2] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                  :tr-alkuosa 6 :tr-alkuetaisyys 350
                                                  :tr-loppuosa 8 :tr-loppuetaisyys 120}
                                                 tr-tieto-kaistat)))
    (is (= [1 2] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                  :tr-alkuosa 6 :tr-alkuetaisyys 350
                                                  :tr-loppuosa 8 :tr-loppuetaisyys 250}
                                                 tr-tieto-kaistat)))
    (is (= [2] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                :tr-alkuosa 6 :tr-alkuetaisyys 350
                                                :tr-loppuosa 8 :tr-loppuetaisyys 251}
                                               tr-tieto-kaistat))))
  (testing "ei löydä mitään jos on keskellä osa jossa ei ole jatkuva kaista"
    (is (= [2] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                :tr-alkuosa 6 :tr-alkuetaisyys 350
                                                :tr-loppuosa 9 :tr-loppuetaisyys 251}
                                               tr-tieto-kaistat)))
    (is (= [2] (yllapitokohteet/kaikki-kaistat {:tr-numero 555 :tr-ajorata 0
                                                :tr-alkuosa 6 :tr-alkuetaisyys 350
                                                :tr-loppuosa 9 :tr-loppuetaisyys 250}
                                               tr-tieto-kaistat)))))

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

(defn- tietyn-kaistan-rivi [rivit kaista]
  (first (filter #(= (:tr-kaista %) kaista) rivit)))

(deftest pot2-paallysterivin-idn-sailytys-jos-tr-matsaa
  (testing "Jos POT2 päällysterivi kopioidaan, säilytä id:t jos on olemassa rivi jonka tierekisteriosoite ja toimenpide on full match"
    (let [rivi-ja-kopiot [{:kohdeosa-id 12, :tr-kaista 11, :tr-ajorata 1,
                           :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
                           :nimi "Tärkeä kohdeosa kaista 12", :tr-alkuetaisyys 1066, :tr-numero 20,
                           :toimenpide 23, :pot2p_id 2}
                          {:kohdeosa-id 12, :tr-kaista 12, :tr-ajorata 1,
                           :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
                           :nimi "Tärkeä kohdeosa kaista 12", :tr-alkuetaisyys 1066, :tr-numero 20,
                           :toimenpide 23, :pot2p_id 2}]
          kaikki-rivit [{:kohdeosa-id 11, :tr-kaista 11, :tr-ajorata 1,
                         :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
                         :nimi "Tärkeä kohdeosa kaista 11", :tr-alkuetaisyys 1066, :tr-numero 20,
                         :toimenpide 23, :pot2p_id 1}
                        {:kohdeosa-id 12, :tr-kaista 12, :tr-ajorata 1,
                         :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
                         :nimi "Tärkeä kohdeosa kaista 12", :tr-alkuetaisyys 1066, :tr-numero 20,
                         :toimenpide 23, :pot2p_id 2}]
          laskettu-tulos (yllapitokohteet/sailyta-idt-jos-sama-tr-osoite rivi-ja-kopiot kaikki-rivit)
          odotettu-tulos [{:kohdeosa-id 11, :tr-kaista 11, :tr-ajorata 1,
                           :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
                           :nimi "Tärkeä kohdeosa kaista 11", :tr-alkuetaisyys 1066, :tr-numero 20,
                           :toimenpide 23, :pot2a_id nil, :pot2p_id 1}
                          {:kohdeosa-id 12, :tr-kaista 12, :tr-ajorata 1,
                           :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
                           :nimi "Tärkeä kohdeosa kaista 12", :tr-alkuetaisyys 1066, :tr-numero 20,
                           :toimenpide 23, :pot2a_id nil, :pot2p_id 2}]]
      (is (= (tietyn-kaistan-rivi laskettu-tulos 11) (tietyn-kaistan-rivi odotettu-tulos 11)) "POT2 kaistan 11  kopioitu oikein")
      (is (= (tietyn-kaistan-rivi laskettu-tulos 12) (tietyn-kaistan-rivi odotettu-tulos 12)) "POT2 kaistan 12 päällyste kopioitu oikein"))))

(deftest pot2-paallysterivin-idn-nillaus-jos-tr-ei-matsaa
  (testing "Jos POT2 päällysterivi kopioidaan, älä säilytä id:itä jos tr ei mätsää"
    (let [rivi-ja-kopiot [{:kohdeosa-id 12, :tr-kaista 11, :leveys 3, :kokonaismassamaara 5000, :tr-ajorata 1, :pinta_ala 8283, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 333, :tr-loppuetaisyys 3827, :nimi "Tärkeä kohdeosa kaista 12", :materiaali 2, :tr-alkuetaisyys 1066, :piennar false, :tr-numero 20, :toimenpide 23, :pot2p_id 2} {:kohdeosa-id 12, :tr-kaista 12, :leveys 3, :kokonaismassamaara 5000, :tr-ajorata 1, :pinta_ala 8283, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 333, :tr-loppuetaisyys 3827, :nimi "Tärkeä kohdeosa kaista 12", :materiaali 2, :tr-alkuetaisyys 1066, :piennar false, :tr-numero 20, :toimenpide 23, :pot2p_id 2}]
          kaikki-rivit [{:kohdeosa-id 11, :tr-kaista 11, :leveys 3, :kokonaismassamaara 5000, :tr-ajorata 1, :pinta_ala 8283, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 333, :tr-loppuetaisyys 2400, :nimi "Tärkeä kohdeosa kaista 11", :materiaali 1, :tr-alkuetaisyys 1066, :piennar true, :tr-numero 20, :toimenpide 22, :pot2p_id 1} {:kohdeosa-id 12, :tr-kaista 12, :leveys 3, :kokonaismassamaara 5000, :tr-ajorata 1, :pinta_ala 8283, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 333, :tr-loppuetaisyys 3827, :nimi "Tärkeä kohdeosa kaista 12", :materiaali 2, :tr-alkuetaisyys 1066, :piennar false, :tr-numero 20, :toimenpide 23, :pot2p_id 2}]
          laskettu-tulos (yllapitokohteet/sailyta-idt-jos-sama-tr-osoite rivi-ja-kopiot kaikki-rivit)
          odotettu-tulos [{:jarjestysnro 1
                           :kohdeosa-id nil
                           :kokonaismassamaara 5000
                           :leveys 3
                           :massamenekki 333
                           :materiaali 2
                           :nimi nil
                           :piennar false
                           :pinta_ala 8283
                           :pot2a_id nil
                           :pot2p_id nil
                           :toimenpide 23
                           :tr-ajorata 1
                           :tr-alkuetaisyys 1066
                           :tr-alkuosa 1
                           :tr-kaista 11
                           :tr-loppuetaisyys 3827
                           :tr-loppuosa 1
                           :tr-numero 20}
                          {:kohdeosa-id 12, :tr-kaista 12, :leveys 3, :kokonaismassamaara 5000, :tr-ajorata 1, :pinta_ala 8283, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 333, :tr-loppuetaisyys 3827, :nimi "Tärkeä kohdeosa kaista 12", :materiaali 2, :tr-alkuetaisyys 1066, :piennar false, :tr-numero 20, :toimenpide 23, :pot2a_id nil, :pot2p_id 2}]]
      (is (= (tietyn-kaistan-rivi laskettu-tulos 11) (tietyn-kaistan-rivi odotettu-tulos 11)) "POT2 kaistan 11  kopioitu oikein")
      (is (= (tietyn-kaistan-rivi laskettu-tulos 12) (tietyn-kaistan-rivi odotettu-tulos 12)) "POT2 kaistan 12 päällyste kopioitu oikein"))))

(deftest pot2-alustarivin-id-sailyy-jos-tr-matsaa
  (testing "Jos POT2 alustarivi kopioidaan, säilytä id:t jos on olemassa rivi jonka tierekisteriosoite ja toimenpide on full match"
    (let [rivi-ja-kopiot [{:tr-kaista 11, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2} {:tr-kaista 12, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2}]
          kaikki-rivit [{:tr-kaista 11, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 7} {:tr-kaista 12, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2}]
          laskettu-tulos (yllapitokohteet/sailyta-idt-jos-sama-tr-osoite rivi-ja-kopiot kaikki-rivit)
          odotettu-tulos [{:kohdeosa-id nil, :tr-kaista 11, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :nimi nil, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 7, :pot2p_id nil} {:kohdeosa-id nil, :tr-kaista 12, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :nimi nil, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2, :pot2p_id nil}]]
(is (= (tietyn-kaistan-rivi laskettu-tulos 11) (tietyn-kaistan-rivi odotettu-tulos 11)) "POT2 kaistan 11 alusta kopioitu oikein")
      (is (= (tietyn-kaistan-rivi laskettu-tulos 12) (tietyn-kaistan-rivi odotettu-tulos 12)) "POT2 kaistan 12 alusta kopioitu oikein"))))

(deftest pot2-alustarivin-id-nillataan-jos-tr-ei-matsaa
  (testing "Jos POT2 alustarivi kopioidaan, älä säilytä id:itä jos tr ei mätsää"
    (let [rivi-ja-kopiot [{:tr-kaista 11, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2} {:tr-kaista 12, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2}]
          kaikki-rivit [{:tr-kaista 11, :leveys nil, :kokonaismassamaara nil, :murske 1, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus nil, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 3827, :lisatty-paksuus 10, :verkon-tyyppi nil, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 23, :verkon-sijainti nil, :pot2a_id 1} {:tr-kaista 12, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2}]
          laskettu-tulos (yllapitokohteet/sailyta-idt-jos-sama-tr-osoite rivi-ja-kopiot kaikki-rivit)
          odotettu-tulos [{:kohdeosa-id nil, :tr-kaista 11, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :nimi nil, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id nil, :pot2p_id nil} {:kohdeosa-id nil, :tr-kaista 12, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1, :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil, :kasittelysyvyys nil, :tr-loppuetaisyys 2000, :nimi nil, :lisatty-paksuus nil, :verkon-tyyppi 1, :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2, :pot2p_id nil}]]
      (is (= (tietyn-kaistan-rivi laskettu-tulos 11) (tietyn-kaistan-rivi odotettu-tulos 11)) "POT2 kaistan 11 alusta kopioitu oikein")
      (is (= (tietyn-kaistan-rivi laskettu-tulos 12) (tietyn-kaistan-rivi odotettu-tulos 12)) "POT2 kaistan 12 alusta kopioitu oikein"))))

(deftest pot2-alustarivin-ei-poista-jos-eri-toimenpide
  (testing "Jos POT2 alustarivi kopioidaan, ei säilytä id:itä jos on eri toimenpide"
    (let [rivi-ja-kopiot [{:tr-kaista 11, :tr-ajorata 1, :nimi nil,
                           :tr-loppuosa 1, :tr-alkuosa 1,
                           :tr-loppuetaisyys 2000, :tr-alkuetaisyys 1066,
                           :tr-numero 20, :toimenpide 3, :pot2a_id 2}
                          {:tr-kaista 12, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1, :nimi nil,
                           :tr-loppuetaisyys 2000, :tr-alkuetaisyys 1066,
                           :tr-numero 20, :toimenpide 3, :pot2a_id 2}]
          kaikki-rivit [{:tr-kaista 11, :tr-ajorata 1, :nimi nil,
                         :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827, :tr-alkuetaisyys 1066,
                         :tr-numero 20, :toimenpide 23, :pot2a_id 1}
                        {:tr-kaista 12, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1, :nimi nil,
                         :tr-loppuetaisyys 2000, :tr-alkuetaisyys 1066,
                         :tr-numero 20, :toimenpide 23, :pot2a_id 2}]
          laskettu-tulos (yllapitokohteet/sailyta-idt-jos-sama-tr-osoite rivi-ja-kopiot kaikki-rivit)
          odotettu-tulos [{:kohdeosa-id nil, :tr-kaista 11, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1,
                           :tr-loppuetaisyys 2000, :tr-alkuetaisyys 1066, :nimi nil,
                           :tr-numero 20, :toimenpide 3, :pot2a_id nil, :pot2p_id nil}
                          {:kohdeosa-id nil, :tr-kaista 12, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1,
                           :tr-loppuetaisyys 2000, :tr-alkuetaisyys 1066, :nimi nil,
                           :tr-numero 20, :toimenpide 3, :pot2a_id nil, :pot2p_id nil}]]
      #_(is (= odotettu-tulos laskettu-tulos) "Toimenpide 3 kopioitu, Toimenpide 23 jäännyt")
      (is (= (tietyn-kaistan-rivi laskettu-tulos 11) (tietyn-kaistan-rivi odotettu-tulos 11)) "POT2 kaistan 11 alusta kopioitu oikein")
      (is (= (tietyn-kaistan-rivi laskettu-tulos 12) (tietyn-kaistan-rivi odotettu-tulos 12)) "POT2 kaistan 12 alusta kopioitu oikein"))))


(deftest pot2-paallysterivin-id-nillataan-jos-tr-ei-matsaa
  (testing "Jos POT2 alustarivi kopioidaan, älä säilytä id:itä jos tr ei mätsää"
    (let [rivi-ja-kopiot [{:kohdeosa-id 52, :tr-kaista 11, :leveys 1, :kokonaismassamaara 2, :tr-ajorata 1, :pinta_ala 1000, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 3, :tr-loppuetaisyys 3000, :nimi nil, :materiaali 2, :tr-alkuetaisyys 2000, :piennar false, :tr-numero 20, :toimenpide 12, :pot2p_id 4}
                          {:kohdeosa-id 52, :tr-kaista 12, :leveys 1, :kokonaismassamaara 2, :tr-ajorata 1, :pinta_ala 1000, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 3, :tr-loppuetaisyys 3000, :nimi nil, :materiaali 2, :tr-alkuetaisyys 2000, :piennar false, :tr-numero 20, :toimenpide 12, :pot2p_id 4}]
          kaikki-rivit [{:kohdeosa-id 11, :tr-kaista 11, :leveys 3, :kokonaismassamaara 5000, :tr-ajorata 1, :pinta_ala 2802, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 333, :tr-loppuetaisyys 2000, :nimi "Tärkeä kohdeosa kaista 11", :materiaali 1, :tr-alkuetaisyys 1066, :piennar false, :tr-numero 20, :toimenpide 23, :pot2p_id 1}
                        {:kohdeosa-id 12, :tr-kaista 12, :leveys 3, :kokonaismassamaara 5000, :tr-ajorata 1, :pinta_ala 2802, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 333, :tr-loppuetaisyys 2000, :nimi "Tärkeä kohdeosa kaista 12", :materiaali 1, :tr-alkuetaisyys 1066, :piennar false, :tr-numero 20, :toimenpide 23, :pot2p_id 2}
                        {:kohdeosa-id 52, :tr-kaista 12, :leveys 1, :kokonaismassamaara 2, :tr-ajorata 1, :pinta_ala 1000, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 3, :tr-loppuetaisyys 3000, :nimi nil, :materiaali 2, :tr-alkuetaisyys 2000, :piennar false, :tr-numero 20, :toimenpide 12, :pot2p_id 4}]
          laskettu-tulos (yllapitokohteet/sailyta-idt-jos-sama-tr-osoite rivi-ja-kopiot kaikki-rivit)
          odotettu-tulos [{:kohdeosa-id nil, :tr-kaista 11, :leveys 1, :kokonaismassamaara 2, :tr-ajorata 1, :pinta_ala 1000, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 3, :tr-loppuetaisyys 3000, :nimi nil, :materiaali 2, :tr-alkuetaisyys 2000, :piennar false, :tr-numero 20, :toimenpide 12, :pot2a_id nil, :pot2p_id nil} {:kohdeosa-id 52, :tr-kaista 12, :leveys 1, :kokonaismassamaara 2, :tr-ajorata 1, :pinta_ala 1000, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1, :massamenekki 3, :tr-loppuetaisyys 3000, :nimi nil, :materiaali 2, :tr-alkuetaisyys 2000, :piennar false, :tr-numero 20, :toimenpide 12, :pot2a_id nil, :pot2p_id 4}]]
      (is (= (tietyn-kaistan-rivi laskettu-tulos 11) (tietyn-kaistan-rivi odotettu-tulos 11)) "POT2 kaistan 11 alusta kopioitu oikein")
      (is (= (tietyn-kaistan-rivi laskettu-tulos 12) (tietyn-kaistan-rivi odotettu-tulos 12)) "POT2 kaistan 12 alusta kopioitu oikein"))))


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
    (testing "validoi-kaikki ottaa järjestysnro huomioon kun laske onko päällekkäin tai ei"
      (let [virheviestit (yllapitokohteet/validoi-kaikki tr-osoite kohteiden-tiedot muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                                                         vuosi
                                                         [(assoc tr-osoite :tr-ajorata 0 :tr-kaista 1 :tr-loppuosa 3 :tr-loppuetaisyys 900
                                                                           :jarjestysnro 5)
                                                          (assoc tr-osoite :tr-ajorata 0 :tr-kaista 1 :tr-loppuosa 3 :tr-loppuetaisyys 800
                                                                           :jarjestysnro 6)]
                                                         muutkohteet
                                                         [(assoc tr-osoite :tr-ajorata 0 :tr-kaista 1 :tr-alkuosa 3 :tr-alkuetaisyys 0
                                                                           :tr-loppuosa 3 :tr-loppuetaisyys 900)]
                                                         urakan-muiden-kohteiden-alikohteet muiden-urakoiden-alikohteet)]
        (is (= {} virheviestit) "Ei saa olla virheviestiä koska kohteenosa on eri järjestelmänro:lla")))

    (testing "validoi-kaikki huomauttaa kohteen kohdeosien ja saman urakan toisen kohteen kohdeosien päällekkyydestä"
      (let [virheviestit (yllapitokohteet/validoi-kaikki tr-osoite kohteiden-tiedot muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                                                         vuosi kohteen-alikohteet muutkohteet alustatoimet
                                                         (conj urakan-muiden-kohteiden-alikohteet
                                                               (assoc (second kohteen-alikohteet)
                                                                 :paakohteen-nimi "Foo-kohde")) muiden-urakoiden-alikohteet)]
        (is (= #{:alikohde} (into #{} (keys virheviestit))) "Virheviesti ei näy kaikilla osa-alueilla")
        (is (= ["Kohteenosa (22, 0, 1, 3, 1000, 3, 2000) on päällekkäin kohteen \"Foo-kohde\" kohdeosan kanssa"]
               (->> virheviestit vals flatten (mapcat vals) flatten distinct)))))
    (testing "validoi-kaikki huomauttaa kohteen kohdeosien ja toisen urakan kohteen kohdeosien päällekkyydestä"
      (let [virheviestit (yllapitokohteet/validoi-kaikki tr-osoite kohteiden-tiedot muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                                                         vuosi kohteen-alikohteet muutkohteet alustatoimet urakan-muiden-kohteiden-alikohteet
                                                         (conj muiden-urakoiden-alikohteet
                                                               (assoc (second kohteen-alikohteet)
                                                                 :urakka "Foo-urakka")))]
        (is (= #{:alikohde} (into #{} (keys virheviestit))) "Virheviesti ei näy kaikilla osa-alueilla")
        (is (= ["Kohteenosa (22, 0, 1, 3, 1000, 3, 2000) on päällekkäin toisen urakan kohdeosan kanssa"]
               (->> virheviestit vals flatten (mapcat vals) flatten distinct)))))))


(deftest validoi-muut-kohteet-ei-herjaa-itsensa-kanssa-paallekkaisyydesta
  (let [tr-osoite {:tr-kaista nil, :tr-ajorata nil, :tr-loppuosa 404, :tr-alkuosa 363, :tr-loppuetaisyys 2500, :tr-alkuetaisyys 3100, :tr-numero 4}
        muut-kohteet (list
                       {:kohdeosa-id 11067, :tr-kaista 11, :leveys 4, :kokonaismassamaara 1, :velho-lahetyksen-aika nil, :tr-ajorata 0, :pinta_ala 1300, :tr-loppuosa 23, :jarjestysnro 1, :velho-lahetyksen-vastaus nil, :tr-alkuosa 23, :tr-loppuetaisyys 850, :tr-alkuetaisyys 525, :piennar false, :tr-numero 28410, :toimenpide 12})
        vuosi 2021
        muiden-kohteiden-tiedot [] ;; tässäkin oli tavaraa, mutta ei vaikuta vian toistamiseen
        muiden-kohteiden-verrattavat-kohteet  (list (list ) (list {:tr-kaista 11, :kohdenumero 22, :tr-ajorata 0, :urakka-id 450, :kohde-id 4243,  :tr-loppuosa 56, :tr-alkuosa 56, :tr-loppuetaisyys 360, :nimi nil, :id 11057, :tr-alkuetaisyys 53, :tr-numero 28406}) (list {:tr-kaista 11, :kohdenumero 21, :tr-ajorata 0, :urakka-id 450, :kohde-id 4244,  :tr-loppuosa 23, :tr-alkuosa 23, :tr-loppuetaisyys 850, :nimi nil, :id 11067, :tr-alkuetaisyys 525, :tr-numero 28410}) (list {:tr-kaista 11, :kohdenumero 21, :tr-ajorata 0, :urakka-id 450, :kohde-id 4244,  :tr-loppuosa 23, :tr-alkuosa 23, :tr-loppuetaisyys 525, :nimi nil, :id 11059, :tr-alkuetaisyys 35, :tr-numero 28410}))
        urakan-toiset-kohdeosat [] ;; tässä oli oikeasti valtava lista, mutta haettu virhe syntyy ilman tätäkin...
        validointivirheet (yllapitokohteet/validoi-muut-kohteet tr-osoite vuosi muut-kohteet muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet urakan-toiset-kohdeosat)]
(is (empty? validointivirheet) "Ei validointivirheitä muissa kohteissa koska eivät ole päällekkäin")))