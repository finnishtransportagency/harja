(ns harja.domain.kohteiden-validointi-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
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
                        :sijainti {:aosa 1, :aet 1, :losa 4, :let 4}}]
        kaksi-alikohdetta [{:tunnus "A"
                            :sijainti {:aosa 1, :aet 1, :losa 2, :let 2}}
                           {:tunnus "B"
                            :sijainti {:aosa 2, :aet 2, :losa 4, :let 4}}]
        monta-alikohdetta [{:tunnus "A"
                            :sijainti {:aosa 1, :aet 1, :losa 2, :let 2}}
                           {:tunnus "B"
                            :sijainti {:aosa 2, :aet 2, :losa 3, :let 3}}
                           {:tunnus "C"
                            :sijainti {:aosa 3, :aet 3, :losa 4, :let 4}}]
        yksi-alustatoimenpide [{:sijainti {:aosa 1, :aet 1, :losa 2, :let 2}}]
        kaksi-alustatoimenpidetta [{:sijainti {:aosa 1, :aet 1, :losa 2, :let 2}}
                                   {:sijainti {:aosa 2, :aet 2, :losa 4, :let 4}}]
        monta-alustatoimenpidetta [{:sijainti {:aosa 1, :aet 1, :losa 2, :let 2}}
                                   {:sijainti {:aosa 2, :aet 2, :losa 3, :let 3}}
                                   {:sijainti {:aosa 3, :aet 3, :losa 4, :let 4}}]]

    (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde yksi-alikohde)
    (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde kaksi-alikohdetta)
    (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde monta-alikohdetta)

    (yllapitokohteet/tarkista-alustatoimenpiteiden-sijainnit 1 kohde yksi-alustatoimenpide)
    (yllapitokohteet/tarkista-alustatoimenpiteiden-sijainnit 1 kohde kaksi-alustatoimenpidetta)
    (yllapitokohteet/tarkista-alustatoimenpiteiden-sijainnit 1 kohde monta-alustatoimenpidetta)))

(deftest tarkista-alustatoimenpiteiden-validius
  (let [kohde {:aosa 1 :aet 1 :losa 4 :let 4}
        alustatoimenpiteet [{:sijainti {:aosa 1, :aet 1, :losa 2, :let 2}}
                            {:sijainti {:aosa 2, :aet 2, :losa 5, :let 3}}]]
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
           :viesti "Alikohteet (tunnus: 1 ja tunnus: 2) menevät päällekäin"}]
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
           :viesti "Alikohteet (tunnus: 3 ja tunnus: 4) menevät päällekäin"}]
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

(deftest tarkista-alikohteiden-ajoradat-ja-kaistat
  (let [kohteen-sijainti {:tie 20
                          :numero 20
                          :ajorata 1
                          :kaista 1
                          :aosa 1
                          :aet 100
                          :losa 1
                          :let 200}
        alikohteet [{:tunniste {:id 2}
                     :sijainti {:tie 20
                                :numero 20
                                :ajorata 1
                                :kaista 1
                                :aosa 3
                                :aet 1
                                :losa 4
                                :let 100}}]]

    (let [kohteen-sijainti (assoc kohteen-sijainti :ajorata 2)
          odotettu-virhe [{:koodi "viallinen-alikohteen-sijainti",
                           :viesti "Alikohteen (tunniste: 2) ajorata (1) ei ole pääkohteen (tunniste: 666) kanssa sama (2)."}]]
      (is (= odotettu-virhe
             (yllapitokohteet/tarkista-alikohteiden-ajoradat-ja-kaistat 666 kohteen-sijainti alikohteet))
          "Kun pääkohteella on ajorata, pitää alikohteella sen olla sama"))

    (let [kohteen-sijainti (assoc kohteen-sijainti :kaista 11)
          odotettu-virhe [{:koodi "viallinen-alikohteen-sijainti",
                           :viesti "Alikohteen (tunniste: 2) kaista: (1) ei ole pääkohteen (tunniste: 666) kanssa sama (11)."}]]
      (is (= odotettu-virhe
             (yllapitokohteet/tarkista-alikohteiden-ajoradat-ja-kaistat 666 kohteen-sijainti alikohteet))
          "Kun pääkohteella on kaista, pitää alikohteilla sen olla sama"))

    (let [kohteen-sijainti (dissoc kohteen-sijainti :ajorata :kaista)]
      (is (= [] (yllapitokohteet/tarkista-alikohteiden-ajoradat-ja-kaistat 666 kohteen-sijainti alikohteet))
          "Kun pääkohteella ei ole ajorataa tai kaistaa, ei alikohteiden "))))