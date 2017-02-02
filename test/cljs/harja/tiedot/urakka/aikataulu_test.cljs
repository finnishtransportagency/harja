(ns harja.tiedot.urakka.aikataulu-test
  (:require [harja.tiedot.urakka.aikataulu :as aikataulu]
            [clojure.test :as t :refer-macros [deftest is testing]]
            [harja.pvm :as pvm]))

(deftest aikataulurivin-luokittelu
  (testing "Valmiit kohteet luokitellaan oikein"

    (is (= :valmis (aikataulu/luokittele-valmiuden-mukaan {:aikataulu-kohde-valmis (pvm/nyt)}))))

  (testing "Keskeneräiset kohteet luokitellaan oikein"
    (is (= :kesken (aikataulu/luokittele-valmiuden-mukaan {:aikataulu-kohde-alku (pvm/nyt)}))))

  (testing "Aloittamatta olevat kohteet luokitellaan oikein"
    (is (= :aloittamatta (aikataulu/luokittele-valmiuden-mukaan {:aikataulu-kohde-valmis nil
                                                                 :aikataulu-kohde-alku nil})))))
