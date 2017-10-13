(ns harja.palvelin.integraatiot.digitraffic.ais-data-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.integraatiot.digitraffic.ais-data :as ais]

            [harja.domain.vesivaylat.alus :as alus]))

(deftest kasiteltavien-alusten-kaivaminen
  (is (= #{1 2 3 4}
         (#'ais/kasiteltavat-alukset*
           [{::alus/mmsi 1
             ::alus/nimi "Foobar"}
            {::alus/mmsi 2}
            {::alus/mmsi 3}
            {::alus/mmsi 4}
            {:mmsi 5}]))))
