(ns harja.palvelin.palvelut.kanavat.kohteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as pois-kytketyt-ominaisuudet]
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
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet testi-pois-kytketyt-ominaisuudet
                        :kan-kanavat (component/using
                                       (kan-kohteet/->Kohteet)
                                       [:http-palvelin :db :pois-kytketyt-ominaisuudet])))))
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

(deftest kohteiden-tallennus
  (let [kohteiden-lkm (count (q "SELECT * FROM kan_kohde WHERE poistettu IS NOT TRUE;"))]
    (testing "Nimen muuttaminen"
      (let [[kohde-id kohdekokonaisuus-id] (first (q "SELECT id, \"kohdekokonaisuus-id\" FROM kan_kohde WHERE nimi = 'Pälli';"))
            _ (is (some? kohde-id))
            params [{::kohde/nimi "FOOBAR"
                     ::kohde/id kohde-id
                     ::kohde/kohdekokonaisuus-id kohdekokonaisuus-id
                     ::m/poistettu? false}]
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :lisaa-kohdekokonaisuudelle-kohteita
                                    +kayttaja-jvh+
                                    params)]
        (is (s/valid? ::kok/lisaa-kohdekokonaisuudelle-kohteita-kysely params))
        (is (s/valid? ::kok/lisaa-kohdekokonaisuudelle-kohteita-vastaus vastaus))

        (is (jollain-kanavalla-nimi? vastaus))
        (is (pakolliset-kentat? vastaus))

        (some
          (partial = "FOOBAR")
          (mapcat
            (fn [kanava]
              (map
                (fn [kohde]
                  (::kohde/nimi kohde))
                (::kok/kohteet kanava)))
            vastaus))))

    (testing "Poistaminen"
      (let [[kohde-id kohdekokonaisuus-id] (first (q "SELECT id, \"kohdekokonaisuus-id\" FROM kan_kohde WHERE nimi = 'FOOBAR';"))
            _ (is (some? kohde-id))
            params [{::kohde/nimi "FOOBAR"
                     ::kohde/id kohde-id
                     ::kohde/kohdekokonaisuus-id kohdekokonaisuus-id
                     ::m/poistettu? true}]
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :lisaa-kohdekokonaisuudelle-kohteita
                                    +kayttaja-jvh+
                                    params)]
        (is (s/valid? ::kok/lisaa-kohdekokonaisuudelle-kohteita-kysely params))
        (is (s/valid? ::kok/lisaa-kohdekokonaisuudelle-kohteita-vastaus vastaus))


        (is (pakolliset-kentat? vastaus))
        (is (= (dec kohteiden-lkm)
               (count (q "SELECT * FROM kan_kohde WHERE poistettu IS NOT TRUE;"))))))

    (testing "Uuden lisääminen"
      (let [[kohdekokonaisuus-id] (first (q "SELECT \"kohdekokonaisuus-id\" FROM kan_kohde WHERE nimi = 'FOOBAR';"))
            params [{::kohde/nimi "UUSI"
                     ::kohde/kohdekokonaisuus-id kohdekokonaisuus-id
                     ::kohde/id -1
                     ::m/poistettu? false}]
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :lisaa-kohdekokonaisuudelle-kohteita
                                    +kayttaja-jvh+
                                    params)]
        (is (s/valid? ::kok/lisaa-kohdekokonaisuudelle-kohteita-kysely params))
        (is (s/valid? ::kok/lisaa-kohdekokonaisuudelle-kohteita-vastaus vastaus))

        (is (jollain-kanavalla-nimi? vastaus))
        (is (pakolliset-kentat? vastaus))

        (some
          (partial = "UUSI")
          (mapcat
            (fn [kanava]
              (map
                (fn [kohde]
                  (::kohde/nimi kohde))
                (::kok/kohteet kanava)))
            vastaus))

        (is (= kohteiden-lkm
               (count (q "SELECT * FROM kan_kohde WHERE poistettu IS NOT TRUE;"))))))))

(deftest kohteen-liittaminen-urakkaan
  (testing "Uuden linkin lisääminen"
    (let [kohde-id (hae-kohde-iisalmen-kanava)
          urakka-id (hae-saimaan-kanavaurakan-id)
          linkki (first (q (str "SELECT * FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                " AND \"urakka-id\" =" urakka-id ";")))
          _ (is (and (some? kohde-id) (some? urakka-id)))
          _ (is (empty? linkki))
          params {:urakka-id urakka-id
                  :kohde-id kohde-id
                  :poistettu? false}
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
          params {:urakka-id urakka-id
                  :kohde-id kohde-id
                  :poistettu? true}
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
          params {:urakka-id urakka-id
                  :kohde-id kohde-id
                  :poistettu? false}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :liita-kohteet-urakkaan
                                  +kayttaja-jvh+
                                  params)]

      (let [[ur koh poistettu?] (first (q (str "SELECT \"urakka-id\", \"kohde-id\", poistettu FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                               " AND \"urakka-id\" =" urakka-id ";")))]
        (is (= ur urakka-id))
        (is (= koh kohde-id))
        (is (= poistettu? false))))))

;; TODO Poistaminen disabloitu toistaiseksi
#_(deftest kohteen-poistaminen
  (let [[kohde-id, poistettu?] (first (q "SELECT id, poistettu FROM kan_kohde WHERE nimi = 'Iisalmen kanava';"))
        _ (is (some? kohde-id))
        _ (is (false? poistettu?))
        params {:kohde-id kohde-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :poista-kohde
                                +kayttaja-jvh+
                                params)]
    (is (s/valid? ::kok/poista-kohde-kysely params))

    (let [[id poistettu?] (first (q (str "SELECT id, poistettu FROM kan_kohde WHERE id = " kohde-id ";")))]
      (is (= id kohde-id))
      (is (= poistettu? true)))))