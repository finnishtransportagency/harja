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
             "Alikohde (tunnus: C) ei ole kohteen (id: 1) sisällä.")
          (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde alikohteet))
        "Kohteen ulkopuolinen alikohde otettiin kiinni"))

  (let [kohde {:aosa 1 :aet 1 :losa 5 :let 5}
        alikohteet [{:tunnus "A"
                     :sijainti {:aosa 1, :aet 1, :losa 2, :let 2}}
                    {:tunnus "B"
                     :sijainti {:aosa 2, :aet 2, :losa 3, :let 3}}
                    {:tunnus "C"
                     :sijainti {:aosa 3, :aet 3, :losa 4, :let 1}}
                    {:tunnus "D"
                     :sijainti {:aosa 4, :aet 4, :losa 5, :let 5}}]]
    (is (thrown+?
          #(tasmaa-poikkeus
             %
             yllapitokohteet/+kohteissa-viallisia-sijainteja+
             yllapitokohteet/+viallinen-yllapitokohdeosan-sijainti+
             "Alikohteet (tunnus: C ja tunnus: D) eivät muodosta yhteistä osuutta")
          (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde alikohteet))
        "Alikohteet jotka eivät muodosta yhtenäistä osaa otettiin kiinni"))

  (let [kohde {:aosa 1 :aet 1 :losa 1 :let 1}
        alikohteet [{:tunnus "A"
                     :sijainti {:aosa 1, :aet 1, :losa 2, :let 2}}
                    {:tunnus "B"
                     :sijainti {:aosa 2, :aet 2, :losa 3, :let 3}}]]
    (is (thrown+?
          #(tasmaa-poikkeus
             %
             yllapitokohteet/+kohteissa-viallisia-sijainteja+
             yllapitokohteet/+viallinen-yllapitokohdeosan-sijainti+
             "Alikohteet eivät täytä kohdetta (id: 1)")
          (yllapitokohteet/tarkista-kohteen-ja-alikohteiden-sijannit 1 kohde alikohteet))
        "Alikohteet jotka eivät täytä kohdetta otettiin kiinni")))

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