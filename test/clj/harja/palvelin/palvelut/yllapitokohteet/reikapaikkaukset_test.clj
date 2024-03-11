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
  

(deftest testi12345678
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        fn-hae-reikapaikkaukset (fn [params]
                                  (kutsu-palvelua (:http-palvelin jarjestelma) :hae-reikapaikkaukset +kayttaja-jvh+ params))

        vastaus (fn-hae-reikapaikkaukset {:tr nil
                                         :aikavali nil
                                         :urakka-id urakka-id})
        
        _ (println "\n vastaus : " vastaus)]))
