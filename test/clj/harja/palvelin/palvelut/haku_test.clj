(ns harja.palvelin.palvelut.haku-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.haku :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
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
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


(use-fixtures :once jarjestelma-fixture)

(deftest haku
  (let [tulokset-oulu (kutsu-palvelua (:http-palvelin jarjestelma)
                   :hae +kayttaja-jvh+ "Oulu")
        tulokset-tero (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae +kayttaja-jvh+ "Tero")
        tulokset-tori (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae +kayttaja-jvh+ "Tori")
        tulokset-terotori (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae +kayttaja-jvh+ "Tero Tori")
        tulokset-pohj (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae +kayttaja-jvh+ "Pohj")
        urakat (filter #(= (:tyyppi %) :urakka) tulokset-oulu)
        oulun-kaynnissaoleva-urakka (first (filter #(= "Oulun alueurakka 2014-2019" (:nimi %)) urakat))
        kayttajat (filter #(= (:tyyppi %) :kayttaja) tulokset-tero)
        kayttajat-2 (filter #(= (:tyyppi %) :kayttaja) tulokset-tori)
        kayttajat-3 (filter #(= (:tyyppi %) :kayttaja) tulokset-terotori)
        kayttaja-tero-toripolliisi (first (filter #(= "Toripolliisi" (:sukunimi %)) kayttajat))
        organisaatiot (filter #(= (:tyyppi %) :organisaatio) tulokset-pohj)
        pop-ely (first (filter #(= "Pohjois-Pohjanmaa" (:nimi %)) organisaatiot))]

    (is (> (count urakat) 0) "haku: urakoiden määrä")
    (is (= "Oulun alueurakka 2014-2019" (:nimi oulun-kaynnissaoleva-urakka)) "haku: urakan nimi")
    (is (= "Oulun alueurakka 2014-2019, 1242141-OULU2" (:hakusanat oulun-kaynnissaoleva-urakka)) "haku: urakan hakusanat")
    (is (= :urakka (:tyyppi oulun-kaynnissaoleva-urakka)) "haku: urakan tyyppi")
    (is (number? (:hallintayksikko oulun-kaynnissaoleva-urakka)) "haku: urakan hallintayksikkö")
    (is (number? (:id oulun-kaynnissaoleva-urakka)) "haku: urakan id")

    (is (> (count kayttajat) 0) "haku: käyttäjien määrä")
    (is (some #(= (:id kayttaja-tero-toripolliisi) (:id %)) kayttajat-2) "Torikin palauttaa Tero Toripollisiin")
    (is (some #(= (:id kayttaja-tero-toripolliisi) (:id %)) kayttajat-3) "Tero Torikin palauttaa Tero Toripollisiin")

    (is (= :kayttaja (:tyyppi kayttaja-tero-toripolliisi)) "haku: käyttäjän tyyppi")
    (is (= "Tero" (:etunimi kayttaja-tero-toripolliisi)) "haku: käyttäjän etunimi")
    (is (= "Toripolliisi" (:sukunimi kayttaja-tero-toripolliisi)) "haku: käyttäjän sukunimi")
    (is (= "tero" (:kayttajanimi kayttaja-tero-toripolliisi)) "haku: käyttäjän käyttäjänimi")
    (is (= "Pohjois-Pohjanmaa" (:org_nimi kayttaja-tero-toripolliisi)) "haku: käyttäjän org_nimi")
    (is (= "Tero Toripolliisi, Pohjois-Pohjanmaa" (:hakusanat kayttaja-tero-toripolliisi)) "haku: käyttäjän hakusanat")
    (is (number? (:id kayttaja-tero-toripolliisi)) "haku: käyttäjän id")


    (is (> (count organisaatiot) 0) "haku: organisaatioiden määrä")
    (is (= :organisaatio (:tyyppi pop-ely)) "haku: org tyyppi")
    (is (= "hallintayksikko" (:organisaatiotyyppi pop-ely)) "haku: org organisaatiotyyppi")
    (is (= "POP Pohjois-Pohjanmaa, hallintayksikko" (:hakusanat pop-ely)) "haku: org organisaatiotyyppi")
    (is (number? (:id pop-ely)) "haku: urakan hallintayksikkö")))





