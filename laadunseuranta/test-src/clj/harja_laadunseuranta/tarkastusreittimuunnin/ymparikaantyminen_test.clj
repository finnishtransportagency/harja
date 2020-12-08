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
            [harja-laadunseuranta.core :as harja-laadunseuranta]
            [clj-time.core :as t]
            [clojure.core.async :refer [go <! <!! >! thread >!!] :as async]
            [harja-laadunseuranta.tarkastusreittimuunnin.testityokalut :as tyokalut]))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                         :mobiili-laadunseuranta
                        (component/using
                          (harja-laadunseuranta/->Laadunseuranta)
                          [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

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

(deftest ymparikaantymisanalyysi-havaitsee-ymparikaantymisen-reilun-100m-matkalla
  (let [tarkastusajo-id 901
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
                            (take 3 (drop 4 merkinnat-ymparikaantymisilla))))
             1)))))

(deftest ymparikaantymisanalyysi-havaitsee-ymparikaantymisen-oikeassa-ajossa
  (let [tarkastusajo-id 2
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")

    (let [merkinnat-ymparikaantymisilla (ymparikaantyminen/lisaa-tieto-ymparikaantymisesta merkinnat)]
      (is (= (count merkinnat-ymparikaantymisilla) (count merkinnat)))
      ;; Havaittiin yksi ympärikääntyminen
      (is (= (count (filter :ymparikaantyminen? merkinnat-ymparikaantymisilla)) 1))
      ;; Ympärikääntyminen on merkitty suunnilleen oikeaan pisteeseen
      (is (= (count (filter :ymparikaantyminen?
                            (take 23 (drop 20 merkinnat-ymparikaantymisilla))))
             1)))))

(deftest ymparikaantymisanalyysi-ei-havaitse-ymparikaantymista-tosi-lyhyella-valilla
  (let [tarkastusajo-id 902
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (every? :tr-osoite merkinnat) "Merkinnät projisoitiin tielle oikein")

    (let [merkinnat-ymparikaantymisilla (ymparikaantyminen/lisaa-tieto-ymparikaantymisesta merkinnat)]
      (is (= (count merkinnat-ymparikaantymisilla) (count merkinnat)))
      ;; Näin lyhyt väli voi syntyä GPS-kohinasta, joten sitä ei hyväksytä ympärikääntymiseksi
      (is (empty? (filter :ymparikaantyminen? merkinnat-ymparikaantymisilla))))))

(defn- ymparikaantymisen-analyysi-ei-havaitse-ymparikaantymisia
  ([tarkastusajo-id] (ymparikaantymisen-analyysi-ei-havaitse-ymparikaantymisia tarkastusajo-id nil))
  ([tarkastusajo-id tarkkuus]
   (let [merkinnat (as-> (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                                {:tarkastusajo tarkastusajo-id
                                                                 :laheiset_tiet_threshold 100})
                         merkinnat
                         (if tarkkuus (tyokalut/aseta-merkintojen-tarkkuus merkinnat tarkkuus) merkinnat))]

     (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")

     (let [merkinnat-ymparikaantymisilla (ymparikaantyminen/lisaa-tieto-ymparikaantymisesta merkinnat)
           ei-ymparikaantymisia? (empty? (filter :ymparikaantyminen? merkinnat-ymparikaantymisilla))]
       (is (= (count merkinnat-ymparikaantymisilla) (count merkinnat)))
       (is ei-ymparikaantymisia?)
       ei-ymparikaantymisia?))))

(deftest ymparikaantymisanalyysi-ei-havaitse-ymparikaantymista-ajoissa-joissa-sita-ei-ole
  (let [tarkastusajo-idt [1 3 754 664 665 666 667 668]
        vastaus-kanava (async/chan)]

    ;; Analysoidaan jokainen ajo asynkronisesti, muuten testi on hidas
    (doseq [tarkastusajo-id tarkastusajo-idt]
      (go
        (let [ei-ymparikaantymisia? (ymparikaantymisen-analyysi-ei-havaitse-ymparikaantymisia tarkastusajo-id)]
          (>! vastaus-kanava ei-ymparikaantymisia?))))


    (loop [vastaukset []]
      (if (< (count vastaukset) (count tarkastusajo-idt))
        (let [vastaus (<!! vastaus-kanava)]
          (recur (conj vastaukset vastaus)))
        (is (every? true? vastaukset) "Ympärikääntymisiä ei havaittu")))))

(deftest ymparikaantymisanalyysi-ei-havaitse-ymparikaantymista-eptarkoissa-ajoissa-joissa-sita-ei-ole
  (let [tarkastusajo-idt [1 3 754 664 665 666 667 668]
        vastaus-kanava (async/chan)]

    ;; Analysoidaan jokainen ajo asynkronisesti, muuten testi on hidas
    (doseq [tarkastusajo-id tarkastusajo-idt]
      (go
        (let [ei-ymparikaantymisia? (ymparikaantymisen-analyysi-ei-havaitse-ymparikaantymisia tarkastusajo-id
                                                                                              60)]
          (>! vastaus-kanava ei-ymparikaantymisia?))))


    (loop [vastaukset []]
      (if (< (count vastaukset) (count tarkastusajo-idt))
        (let [vastaus (<!! vastaus-kanava)]
          (recur (conj vastaukset vastaus)))
        (is (every? true? vastaukset) "Ympärikääntymisiä ei havaittu")))))