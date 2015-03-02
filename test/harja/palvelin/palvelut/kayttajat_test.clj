(ns harja.palvelin.palvelut.kayttajat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.kayttajat :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(def jarjestelma nil)

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start 
                     (component/system-map
                      :db (apply tietokanta/luo-tietokanta testitietokanta)
                      :http-palvelin (testi-http-palvelin)
                      :kayttajat (component/using
                                  (->Kayttajat)
                                  [:http-palvelin :db])))))
  
  (testit)
  (alter-var-root #'jarjestelma component/stop))



  
(use-fixtures :once jarjestelma-fixture)

(deftest tavallinen-kayttaja-ei-nae-mitaan []
  (let [[lkm kayttajat] (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-kayttajat
                                        +kayttaja-tero+
                                        ["" 0 10])]
    (is (= lkm 0) "ei käyttäjiä")
    (is (= kayttajat []) "tyhjä vektori")))

(deftest jarjestelmavastuuhenkilo-nakee-kaikki []
  (let [[lkm kayttajat] (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-kayttajat +kayttaja-jvh+
                                        ["" 0 10])]
    (is (= lkm +testikayttajia+))
    (is (= (count kayttajat) +testikayttajia+))))

                                       
  




