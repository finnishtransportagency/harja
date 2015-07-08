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
  (let [tulokset-oulu (kutsu-palvelua (:http-palvelin jarjestelma)
                   :hae +kayttaja-jvh+ "Oulu")
        tulokset-tero (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae +kayttaja-jvh+ "Tero")
        tulokset-pohj (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae +kayttaja-jvh+ "Pohj")
        urakat (filter #(= (:tyyppi %) :urakka) tulokset-oulu)
        kayttajat (filter #(= (:tyyppi %) :kayttaja) tulokset-tero)
        organisaatiot (filter #(= (:tyyppi %) :organisaatio) tulokset-pohj)]
    (is (> (count urakat) 0) "haku: urakoiden määrä")
    (is (> (count kayttajat) 0) "haku: käyttäjien määrä")
    (is (> (count organisaatiot) 0) "haku: organisaatioiden määrä")))





