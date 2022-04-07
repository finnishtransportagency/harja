(ns harja.kyselyt.tieverkko-test
    (:require [clojure.test :refer :all]
      [harja.kyselyt.konversio :as konversio]
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



(deftest testaa_yrita_tierekisteriosoite_pisteille2

         ;; Testataan löytyykö palautuuko tierekisteriosoite oikein, kun lähtötietona on alku- ja loppupiste.
         ;; Testin lähtöoletus on, että tierekisteriosoitteelle_piste-proseduuri palauttaa oikeita tietoja. Se on testiä kirjoittaessa manuaalisesti validoitu.
         ;; Jos testi feilaa, vika voi siis olla joko tierekisteriosoitteelle_piste- tai yrita_tierekisteriosoite_pisteille2-
         ;; proseduurissa tai muuttuneessa testiaineistossa.

         ;; TODO:
         ;; Olisi hyvä jos palautuva tierekisteriosoite ei hyppäisi turhaan seuraavaan tieosaan.
         ;; Esim. nämä osoitteet ovat käytännössä samassa sijainnissa, mutta ensimmäinen on luettavampi
         ;; {:numero 20, :alkuosa 22, :alkuetaisyys 0, :loppuosa 22, :loppuetaisyys 3000}
         ;; {:numero 20, :alkuosa 21, :alkuetaisyys 1942, :loppuosa 22, :loppuetaisyys 3000}

         (is (= {:numero 20, :alkuosa 22, :alkuetaisyys 2000, :loppuosa 22, :loppuetaisyys 3000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,22,2000)), (SELECT tierekisteriosoitteelle_piste(20,22, 3000)), 1)"))))) "Testi 1. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 22, :alkuetaisyys 3000, :loppuosa 22, :loppuetaisyys 2000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,22,3000)), (SELECT tierekisteriosoitteelle_piste(20,22, 2000)), 1)"))))) "Testi 2. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 21, :alkuetaisyys 1942, :loppuosa 22, :loppuetaisyys 3000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,22, 0)), (SELECT tierekisteriosoitteelle_piste(20,22, 3000)), 1)"))))) "Testi 3. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 22, :alkuetaisyys 9481, :loppuosa 22, :loppuetaisyys 3000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,22, 9481)), (SELECT tierekisteriosoitteelle_piste(20,22, 3000)), 1)"))))) "Testi 4. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 21, :alkuetaisyys 1942, :loppuosa 22, :loppuetaisyys 9481} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,22, 0)), (SELECT tierekisteriosoitteelle_piste(20,22, 9481)), 1)"))))) "Testi 5. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 22, :alkuetaisyys 9481, :loppuosa 21, :loppuetaisyys 1942} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,22, 9481)), (SELECT tierekisteriosoitteelle_piste(20,22, 0)), 1)"))))) "Testi 6. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 21, :alkuetaisyys 1000, :loppuosa 21, :loppuetaisyys 1942} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,21, 1000)), (SELECT tierekisteriosoitteelle_piste(20,22, 0)), 1)"))))) "Testi 7. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 21, :alkuetaisyys 1000, :loppuosa 22, :loppuetaisyys 9481} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,21, 1000)), (SELECT tierekisteriosoitteelle_piste(20,22, 9481)), 1)"))))) "Testi 8. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 19, :alkuetaisyys 10127, :loppuosa 22, :loppuetaisyys 9481} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,21, 0)), (SELECT tierekisteriosoitteelle_piste(20,22, 9481)), 1)"))))) "Testi 9. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 21, :alkuetaisyys 1942, :loppuosa 21, :loppuetaisyys 1942} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,21, 1942)), (SELECT tierekisteriosoitteelle_piste(20,22, 0)), 1)"))))) "Testi 10. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 22, :alkuetaisyys 3000, :loppuosa 21, :loppuetaisyys 1941} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,22, 3000)), (SELECT tierekisteriosoitteelle_piste(20,21, 1941)), 1)"))))) "Testi 11. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 22, :alkuetaisyys 3000, :loppuosa 19, :loppuetaisyys 10127} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,22, 3000)), (SELECT tierekisteriosoitteelle_piste(20,21, 0)), 1)"))))) "Testi 12. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 21, :alkuetaisyys 1942, :loppuosa 21, :loppuetaisyys 1941} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,22,0)), (SELECT tierekisteriosoitteelle_piste(20,21, 1941)), 1)"))))) "Testi 13. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 22, :alkuetaisyys 9481, :loppuosa 19, :loppuetaisyys 10127} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,22, 9481)), (SELECT tierekisteriosoitteelle_piste(20,21, 0)), 1)"))))) "Testi 14. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 21, :alkuetaisyys 1000, :loppuosa 22, :loppuetaisyys 9485} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,21, 1000)), (SELECT tierekisteriosoitteelle_piste(20,23, 0)), 1)"))))) "Testi 15. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 21, :alkuetaisyys 1000, :loppuosa 23, :loppuetaisyys 4628} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,21, 1000)), (SELECT tierekisteriosoitteelle_piste(20,23, 4628)), 1)"))))) "Testi 16. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 19, :alkuetaisyys 10127, :loppuosa 23, :loppuetaisyys 4628} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,21, 0)), (SELECT tierekisteriosoitteelle_piste(20,23, 4628)), 1)"))))) "Testi 17. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 23, :alkuetaisyys 2000, :loppuosa 21, :loppuetaisyys 1941} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,23, 2000)), (SELECT tierekisteriosoitteelle_piste(20,21, 1941)), 1)"))))) "Testi 18. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 23, :alkuetaisyys 2000, :loppuosa 19, :loppuetaisyys 10127} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,23, 2000)), (SELECT tierekisteriosoitteelle_piste(20,21, 0)), 1)"))))) "Testi 19. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 22, :alkuetaisyys 9485, :loppuosa 21, :loppuetaisyys 1941} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,23, 0)), (SELECT tierekisteriosoitteelle_piste(20,21, 1941)), 1)"))))) "Testi 20. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 2000, :loppuosa 3, :loppuetaisyys 3000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3,2000)), (SELECT tierekisteriosoitteelle_piste(20,3, 3000)), 1)"))))) "Testi 21. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 3000, :loppuosa 3, :loppuetaisyys 2000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3, 3000)), (SELECT tierekisteriosoitteelle_piste(20,3, 2000)), 1)"))))) "Testi 22. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 1, :alkuetaisyys 3833, :loppuosa 3, :loppuetaisyys 3000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3, 0)), (SELECT tierekisteriosoitteelle_piste(20,3, 3000)), 1)"))))) "Testi 23. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 6353, :loppuosa 3, :loppuetaisyys 3000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3, 6358)), (SELECT tierekisteriosoitteelle_piste(20,3, 3000)), 1)"))))) "Testi 24. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 1, :alkuetaisyys 3833, :loppuosa 3, :loppuetaisyys 6353} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3, 0)), (SELECT tierekisteriosoitteelle_piste(20,3, 6358)), 1)"))))) "Testi 25. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 6353, :loppuosa 1, :loppuetaisyys 3833} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3, 6358)), (SELECT tierekisteriosoitteelle_piste(20,3, 0)), 1)"))))) "Testi 26. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 2000, :loppuosa 3, :loppuetaisyys 6353} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3,2000)), (SELECT tierekisteriosoitteelle_piste(20,4, 0)), 1)"))))) "Testi 27. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 2000, :loppuosa 4, :loppuetaisyys 5752} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3,2000)), (SELECT tierekisteriosoitteelle_piste(20,4, 5756)), 1)"))))) "Testi 28. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 2000, :loppuosa 4, :loppuetaisyys 2000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3,2000)), (SELECT tierekisteriosoitteelle_piste(20,4, 2000)), 1)"))))) "Testi 29. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 1, :alkuetaisyys 3833, :loppuosa 4, :loppuetaisyys 5752} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3, 0)), (SELECT tierekisteriosoitteelle_piste(20,4, 5756)), 1)"))))) "Testi 30. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 6353, :loppuosa 3, :loppuetaisyys 6353} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3, 6358)), (SELECT tierekisteriosoitteelle_piste(20,4, 0)), 1)"))))) "Testi 31. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 4, :alkuetaisyys 2000, :loppuosa 3, :loppuetaisyys 6353} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,4, 2000)), (SELECT tierekisteriosoitteelle_piste(20,3, 6358)), 1)"))))) "Testi 32. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 4, :alkuetaisyys 2000, :loppuosa 1, :loppuetaisyys 3833} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,4, 2000)), (SELECT tierekisteriosoitteelle_piste(20,3, 0)), 1)"))))) "Testi 33. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 4, :alkuetaisyys 2000, :loppuosa 3, :loppuetaisyys 2000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,4,2000)), (SELECT tierekisteriosoitteelle_piste(20,3, 2000)), 1)"))))) "Testi 34. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 4, :alkuetaisyys 5752, :loppuosa 1, :loppuetaisyys 3833} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,4, 5756)), (SELECT tierekisteriosoitteelle_piste(20,3, 0)), 1)"))))) "Testi 35. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 6353, :loppuosa 3, :loppuetaisyys 6353} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,4, 0)), (SELECT tierekisteriosoitteelle_piste(20,3, 6358)), 1)"))))) "Testi 36. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 2000, :loppuosa 4, :loppuetaisyys 5752} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3, 2000)), (SELECT tierekisteriosoitteelle_piste(20,5, 0)), 1)"))))) "Testi 37. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 2000, :loppuosa 5, :loppuetaisyys 4231} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3, 2000)), (SELECT tierekisteriosoitteelle_piste(20,5, 4231)), 1)"))))) "Testi 38. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 3, :alkuetaisyys 2000, :loppuosa 5, :loppuetaisyys 2000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3, 2000)), (SELECT tierekisteriosoitteelle_piste(20,5, 2000)), 1)"))))) "Testi 39. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 1, :alkuetaisyys 3833, :loppuosa 5, :loppuetaisyys 4231} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,3,0)), (SELECT tierekisteriosoitteelle_piste(20,5, 4231)), 1)"))))) "Testi 40. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 5, :alkuetaisyys 2000, :loppuosa 3, :loppuetaisyys 6353} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,5,2000)), (SELECT tierekisteriosoitteelle_piste(20,3, 6353)), 1)"))))) "Testi 41. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 5, :alkuetaisyys 2000, :loppuosa 1, :loppuetaisyys 3833} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,5, 2000)), (SELECT tierekisteriosoitteelle_piste(20,3, 0)), 1)"))))) "Testi 42. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 5, :alkuetaisyys 4231, :loppuosa 1, :loppuetaisyys 3833} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,5,4231)), (SELECT tierekisteriosoitteelle_piste(20,3, 0)), 1)"))))) "Testi 43. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 4, :alkuetaisyys 5752, :loppuosa 1, :loppuetaisyys 3833}(konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,5,0)), (SELECT tierekisteriosoitteelle_piste(20,3, 0)), 1)"))))) "Testi 44. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 5, :alkuetaisyys 2000, :loppuosa 10, :loppuetaisyys 2000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,5,2000)), (SELECT tierekisteriosoitteelle_piste(20,10, 2000)), 1)"))))) "Testi 45. Yritä tierekisteriosoite pisteille.")
         (is (= {:numero 20, :alkuosa 10, :alkuetaisyys 2000, :loppuosa 5, :loppuetaisyys 2000} (konversio/lue-tr-osoite (first (first (q "SELECT yrita_tierekisteriosoite_pisteille2((SELECT tierekisteriosoitteelle_piste(20,10,2000)), (SELECT tierekisteriosoitteelle_piste(20,5, 2000)), 1)"))))) "Testi 46. Yritä tierekisteriosoite pisteille."))

