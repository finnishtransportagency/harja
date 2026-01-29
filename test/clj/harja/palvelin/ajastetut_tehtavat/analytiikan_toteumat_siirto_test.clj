(ns harja.palvelin.ajastetut-tehtavat.analytiikan-toteumat-siirto-test
  (:require [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.ajastetut-tehtavat.analytiikan-toteumat :as analytiikan-toteumat]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.fim-test :as fim-test]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.pvm :refer [luo-pvm]]
            [clj-time.core :as t]
            [clj-time.coerce :as t-coerce]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konversio])
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

(use-fixtures :once jarjestelma-fixture)

(deftest siirra-analytiikan-toteumat-toimii-vuodelle-2015
  (let [testitietokanta (:db jarjestelma)
        alkupaiva (pvm/luo-pvm 2015 0 1)
        loppupaiva (pvm/luo-pvm 2015 11 31)
        ;; HAetaan vain vuoden 2015 toteumat ja siivotaan varalta kaikki 2015 vuoden mahdollisesti siirretyt toteumat pois
        _ (u "DELETE FROM analytiikka_toteumat WHERE toteuma_alkanut > '2014-12-31' AND toteuma_alkanut < '2016-01-01'")
        _ (u "DELETE FROM ajastetut_tehtavat WHERE suoritusyritys_aika > '2014-12-31' AND suoritusyritys_aika < '2016-01-01'")
        hae-maarat (fn []
                     [(first (first (q "SELECT count(*) FROM toteuma WHERE alkanut > '2014-12-31' AND alkanut < '2016-01-01'")))
                      (first (first (q "SELECT count(*) FROM analytiikka_toteumat WHERE toteuma_alkanut > '2014-12-31' AND toteuma_alkanut < '2016-01-01'")))])
        maarat-alussa (hae-maarat)
        _ (mapv (fn [paiva]
                  (let [alkupaiva (pvm/paivan-alussa paiva)
                        loppupaiva (pvm/paivan-lopussa paiva)]
                    (analytiikan-toteumat/siirra-toteumat testitietokanta (t-coerce/to-sql-time alkupaiva) (t-coerce/to-sql-time loppupaiva))))
            (pvm/paivat-valissa alkupaiva loppupaiva))
        maarat-lopussa (hae-maarat)
        siirretty (first (q "SELECT luotu, toteumatehtavat, toteumamateriaalit FROM analytiikka_toteumat WHERE toteuma_tunniste_id = '1112'"))
        siirron-luotu-aikaleima (first siirretty)
        toteumatehtavat (first (konversio/jsonb->clojuremap (second siirretty)))
        toteumamateriaalit (first (konversio/jsonb->clojuremap (last siirretty)))

        ;; Haetaan kantaan logitetut ajastetut tehtävät
        ajastetut-tehtavat-logit (q-map (format "SELECT * FROM ajastetut_tehtavat
                                      WHERE tyyppi = 'siirra_toteumat_analytiikalle'
                                      AND suoritusyritys_aika between '%s' AND '%s'"
                                      alkupaiva loppupaiva))]

    ;; Varmista toteumien siirto
    (is (not (nil? siirron-luotu-aikaleima)) "Toteuman siirron aikaleima on tallentunut.")
    (is (= false (:f7 toteumatehtavat)) "Toteumatehtävän poistettu-tieto löytyy ja on oikein.")
    (is (not (nil? (:f8 toteumatehtavat))) "Toteuman tehtäväriviltä löytyy luontiakaileima.")
    (is (= 18 (:f5 toteumamateriaalit)) "Toteumamateriaalin rivi-id löytyy ja on oikein.")
    (is (not (nil? (:f7 toteumamateriaalit))) "Toteuman materiaaliriviltä löytyy luontiakaileima.")
    (is (> (first maarat-alussa) (second maarat-alussa)))
    (is (= (first maarat-alussa) (first maarat-lopussa)))
    (is (= (first maarat-alussa) (second maarat-lopussa)))

    ;; Varmista ajastetut_tehtavat logitus
    (is (= 363 (count ajastetut-tehtavat-logit)) "Yksi ajastettu tehtävä on logitettu.")
    (is (= true (:onnistunut (first ajastetut-tehtavat-logit))))
    (is (= true (:onnistunut (last ajastetut-tehtavat-logit))))))
