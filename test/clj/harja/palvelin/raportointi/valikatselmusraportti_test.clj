(ns harja.palvelin.raportointi.valikatselmusraportti-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer [+kayttaja-jvh+
                                 hae-urakan-id-nimella
                                 jarjestelma
                                 pudota-ja-luo-testitietokanta-templatesta
                                 testitietokanta]]
            [harja.palvelin.raportointi.raportit.valikatselmusraportti :as raportti]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest suorita-valikatselmusraportti-onnistuu
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        parametrit {:nimi :valikatselmusraportti
                    :konteksti "urakka"
                    :urakka-id urakka-id
                    :alkupvm (pvm/->pvm "01.10.2025")
                    :loppupvm (pvm/->pvm "30.09.2026")}
        raportti (raportti/suorita (:db jarjestelma) +kayttaja-jvh+ parametrit)
        metadata (second raportti)
        otsikot (->> raportti
                     (filter #(and (vector? %)
                                   (= :otsikko-heading (first %))))
                     (map second)
                     vec)
        taulukot (filter #(and (vector? %)
                               (= :taulukko (first %)))
                         raportti)
        taulukon-sarakkeet (map #(nth % 2) taulukot)
        rivit (mapcat #(nth % 3) taulukot)
        rivien-nimet (set (map #(first (:rivi %)) rivit))]
    (is (= :raportti (first raportti)))
    (is (= "Välikatselmus" (:nimi metadata)))
    (is (= :portrait (:orientaatio metadata)))
    (is (= "POP MHU Suomussalmi 2024-2029" (:urakan-nimi metadata)))
    (is (= "01.10.2025 - 30.09.2026" (:aikajakso metadata)))
    (is (= ["Hoitovuoden lopun tavoite- ja kattohinta"
            "Tavoitehintaan kuuluvat toteutuneet kustannukset"
            "Bonukset"
            "Sanktiot"
            "Hoidonjohtopalkkion muutos"]
          otsikot))
    (is (= 5 (count taulukot)))
    (is (every? #(= [{:leveys 10 :otsikko ""}
                     {:leveys 3 :otsikko "Määrä (€)" :tasaa :oikea}]
                    %)
                taulukon-sarakkeet))
    (is (contains? rivien-nimet "Hoitovuoden lopun tavoitehinta"))
    (is (contains? rivien-nimet "Hoitovuoden lopun kattohinta"))
    (is (contains? rivien-nimet "Toteutuma yhteensä"))
    (is (contains? rivien-nimet "Lupausbonus"))
    (is (contains? rivien-nimet "Arvonvähennykset"))
    (is (contains? rivien-nimet "Hoidonjohtopalkkion muutos"))))





