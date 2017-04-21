(ns harja.palvelin.palvelut.sopimukset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.sopimus :as sopimus]
            [taoensso.timbre :as log]
            [clojure.spec.gen :as gen]
            [clojure.spec :as s]
            [clojure.string :as str]
            [harja.palvelin.palvelut.sopimukset :as sopimukset]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :sopimukset (component/using
                                    (sopimukset/->Sopimukset)
                                    [:db :http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest sopimuksien-haku-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-harjassa-luodut-sopimukset +kayttaja-jvh+ {})]
    (is (>= (count vastaus) 3))))
