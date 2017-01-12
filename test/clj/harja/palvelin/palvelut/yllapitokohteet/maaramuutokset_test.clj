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
    (is (= (count vastaus) 1) "Määrämuutosten määrä täsmää")
    (is (= (:yllapitokohde maaramuutos) yllapitokohde-id) "Ylläpitokohteen id täsmää")
    (is (= (:tyyppi maaramuutos) :ajoradan-paallyste) "Tyyppi täsmää")
    (is (= (:tyo maaramuutos) "Testityö") "Työn kuvaus täsmää")
    (is (= (:yksikko maaramuutos) "kg") "Työn yksikkö täsmää")
    (is (== (:tilattu-maara maaramuutos) 100) "Tilattu määrä tmsää")
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