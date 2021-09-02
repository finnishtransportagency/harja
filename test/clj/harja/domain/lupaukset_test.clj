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
                :pisteet 10
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
                :vastaukset [{:vuosi 2021
                              :kuukausi 10
                              :vastaus false
                              :paatos true}
                             {:vuosi 2021
                              :kuukausi 11
                              :vastaus false
                              :paatos true}]}
        ;; 2022-01-01 (kuukausi on 0-index)
        nykyhetki (pvm/luo-pvm 2022 0 1)
        valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                            #inst "2022-09-30T20:59:59.000-00:00"]
        kuukaudet [;; Menneet kuukaudet
                   {:vuosi 2021
                    :kuukausi 10
                    :odottaa-kannanottoa? false
                    :paatos-hylatty? true
                    :paattava-kuukausi? true
                    :kirjauskuukausi? false
                    :nykyhetkeen-verrattuna :mennyt-kuukausi
                    :vastaus {:vuosi 2021
                              :kuukausi 10
                              :vastaus false
                              :paatos true}}
                   {:vuosi 2021
                    :kuukausi 11
                    :odottaa-kannanottoa? false
                    :paatos-hylatty? true
                    :paattava-kuukausi? true
                    :kirjauskuukausi? false
                    :nykyhetkeen-verrattuna :mennyt-kuukausi
                    :vastaus {:vuosi 2021
                              :kuukausi 11
                              :vastaus false
                              :paatos true}}
                   {:vuosi 2021
                    :kuukausi 12
                    :odottaa-kannanottoa? false
                    :paatos-hylatty? true
                    :paattava-kuukausi? true
                    :kirjauskuukausi? false
                    :nykyhetkeen-verrattuna :mennyt-kuukausi}

                   ;; Kuluva kuukausi
                   {:vuosi 2022
                    :kuukausi 1
                    :odottaa-kannanottoa? false
                    :paatos-hylatty? true
                    :paattava-kuukausi? true
                    :kirjauskuukausi? false
                    :nykyhetkeen-verrattuna :kuluva-kuukausi}

                   ;; Tulevat kuukaudet
                   {:vuosi 2022 :kuukausi 2 :odottaa-kannanottoa? false :paatos-hylatty? true :paattava-kuukausi? true :kirjauskuukausi? false :nykyhetkeen-verrattuna :tuleva-kuukausi}
                   {:vuosi 2022 :kuukausi 3 :odottaa-kannanottoa? false :paatos-hylatty? true :paattava-kuukausi? true :kirjauskuukausi? false :nykyhetkeen-verrattuna :tuleva-kuukausi}
                   {:vuosi 2022 :kuukausi 4 :odottaa-kannanottoa? false :paatos-hylatty? true :paattava-kuukausi? true :kirjauskuukausi? false :nykyhetkeen-verrattuna :tuleva-kuukausi}
                   {:vuosi 2022 :kuukausi 5 :odottaa-kannanottoa? false :paatos-hylatty? true :paattava-kuukausi? true :kirjauskuukausi? false :nykyhetkeen-verrattuna :tuleva-kuukausi}
                   {:vuosi 2022 :kuukausi 6 :odottaa-kannanottoa? false :paatos-hylatty? true :paattava-kuukausi? true :kirjauskuukausi? false :nykyhetkeen-verrattuna :tuleva-kuukausi}
                   {:vuosi 2022 :kuukausi 7 :odottaa-kannanottoa? false :paatos-hylatty? true :paattava-kuukausi? true :kirjauskuukausi? false :nykyhetkeen-verrattuna :tuleva-kuukausi}
                   {:vuosi 2022 :kuukausi 8 :odottaa-kannanottoa? false :paatos-hylatty? true :paattava-kuukausi? true :kirjauskuukausi? false :nykyhetkeen-verrattuna :tuleva-kuukausi}
                   {:vuosi 2022 :kuukausi 9 :odottaa-kannanottoa? false :paatos-hylatty? true :paattava-kuukausi? true :kirjauskuukausi? false :nykyhetkeen-verrattuna :tuleva-kuukausi}]]
    (is (= kuukaudet
           (ld/lupaus->kuukaudet lupaus nykyhetki valittu-hoitokausi)))
    (let [lupaus (dissoc lupaus :vastaukset)
          valittu-hoitokausi [#inst "2022-09-30T21:00:00.000-00:00"
                              #inst "2023-09-30T20:59:59.000-00:00"]
          lupaus-kuukaudet (ld/lupaus->kuukaudet lupaus nykyhetki valittu-hoitokausi)]
      (is (= (repeat 12 false)
             (->> lupaus-kuukaudet (map :odottaa-kannanottoa?)))
          "Tuleviin hoitokausiin ei oteta kantaa")
      (is (= (repeat 12 :tuleva-kuukausi)
             (->> lupaus-kuukaudet (map :nykyhetkeen-verrattuna)))
          "Vertailu nykyhetkeen toimii"))

    ;; Muutetaan 11/2021 vastaus myöntäväksi
    (let [lupaus (assoc-in lupaus [:vastaukset 1 :vastaus] true)
          kuukaudet (-> kuukaudet
                        (assoc-in [1 :vastaus :vastaus] true)
                        (assoc-in [2 :odottaa-kannanottoa?] true))
          kuukaudet (map #(assoc % :paatos-hylatty? false) kuukaudet)]
      (is (= kuukaudet
             (ld/lupaus->kuukaudet lupaus nykyhetki valittu-hoitokausi))))))
