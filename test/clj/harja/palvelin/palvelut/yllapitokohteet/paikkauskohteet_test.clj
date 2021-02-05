(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet :as paikkauskohteet]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :paikkauskohteet (component/using
                                           (paikkauskohteet/->Paikkauskohteet)
                                           [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest paikkauskohteet-urakalle-testi
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        paikkauskohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :paikkauskohteet-urakalle
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id})
        _ (println "löydettiin paikkauskohteet" (pr-str paikkauskohteet))]
    (is (> (count paikkauskohteet) 0))))

