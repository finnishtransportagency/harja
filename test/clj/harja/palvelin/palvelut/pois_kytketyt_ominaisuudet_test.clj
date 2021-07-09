(ns harja.palvelin.palvelut.pois-kytketyt-ominaisuudet-test
  (:require [harja.palvelin.komponentit.todennus :as todennus]
            [harja.testi :refer :all]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa? pois-kytketyt-ominaisuudet]]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [com.stuartsierra.component :as component]))

(t/deftest ominaisuudet-pois-paalta-ennen-asetusten-lukua
  (let [alkup-pko @pois-kytketyt-ominaisuudet]
    (reset! pois-kytketyt-ominaisuudet nil)
    (is (false? (ominaisuus-kaytossa? :seppo)))
    (reset! pois-kytketyt-ominaisuudet #{:keke})
    (is (true? (ominaisuus-kaytossa? :seppo)))
    (is (false? (ominaisuus-kaytossa? :keke)))
    (reset! pois-kytketyt-ominaisuudet alkup-pko)))
