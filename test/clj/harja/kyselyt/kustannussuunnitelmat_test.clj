(ns harja.kyselyt.kustannussuunnitelmat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.kyselyt.kustannussuunnitelmat :as kustannussuunnitelmat-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]))

(use-fixtures :once tietokantakomponentti-fixture)

(deftest hae-kustannussuunnitelman-yksikkohintaiset-summat-kanavaurakalle
  (let [db (:db jarjestelma)
        maksueranumero (ffirst (q "select numero from maksuera where nimi = 'Väylänhoito : Lisätyöt' and tyyppi = 'lisatyo';"))
        odotettu [{:vuosi 2017, :summa nil}
                  {:vuosi 2016, :summa nil}]]
    (is (= odotettu
          (map
            ;; Huom: PostgreSQL 14 myötä EXTRACT SQL-funktio palauttaa return typenä "numeric", eikä "float"
            ;;       Vanhemmissa versioissa EXTRACT-funktion return-type on "float8".
            ;; Pakotetaan vuosi tässä testissä intiksi.
            #(update % :vuosi int)
            (vec (kustannussuunnitelmat-q/hae-kustannussuunnitelman-yksikkohintaiset-summat db maksueranumero)))))))

