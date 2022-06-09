(ns harja.palvelin.ajastetut-tehtavat.analytiikan-toteumat-siirto-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.ajastetut-tehtavat.analytiikan-toteumat :as analytiikan-toteumat]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.fim-test :as fim-test]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.pvm :refer [luo-pvm]]
            [clj-time.core :as t]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.palvelut.urakat :as urakat])
  (:use org.httpkit.fake))

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

(defn paivamaara-vali
  "Palautetaan sequenssi DateTime arvoista annettujen päivien väliltä. Steppinä käytetään annettua parametria."
  [alku end step]
  (let [inf-range (periodic-seq alku step)
        loppunut? (fn [t] (t/within? (t/interval alku end)
                             t))]
    (take-while loppunut? inf-range)))

(use-fixtures :once jarjestelma-fixture)

(deftest siirra-analytiikan-toteumat-toimii-vuodelle-2015-idempotentisti
  (let [testitietokanta (:db jarjestelma)
        ;; HAetaan vain vuoden 2015 toteumat ja siivotaan varalta kaikki 2015 vuoden mahdollisesti siirretyt toteumat pois
        _ (u "DELETE FROM analytiikan_toteumat WHERE alkanut > '2014-12-31' AND alkanut < '2016-01-01'")
        hae-maarat (fn []
                     [(first (first (q "SELECT count(*) FROM toteuma WHERE alkanut > '2014-12-31' AND alkanut < '2016-01-01'")))
                      (first (first (q "SELECT count(*) FROM analytiikan_toteumat WHERE alkanut > '2014-12-31' AND alkanut < '2016-01-01'")))])
        maarat-alussa (hae-maarat)
        _ (println "maarat-alussa" maarat-alussa)
        _ (reduce (fn [maara paiva]
                    (analytiikan-toteumat/siirra-toteumat testitietokanta paiva))
            0
            (paivamaara-vali (luo-pvm 2015 1 1) (luo-pvm 2015 12 31) 1))
        maarat-lopussa (hae-maarat)
        _ (println "maarat-lopussa" maarat-lopussa)]
    (is (> (first maarat-alussa) (second maarat-alussa)))
    (is (= (first maarat-alussa) (second maarat-lopussa)))))

