(ns harja.palvelin.palvelut.kanavat.kanavat-test
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
            [harja.palvelin.palvelut.kanavat.kanavat :as kan-kanavat]
            [clojure.string :as str]

            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
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
                                       (kan-kanavat/->Kanavat)
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
          (::kanava/kohteet kanava)))
      vastaus)))

(defn- pakolliset-kentat? [vastaus]
  (every?
    (fn [kanava]
      ;; nämä arvot täytyy löytyä
      (and (every? some? ((juxt ::kanava/id ::kanava/nimi ::kanava/kohteet) kanava))
           (every?
             (fn [kohde]
               ;; nämä arvot täytyy löytyä
               (and (every? some? ((juxt ::kohde/id ::kohde/tyyppi) kohde))
                    ;; Nämä avaimet pitää olla
                    (every? (partial contains? kohde) [::kohde/urakat])
                    (every?
                      (fn [urakka]
                        ;; nämä arvot täytyy löytyä
                        (every? some? ((juxt ::ur/id ::ur/nimi) urakka)))
                      (::kohde/urakat kohde))))
             (::kanava/kohteet kanava))))
    vastaus))

(deftest kanavien-haku
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-kanavat-ja-kohteet
                                +kayttaja-jvh+)]

    (is (s/valid? ::kanava/hae-kanavat-ja-kohteet-vastaus vastaus))

    (is (jollain-kanavalla-nimi? vastaus))
    (is (pakolliset-kentat? vastaus))))

(deftest kohteiden-tallennus
  (let [kohteiden-lkm (count (q "SELECT * FROM kan_kohde WHERE poistettu IS NOT TRUE;"))]
    (testing "Nimen muuttaminen"
      (let [[kohde-id kanava-id] (first (q "SELECT id, \"kanava-id\" FROM kan_kohde WHERE nimi = 'Tikkalansaaren avattava ratasilta';"))
            _ (is (some? kohde-id))
            params [{::kohde/nimi "FOOBAR"
                     ::kohde/id kohde-id
                     ::kohde/kanava-id kanava-id
                     ::kohde/tyyppi :silta
                     ::m/poistettu? false}]
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :lisaa-kanavalle-kohteita
                                    +kayttaja-jvh+
                                    params)]
        (is (s/valid? ::kanava/lisaa-kanavalle-kohteita-kysely params))
        (is (s/valid? ::kanava/lisaa-kanavalle-kohteita-vastaus vastaus))

        (is (jollain-kanavalla-nimi? vastaus))
        (is (pakolliset-kentat? vastaus))

        (some
          (partial = "FOOBAR")
          (mapcat
            (fn [kanava]
              (map
                (fn [kohde]
                  (::kohde/nimi kohde))
                (::kanava/kohteet kanava)))
            vastaus))))

    (testing "Poistaminen"
      (let [[kohde-id kanava-id] (first (q "SELECT id, \"kanava-id\" FROM kan_kohde WHERE nimi = 'FOOBAR';"))
            _ (is (some? kohde-id))
            params [{::kohde/nimi "FOOBAR"
                     ::kohde/id kohde-id
                     ::kohde/kanava-id kanava-id
                     ::kohde/tyyppi :silta
                     ::m/poistettu? true}]
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :lisaa-kanavalle-kohteita
                                    +kayttaja-jvh+
                                    params)]
        (is (s/valid? ::kanava/lisaa-kanavalle-kohteita-kysely params))
        (is (s/valid? ::kanava/lisaa-kanavalle-kohteita-vastaus vastaus))


        (is (pakolliset-kentat? vastaus))
        (is (= (dec kohteiden-lkm)
               (count (q "SELECT * FROM kan_kohde WHERE poistettu IS NOT TRUE;"))))))

    (testing "Uuden lisääminen"
      (let [[kanava-id] (first (q "SELECT \"kanava-id\" FROM kan_kohde WHERE nimi = 'FOOBAR';"))
            params [{::kohde/nimi "UUSI"
                     ::kohde/kanava-id kanava-id
                     ::kohde/tyyppi :silta
                     ::kohde/id -1
                     ::m/poistettu? false}]
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :lisaa-kanavalle-kohteita
                                    +kayttaja-jvh+
                                    params)]
        (is (s/valid? ::kanava/lisaa-kanavalle-kohteita-kysely params))
        (is (s/valid? ::kanava/lisaa-kanavalle-kohteita-vastaus vastaus))

        (is (jollain-kanavalla-nimi? vastaus))
        (is (pakolliset-kentat? vastaus))

        (some
          (partial = "UUSI")
          (mapcat
            (fn [kanava]
              (map
                (fn [kohde]
                  (::kohde/nimi kohde))
                (::kanava/kohteet kanava)))
            vastaus))

        (is (= kohteiden-lkm
               (count (q "SELECT * FROM kan_kohde WHERE poistettu IS NOT TRUE;"))))))))

(deftest kohteen-liittaminen-urakkaan
  (testing "Uuden linkin lisääminen"
    (let [[kohde-id] (first (q "SELECT id FROM kan_kohde WHERE nimi = 'Taipaleen sulku';"))
          [urakka-id] (first (q "SELECT id FROM urakka WHERE nimi = 'Saimaan kanava';"))
          linkki (first (q (str "SELECT * FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                " AND \"urakka-id\" =" urakka-id ";")))
          _ (is (and (some? kohde-id) (some? urakka-id)))
          _ (is (empty? linkki))
          params {:urakka-id urakka-id
                  :kohde-id kohde-id
                  :poistettu? false}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :liita-kohde-urakkaan
                                  +kayttaja-jvh+
                                  params)]
      (is (s/valid? ::kanava/liita-kohde-urakkaan-kysely params))

      (let [[ur koh poistettu?] (first (q (str "SELECT \"urakka-id\", \"kohde-id\", poistettu FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                 " AND \"urakka-id\" =" urakka-id ";")))]
        (is (= ur urakka-id))
        (is (= koh kohde-id))
        (is (= poistettu? false)))))

  (testing "Linkin poistaminen"
    (let [[kohde-id] (first (q "SELECT id FROM kan_kohde WHERE nimi = 'Taipaleen sulku';"))
          [urakka-id] (first (q "SELECT id FROM urakka WHERE nimi = 'Saimaan kanava';"))
          linkki (first (q (str "SELECT * FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                " AND \"urakka-id\" =" urakka-id ";")))
          _ (is (and (some? kohde-id) (some? urakka-id)))
          _ (is (some? linkki))
          params {:urakka-id urakka-id
                  :kohde-id kohde-id
                  :poistettu? true}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :liita-kohde-urakkaan
                                  +kayttaja-jvh+
                                  params)]
      (is (s/valid? ::kanava/liita-kohde-urakkaan-kysely params))

      (let [[ur koh poistettu?] (first (q (str "SELECT \"urakka-id\", \"kohde-id\", poistettu FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                               " AND \"urakka-id\" =" urakka-id ";")))]
        (is (= ur urakka-id))
        (is (= koh kohde-id))
        (is (= poistettu? true)))))

  (testing "Linkin palauttaminen"
    (let [[kohde-id] (first (q "SELECT id FROM kan_kohde WHERE nimi = 'Taipaleen sulku';"))
          [urakka-id] (first (q "SELECT id FROM urakka WHERE nimi = 'Saimaan kanava';"))
          linkki (first (q (str "SELECT * FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                " AND \"urakka-id\" =" urakka-id ";")))
          _ (is (and (some? kohde-id) (some? urakka-id)))
          _ (is (some? linkki))
          params {:urakka-id urakka-id
                  :kohde-id kohde-id
                  :poistettu? false}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :liita-kohde-urakkaan
                                  +kayttaja-jvh+
                                  params)]
      (is (s/valid? ::kanava/liita-kohde-urakkaan-kysely params))

      (let [[ur koh poistettu?] (first (q (str "SELECT \"urakka-id\", \"kohde-id\", poistettu FROM kan_kohde_urakka WHERE \"kohde-id\" = " kohde-id
                                               " AND \"urakka-id\" =" urakka-id ";")))]
        (is (= ur urakka-id))
        (is (= koh kohde-id))
        (is (= poistettu? false))))))

(deftest kohteen-poistaminen
  (let [[kohde-id, poistettu?] (first (q "SELECT id, poistettu FROM kan_kohde WHERE nimi = 'Taipaleen sulku';"))
        _ (is (some? kohde-id))
        _ (is (false? poistettu?))
        params {:kohde-id kohde-id}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :poista-kohde
                                +kayttaja-jvh+
                                params)]
    (is (s/valid? ::kanava/poista-kohde-kysely params))

    (let [[id poistettu?] (first (q (str "SELECT id, poistettu FROM kan_kohde WHERE id = " kohde-id ";")))]
      (is (= id kohde-id))
      (is (= poistettu? true)))))