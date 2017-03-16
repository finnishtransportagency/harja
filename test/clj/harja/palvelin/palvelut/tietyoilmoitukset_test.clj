(ns harja.palvelin.palvelut.tietyoilmoitukset-test
  (:require [clojure.test :refer :all]
            [harja.domain.ilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tietyoilmoitukset :as tietyoilmoitukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pdf-vienti (component/using
                                     (pdf-vienti/luo-pdf-vienti)
                                     [:http-palvelin])
                        :tietyoilmoitukset (component/using
                                             (tietyoilmoitukset/->Tietyoilmoitukset)
                                             [:http-palvelin :db :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-ilmoitukset-sarakkeet
  (let [parametrit {:alkuaika (pvm/luo-pvm 2016 1 1)
                    :loppuaika (pvm/luo-pvm 2017 3 1)
                    :urakka nil
                    :sijainti nil
                    :vain-kayttajan-luomat nil}]
    (is (= 1 (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-tietyoilmoitukset +kayttaja-jvh+ parametrit))))))

(deftest hae-ilmoituksia
  (let [parametrit {:alkuaika (pvm/luo-pvm 2016 1 1)
                    :loppuaika (pvm/luo-pvm 2017 3 1)
                    :urakka nil
                    :sijainti nil
                    :vain-kayttajan-luomat nil}
        tietyoilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-tietyoilmoitukset
                                          +kayttaja-jvh+
                                          parametrit)]
    (is (= 1 (count tietyoilmoitukset)) "Ilmoituksia on palautunut oikea määrä")
    (is (= 1 (count (:tyovaiheet (first tietyoilmoitukset)))) "Ilmoituksella on työvaiheita oikea määrä")))
