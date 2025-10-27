(ns harja.palvelin.palvelut.suunnittelu.tehtavat-maarat-test
  (:require [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [harja.palvelin.palvelut.suunnittelu.tehtavat-maarat-palvelu :as tm-palvelu]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.yleiset :refer :all]
            [harja.kyselyt.tehtavat-maarat-kyselyt :as tm-kyselyt]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :tehtavat-maarat (component/using
                             (tm-palvelu/->TehtavatJaMaarat)
                             [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest hae-tehtavat-ja-maarat-tietokannasta-onnistuneesti
  (testing "Hoitovuosi 2021"
    (let [db (:db jarjestelma)
          urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitovuoden-alkuvuosi 2021
          tehtavat (:tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitovuoden-alkuvuosi))
          tehtavaryhmat (filter #(not (nil? (:valiotsikko %))) tehtavat)]

      (is (= (:nimi (first tehtavat)) "1.0 TALVIHOITO") "Ensimmäinen rivi on talvihoidon tehtäväryhmä")
      (is (= (count tehtavaryhmat) 13) "Tehtäväryhmiä on 13")))

  (testing "Hoitovuosi 2022"
    (let [db (:db jarjestelma)
          urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitovuoden-alkuvuosi 2022
          tehtavat (:tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitovuoden-alkuvuosi))
          tehtavaryhmat (filter #(not (nil? (:valiotsikko %))) tehtavat)]

      (is (= (:nimi (first tehtavat)) "1.0 TALVIHOITO") "Ensimmäinen rivi on talvihoidon tehtäväryhmä")
      (is (= (count tehtavaryhmat) 13) "Tehtäväryhmiä on 13"))))

(deftest tallenna-tarjouksen-tehtavat-ja-maarat-onnistuneesti
  (let [db (:db jarjestelma)]
    (testing "Talletetaan tehtävät ja määrät hoitovuodelle 2024"
      (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
            kayttaja-id (:id +kayttaja-jvh+)
            hoitokauden-alkuvuosi 2024
            tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitokauden-alkuvuosi)
            ; Ise 2-ajorat, Ise 1-ajorat, Ise rampit
            tehtava-idt (map :tehtava_id
                          (take 3 (filter (fn [tehtava]
                                            (not (nil? (:tehtava_id tehtava)))) (:tehtavat tehtavat))))

            ;; Muokataan muutamia määriä
            tehtavat (mapv (fn [t]
                             (cond
                               (= (:tehtava_id t) (nth tehtava-idt 0)) (assoc t :tarjous_maara 10) ; Ise 2-ajorat.
                               (= (:tehtava_id t) (nth tehtava-idt 1)) (assoc t :tarjous_maara 20) ; Ise 1-ajorat.
                               (= (:tehtava_id t) (nth tehtava-idt 2)) (assoc t :tarjous_maara 30) ; Ise rampit
                               :else t))
                       (:tehtavat tehtavat))]
        (tm-kyselyt/tallenna-tarjouksen-tehtavat-ja-maarat db urakka-id kayttaja-id hoitokauden-alkuvuosi tehtavat)

        ;; Haetaan talletetut tehtävät ja määrät
        (let [talteenotetut-tehtavat (:tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitokauden-alkuvuosi))
              talteenotetut-tehtavat (filter #(contains? (into #{} tehtava-idt) (:tehtava_id %)) talteenotetut-tehtavat)
              tehtava1 (first (filter #(= (nth tehtava-idt 0) (:tehtava_id %)) talteenotetut-tehtavat))
              tehtava2 (first (filter #(= (:tehtava_id %) (nth tehtava-idt 1)) talteenotetut-tehtavat))
              tehtava3 (first (filter #(= (:tehtava_id %) (nth tehtava-idt 2)) talteenotetut-tehtavat))]

          (is (= (:tarjous_maara tehtava1) 10M) "Ise 2-ajorat (eli tehtävä1) määrä on tallennettu oikein")
          (is (= (:tarjous_maara tehtava2) 20M) "Ise 1-ajorat (eli tehtävä2) määrä on tallennettu oikein")
          (is (= (:tarjous_maara tehtava3) 30M) "Ise rampit (eli tehtävä3) määrä on tallennettu oikein"))))))
