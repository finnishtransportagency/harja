(ns harja.kyselyt.tieverkko-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.tieverkko :as tieverkko]))

(use-fixtures :each tietokantakomponentti-fixture)

(def tienumero 6666)

(defn luo-tr-osoite [[osa a-et l-et ajorata kaista]]
  (u (str
       "INSERT INTO tr_osoitteet
        (\"tr-numero\", \"tr-ajorata\", \"tr-kaista\", \"tr-osa\",  \"tr-alkuetaisyys\", \"tr-loppuetaisyys\", tietyyppi)
        VALUES (" tienumero ", " ajorata ", " kaista ", " osa ", " a-et ", " l-et ", 1)")))

(defn luo-tr-osoitteet [osoitteet]
  (u (str "DELETE FROM tr_osoitteet WHERE \"tr-numero\" = " tienumero))
  (doseq [osoite osoitteet]
    (luo-tr-osoite osoite))
  (u "REFRESH MATERIALIZED VIEW tr_tiedot"))

(defn parametrit
  [a b c]
  {:tr-numero a
   :tr-alkuosa b
   :tr-loppuosa c})

(defn tarkista-tulos-ja-raaka-tulos
  [odotettu-tulos odotettu-raaka-tulos aosa losa]
  (let [db (:db jarjestelma)
        p (parametrit tienumero aosa losa)
        raaka-tulos (tieverkko/hae-trpisteiden-valinen-tieto-raaka db p)
        tulos (tieverkko/hae-trpisteiden-valinen-tieto-yhdistaa db p)]
    (is (= odotettu-raaka-tulos raaka-tulos) "Raaka tulos täytyy olla kuin odotettu")
    (is (= odotettu-tulos tulos) "Tulos täytyy olla kuin odotettu")))

(deftest eri-osat
  (luo-tr-osoitteet [[5 0 1500 1 11]
                     [2 1500 2500 1 11]
                     [4 400 5000 1 11]
                     [4 450 5000 2 11]
                     [7 700 5000 1 11]
                     [1 100 5000 1 11]])
  (let [haku-alku 2
        haku-loppu 5
        odotettu-raaka-tulos [{:tr-numero 6666,
                               :tr-osa 5,
                               :pituudet {:pituus 1500,
                                          :osoitteet [{:pituus 1500, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 0}],
                                          :tr-alkuetaisyys 0}}
                              {:tr-numero 6666,
                               :tr-osa 4,
                               :pituudet {:pituus 4600,
                                          :osoitteet [{:pituus 4600, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 400}
                                                      {:pituus 4550, :tr-kaista 11, :tr-ajorata 2, :tr-alkuetaisyys 450}],
                                          :tr-alkuetaisyys 400}}
                              {:tr-numero 6666,
                               :tr-osa 2,
                               :pituudet {:pituus 1000,
                                          :osoitteet [{:pituus 1000, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 1500}],
                                          :tr-alkuetaisyys 1500}}]
        odotettu-tulos [{:tr-numero 6666,
                         :tr-osa 5,
                         :pituudet {:pituus 1500,
                                    :ajoradat [{:osiot [{:pituus 1500,
                                                         :kaistat [{:pituus 1500, :tr-kaista 11, :tr-alkuetaisyys 0}],
                                                         :tr-alkuetaisyys 0}],
                                                :tr-ajorata 1}],
                                    :tr-alkuetaisyys 0}}
                        {:tr-numero 6666,
                         :tr-osa 4,
                         :pituudet {:pituus 4600,
                                    :ajoradat [{:osiot [{:pituus 4600,
                                                         :kaistat [{:pituus 4600, :tr-kaista 11, :tr-alkuetaisyys 400}],
                                                         :tr-alkuetaisyys 400}],
                                                :tr-ajorata 1}
                                               {:osiot [{:pituus 4550,
                                                         :kaistat [{:pituus 4550, :tr-kaista 11, :tr-alkuetaisyys 450}],
                                                         :tr-alkuetaisyys 450}],
                                                :tr-ajorata 2}],
                                    :tr-alkuetaisyys 400}}
                        {:tr-numero 6666,
                         :tr-osa 2,
                         :pituudet {:pituus 1000,
                                    :ajoradat [{:osiot [{:pituus 1000,
                                                         :kaistat [{:pituus 1000, :tr-kaista 11, :tr-alkuetaisyys 1500}],
                                                         :tr-alkuetaisyys 1500}],
                                                :tr-ajorata 1}],
                                    :tr-alkuetaisyys 1500}}]]
    (tarkista-tulos-ja-raaka-tulos odotettu-tulos odotettu-raaka-tulos haku-alku haku-loppu)))

(deftest sama-kaista-ja-rako
  (luo-tr-osoitteet [[1 0 1500 1 11]
                     [1 1500 2500 1 11]
                     [1 4500 5000 1 11]])
  (let [odotettu-raaka-tulos [{:tr-numero 6666,
                               :tr-osa 1,
                               :pituudet {:pituus 3000,
                                          :osoitteet [{:pituus 1500, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 0}
                                                      {:pituus 1000, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 1500}
                                                      {:pituus 500, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 4500}],
                                          :tr-alkuetaisyys 0}}]
        odotettu-tulos [{:tr-numero 6666,
                         :tr-osa 1,
                         :pituudet {:pituus 3000,
                                    :ajoradat [{:osiot [{:pituus 2500,
                                                         :kaistat [{:pituus 2500, :tr-kaista 11, :tr-alkuetaisyys 0}],
                                                         :tr-alkuetaisyys 0}
                                                        {:pituus 500,
                                                         :kaistat [{:pituus 500, :tr-kaista 11, :tr-alkuetaisyys 4500}],
                                                         :tr-alkuetaisyys 4500}],
                                                :tr-ajorata 1}],
                                    :tr-alkuetaisyys 0}}]]
    (tarkista-tulos-ja-raaka-tulos odotettu-tulos odotettu-raaka-tulos 1 1)))

(deftest eri-kaistat-kaikki-sisalla
  (luo-tr-osoitteet [[1 0 1500 1 11]
                     [1 100 300 1 21]
                     [1 1500 2500 1 11]])
  (let [odotettu-raaka-tulos [{:tr-numero 6666,
                               :tr-osa 1,
                               :pituudet {:pituus 2500,
                                          :osoitteet [{:pituus 1500, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 0}
                                                      {:pituus 1000, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 1500}
                                                      {:pituus 200, :tr-kaista 21, :tr-ajorata 1, :tr-alkuetaisyys 100}],
                                          :tr-alkuetaisyys 0}}]
        odotettu-tulos [{:tr-numero 6666,
                         :tr-osa 1,
                         :pituudet {:pituus 2500,
                                    :tr-alkuetaisyys 0,
                                    :ajoradat [{:osiot [{:pituus 2500,
                                                         :kaistat [{:pituus 2500, :tr-kaista 11, :tr-alkuetaisyys 0}
                                                                   {:pituus 200, :tr-kaista 21, :tr-alkuetaisyys 100}],
                                                         :tr-alkuetaisyys 0}],
                                                :tr-ajorata 1}]}}]]
    (tarkista-tulos-ja-raaka-tulos odotettu-tulos odotettu-raaka-tulos 1 1)))

(deftest eri-kaistat-kaksi-sisalla
  (luo-tr-osoitteet [[1 0 1500 1 11]
                     [1 100 300 1 21]
                     [1 350 400 1 21]
                     [1 1500 2500 1 11]])
  (let [odotettu-raaka-tulos [{:tr-numero 6666,
                              :tr-osa 1,
                              :pituudet {:pituus 2500,
                                         :osoitteet [{:pituus 1500, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 0}
                                                    {:pituus 1000, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 1500}
                                                    {:pituus 200, :tr-kaista 21, :tr-ajorata 1, :tr-alkuetaisyys 100}
                                                    {:pituus 50, :tr-kaista 21, :tr-ajorata 1, :tr-alkuetaisyys 350}],
                                         :tr-alkuetaisyys 0}}]
        odotettu-tulos [{:tr-numero 6666,
                         :tr-osa 1,
                         :pituudet {:pituus 2500,
                                    :tr-alkuetaisyys 0,
                                    :ajoradat [{:osiot [{:pituus 2500,
                                                         :kaistat [{:pituus 2500, :tr-kaista 11, :tr-alkuetaisyys 0}
                                                                   {:pituus 200, :tr-kaista 21, :tr-alkuetaisyys 100}
                                                                   {:pituus 50, :tr-kaista 21, :tr-alkuetaisyys 350}],
                                                         :tr-alkuetaisyys 0}],
                                                :tr-ajorata 1}]}}]]
    (tarkista-tulos-ja-raaka-tulos odotettu-tulos odotettu-raaka-tulos 1 1)))

(deftest eri-kaistat-menee-yli
  (luo-tr-osoitteet [[1 0 1500 1 11]
                     [1 100 3000 1 21]
                     [1 1600 2500 1 11]])
  (let [odotettu-raaka-tulos [{:tr-numero 6666,
                               :tr-osa 1,
                               :pituudet {:pituus 2400,
                                          :osoitteet [{:pituus 1500, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 0}
                                                      {:pituus 900, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 1600}
                                                      {:pituus 2900, :tr-kaista 21, :tr-ajorata 1, :tr-alkuetaisyys 100}],
                                          :tr-alkuetaisyys 0}}]
        odotettu-tulos [{:tr-numero 6666,
                         :tr-osa 1,
                         :pituudet {:pituus 2400,
                                    :tr-alkuetaisyys 0,
                                    :ajoradat [{:osiot [{:pituus 3000,
                                                         :kaistat [{:pituus 1500, :tr-kaista 11, :tr-alkuetaisyys 0}
                                                                   {:pituus 2900, :tr-kaista 21, :tr-alkuetaisyys 100}
                                                                   {:pituus 900, :tr-kaista 11, :tr-alkuetaisyys 1600}],
                                                         :tr-alkuetaisyys 0}],
                                                :tr-ajorata 1}]}}]]
    (tarkista-tulos-ja-raaka-tulos odotettu-tulos odotettu-raaka-tulos 1 1)))

(deftest eri-kaistat-menee-kaikien-yli-mutta-vain-kaista-11-katsotaan
  ; TODO onko tämä realistinen tapaus?
  (luo-tr-osoitteet [[1 0 1500 1 11]
                     [1 100 3300 1 21]
                     [1 1500 2500 1 11]])
  (let [odotettu-raaka-tulos [{:tr-numero 6666,
                               :tr-osa 1,
                               :pituudet {:pituus 2500,
                                          :osoitteet [{:pituus 1500, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 0}
                                                      {:pituus 1000, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 1500}
                                                      {:pituus 3200, :tr-kaista 21, :tr-ajorata 1, :tr-alkuetaisyys 100}],
                                          :tr-alkuetaisyys 0}}]
        odotettu-tulos [{:tr-numero 6666,
                         :tr-osa 1,
                         :pituudet {:pituus 2500,
                                    :tr-alkuetaisyys 0,
                                    :ajoradat [{:osiot [{:pituus 3300,
                                                         :kaistat [{:pituus 2500, :tr-kaista 11, :tr-alkuetaisyys 0}
                                                                   {:pituus 3200, :tr-kaista 21, :tr-alkuetaisyys 100}],
                                                         :tr-alkuetaisyys 0}],
                                                :tr-ajorata 1}]}}]]
    (tarkista-tulos-ja-raaka-tulos odotettu-tulos odotettu-raaka-tulos 1 1)))

