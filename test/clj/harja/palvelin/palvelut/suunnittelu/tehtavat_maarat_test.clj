(ns harja.palvelin.palvelut.suunnittelu.tehtavat-maarat-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as tehtava-kyselyt]
            [harja.palvelin.palvelut.suunnittelu.apurit :as apurit]
            [harja.palvelin.palvelut.suunnittelu.tehtavat-maarat-palvelu :as tm-palvelu]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.yleiset :refer :all]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.tehtavat-maarat-kyselyt :as tm-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.toimenpideinstanssit :as tpi-kyselyt]))

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
            ;; Muokataan muutamia määriä - Tehtävä id:t kovakoodattu. Voivat säkällä muuttua.
            tehtava-set #{2988 2989 2991} ; Ise 2-ajorat, Ise 1-ajorat, Ise rampit
            tehtavat (mapv (fn [t]
                             (cond
                               (= (:tehtava_id t) 2988) (assoc t :tarjous_maara 10) ; Ise 2-ajorat.
                               (= (:tehtava_id t) 2989) (assoc t :tarjous_maara 20) ; Ise 1-ajorat.
                               (= (:tehtava_id t) 2991) (assoc t :tarjous_maara 30) ; Ise rampit
                               :else t))
                           (:tehtavat tehtavat))]
        (tm-kyselyt/tallenna-tarjouksen-tehtavat-ja-maarat db urakka-id kayttaja-id hoitokauden-alkuvuosi tehtavat)

        ;; Haetaan talletetut tehtävät ja määrät
        (let [talteenotetut-tehtavat (:tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitokauden-alkuvuosi))
              talteenotetut-tehtavat (filter #(contains? tehtava-set (:tehtava_id %)) talteenotetut-tehtavat)
              ise-2-ajorat (first (filter #(= 2988 (:tehtava_id %)) talteenotetut-tehtavat))
              ise-1-ajorat (first (filter #(= (:tehtava_id %) 2989) talteenotetut-tehtavat))
              ise-rampit (first (filter #(= (:tehtava_id %) 2991) talteenotetut-tehtavat))]

          (is (= (:tarjous_maara ise-2-ajorat) 10M) "Ise 2-ajorat määrä on tallennettu oikein")
          (is (= (:tarjous_maara ise-1-ajorat) 20M) "Ise 1-ajorat määrä on tallennettu oikein")
          (is (= (:tarjous_maara ise-rampit) 30M) "Ise rampit määrä on tallennettu oikein"))))))
