(ns harja.palvelin.palvelut.yllapitokohteet.maaramuutokset-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.palvelin.palvelut.yllapitokohteet.maaramuutokset :as maaramuutokset]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :maaramuutokset (component/using
                                          (maaramuutokset/->Maaramuutokset)
                                          [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest maaramuutokset-haettu-oikein
  (let [yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-maaramuutokset +kayttaja-jvh+
                                {:urakka-id @muhoksen-paallystysurakan-id
                                 :yllapitokohde-id yllapitokohde-id})
        maaramuutos (first vastaus)]
    (is (= (count vastaus) 4) "Määrämuutosten määrä täsmää")
    (is (= (:yllapitokohde-id maaramuutos) yllapitokohde-id) "Ylläpitokohteen id täsmää")
    (is (= (:tyyppi maaramuutos) :ajoradan-paallyste) "Tyyppi täsmää")
    (is (= (:tyo maaramuutos) "Testityö") "Työn kuvaus täsmää")
    (is (= (:yksikko maaramuutos) "kg") "Työn yksikkö täsmää")
    (is (== (:tilattu-maara maaramuutos) 100) "Tilattu määrä täsmää")
    (is (== (:toteutunut-maara maaramuutos) 120) "Toteutunut määrä täsmää")
    (is (== (:yksikkohinta maaramuutos) 2) "Yksikköhinta täsmää")))

(deftest maaramuutokset-haku-epaonnistuu-ilman-oikeuksia
  (try (let [yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
             _ (kutsu-palvelua (:http-palvelin jarjestelma)
                               :hae-maaramuutokset +kayttaja-ulle+
                               {:urakka-id @muhoksen-paallystysurakan-id
                                :yllapitokohde-id yllapitokohde-id})]
         (is false "Kutsu meni virheellisesti läpi"))
       (catch Exception e
         (is true "Kutsu heitti virheen odotetusti"))))

(deftest maaramuutosten-tallennus-toimii
  (let [yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        maaramuutokset-ennen-testia (kutsu-palvelua
                                      (:http-palvelin jarjestelma)
                                      :hae-maaramuutokset +kayttaja-jvh+
                                      {:urakka-id @muhoksen-paallystysurakan-id
                                       :yllapitokohde-id yllapitokohde-id})
        testipayload [{:yllapitokohde yllapitokohde-id
                       :tyyppi :ajoradan-paallyste
                       :tyo "Testissä luotu määrämuutos"
                       :yksikko "kg"
                       :tilattu-maara 100
                       :toteutunut-maara 120
                       :yksikkohinta 3}]
        vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :tallenna-maaramuutokset +kayttaja-jvh+
                  {:urakka-id @muhoksen-paallystysurakan-id
                   :yllapitokohde-id yllapitokohde-id
                   :maaramuutokset testipayload
                   :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
                   :vuosi 2017})
        maaramuutokset-tallennuksen-jalkeen (:maaramuutokset vastaus)
        yllapitokohteet-tallennuksen-jalkeen (:yllapitokohteet vastaus)
        leppajarven-ramppi (first (filter #(= (:nimi %) "Leppäjärven ramppi")
                                          yllapitokohteet-tallennuksen-jalkeen))]
    (is (= (count maaramuutokset-tallennuksen-jalkeen)
           (+ (count maaramuutokset-ennen-testia) 1)) "Tallennuksen jälkeen määrä kasvoi yhdellä")
    (is (== (:maaramuutokset leppajarven-ramppi) 265)
        "Leppäjärven rampin määrämuutos laskettu oikein eli määrämuutoksien
        (toteutunut - tilattu) * hinta
        summattuna yhteen")

    ;; Siivoa sotkut
    (u "DELETE FROM yllapitokohteen_maaramuutos WHERE tyo = 'Testissä luotu määrämuutos';")))

(deftest maaramuutosten-tallennus-ei-anna-muokata-jarjestelman-luomaa
  (let [yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        testipayload [{:id (ffirst (q "SELECT id FROM yllapitokohteen_maaramuutos WHERE tyo = 'Järjestelmän luoma työ' LIMIT 1;"))
                       :yllapitokohde yllapitokohde-id
                       :tyyppi :ajoradan-paallyste
                       :tyo "Testissä luotu määrämuutos"
                       :yksikko "kg"
                       :tilattu-maara 100
                       :toteutunut-maara 120
                       :yksikkohinta 3}]]
    (is (thrown? SecurityException (kutsu-palvelua
                                     (:http-palvelin jarjestelma)
                                     :tallenna-maaramuutokset +kayttaja-jvh+
                                     {:urakka-id @muhoksen-paallystysurakan-id
                                      :yllapitokohde-id yllapitokohde-id
                                      :maaramuutokset testipayload
                                      :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
                                      :vuosi 2017})))))

(deftest maaramuutosten-paivitystoimii
  (let [yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        uusi-maaramuutos {:yllapitokohde yllapitokohde-id
                          :tyyppi :ajoradan-paallyste
                          :tyo "Testissä luotu määrämuutos"
                          :yksikko "kg"
                          :tilattu-maara 100
                          :toteutunut-maara 120
                          :yksikkohinta 3}
        muokattu-maaramuutos {:yllapitokohde yllapitokohde-id
                              :tyyppi :ajoradan-paallyste
                              :tyo "Testissä luotu määrämuutos"
                              :yksikko "kg"
                              :tilattu-maara 123
                              :toteutunut-maara 666
                              :yksikkohinta 3}
        poistettu-maaramuutos {:yllapitokohde yllapitokohde-id
                               :tyyppi :ajoradan-paallyste
                               :tyo "Testissä luotu määrämuutos"
                               :yksikko "kg"
                               :tilattu-maara 123
                               :toteutunut-maara 666
                               :yksikkohinta 3
                               :poistettu true}
        tallenna-maaramuutokset (fn [maaramuutokset]
                                  (kutsu-palvelua
                                    (:http-palvelin jarjestelma)
                                    :tallenna-maaramuutokset +kayttaja-jvh+
                                    {:urakka-id @muhoksen-paallystysurakan-id
                                     :yllapitokohde-id yllapitokohde-id
                                     :maaramuutokset maaramuutokset
                                     :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
                                     :vuosi 2017}))
        hae-maaramuutokset #(kutsu-palvelua
                              (:http-palvelin jarjestelma)
                              :hae-maaramuutokset +kayttaja-jvh+
                              {:urakka-id @muhoksen-paallystysurakan-id
                               :yllapitokohde-id yllapitokohde-id})
        hae-tarkasteltava-maaramuutos (fn [maaramuutokset]
                                        (first (filter #(= "Testissä luotu määrämuutos" (:tyo %)) maaramuutokset)))
        maaramuutokset-ennen-testia (count (hae-maaramuutokset))]

    (tallenna-maaramuutokset [uusi-maaramuutos])

    (let [maaramuutokset (hae-maaramuutokset)
          maaramuutos-id (:id (hae-tarkasteltava-maaramuutos maaramuutokset))]
      (is (= (+ 1 maaramuutokset-ennen-testia) (count maaramuutokset)) "Kirjauksen jälkeen löytyi vain 1 uusi määrämuutos")
      (is maaramuutos-id "Id on saatu haettua")

      (tallenna-maaramuutokset [(assoc muokattu-maaramuutos :id maaramuutos-id)])
      (let [maaramuutokset (hae-maaramuutokset)
            maaramuutos (hae-tarkasteltava-maaramuutos maaramuutokset)]
        (is (= (+ 1 maaramuutokset-ennen-testia) (count maaramuutokset)) "Muokkauksen jälkeen löytyi sama määrä määrämuutoksia")
        (is (= 123M (:tilattu-maara maaramuutos)) "Tilattu määrä on päivitetty oikein")
        (is (= 666M (:toteutunut-maara maaramuutos)) "Toteutunut määrä on päivitetty oikein"))

      (tallenna-maaramuutokset [(assoc poistettu-maaramuutos :id maaramuutos-id)])
      (is (= maaramuutokset-ennen-testia (count (hae-maaramuutokset)))))

    (u "DELETE FROM yllapitokohteen_maaramuutos WHERE tyo = 'Testissä luotu määrämuutos';")))