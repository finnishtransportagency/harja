(ns harja.ui.perus.esimerkkisivut-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [harja.ui.perus.esimerkkisivut :as esimerkkisivut]
            [harja.ui.perus.simulaattori :as simulaattori]))

(deftest esimerkkisivut-latautuvat
  (is (seq esimerkkisivut/esimerkkisivut)))

(deftest esimerkkisivujen-id-t-ovat-yksilollisia
  (let [idt (map :id esimerkkisivut/esimerkkisivut)]
    (is (= (count idt) (count (set idt))))))

(deftest jokaisella-esimerkkisivulla-on-kuvaus-ja-kelvollinen-puu
  (doseq [{:keys [id nimi kuvaus puu]} esimerkkisivut/esimerkkisivut]
    (testing (str id)
      (is (keyword? id))
      (is (not (str/blank? nimi)) "nimi puuttuu")
      (is (not (str/blank? kuvaus)) "kuvaus puuttuu")
      (is (seq puu) "puu puuttuu")
      (is (empty? (simulaattori/virheet puu))
          "esimerkkisivun puun pitää läpäistä simulaattorin validointi"))))

(deftest hae-palauttaa-esimerkkisivun-id-lla
  (is (= :listaussivu (:id (esimerkkisivut/hae :listaussivu))))
  (is (nil? (esimerkkisivut/hae :ei-ole))))

(deftest esimerkkisivut-eivat-kayta-poistettua-valintaryhmaa
  (doseq [{:keys [id puu]} esimerkkisivut/esimerkkisivut]
    (testing (str id)
      (is (not-any? #(= :valintaryhma (:komponentti %))
            (tree-seq coll? seq puu))))))

(deftest tuotantosivujen-pilottiesimerkit-ovat-kuvattuna
  (let [pilottisivut (->> esimerkkisivut/esimerkkisivut
                          (filter :pilottisivu)
                          (map #(select-keys % [:id :pilottisivu]))
                          set)]
    (is (= #{{:id :pilotti-tarjoushinnat
              :pilottisivu {:tyyppi :listaus
                            :lahde "src/cljs/harja/views/hallinta/tarjoushinnat.cljs"
                            :less "dev-resources/less/pages/tarjoushinnat.less"}}
             {:id :pilotti-kulujen-kirjaus
              :pilottisivu {:tyyppi :lomake
                            :lahde "src/cljs/harja/views/urakka/kulut/kululomake.cljs"
                            :less "dev-resources/less/pages/kulujenkirjaus.less"}}
             {:id :pilotti-urakoiden-tilanne
              :pilottisivu {:tyyppi :yhteenveto
                            :lahde "src/cljs/harja/views/urakkatilanne/kojelauta.cljs"
                            :less "dev-resources/less/pages/kojelauta.less"}}}
           pilottisivut))))
