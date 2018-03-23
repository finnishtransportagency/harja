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
  (let [odotettu-virhe {:koodi "virheellinen-sijainti"
                        :viesti "Alikohteiden: Kohde 1 (tunniste: 3) ja: Kohde 2 (tunniste: 666) osoitteet leikkaavat toisiaan."}
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
    (is (= odotettu-virhe (first (validointi/tarkista-leikkaavatko-alikohteet-toisiaan paallekkaiset-kohteet))))
    (is (empty? (validointi/tarkista-leikkaavatko-alikohteet-toisiaan validit-kohteet)))))

(deftest tarkista-ovatko-tierekisterosoitteet-validit
  (let [db (luo-testitietokanta)]
    (let [olematon-tieosa [{:tunniste {:id 3}
                            :nimi "Kohde 1"
                            :sijainti {:numero 70012
                                       :tie 70012
                                       :aosa 1
                                       :aet 1
                                       :losa 1
                                       :let 100
                                       :ajr 1
                                       :kaista 1}}]
          virhe (first (validointi/tarkista-ovatko-tierekisterosoitteet-validit db olematon-tieosa))]
      (is (= "virheellinen-sijainti" (:koodi virhe)) "Virhekoodi on oikea")
      (is (= "Kohteen: Kohde 1 (tunniste: 3) osoite ei ole validi. Tietä tai osaa ei löydy."
             (:viesti virhe))
          "Virheviesti on oikea"))

    (let [olematon-tieosa [{:tunniste {:id 3}
                            :nimi "Kohde 1"
                            :sijainti {:numero 70012
                                       :tie 70012
                                       :aosa 1
                                       :aet 1
                                       :losa 1
                                       :let 100
                                       :ajr 1
                                       :kaista 1}}]
          virhe (first (validointi/tarkista-ovatko-tierekisterosoitteet-validit db olematon-tieosa))]
      (is (= "virheellinen-sijainti" (:koodi virhe)) "Virhekoodi on oikea")
      (is (= "Kohteen: Kohde 1 (tunniste: 3) osoite ei ole validi. Tietä tai osaa ei löydy."
             (:viesti virhe))
          "Virheviesti on oikea"))

    (let [liian-pitka-osa [{:tunniste {:id 3}
                            :nimi "Kohde 1"
                            :sijainti {:numero 70012
                                       :tie 70012
                                       :aosa 270
                                       :aet 100
                                       :losa 270
                                       :let 300
                                       :ajr 0
                                       :kaista 0}}]
          virhe (first (validointi/tarkista-ovatko-tierekisterosoitteet-validit db liian-pitka-osa))]
      (is (= "virheellinen-sijainti" (:koodi virhe)) "Virhekoodi on oikea")
      (is (= "Kohteen: Kohde 1 (tunniste: 3) osoite ei ole validi. Etäisyydet ovat liian pitkiä."
             (:viesti virhe))
          "Virheviesti on oikea"))
    
    (let [validi-osoite [{:tunniste {:id 3}
                          :nimi "Kohde 1"
                          :sijainti {:numero 70012
                                     :tie 70012
                                     :aosa 270
                                     :aet 100
                                     :losa 270
                                     :let 200
                                     :ajr 0
                                     :kaista 0}}]
          virheet (validointi/tarkista-ovatko-tierekisterosoitteet-validit db validi-osoite)]
      (is (empty? virheet) "Virheitä ei palautunut"))))


