(ns harja.palvelin.palvelut.yllapitokohteet.reikapaikkaukset-test
  ;; TODO ... 
  (:require [clojure.test :refer :all]
             [harja.testi :refer :all]
             [clojure.set :as set]
             [com.stuartsierra.component :as component]
             [clojure.data.json :as json]
             [cheshire.core :as cheshire]
             [harja.palvelin.palvelut.yllapitokohteet.reikapaikkaukset :as reikapaikkaukset]
             [harja.kyselyt.tieverkko :as tieverkko-kyselyt]
             [harja.palvelin.komponentit.tietokanta :as tietokanta]
             [harja.pvm :as pvm]
             [clojure.java.io :as io]
             [harja.kyselyt.reikapaikkaukset :as q]
             [harja.kyselyt.konversio :as konv]))
  
  (defn jarjestelma-fixture [testit]
    (alter-var-root #'jarjestelma
      (fn [_]
        (component/start
          (component/system-map
            :db (tietokanta/luo-tietokanta testitietokanta)
            :http-palvelin (testi-http-palvelin)
            :paikkauskohteet (component/using
                               (reikapaikkaukset/->Reikapaikkaukset)
                               [:http-palvelin :db])))))
  
    (testit)
    (alter-var-root #'jarjestelma component/stop))
  
  
  (use-fixtures :each (compose-fixtures
                        jarjestelma-fixture
                        urakkatieto-fixture))
  

(deftest hae-reikapaikkaukset-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        fn-hae-reikapaikkaukset (fn [params]
                                  (kutsu-palvelua (:http-palvelin jarjestelma) :hae-reikapaikkaukset +kayttaja-jvh+ params))

        vastaus (fn-hae-reikapaikkaukset {:tr nil
                                          :aikavali nil
                                          :urakka-id urakka-id})]
    (is (= (-> vastaus count) 5))
    (is (= (-> vastaus first :aosa) 1))
    (is (= (-> vastaus first :kustannus) 215000.0M))
    (is (= (-> vastaus first :tie) 20))
    (is (= (-> vastaus first :let) 1020))
    (is (= (-> vastaus first :losa) 1))
    (is (= (-> vastaus first :aet) 860))
    (is (= (-> vastaus first :tyomenetelma) 8))
    (is (= (-> vastaus first :maara) 81))
    (is (some? (-> vastaus first :sijainti)))
    (is (some? (-> vastaus first :luotu)))
    (is (some? (-> vastaus first :loppuaika)))
    (is (some? (-> vastaus first :alkuaika)))
    (is (some? (-> vastaus first :reikapaikkaus-yksikko)))
    (is (some? (-> vastaus first :tyomenetelma-nimi)))
    (is (some? (-> vastaus first :massatyyppi)))
    (is (some? (-> vastaus first :luoja-id)))))
