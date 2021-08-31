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
    (is (false? (ld/odottaa-kannanottoa? lupaus 6))
        "Vielä kesäkuussa ei tarvitse ottaa kantaa, koska kesäkuu on päättävä kuukausi, ja kirjaus-kk:t on jo kirjattu.")
    (is (true? (ld/odottaa-kannanottoa? lupaus 7))
        "Heinäkuussa täytyy ottaa kantaa, koska kesäkuu on päättävä kuukausi."))

  (let [lupaus {:kirjaus-kkt [10 11]
                :paatos-kk 6
                :joustovara-kkta 0
                :lupaustyyppi "yksittainen"
                :vastaukset [{:lupaus-vaihtoehto-id nil
                              :vastaus true
                              :paatos false
                              :vuosi 2021
                              :kuukausi 10}
                             {:lupaus-vaihtoehto-id nil
                              :vastaus true
                              :paatos true
                              :vuosi 2022
                              :kuukausi 6}]}]
    (is (false? (ld/odottaa-kannanottoa? lupaus 11))
        "Hyväksyttyyn lupaukseen ei tarvitse enää ottaa kantaa.")
    (is (false? (ld/odottaa-kannanottoa? lupaus 7))
        "Hyväksyttyyn lupaukseen ei tarvitse enää ottaa kantaa."))

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

(deftest bonus-tai-sanktio
  (is (= {:bonus 0.0}
         (ld/bonus-tai-sanktio {:lupaus 100 :toteuma 100 :tavoitehinta 1000})))
  (is (= {:bonus 13.0}
         (ld/bonus-tai-sanktio {:lupaus 90 :toteuma 100 :tavoitehinta 1000})))
  (is (= {:sanktio -33.0}
         (ld/bonus-tai-sanktio {:lupaus 100 :toteuma 90 :tavoitehinta 1000})))
  (is (= {:bonus 5200.0}
         (ld/bonus-tai-sanktio {:lupaus 76 :toteuma 78 :tavoitehinta 2000000})))
  (is (= {:sanktio -13200.0}
         (ld/bonus-tai-sanktio {:lupaus 76 :toteuma 74 :tavoitehinta 2000000})))
  (is (nil? (ld/bonus-tai-sanktio {})))
  (is (nil? (ld/bonus-tai-sanktio nil))))

(deftest lupaus-kuukaudet
  (let [lupaus {:kirjaus-kkt nil
                :paatos-kk 0
                :joustovara-kkta 1
                :lupaustyyppi "yksittainen"
                :vastaukset [{:vastaus false
                              :paatos true
                              :kuukausi 10}
                             {:vastaus false
                              :paatos true
                              :kuukausi 11}]}
        kuluva-kuukausi 1]
    (is (= [;; Menneet kuukaudet
            {:kuukausi 10
             :odottaa-kannanottoa? false
             :paattava-kuukausi? true
             :nykyhetkeen-verrattuna :mennyt-kuukausi
             :vastaus false}
            {:kuukausi 11
             :odottaa-kannanottoa? false
             :paattava-kuukausi? true
             :nykyhetkeen-verrattuna :mennyt-kuukausi
             :vastaus false}
            {:kuukausi 12
             :odottaa-kannanottoa? false
             :paattava-kuukausi? true
             :nykyhetkeen-verrattuna :mennyt-kuukausi}

            ;; Kuluva kuukausi
            {:kuukausi 1
             :odottaa-kannanottoa? false
             :paattava-kuukausi? true
             :nykyhetkeen-verrattuna :kuluva-kuukausi}

            ;; Tulevat kuukaudet
            {:kuukausi 2 :odottaa-kannanottoa? false :paattava-kuukausi? true :nykyhetkeen-verrattuna :tuleva-kuukausi}
            {:kuukausi 3 :odottaa-kannanottoa? false :paattava-kuukausi? true :nykyhetkeen-verrattuna :tuleva-kuukausi}
            {:kuukausi 4 :odottaa-kannanottoa? false :paattava-kuukausi? true :nykyhetkeen-verrattuna :tuleva-kuukausi}
            {:kuukausi 5 :odottaa-kannanottoa? false :paattava-kuukausi? true :nykyhetkeen-verrattuna :tuleva-kuukausi}
            {:kuukausi 6 :odottaa-kannanottoa? false :paattava-kuukausi? true :nykyhetkeen-verrattuna :tuleva-kuukausi}
            {:kuukausi 7 :odottaa-kannanottoa? false :paattava-kuukausi? true :nykyhetkeen-verrattuna :tuleva-kuukausi}
            {:kuukausi 8 :odottaa-kannanottoa? false :paattava-kuukausi? true :nykyhetkeen-verrattuna :tuleva-kuukausi}
            {:kuukausi 9 :odottaa-kannanottoa? false :paattava-kuukausi? true :nykyhetkeen-verrattuna :tuleva-kuukausi}]
           (ld/lupaus->kuukaudet lupaus kuluva-kuukausi)))))
