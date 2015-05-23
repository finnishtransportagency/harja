(ns harja.palvelin.palvelut.haku-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.haku :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (apply tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae (component/using
                      (->Haku)
                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest haku
  (let [tulokset (kutsu-palvelua (:http-palvelin jarjestelma)
                   :hae +kayttaja-jvh+ "Pohj")
        urakat (filter #(= (:tyyppi %) :urakka) tulokset)
        kayttajat (filter #(= (:tyyppi %) :kayttaja) tulokset)
        organisaatiot (filter #(= (:tyyppi %) :organisaatio) tulokset)]
    (is (= 6 (count urakat)) "haku: urakoiden määrä")
    (is (= 1 (count kayttajat)) "haku: käyttäjien määrä")
    (is (= 3 (count organisaatiot)) "haku: organisaatioiden määrä")))





