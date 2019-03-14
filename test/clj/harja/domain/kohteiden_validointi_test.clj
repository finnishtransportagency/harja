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

(deftest tarkista-kohteen-validius
  (is (thrown+?
        #(tasmaa-poikkeus
           %
           yllapitokohteet/+kohteissa-viallisia-sijainteja+
           yllapitokohteet/+viallinen-yllapitokohteen-sijainti+
           "Alkuosa on loppuosaa isompi. Sijainti: {:aosa 2, :aet 1, :losa 1, :let 1}")
        (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 {:aosa 2 :aet 1 :losa 1 :let 1} nil))
      "Loppuosaa suurempi alkuosa otettiin kiini"))

(deftest tarkista-alikohteen-validius
  (let [kohde {:aosa 1 :aet 1 :losa 4 :let 4}
        alikohteet [{:tunnus "A"
                     :sijainti {:aosa 1, :aet 1, :losa 2, :let 2}}
                    {:tunnus "B"
                     :sijainti {:aosa 2, :aet 2, :losa 3, :let 3}}
                    {:tunnus "C"
                     :sijainti {:aosa 3, :aet 3, :losa 5, :let 5}}]]
    (is (thrown+?
          #(tasmaa-poikkeus
             %
             yllapitokohteet/+kohteissa-viallisia-sijainteja+
             yllapitokohteet/+viallinen-yllapitokohdeosan-sijainti+
             "Alikohde (tunniste: C) ei ole kohteen (tunniste: 1) sisällä.")
          (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde alikohteet))
        "Kohteen ulkopuolinen alikohde otettiin kiinni")))

(deftest tarkista-validi-kohde
  (let [kohde {:aosa 1 :aet 1 :losa 4 :let 4}
        yksi-alikohde [{:tunnus "A"
                        :sijainti {:aosa 1, :aet 1, :losa 4, :let 4 :ajorata 1 :kaista 1}}]
        kaksi-alikohdetta [{:tunnus "A"
                            :sijainti {:aosa 1, :aet 1, :losa 2, :let 2 :ajorata 1 :kaista 1}}
                           {:tunnus "B"
                            :sijainti {:aosa 2, :aet 2, :losa 4, :let 4 :ajorata 1 :kaista 1}}]
        monta-alikohdetta [{:tunnus "A"
                            :sijainti {:aosa 1, :aet 1, :losa 2, :let 2 :ajorata 1 :kaista 1}}
                           {:tunnus "B"
                            :sijainti {:aosa 2, :aet 2, :losa 3, :let 3 :ajorata 1 :kaista 1}}
                           {:tunnus "C"
                            :sijainti {:aosa 3, :aet 3, :losa 4, :let 4 :ajorata 1 :kaista 1}}]
        yksi-alustatoimenpide [{:sijainti {:aosa 1, :aet 1, :losa 2, :let 2 :ajorata 1 :kaista 1}}]
        kaksi-alustatoimenpidetta [{:sijainti {:aosa 1, :aet 1, :losa 2, :let 2 :ajorata 1 :kaista 1}}
                                   {:sijainti {:aosa 2, :aet 2, :losa 4, :let 4 :ajorata 1 :kaista 1}}]
        monta-alustatoimenpidetta [{:sijainti {:aosa 1, :aet 1, :losa 2, :let 2 :ajorata 1 :kaista 1}}
                                   {:sijainti {:aosa 2, :aet 2, :losa 3, :let 3 :ajorata 1 :kaista 1}}
                                   {:sijainti {:aosa 3, :aet 3, :losa 4, :let 4 :ajorata 1 :kaista 1}}]]

    (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde yksi-alikohde)
    (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde kaksi-alikohdetta)
    (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde monta-alikohdetta)

    (yllapitokohteet/tarkista-alustatoimenpiteiden-sijainnit 1 kohde yksi-alustatoimenpide)
    (yllapitokohteet/tarkista-alustatoimenpiteiden-sijainnit 1 kohde kaksi-alustatoimenpidetta)
    (yllapitokohteet/tarkista-alustatoimenpiteiden-sijainnit 1 kohde monta-alustatoimenpidetta)))

(deftest tarkista-alustatoimenpiteiden-validius
  (let [kohde {:aosa 1 :aet 1 :losa 4 :let 4}
        alustatoimenpiteet [{:sijainti {:aosa 1, :aet 1, :losa 2, :let 2 :ajorata 1 :kaista 1}}
                            {:sijainti {:aosa 2, :aet 2, :losa 5, :let 3 :ajorata 1 :kaista 1}}]]
    (is (thrown+?
          #(tasmaa-poikkeus
             %
             yllapitokohteet/+kohteissa-viallisia-sijainteja+
             yllapitokohteet/+viallinen-alustatoimenpiteen-sijainti+
             "Alustatoimenpide ei ole kohteen (id: 1) sisällä")
          (yllapitokohteet/tarkista-alustatoimenpiteiden-sijainnit 1 kohde alustatoimenpiteet))
        "Kohteen ulkopuolinen alustatoimenpide otettiin kiinni")))

(deftest tarkista-negatiiviset-arvot
  (let [kohde {:aosa -1 :aet 1 :losa 2 :let 1}
        alustatoimenpiteet [{:sijainti {:aosa 1, :aet -1, :losa 2, :let 2}}]
        alikohteet [{:tunnus "A" :sijainti {:aosa 1, :aet 1, :losa 2, :let -1}}]]
    (is (thrown+?
          #(tasmaa-poikkeus
             %
             yllapitokohteet/+kohteissa-viallisia-sijainteja+
             yllapitokohteet/+viallinen-yllapitokohteen-sijainti+
             "Alkuosa ei saa olla negatiivinen. Sijainti: {:aosa -1, :aet 1, :losa 2, :let 1}")
          (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde alikohteet))
        "Kohteen negatiivinen alkuosa otettiin kiinni")
    (is (thrown+?
          #(tasmaa-poikkeus
             %
             yllapitokohteet/+kohteissa-viallisia-sijainteja+
             yllapitokohteet/+viallinen-yllapitokohteen-sijainti+
             "Loppuetäisyys ei saa olla negatiivinen. Sijainti: {:aosa 1, :aet 1, :losa 2, :let -1}")
          (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde alikohteet))
        "Alikohteen negatiivinen loppuetäisyys otettiin kiinni")
    (is (thrown+?
          #(tasmaa-poikkeus
             %
             yllapitokohteet/+kohteissa-viallisia-sijainteja+
             yllapitokohteet/+viallinen-yllapitokohteen-sijainti+
             "Alkuetäisyys ei saa olla negatiivinen. Sijainti: {:aosa 1, :aet -1, :losa 2, :let 2}")
          (yllapitokohteet/tarkista-alustatoimenpiteiden-sijainnit 1 kohde alustatoimenpiteet))
        "Alustatoimenpiteen negatiivinen alkuetäisyys otettiin kiinni")))

(deftest tarkista-alikohteen-sisaltyminen-kohteeseen
  (is (yllapitokohteet/alikohde-kohteen-sisalla? {:aosa 1 :aet 1 :losa 2 :let 1} {:aosa 1 :aet 1 :losa 2 :let 1}))
  (is (yllapitokohteet/alikohde-kohteen-sisalla? {:aosa 1 :aet 1 :losa 3 :let 1} {:aosa 1 :aet 1 :losa 2 :let 1}))
  (is (yllapitokohteet/alikohde-kohteen-sisalla? {:aosa 1 :aet 1 :losa 3 :let 1} {:aosa 1 :aet 1 :losa 2 :let 1000}))
  (is (yllapitokohteet/alikohde-kohteen-sisalla? {:aosa 1 :aet 1 :losa 3 :let 1} {:aosa 2 :aet 1000 :losa 3 :let 1}))

  (is (not (yllapitokohteet/alikohde-kohteen-sisalla? {:aosa 2 :aet 1 :losa 3 :let 1} {:aosa 1 :aet 1 :losa 3 :let 1})))
  (is (not (yllapitokohteet/alikohde-kohteen-sisalla? {:aosa 1 :aet 2 :losa 3 :let 1} {:aosa 1 :aet 1 :losa 3 :let 1})))
  (is (not (yllapitokohteet/alikohde-kohteen-sisalla? {:aosa 1 :aet 1 :losa 3 :let 1} {:aosa 1 :aet 1 :losa 4 :let 1})))
  (is (not (yllapitokohteet/alikohde-kohteen-sisalla? {:aosa 1 :aet 1 :losa 3 :let 1} {:aosa 1 :aet 1 :losa 3 :let 2}))))


(deftest tarkista-etteivat-alikohteet-mene-paallekkain
  (is (= []
         (yllapitokohteet/tarkista-etteivat-alikohteet-mene-paallekkain [{:tunniste {:id 1}
                                                                          :sijainti {:tie 20
                                                                                     :numero 20
                                                                                     :ajorata 1
                                                                                     :ajr 1
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
                                                                                     :let 100}}]))
      "Toisiaan jatkavat kohteet eivät palauta virheitä")
  (is (= []
         (yllapitokohteet/tarkista-etteivat-alikohteet-mene-paallekkain [{:tunniste {:id 1}
                                                                          :sijainti {:tie 20
                                                                                     :numero 20
                                                                                     :ajorata 1
                                                                                     :ajr 1
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
                                                                                     :aosa 4
                                                                                     :aet 10
                                                                                     :losa 4
                                                                                     :let 100}}]))
      "Hypylliset kohteet eivät palauta virheitä")

  (is (= [{:koodi "viallinen-alikohteen-sijainti"
           :viesti "Alikohteet (tunnus: 1 ja tunnus: 2) menevät päällekkäin"}]
         (yllapitokohteet/tarkista-etteivat-alikohteet-mene-paallekkain [{:tunniste {:id 1}
                                                                          :sijainti {:tie 20
                                                                                     :numero 20
                                                                                     :ajorata 1
                                                                                     :ajr 1
                                                                                     :kaista 1
                                                                                     :aosa 1
                                                                                     :aet 1
                                                                                     :losa 3
                                                                                     :let 100}}
                                                                         {:tunniste {:id 2}
                                                                          :sijainti {:tie 20
                                                                                     :numero 20
                                                                                     :ajorata 1
                                                                                     :ajr 1
                                                                                     :kaista 1
                                                                                     :aosa 3
                                                                                     :aet 10
                                                                                     :losa 4
                                                                                     :let 100}}]))
      "Päällekkäin menevät kohteet huomataan")

  (is (= []
         (yllapitokohteet/tarkista-etteivat-alikohteet-mene-paallekkain [{:tunniste {:id 4}
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
                                                                                     :kaista 11
                                                                                     :aosa 1
                                                                                     :aet 1
                                                                                     :losa 3
                                                                                     :let 1}}
                                                                         {:tunniste {:id 2}
                                                                          :sijainti {:tie 20
                                                                                     :numero 20
                                                                                     :ajorata 2
                                                                                     :ajr 2
                                                                                     :kaista 11
                                                                                     :aosa 3
                                                                                     :aet 1
                                                                                     :losa 4
                                                                                     :let 100}}]))
      "Eri ajoradoilla ja kaistoilla olevat validit kohteet eivät palauta virhettä")

  (is (= [{:koodi "viallinen-alikohteen-sijainti"
           :viesti "Alikohteet (tunnus: 3 ja tunnus: 4) menevät päällekkäin"}]
         (yllapitokohteet/tarkista-etteivat-alikohteet-mene-paallekkain [{:tunniste {:id 4}
                                                                          :sijainti {:tie 20
                                                                                     :numero 20
                                                                                     :ajorata 2
                                                                                     :ajr 2
                                                                                     :kaista 11
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
                                                                                     :kaista 11
                                                                                     :aosa 1
                                                                                     :aet 1
                                                                                     :losa 3
                                                                                     :let 100}}
                                                                         {:tunniste {:id 2}
                                                                          :sijainti {:tie 20
                                                                                     :numero 20
                                                                                     :ajorata 1
                                                                                     :ajr 1
                                                                                     :kaista 1
                                                                                     :aosa 3
                                                                                     :aet 1
                                                                                     :losa 4
                                                                                     :let 100}}]))
      "Eri ajoradoilla ja kaistoilla olevat päällekkäin olevat kohteet huomataan"))

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
(def oikea-tr-vali {:tr-numero 22 :ajorata 1 :kaista 12 :tr-alkuosa 1 :tr-alkuetaisyys 5000 :tr-loppuosa 1 :tr-loppuetaisyys 5200})

(def vaara-tr-paaluvali {:tr-numero 22 :tr-alkuosa 6 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 1})
(def vaara-tr-vali {:tr-numero 22 :ajorata 0 :kaista 11 :tr-alkuosa 5 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 100})

(def tr-tieto [{:tr-numero 22
                :tr-osa 5
                :pituudet {:pituus 10000
                           :ajoradat [{:osiot [{:pituus 10000
                                                :kaistat [{:kaista 1 :pituus 10000 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}]
                                       :ajorata 0}]
                           :tr-alkuetaisyys 0}}
               {:tr-numero 22
                :tr-osa 1
                :pituudet {:pituus 10000
                           :ajoradat [{:osiot [{:pituus 100
                                                :kaistat [{:kaista 1 :pituus 100 :tr-alkuetaisyys 0}]
                                                :tr-alkuetaisyys 0}
                                               {:pituus 1000
                                                :kaistat [{:kaista 1 :pituus 1000 :tr-alkuetaisyys 4000}]
                                                :tr-alkuetaisyys 4000}
                                               {:pituus 4400
                                                :kaistat [{:kaista 1 :pituus 4400 :tr-alkuetaisyys 5600}]
                                                :tr-alkuetaisyys 5600}]
                                       :ajorata 0}
                                      {:osiot [{:pituus 3900
                                                :kaistat [{:kaista 12 :pituus 3900 :tr-alkuetaisyys 100}
                                                          {:kaista 11 :pituus 3900 :tr-alkuetaisyys 100}]
                                                :tr-alkuetaisyys 100}
                                               {:pituus 600
                                                :kaistat [{:kaista 11 :pituus 600 :tr-alkuetaisyys 5000}
                                                          {:kaista 12 :pituus 600 :tr-alkuetaisyys 5000}]
                                                :tr-alkuetaisyys 5000}]
                                       :ajorata 1}
                                      {:osiot [{:pituus 3900
                                                :kaistat [{:kaista 22 :pituus 3900 :tr-alkuetaisyys 100}
                                                          {:kaista 21 :pituus 3900 :tr-alkuetaisyys 100}]
                                                :tr-alkuetaisyys 100}
                                               {:pituus 600
                                                :kaistat [{:kaista 21 :pituus 600 :tr-alkuetaisyys 5000}
                                                          {:kaista 22 :pituus 600 :tr-alkuetaisyys 5000}]
                                                :tr-alkuetaisyys 5000}]
                                       :ajorata 2}]
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
    (is (yllapitokohteet/tr-paalupiste-tr-tiedon-mukainen? (dissoc oikea-tr-paaluvali :tr-loppuosa :tr-loppuetaisyys) (second tr-tieto)))
    (is (yllapitokohteet/tr-paalupiste-tr-tiedon-mukainen? (-> oikea-tr-paaluvali
                                                               (dissoc :tr-alkuosa :tr-alkuetaisyys)
                                                               (clj-set/rename-keys {:tr-loppuosa :tr-alkuosa
                                                                                     :tr-loppuetaisyys :tr-alkuetaisyys}))
                                                           (first tr-tieto))))
  (testing "tr piste tr tiedon mukainen"
    (is (yllapitokohteet/tr-piste-tr-tiedon-mukainen? (dissoc oikea-tr-vali :tr-loppuosa :tr-loppuetaisyys) (second tr-tieto)))
    (is (yllapitokohteet/tr-piste-tr-tiedon-mukainen? (-> oikea-tr-vali
                                                          (dissoc :tr-alkuosa :tr-alkuetaisyys)
                                                          (clj-set/rename-keys {:tr-loppuosa :tr-alkuosa
                                                                                :tr-loppuetaisyys :tr-alkuetaisyys}))
                                                      (second tr-tieto)))))

(deftest validoi-paatrt-paallekkain
  (testing "tr vaalit paallekkain"
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 1}))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 100 :tr-loppuosa 5 :tr-loppuetaisyys 1}))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 3 :tr-alkuetaisyys 1 :tr-loppuosa 6 :tr-loppuetaisyys 1}))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0 :tr-loppuosa 6 :tr-loppuetaisyys 1}))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0 :tr-loppuosa 3 :tr-loppuetaisyys 1}))
    (is (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 1 :tr-loppuosa 3 :tr-loppuetaisyys 1}))
    (is (not (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 1 :tr-alkuetaisyys 0 :tr-loppuosa 1 :tr-loppuetaisyys 1})))
    (is (not (yllapitokohteet/tr-valit-paallekkain? oikea-tr-paaluvali {:tr-numero 22 :tr-alkuosa 5 :tr-alkuetaisyys 1 :tr-loppuosa 5 :tr-loppuetaisyys 100})))))