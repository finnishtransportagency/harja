(ns harja.palvelin.palvelut.yksikkohintaiset-tyot-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            
            [harja.kyselyt.urakat :as urk-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yksikkohintaiset-tyot :refer :all]
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
                      :yksikkohintaiset-tyot (component/using
                                  (->Yksikkohintaiset-tyot)
                                  [:http-palvelin :db])))))
  
  (testit)
  (alter-var-root #'jarjestelma component/stop))



  
(use-fixtures :once jarjestelma-fixture)

;; käyttää testidata.sql:stä tietoa
(deftest kaikki-yksikkohintaiset-tyot-haettu-oikein []
  (let [oulun-alueurakan-id (:id (first (urk-q/hae-urakoita (:db jarjestelma) (str "%Oulun alueurakka 2005-2010%"))))
        yksikkohintaiset-tyot (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :yksikkohintaiset-tyot +kayttaja-tero+ oulun-alueurakan-id)
        oulun-alueurakan-toiden-lkm (ffirst (q jarjestelma (str "SELECT count(*) FROM yksikkohintainen_tyo where urakka = " oulun-alueurakan-id)))]
    (is (= (count yksikkohintaiset-tyot) oulun-alueurakan-toiden-lkm))))




