(ns harja.palvelin.raportointi.raportit.valikatselmusraportti-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [harja.palvelin.raportointi.raportit.valikatselmusraportti :as raportti]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmukset]
            [harja.kyselyt.urakat :as urakat-q]
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
        raportti (raportti/suorita (:db jarjestelma) +kayttaja-jvh+ parametrit)]

    (is (= :raportti (first raportti)))
    (is (= "Välikatselmus" (:nimi (second raportti))))))





