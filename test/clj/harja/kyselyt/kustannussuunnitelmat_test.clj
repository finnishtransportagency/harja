(ns harja.kyselyt.kustannussuunnitelmat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.kustannussuunnitelmat :as kustannussuunnitelmat-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(deftest hae-kustannussuunnitelman-yksikkohintaiset-summat-kanavaurakalle
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        maksueranumero (ffirst (q "select numero from maksuera where nimi = 'Väylänhoito : Lisätyöt' and tyyppi = 'lisatyo';"))
        odotettu [{:vuosi 2017.0, :summa nil}
                  {:vuosi 2016.0, :summa nil}]]
    (is (= odotettu (vec(kustannussuunnitelmat-q/hae-kustannussuunnitelman-yksikkohintaiset-summat db maksueranumero))))))

(deftest hae-kustannussuunnitelman-yksikkohintaiset-summat-teiden-hoidon-urakalle
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        maksueranumero (ffirst (q "select numero from maksuera where nimi =  'Oulu Talvihoito TP ME 2014-2019' and tyyppi = 'yksikkohintainen';"))
        odotettu [{:summa 300M
                   :vuosi 2014.0}
                  {:summa 900M
                   :vuosi 2015.0}]]
    (is (= odotettu (vec(kustannussuunnitelmat-q/hae-kustannussuunnitelman-yksikkohintaiset-summat db maksueranumero))))))

