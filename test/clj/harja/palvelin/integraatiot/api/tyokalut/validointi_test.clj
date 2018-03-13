(ns harja.palvelin.integraatiot.api.tyokalut.validointi_test
  (:require [clojure.test :refer :all]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))

(defn tasmaa-poikkeus [{:keys [type virheet]} tyyppi koodi viesti]
  (and
    (= tyyppi type)
    (some (fn [virhe] (and (= koodi (:koodi virhe)) (.contains (:viesti virhe) viesti)))
          virheet)))

(deftest onko-liikenneviraston-jarjestelma
  (let [db (luo-testitietokanta)]
    (is (thrown+?
          #(tasmaa-poikkeus
             %
             virheet/+kayttajalla-puutteelliset-oikeudet+
             virheet/+kayttajalla-puutteelliset-oikeudet+
             "Käyttäjällä ei resurssiin.")
          (validointi/tarkista-onko-liikenneviraston-jarjestelma db +kayttaja-jvh+)))
    (validointi/tarkista-onko-liikenneviraston-jarjestelma db +livi-jarjestelma-kayttaja+)))

(deftest tarkista-leikkaavatko-alikohteet-toisiaan
  (let [odotettu-virhe "Alikohteiden Kohde 1 (tunniste: 3 ) ja Kohde 2 (tunniste: 666 ) osoitteet leikkaavat toisiaan. Osoitteet: {:numero 21101, :aosa 1, :aet 1, :losa 1, :let 100, :ajr 1, :kaista 1, :tie 21101} {:numero 21101, :aosa 1, :aet 99, :losa 1, :let 120, :ajr 1, :kaista 1, :tie 21101}."
        paallekkaiset-kohteet [{:tunniste {:id 3}
                                :nimi "Kohde 1"
                                :sijainti {:numero 21101
                                           :aosa 1
                                           :aet 1
                                           :losa 1
                                           :let 100
                                           :ajr 1
                                           :kaista 1
                                           :tie 21101}}
                               {:tunniste {:id 666}
                                :nimi "Kohde 2"
                                :sijainti {:numero 21101
                                           :aosa 1
                                           :aet 99
                                           :losa 1
                                           :let 120
                                           :ajr 1
                                           :kaista 1
                                           :tie 21101}}]
        validit-kohteet [{:tunniste {:id 3}
                          :nimi "Kohde 1"
                          :sijainti {:numero 21101
                                     :aosa 1
                                     :aet 1
                                     :losa 1
                                     :let 100
                                     :ajr 1
                                     :kaista 1
                                     :tie 21101}}
                         {:tunniste {:id 666}
                          :nimi "Kohde 2"
                          :sijainti {:numero 21101
                                     :aosa 1
                                     :aet 100
                                     :losa 1
                                     :let 120
                                     :ajr 1
                                     :kaista 1
                                     :tie 21101}}]]
    (is (= odotettu-virhe (first (validointi/tarkista-leikkaako-alikohteet-toisiaan paallekkaiset-kohteet))))
    (is (empty? (validointi/tarkista-leikkaako-alikohteet-toisiaan validit-kohteet)))))

