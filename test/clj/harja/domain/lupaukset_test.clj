(ns harja.domain.lupaukset-test
  (:require [clojure.test :refer :all]
            [harja.domain.lupaukset :as ld]
            [harja.pvm :as pvm]
            [clj-time.coerce :as tc]))

(defn- kk->pvm [vuosi kuukausi]
  (pvm/suomen-aikavyohykkeessa
    (tc/from-string (str vuosi "-" kuukausi))))

(deftest hoitokuukausi-ennen?
  (is (true? (ld/hoitokuukausi-ennen? 10 11)))
  (is (true? (ld/hoitokuukausi-ennen? 10 12)))
  (is (true? (ld/hoitokuukausi-ennen? 10 1)))
  (is (true? (ld/hoitokuukausi-ennen? 10 2)))
  (is (true? (ld/hoitokuukausi-ennen? 10 9)))
  (is (true? (ld/hoitokuukausi-ennen? 12 1))))

(deftest odottaa-kannanottoa
  (let [lupaus {:kirjaus-kkt [10 11]
                :paatos-kk 6
                :joustovara-kkta 0
                :lupaustyyppi "yksittainen"
                :vastaukset [{:lupaus-vaihtoehto-id nil
                              :vastaus true
                              :vuosi 2021
                              :kuukausi 10}
                             {:lupaus-vaihtoehto-id nil
                              :vastaus true
                              :vuosi 2021
                              :kuukausi 11}]}]
    (is (false? (ld/odottaa-kannanottoa? lupaus 11)))
    (is (false? (ld/odottaa-kannanottoa? lupaus 12)))
    (is (false? (ld/odottaa-kannanottoa? lupaus 1)))
    (is (false? (ld/odottaa-kannanottoa? lupaus 6)))
    (is (true? (ld/odottaa-kannanottoa? lupaus 7))))

  ;; paatos-kk = 0 (kaikki)
  (let [lupaus {:kirjaus-kkt nil
                :paatos-kk 0
                :joustovara-kkta 0
                :lupaustyyppi "yksittainen"
                :vastaukset [{:lupaus-vaihtoehto-id nil
                              :vastaus true
                              :vuosi 2021
                              :kuukausi 10}
                             {:lupaus-vaihtoehto-id nil
                              :vastaus true
                              :vuosi 2021
                              :kuukausi 11}]}]
    (is (false? (ld/odottaa-kannanottoa? lupaus 11)))
    (is (false? (ld/odottaa-kannanottoa? lupaus 12)))
    (is (true? (ld/odottaa-kannanottoa? lupaus 1))))

  ;; paatos-kk = 0 (kaikki)
  ;; Yksittäinen lupaus voidaan hylätä ennen kuin kaikki päättävät vastaukset on annettu
  (let [lupaus {:kirjaus-kkt nil
                :paatos-kk 0
                :joustovara-kkta 1
                :lupaustyyppi "yksittainen"
                :vastaukset [{:lupaus-vaihtoehto-id nil
                              :vastaus false
                              :paatos true
                              :vuosi 2021
                              :kuukausi 10}
                             {:lupaus-vaihtoehto-id nil
                              :vastaus false
                              :paatos true
                              :vuosi 2021
                              :kuukausi 11}]}]
    (is (false? (ld/odottaa-kannanottoa? lupaus 1)))))
