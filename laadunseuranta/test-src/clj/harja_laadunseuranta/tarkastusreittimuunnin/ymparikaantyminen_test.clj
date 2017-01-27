(ns harja-laadunseuranta.tarkastusreittimuunnin.ymparikaantyminen-test
  (:require [clojure.test :refer :all]
            [harja-laadunseuranta.tarkastusreittimuunnin.ymparikaantyminen :as ymparikaantyminen]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [com.stuartsierra.component :as component]
            [harja.domain.tierekisteri :as tr-domain]
            [harja-laadunseuranta.core :as harja-laadunseuranta]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :harja-laadunseuranta
                        (component/using
                          (harja-laadunseuranta/->Laadunseuranta nil)
                          [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

;; HOX! Tässä tehdään testejä kannassa löytyville ajoille, joilla on tietty id.
;; Ajojen tekstuaalisen selityksen löydät: testidata/tarkastusajot.sql
;; Tai käytä #tr näkymää piirtämään pisteet kartalle.

(deftest ymparikaantymisanalyysi-havaitsee-selvan-ymparikaantymisen
  (let [tarkastusajo-id 899
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (every? :tr-osoite merkinnat) "Merkinnät projisoitiin tielle oikein")

    (let [merkmerkinnat-ymparikaantymisillannat (ymparikaantyminen/lisaa-tieto-ymparikaantymisesta merkinnat)]
      (is (= (count merkmerkinnat-ymparikaantymisillannat) (count merkinnat)))
      ;; Havaittiin yksi ympärikääntyminen
      (is (= (count (filter :ymparikaantyminen? merkmerkinnat-ymparikaantymisillannat)) 1))
      ;; Ympärikääntyminen on merkitty suunnilleen oikeaan pisteeseen
      (is (= (count (filter :ymparikaantyminen?
                            (take 3 (drop 8 merkmerkinnat-ymparikaantymisillannat))))
             1)))))

(deftest ymparikaantymisanalyysi-havaitsee-ymparikaantymisen-kun-ollaan-paikallaan
  (let [tarkastusajo-id 900
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (every? :tr-osoite merkinnat) "Merkinnät projisoitiin tielle oikein")

    (let [merkinnat-ymparikaantymisilla (ymparikaantyminen/lisaa-tieto-ymparikaantymisesta merkinnat)]
      (is (= (count merkinnat-ymparikaantymisilla) (count merkinnat)))
      ;; Havaittiin yksi ympärikääntyminen
      (is (= (count (filter :ymparikaantyminen? merkinnat-ymparikaantymisilla)) 1))
      ;; Ympärikääntyminen on merkitty suunnilleen oikeaan pisteeseen
      (is (= (count (filter :ymparikaantyminen?
                            (take 9 (drop 8 merkinnat-ymparikaantymisilla))))
             1)))))

(deftest ymparikaantymisanalyysi-ei-havaitse-ymparikaantymista-tarkastusajossa-1
  (let [tarkastusajo-id 1
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")

    (let [merkinnat-ymparikaantymisilla (ymparikaantyminen/lisaa-tieto-ymparikaantymisesta merkinnat)]
      (is (= (count merkinnat-ymparikaantymisilla) (count merkinnat)))
      (is (empty? (filter :ymparikaantyminen? merkinnat-ymparikaantymisilla))))))

(deftest ymparikaantymisanalyysi-ei-havaitse-ymparikaantymista-tarkastusajossa-754
  (let [tarkastusajo-id 754
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")

    (let [merkinnat-ymparikaantymisilla (ymparikaantyminen/lisaa-tieto-ymparikaantymisesta merkinnat)]
      (is (= (count merkinnat-ymparikaantymisilla) (count merkinnat)))
      (is (empty? (filter :ymparikaantyminen? merkinnat-ymparikaantymisilla))))))