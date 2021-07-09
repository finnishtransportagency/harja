(ns harja.palvelin.palvelut.kanavat.kohteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [harja.palvelin.palvelut.kanavat.kohteet :as kan-kohteet]
            [clojure.string :as str]
            
            [harja.domain.kanavat.kohdekokonaisuus :as kok]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :kan-kanavat (component/using
                                       (kan-kohteet/->Kohteet)
                                       [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn- jollain-kanavalla-nimi? [vastaus]
  (some
    some?
    (mapcat
      (fn [kanava]
        (map
          (fn [kohde]
            (::kohde/nimi kohde))
          (::kok/kohteet kanava)))
      vastaus)))

(defn- pakolliset-kentat? [vastaus]
  (every?
    (fn [kanava]
      ;; nämä arvot täytyy löytyä
      (and (every? some? ((juxt ::kok/id ::kok/nimi ::kok/kohteet) kanava))
           (every?
             (fn [kohde]
               ;; nämä arvot täytyy löytyä
               (and (every? some? ((juxt ::kohde/id) kohde))
                    ;; Nämä avaimet pitää olla
                    (every? (partial contains? kohde) [::kohde/urakat])
                    #_(every? (partial contains? kohde) [::kohde/kohteenosat])
                    #_(every?
                      (fn [osa]
                        ;; nämä arvot täytyy löytyä
                        (every? some? ((juxt ::osa/id ::osa/tyyppi) urakka)))
                      (::kohde/kohteenosat kohde))
                    (every?
                      (fn [urakka]
                        ;; nämä arvot täytyy löytyä
                        (every? some? ((juxt ::ur/id ::ur/nimi) urakka)))
                      (::kohde/urakat kohde))))
             (::kok/kohteet kanava))))
    vastaus))

(deftest kanavien-haku
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kohdekokonaisuudet-ja-kohteet
                                +kayttaja-jvh+)]

    (is (s/valid? ::kok/hae-kohdekokonaisuudet-ja-kohteet-vastaus vastaus))

    (is (jollain-kanavalla-nimi? vastaus))
    (is (pakolliset-kentat? vastaus))))

(deftest urakan-kohteiden-haku
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-kohteet
                                +kayttaja-jvh+
                                {::ur/id (hae-saimaan-kanavaurakan-id)})]
    (is (true? (every? (comp some? ::kohde/nimi) vastaus)))))

(deftest urakan-kohteiden-haku
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kanavien-huoltokohteet
                                +kayttaja-jvh+)]
    (is (true? (every? (comp some? ::huoltokohde/nimi) vastaus)))))

(deftest kohteen-liittaminen-urakkaan
  (testing "Uuden linkin lisääminen"
    (let [kohde-id (hae-kohde-iisalmen-kanava)
          urakka-id (hae-saimaan-kanavaurakan-id)
          linkki (first (q (str "SELECT * FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                " AND \"urakka-id\" =" urakka-id ";")))
          _ (is (and (some? kohde-id) (some? urakka-id)))
          _ (is (empty? linkki))
          params {:liitokset {[kohde-id urakka-id] true}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :liita-kohteet-urakkaan
                                  +kayttaja-jvh+
                                  params)]

      (let [[ur koh poistettu?] (first (q (str "SELECT \"urakka-id\", \"kohde-id\", poistettu FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                               " AND \"urakka-id\" =" urakka-id ";")))]
        (is (= ur urakka-id))
        (is (= koh kohde-id))
        (is (= poistettu? false)))))

  (testing "Linkin poistaminen"
    (let [kohde-id (hae-kohde-soskua)
          urakka-id (hae-saimaan-kanavaurakan-id)
          linkki (first (q (str "SELECT * FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                " AND \"urakka-id\" =" urakka-id ";")))
          _ (is (and (some? kohde-id) (some? urakka-id)))
          _ (is (some? linkki))
          params {:liitokset {[kohde-id urakka-id] false}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :liita-kohteet-urakkaan
                                  +kayttaja-jvh+
                                  params)]

      (let [[ur koh poistettu?] (first (q (str "SELECT \"urakka-id\", \"kohde-id\", poistettu FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                               " AND \"urakka-id\" =" urakka-id ";")))]
        (is (= ur urakka-id))
        (is (= koh kohde-id))
        (is (= poistettu? true)))))

  (testing "Linkin palauttaminen"
    (let [kohde-id (hae-kohde-soskua)
          urakka-id (hae-saimaan-kanavaurakan-id)
          linkki (first (q (str "SELECT * FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                " AND \"urakka-id\" =" urakka-id ";")))
          _ (is (and (some? kohde-id) (some? urakka-id)))
          _ (is (some? linkki))
          params {:liitokset {[kohde-id urakka-id] true}}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :liita-kohteet-urakkaan
                                  +kayttaja-jvh+
                                  params)]

      (let [[ur koh poistettu?] (first (q (str "SELECT \"urakka-id\", \"kohde-id\", poistettu FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                               " AND \"urakka-id\" =" urakka-id ";")))]
        (is (= ur urakka-id))
        (is (= koh kohde-id))
        (is (= poistettu? false))))))