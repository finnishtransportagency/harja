(ns harja.palvelin.palvelut.urakat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat  :refer :all]
            [harja.kyselyt.urakat :as urk-q]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
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
  []
  (let [urakanvalvoja (oulun-urakan-tilaajan-urakanvalvoja)
        uusi-sopimustyyppi
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-urakan-sopimustyyppi urakanvalvoja
                        {:urakka-id     @oulun-alueurakan-id
                         :sopimustyyppi "kokonaisurakka"})]
    (is (= uusi-sopimustyyppi "kokonaisurakka"))
    (u jarjestelma
       (str "UPDATE urakka SET sopimustyyppi = NULL WHERE id = " @oulun-alueurakan-id))))