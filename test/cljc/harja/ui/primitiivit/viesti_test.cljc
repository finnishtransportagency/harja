(ns harja.ui.primitiivit.viesti-test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.ui.primitiivit.viesti :as viesti]))

(deftest flash-viestin-luokat-palauttaa-variantin
  (testing "onnistumisvariantti"
    (is (= {:overlay "harja-viesti-overlay"
            :tausta "harja-viesti-tausta"
            :runko "harja-viesti-runko"
            :sisalto "harja-viesti"
            :variantti "harja-viesti-onnistuminen"}
           (viesti/flash-viestin-luokat :success))))
  (testing "tuntematon luokka putoaa info-varianttiin"
    (is (= "harja-viesti-info" (:variantti (viesti/flash-viestin-luokat :tuntematon)))))
  (testing "nil putoaa info-varianttiin"
    (is (= "harja-viesti-info" (:variantti (viesti/flash-viestin-luokat nil)))))
  (testing "vaaravariantti"
    (is (= "harja-viesti-vaara" (:variantti (viesti/flash-viestin-luokat :danger))))))
