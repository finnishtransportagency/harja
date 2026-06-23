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
          :db (tietokanta/luo-tietokanta testitietokanta)
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
        tulokset-pohj (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae +kayttaja-jvh+ "Pohj")
        urakat (filter #(= (:tyyppi %) :urakka) tulokset-oulu)
        oulun-urakka-2014-2019 (first (filter #(= "Oulun alueurakka 2014-2019" (:nimi %)) urakat))
        organisaatiot (filter #(= (:tyyppi %) :organisaatio) tulokset-pohj)
        pop-ely (first (filter #(= "Pohjois-Pohjanmaa" (:nimi %)) organisaatiot))]

    (is (> (count urakat) 0) "haku: urakoiden määrä")
    (is (= "Oulun alueurakka 2014-2019" (:nimi oulun-urakka-2014-2019)) "haku: urakan nimi")
    (is (= (str (:id oulun-urakka-2014-2019) " Oulun alueurakka 2014-2019, 1242141-OULU2") (:hakusanat oulun-urakka-2014-2019)) "haku: urakan hakusanat")
    (is (= :urakka (:tyyppi oulun-urakka-2014-2019)) "haku: urakan tyyppi")
    (is (number? (:hallintayksikko oulun-urakka-2014-2019)) "haku: urakan hallintayksikkö")
    (is (number? (:id oulun-urakka-2014-2019)) "haku: urakan id")

    (is (> (count organisaatiot) 0) "haku: organisaatioiden määrä")
    (is (= :organisaatio (:tyyppi pop-ely)) "haku: org tyyppi")
    (is (= "hallintayksikko" (:organisaatiotyyppi pop-ely)) "haku: org organisaatiotyyppi")
    (is (= "POP Pohjois-Pohjanmaa, hallintayksikko" (:hakusanat pop-ely)) "haku: org organisaatiotyyppi")
    (is (number? (:id pop-ely)) "haku: urakan hallintayksikkö")))
