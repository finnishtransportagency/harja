(ns harja.palvelin.palvelut.urakat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.urakat :as urk-q]
            [harja.domain.urakka :as u]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clojure.spec :as s]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :tallenna-urakan-sopimustyyppi (component/using
                                                  (->Urakat)
                                                  [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest tallenna-urakan-sopimustyyppi-testi
  (let [urakanvalvoja (oulun-urakan-tilaajan-urakanvalvoja)
        uusi-sopimustyyppi
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-urakan-sopimustyyppi urakanvalvoja
                        {:urakka-id     @oulun-alueurakan-2005-2010-id
                         :sopimustyyppi :kokonaisurakka})]
    (is (= uusi-sopimustyyppi :kokonaisurakka))
    (u (str "UPDATE urakka SET sopimustyyppi = NULL WHERE id = " @oulun-alueurakan-2005-2010-id))))


(deftest hae-urakka-testi
  (let [urakanvalvoja (oulun-urakan-tilaajan-urakanvalvoja)
        haettu-urakka
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakka urakanvalvoja @oulun-alueurakan-2005-2010-id)
        sopimukset (:sopimukset haettu-urakka)
        [eka-sopimuksen-id eka-sopimuksen-sampoid] (first sopimukset)
        [toka-sopimuksen-id toka-sopimuksen-sampoid] (second sopimukset)]
    (is (= (:id haettu-urakka) @oulun-alueurakan-2005-2010-id) "haetun urakan id")
    (is (= (count sopimukset) 2) "haetun urakan sopimusten määrä")
    (is (= eka-sopimuksen-id 1) "haetun urakan sopimustesti")
    (is (= eka-sopimuksen-sampoid "8H05228/01") "haetun urakan sopimustesti")
    (is (= toka-sopimuksen-id 3) "haetun urakan sopimustesti")
    (is (= toka-sopimuksen-sampoid "THII-12-28555") "haetun urakan sopimustesti")
    (is (= (:alkupvm haettu-urakka) (java.sql.Date. 105 9 1)) "haetun urakan alkupvm")
    (is (= (:loppupvm haettu-urakka) (pvm/aikana (pvm/->pvm "30.9.2012") 23 59 59 999)) "haetun urakan loppupvm")))

(deftest urakoiden-haku-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-harjassa-luodut-urakat +kayttaja-jvh+ {})]
    (is (>= (count vastaus) 3))
    (is (s/valid? ::u/hae-harjassa-luodut-urakat-vastaus vastaus))))