(ns harja.palvelin.palvelut.vesivaylat.materiaalit-test
  (:require [harja.palvelin.palvelut.vesivaylat.materiaalit :as sut]
            [clojure.test :as t :refer [deftest is]]
            [harja.testi :refer [jarjestelma kutsu-palvelua q-map] :as testi]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.vesivaylat.materiaalit :as vv-materiaalit]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.pvm :as pvm]
            [harja.tyokalut.functor :refer [fmap]]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testi/testitietokanta)
                        :http-palvelin (testi/testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet testi/testi-pois-kytketyt-ominaisuudet
                        :vv-materiaalit (component/using
                                          (vv-materiaalit/->Materiaalit)
                                          [:db :http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(t/use-fixtures :each testi/tietokanta-fixture jarjestelma-fixture)

(def pvm-gen
  (gen/fmap (fn [[vuosi kk pv]]
              (pvm/luo-pvm vuosi kk pv))
            (gen/tuple (gen/choose 2000 2020)
                       (gen/choose 1 12)
                       (gen/choose 1 28))))

(def testimateriaali-gen
  (gen/fmap
    (fn [[nimi maara pvm lisatieto]]
      {::m/urakka-id 1
       ::m/nimi nimi
       ::m/maara maara
       ::m/pvm pvm
       ::m/lisatieto lisatieto})
    (gen/tuple (gen/elements #{"poiju" "viitta" "akku"})
               (gen/choose -1000 1000)
               pvm-gen
               gen/string-alphanumeric)))

(deftest materiaalen-kirjaus-ja-haku
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)]
    (loop [testauskerrat 50
           generoidut-materiaalit []]

      ;; Assertoi, että palvelun kautta haettu ja generoiduista materiaaleista laskettu
      ;; summa on sama kaikille materiaaleille
      (let [haettu-listaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-vesivayla-materiaalilistaus
                                           testi/+kayttaja-jvh+
                                           {::m/urakka-id urakka-id})

            listauksen-maara (fmap (comp ::m/maara-nyt first)
                                   (group-by ::m/nimi haettu-listaus))

            generoidut-maarat (fmap #(reduce + 0 (map ::m/maara %))
                                    (group-by ::m/nimi generoidut-materiaalit))]

        (is (= listauksen-maara generoidut-maarat))

        (when (pos? testauskerrat)
          ;; Generoi ja kirjaa uusi materiaali
          (let [m (gen/generate testimateriaali-gen)]
            (kutsu-palvelua (:http-palvelin jarjestelma)
                            :kirjaa-vesivayla-materiaali
                            testi/+kayttaja-jvh+
                            m)
            (recur (dec testauskerrat) (conj generoidut-materiaalit m))))))))

(deftest materiaalen-haku-ilman-oikeutta
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-vesivayla-materiaalilistaus
                                          testi/+kayttaja-ulle+
                                          {::m/urakka-id urakka-id})))))

(deftest materiaalen-kirjaus-ilman-oikeutta
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :kirjaa-vesivayla-materiaali
                                           testi/+kayttaja-ulle+
                                           {::m/urakka-id urakka-id})))))

(deftest materiaalien-poisto
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)
        poistettava-materiaali-ennen (first (q-map "SELECT id, poistettu FROM vv_materiaali WHERE poistettu IS NOT TRUE LIMIT 1"))
        materiaalien-lkm-ennen (:maara (first (q-map "SELECT COUNT(*) as maara FROM vv_materiaali WHERE poistettu IS NOT TRUE")))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-materiaalikirjaus
                          testi/+kayttaja-jvh+
                          {::m/urakka-id urakka-id
                           ::m/id (:id poistettava-materiaali-ennen)})
        poistettava-materiaali-jalkeen (first (q-map "SELECT id, poistettu FROM vv_materiaali WHERE id = " (:id poistettava-materiaali-ennen)))
        materiaalien-lkm-jalkeen (:maara (first (q-map "SELECT COUNT(*) as maara FROM vv_materiaali WHERE poistettu IS NOT TRUE")))]

    ;; Ei-poistettu materiaali merkittiin poistetuksi
    (is (false? (:poistettu poistettava-materiaali-ennen)))
    (is (true? (:poistettu poistettava-materiaali-jalkeen)))

    ;; Muita matskuja ei poistettu
    (is (= materiaalien-lkm-ennen (+ materiaalien-lkm-jalkeen 1)))))

(deftest materiaalen-poisto-ilman-oikeutta
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :poista-materiaalikirjaus
                                           testi/+kayttaja-ulle+
                                           {::m/urakka-id urakka-id})))))

(deftest materiaalen-poisto-eri-urakasta
  (let [urakka-id (testi/hae-muhoksen-paallystysurakan-id)
        poistettava-materiaali-id (:id (first (q-map "SELECT id FROM vv_materiaali WHERE poistettu IS NOT TRUE LIMIT 1")))]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :poista-materiaalikirjaus
                                           testi/+kayttaja-jvh+
                                           {::m/urakka-id urakka-id
                                            ::m/id poistettava-materiaali-id})))))