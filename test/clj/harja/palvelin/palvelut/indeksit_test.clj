(ns harja.palvelin.palvelut.indeksit-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.indeksit :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start 
                     (component/system-map
                      :db (apply tietokanta/luo-tietokanta testitietokanta)
                      :http-palvelin (testi-http-palvelin)
                      :indeksit (component/using
                                  (->Indeksit)
                                  [:http-palvelin :db])))))
  
  (testit)
  (alter-var-root #'jarjestelma component/stop))



  
(use-fixtures :once jarjestelma-fixture)

;; maku 2005 vuonna 2013
;; ["MAKU 2005" 2013] {:vuosi 2013, 12 110.1, 11 110.5, 1 109.2}}

(deftest kaikki-indeksit-haettu-oikein
  (let [indeksit (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :indeksit +kayttaja-tero+)
        maku-2005-2013 (get indeksit ["MAKU 2005" 2013])]
    (is (= (count indeksit) 4))
    (is (= (:vuosi maku-2005-2013) 2013))
    (is (= (get maku-2005-2013 12) (float 110.1))))) ;; <- odota ongelmia floatien kanssa





