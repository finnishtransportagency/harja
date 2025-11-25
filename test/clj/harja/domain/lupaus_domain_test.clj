(ns harja.domain.lupaus-domain-test
  (:require [clojure.test :refer :all]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [clj-time.coerce :as tc]))

(defn- kk->pvm [vuosi kuukausi]
  (pvm/suomen-aikavyohykkeessa
    (tc/from-string (str vuosi "-" kuukausi))))

(deftest hoitokuukausi-ennen?
  (is (true? (lupaus-domain/hoitokuukausi-ennen? 10 11)))
  (is (true? (lupaus-domain/hoitokuukausi-ennen? 10 12)))
  (is (true? (lupaus-domain/hoitokuukausi-ennen? 10 1)))
  (is (true? (lupaus-domain/hoitokuukausi-ennen? 10 2)))
  (is (true? (lupaus-domain/hoitokuukausi-ennen? 10 9)))
  (is (true? (lupaus-domain/hoitokuukausi-ennen? 12 1))))

(deftest odottaa-kannanottoa
  (let [lupaus {:kirjaus-kkt [10 11]
                :paatos-kk [6]
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
    (is (false? (lupaus-domain/odottaa-kannanottoa? lupaus 11)))
    (is (false? (lupaus-domain/odottaa-kannanottoa? lupaus 12)))
    (is (false? (lupaus-domain/odottaa-kannanottoa? lupaus 1)))
    (is (false? (lupaus-domain/odottaa-kannanottoa? lupaus 6))
        "Vielä kesäkuussa ei tarvitse ottaa kantaa, koska kesäkuu on päättävä kuukausi, ja kirjaus-kk:t on jo kirjattu.")
    (is (true? (lupaus-domain/odottaa-kannanottoa? lupaus 7))
        "Heinäkuussa täytyy ottaa kantaa, koska kesäkuu on päättävä kuukausi.")

    (let [nykyhetki (pvm/luo-pvm 2022 9 1)                ; 2022-10-01
          valittu-hoitokausi [#inst "2021-09-30T21:00:00.000-00:00"
                              #inst "2022-09-30T20:59:59.000-00:00"]]
      (is (true? (lupaus-domain/odottaa-kannanottoa? lupaus nykyhetki valittu-hoitokausi))
          "Lupaus odottaa kannanottoa, vaikka valittu hoitokausi on menneisyydessä")
      (is (false? (lupaus-domain/odottaa-kannanottoa? lupaus (pvm/luo-pvm 2021 8 30) valittu-hoitokausi))
          "Lupaus ei odota kannanottoa, jos valittu hoitokausi on tulevaisuudessa")))

  (let [lupaus {:kirjaus-kkt [10 11]
                :paatos-kk [6]
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
    (is (false? (lupaus-domain/odottaa-kannanottoa? lupaus 11))
        "Hyväksyttyyn lupaukseen ei tarvitse enää ottaa kantaa.")
    (is (false? (lupaus-domain/odottaa-kannanottoa? lupaus 7))
        "Hyväksyttyyn lupaukseen ei tarvitse enää ottaa kantaa."))

  ;; paatos-kk = 0 (kaikki)
  (let [lupaus {:kirjaus-kkt nil
                :paatos-kk [0]
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
    (is (false? (lupaus-domain/odottaa-kannanottoa? lupaus 11)))
    (is (false? (lupaus-domain/odottaa-kannanottoa? lupaus 12)))
    (is (true? (lupaus-domain/odottaa-kannanottoa? lupaus 1))))

  ;; paatos-kk = 0 (kaikki)
  ;; Yksittäinen lupaus voidaan hylätä ennen kuin kaikki päättävät vastaukset on annettu
  (let [lupaus {:kirjaus-kkt nil
                :paatos-kk [0]
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
    (is (false? (lupaus-domain/odottaa-kannanottoa? lupaus 1)))))

(deftest bonus-tai-sanktio
  (is (= {:tavoite-taytetty true}
         (lupaus-domain/bonus-tai-sanktio {:lupaus 100 :toteuma 100 :tavoitehinta 1000})))
  (is (= {:bonus 13.0}
         (lupaus-domain/bonus-tai-sanktio {:lupaus 90 :toteuma 100 :tavoitehinta 1000})))
  (is (= {:sanktio -33.0}
         (lupaus-domain/bonus-tai-sanktio {:lupaus 100 :toteuma 90 :tavoitehinta 1000})))
  (is (= {:bonus 5200.0}
         (lupaus-domain/bonus-tai-sanktio {:lupaus 76 :toteuma 78 :tavoitehinta 2000000})))
  (is (= {:sanktio -13200.0}
         (lupaus-domain/bonus-tai-sanktio {:lupaus 76 :toteuma 74 :tavoitehinta 2000000})))
  (is (nil? (lupaus-domain/bonus-tai-sanktio {})))
  (is (nil? (lupaus-domain/bonus-tai-sanktio nil))))

(deftest lupaus-kuukaudet
  (let [lupaus {:kirjaus-kkt nil
                :paatos-kk [0]
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
        hoitovuosi-nro 1
        hoitovuoden-erikoisarvot []
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
                   {:vuosi 2022 :kuukausi 9 :odottaa-kannanottoa? false :paatos-hylatty? true :paattava-kuukausi? true :kirjauskuukausi? false :nykyhetkeen-verrattuna :tuleva-kuukausi}]

        ;maarapaiva-tiedot (laske-maarapaiva-tiedot db urakan-alkuvuosi hoitokauden-alkuvuosi nykyhetki)
        ]
    (is (= kuukaudet
           (lupaus-domain/lupaus->kuukaudet lupaus nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot nil)))
    (let [lupaus (dissoc lupaus :vastaukset)
          valittu-hoitokausi [#inst "2022-09-30T21:00:00.000-00:00"
                              #inst "2023-09-30T20:59:59.000-00:00"]
          lupaus-kuukaudet (lupaus-domain/lupaus->kuukaudet lupaus nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot nil)]
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
             (lupaus-domain/lupaus->kuukaudet lupaus nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot nil))))))

(deftest odottaa-urakoitsijan-kannanottoa?
  (is (true? (lupaus-domain/odottaa-urakoitsijan-kannanottoa?
               [{:vuosi 2019 :kuukausi 10 :odottaa-vastausta? false}
                {:vuosi 2019 :kuukausi 11 :odottaa-vastausta? true}
                {:vuosi 2019 :kuukausi 12 :odottaa-vastausta? false}
                {:vuosi 2020 :kuukausi 1 :odottaa-vastausta? false}
                {:vuosi 2020 :kuukausi 2 :odottaa-vastausta? false}
                {:vuosi 2020 :kuukausi 3 :odottaa-vastausta? false}
                {:vuosi 2020 :kuukausi 4 :odottaa-vastausta? false}
                {:vuosi 2020 :kuukausi 5 :odottaa-vastausta? false}
                {:vuosi 2020 :kuukausi 6 :odottaa-vastausta? false}
                {:vuosi 2020 :kuukausi 7 :odottaa-vastausta? false}
                {:vuosi 2020 :kuukausi 8 :odottaa-vastausta? false}
                {:vuosi 2020 :kuukausi 9 :odottaa-vastausta? false}]))
    "Odottaa urakoitsijan kannanottoa, koska kuukausi 11 on vastaamatta")
  (is (false? (lupaus-domain/odottaa-urakoitsijan-kannanottoa?
                [{:vuosi 2019 :kuukausi 10 :odottaa-vastausta? false}
                 {:vuosi 2019 :kuukausi 11 :odottaa-vastausta? true}
                 {:vuosi 2019 :kuukausi 12 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 1 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 2 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 3 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 4 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 5 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 6 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 7 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 8 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 9 :odottaa-vastausta? false :pisteet 99}]))
    "Ei odota urakoitsijan kannanottoa, koska päättävät pisteet on annettu")
  (is (false? (lupaus-domain/odottaa-urakoitsijan-kannanottoa?
                [{:vuosi 2019 :kuukausi 10 :odottaa-vastausta? false}
                 {:vuosi 2019 :kuukausi 11 :odottaa-vastausta? false}
                 {:vuosi 2019 :kuukausi 12 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 1 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 2 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 3 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 4 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 5 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 6 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 7 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 8 :odottaa-vastausta? false}
                 {:vuosi 2020 :kuukausi 9 :odottaa-vastausta? true}]))
    "Ei odota urakoitsijan kannanottoa, koska ainoastaan päättävät pisteet on antamatta"))


(deftest hoitovuoden-kirjauskuukaudet-test
  (let [lupaus {:kirjaus-kkt [10 11 12 1 2 3 4 5 6 7 8 9]}
        erikoisarvot {:kirjaus-kkt [10 1 4 6]}]

    (testing "Käyttää erikoisarvoja kun ne on annettu"
      (is (= [10 1 4 6]
             (lupaus-domain/hoitovuoden-kirjauskuukaudet lupaus 1 erikoisarvot))))

    (testing "Käyttää perusarvoja kun erikoisarvoja ei ole"
      (is (= [10 11 12 1 2 3 4 5 6 7 8 9]
             (lupaus-domain/hoitovuoden-kirjauskuukaudet lupaus 1 nil))))

    (testing "Käyttää perusarvoja kun erikoisarvot on tyhjä"
      (is (= [10 11 12 1 2 3 4 5 6 7 8 9]
             (lupaus-domain/hoitovuoden-kirjauskuukaudet lupaus 1 {}))))))

(deftest sallittu-kuukausi-hoitovuodelle-test
  (let [lupaus {:kirjaus-kkt [10 11 12 1 2 3 4 5 6 7 8 9]
                :paatos-kk [9]}
        erikoisarvot {:kirjaus-kkt [10 1 4 6]  ; Vain neljä kuukautta
                      :paatos-kk [6]}]           ; Kesäkuu päätös

    (testing "Erikoisarvot rajoittavat kirjauskuukausia"
      (is (true? (lupaus-domain/sallittu-kuukausi-hoitovuodelle? lupaus 10 false 1 erikoisarvot)))
      (is (true? (lupaus-domain/sallittu-kuukausi-hoitovuodelle? lupaus 1 false 1 erikoisarvot)))
      (is (false? (lupaus-domain/sallittu-kuukausi-hoitovuodelle? lupaus 11 false 1 erikoisarvot))))

    (testing "Erikoisarvot muuttavat päätöskuukautta"
      (is (true? (lupaus-domain/sallittu-kuukausi-hoitovuodelle? lupaus 6 true 1 erikoisarvot)))
      (is (false? (lupaus-domain/sallittu-kuukausi-hoitovuodelle? lupaus 9 true 1 erikoisarvot))))

    (testing "Ilman erikoisarvoja käyttää perusarvoja"
      (is (true? (lupaus-domain/sallittu-kuukausi-hoitovuodelle? lupaus 11 false 1 nil)))
      (is (true? (lupaus-domain/sallittu-kuukausi-hoitovuodelle? lupaus 9 true 1 nil))))))

(deftest vaaditut-vastauskuukaudet-hoitovuodelle-test
  (let [tavallinen-lupaus {:kirjaus-kkt [10 11 12 1 2 3 4 5 6 7 8 9]
                           :paatos-kk [9]
                           :lupaustyyppi "yksittainen"}
        kustannusennuste-lupaus {:kirjaus-kkt [10 1 4 6]
                                 :paatos-kk [6]
                                 :lupaustyyppi "kustannusennuste"}
        erikoisarvot {:kirjaus-kkt [10 1 4 6]
                      :paatos-kk [6]}]

    (testing "Tavallinen lupaus: yhdistää erikoisarvojen kirjaus- ja päätöskuukaudet"
      (is (= #{10 1 4 6}
             (lupaus-domain/vaaditut-vastauskuukaudet-hoitovuodelle tavallinen-lupaus nil 1 erikoisarvot))))

    (testing "Tavallinen lupaus: suodattaa kuluvan kuukauden mukaan (vanha logiikka)"
      (is (= #{10}
             (lupaus-domain/vaaditut-vastauskuukaudet-hoitovuodelle tavallinen-lupaus 11 1 erikoisarvot))))

    (testing "Kustannusennuste: näyttää vain kuluva kuukausi jos se on kirjauskuukausi"
      ;; Tammikuu on kirjauskuukausissa
      (is (= #{1}
             (lupaus-domain/vaaditut-vastauskuukaudet-hoitovuodelle kustannusennuste-lupaus 1 1 erikoisarvot)))

      ;; Marraskuu ei ole kirjauskuukausissa [10 1 4 6]  
      (is (= #{}
             (lupaus-domain/vaaditut-vastauskuukaudet-hoitovuodelle kustannusennuste-lupaus 11 1 erikoisarvot)))

      ;; Huhtikuu on kirjauskuukausissa
      (is (= #{4}
             (lupaus-domain/vaaditut-vastauskuukaudet-hoitovuodelle kustannusennuste-lupaus 4 1 erikoisarvot))))))


(deftest kayttaja-saa-vastata-test
  (testing "Kirjauskuukausi - perustason kirjoitusoikeus riittää"
    (let [lupaus-kuukausi {:kirjauskuukausi? true :paattava-kuukausi? false}
          kayttaja-jolla-kirjoitusoikeus {:id 1}
          kayttaja-ilman-oikeuksia {:id 2}]

      ;; Mockaa voi-kirjoittaa? palauttamaan true käyttäjälle 1
      (with-redefs [oikeudet/voi-kirjoittaa? (fn [_ urakka-id kayttaja]
                                                (= 1 (:id kayttaja)))]
        (is (true? (lupaus-domain/kayttaja-saa-vastata?
                     kayttaja-jolla-kirjoitusoikeus
                     lupaus-kuukausi
                     "yksittainen"
                     123))
            "Käyttäjä jolla kirjoitusoikeus saa vastata kirjauskuukauteen")
        
        (is (false? (lupaus-domain/kayttaja-saa-vastata?
                      kayttaja-ilman-oikeuksia
                      lupaus-kuukausi
                      "yksittainen"
                      123))
            "Käyttäjä ilman oikeuksia ei saa vastata"))))

  (testing "Päättävä kuukausi - tavallinen lupaus vaatii päätösoikeuden"
    (let [lupaus-kuukausi {:kirjauskuukausi? false :paattava-kuukausi? true}
          tilaajan-kayttaja {:id 3}
          urakoitsijan-kayttaja {:id 4}]

      ;; Mockaa on-muu-oikeus? - tilaajalla on päätösoikeus, urakoitsijalla ei
      (with-redefs [oikeudet/on-muu-oikeus? (fn [oikeus _ urakka-id kayttaja]
                                               (and (= "päätös" oikeus)
                                                    (= 3 (:id kayttaja))))]
        ;; Tilaaja saa tehdä päätöksen (Excel: W,päätös)
        (is (true? (lupaus-domain/kayttaja-saa-vastata?
                     tilaajan-kayttaja
                     lupaus-kuukausi
                     "yksittainen"
                     123))
            "Tilaaja saa tehdä päätöksen")

        ;; Urakoitsija ei saa tehdä päätöstä
        (is (false? (lupaus-domain/kayttaja-saa-vastata?
                      urakoitsijan-kayttaja
                      lupaus-kuukausi
                      "yksittainen"
                      123))
            "Urakoitsija ei saa tehdä päätöstä"))))

  (testing "Päättävä kuukausi - kustannusennuste vaatii kustannusennuste-oikeuden"
    (let [lupaus-kuukausi {:kirjauskuukausi? false :paattava-kuukausi? true}
          tilaajan-kayttaja {:id 3}
          urakoitsijan-kayttaja {:id 4}]

      ;; Mockaa on-muu-oikeus? - tilaajalla on kustannusennuste-oikeus
      (with-redefs [oikeudet/on-muu-oikeus? (fn [oikeus _ urakka-id kayttaja]
                                               (and (= "kustannusennuste" oikeus)
                                                    (= 3 (:id kayttaja))))]
        (is (true? (lupaus-domain/kayttaja-saa-vastata?
                     tilaajan-kayttaja
                     lupaus-kuukausi
                     "kustannusennuste"
                     123))
            "Tilaaja saa syöttää kustannusennusteen")
        
        (is (false? (lupaus-domain/kayttaja-saa-vastata?
                      urakoitsijan-kayttaja
                      lupaus-kuukausi
                      "kustannusennuste"
                      123))
            "Urakoitsija ei saa syöttää kustannusennusteen päättävään kuukauteen"))))

  (testing "Ei kirjaus- eikä päättävä kuukausi - ei saa vastata"
    (let [lupaus-kuukausi {:kirjauskuukausi? false :paattava-kuukausi? false}
          kayttaja {:id 1}]

      ;; Ei tarvita mockausta, koska tämä ei kutsuu oikeusfunktioita
      (is (false? (lupaus-domain/kayttaja-saa-vastata?
                    kayttaja
                    lupaus-kuukausi
                    "yksittainen"
                    123))
          "Ei-vastauskuukauteen ei saa vastata")))

  (testing "Sekä kirjaus- että päättävä kuukausi - päätösoikeus vaaditaan"
    (let [lupaus-kuukausi {:kirjauskuukausi? true :paattava-kuukausi? true}
          tilaajan-kayttaja {:id 3}
          urakoitsijan-kayttaja {:id 4}]

      ;; Mockaa molemmat oikeusfunktiot
      (with-redefs [oikeudet/on-muu-oikeus? (fn [oikeus _ urakka-id kayttaja]
                                               (and (= "päätös" oikeus)
                                                    (= 3 (:id kayttaja))))
                    oikeudet/voi-kirjoittaa? (fn [_ urakka-id kayttaja]
                                                (= 4 (:id kayttaja)))]
        
        ;; Tilaajalla on päätösoikeus - saa vastata
        (is (true? (lupaus-domain/kayttaja-saa-vastata?
                     tilaajan-kayttaja
                     lupaus-kuukausi
                     "yksittainen"
                     123))
            "Tilaaja saa vastata kun on päätösoikeus (vaikka on myös kirjauskuukausi)")

        ;; Urakoitsijalla on vain kirjoitusoikeus, mutta EI päätösoikeutta
        ;; Koska päättävä-kuukausi tarkistetaan ENSIN, urakoitsija ei saa vastata
        (is (false? (lupaus-domain/kayttaja-saa-vastata?
                      urakoitsijan-kayttaja
                      lupaus-kuukausi
                      "yksittainen"
                      123))
            "Urakoitsija ei saa vastata vaikka on kirjoitusoikeus, koska päätösoikeus puuttuu")))))

(deftest kustannusennuste-maarapaiva-paattely-test
  "Testaa määräpäivän päättelylogiikkaa eri ajanhetkinä."
  (let [maarapaiva (pvm/luo-pvm 2025 10 15)
        tiedot-syotetty true
        tiedot-ei-syotetty false
        ulkoinen-disabled false]
    
    (testing "Ennen määräpäivää - vastaaminen sallittu, ei read-only"
      (let [nykyhetki (pvm/luo-pvm 2025 10 14)
            tulos (lupaus-domain/kustannusennuste-maarapaiva-paattely nykyhetki maarapaiva tiedot-syotetty ulkoinen-disabled)]
        (is (false? (:maarapaiva-mennyt-ohi? tulos)) "Määräpäivä ei ole vielä ohitettu")
        (is (false? (:ei-maarapaivan-kuukausi? tulos)) "Kuukausi on oikea")
        (is (false? (:kayta-readonly-nakymaa? tulos)) "Ei käytetä read-only näkymää")
        (is (false? (:disabled? tulos)) "Vastaaminen on sallittu")))
    
    (testing "Määräpäivänä - vastaaminen sallittu, ei read-only"
      (let [nykyhetki (pvm/luo-pvm 2025 10 15)
            tulos (lupaus-domain/kustannusennuste-maarapaiva-paattely nykyhetki maarapaiva tiedot-syotetty ulkoinen-disabled)]
        (is (false? (:maarapaiva-mennyt-ohi? tulos)) "Määräpäivänä ei ole vielä ohitettu")
        (is (false? (:ei-maarapaivan-kuukausi? tulos)) "Kuukausi on oikea")
        (is (false? (:kayta-readonly-nakymaa? tulos)) "Ei käytetä read-only näkymää")
        (is (false? (:disabled? tulos)) "Vastaaminen on sallittu")))
    
    (testing "Määräpäivän jälkeen + tiedot syötetty ajoissa = read-only"
      (let [nykyhetki (pvm/luo-pvm 2025 10 16)
            tulos (lupaus-domain/kustannusennuste-maarapaiva-paattely nykyhetki maarapaiva tiedot-syotetty ulkoinen-disabled)]
        (is (true? (:maarapaiva-mennyt-ohi? tulos)) "Määräpäivä on ohitettu")
        (is (false? (:ei-maarapaivan-kuukausi? tulos)) "Kuukausi on oikea")
        (is (true? (:kayta-readonly-nakymaa? tulos)) "Käytetään read-only näkymää")
        (is (true? (:disabled? tulos)) "Vastaaminen on estetty")))
    
    (testing "Määräpäivän jälkeen + tiedot EI syötetty ajoissa = varoitus"
      (let [nykyhetki (pvm/luo-pvm 2025 10 16)
            tulos (lupaus-domain/kustannusennuste-maarapaiva-paattely nykyhetki maarapaiva tiedot-ei-syotetty ulkoinen-disabled)]
        (is (true? (:maarapaiva-mennyt-ohi? tulos)) "Määräpäivä on ohitettu")
        (is (false? (:ei-maarapaivan-kuukausi? tulos)) "Kuukausi on oikea")
        (is (false? (:kayta-readonly-nakymaa? tulos)) "Ei read-only (tiedot puuttuvat)")
        (is (true? (:disabled? tulos)) "Vastaaminen on estetty (määräpäivä ohitettu)")))
    
    (testing "Väärässä kuukaudessa - vastaaminen estetty"
      (let [nykyhetki (pvm/luo-pvm 2025 11 15)  ; Marraskuu, mutta määräpäivä oli lokakuussa
            tulos (lupaus-domain/kustannusennuste-maarapaiva-paattely nykyhetki maarapaiva tiedot-syotetty ulkoinen-disabled)]
        (is (true? (:maarapaiva-mennyt-ohi? tulos)) "Määräpäivä on ohitettu (lokakuu → marraskuu)")
        (is (true? (:ei-maarapaivan-kuukausi? tulos)) "Kuukausi on väärä")
        (is (true? (:kayta-readonly-nakymaa? tulos)) "Read-only koska määräpäivä ohitettu JA tiedot syötetty")
        (is (true? (:disabled? tulos)) "Vastaaminen on estetty")))
    
    (testing "Ulkoisen disabled-tilan yhdistäminen"
      (let [nykyhetki (pvm/luo-pvm 2025 10 14)
            tulos (lupaus-domain/kustannusennuste-maarapaiva-paattely nykyhetki maarapaiva tiedot-syotetty true)]
        (is (true? (:disabled? tulos)) "Ulkoisen disabled-tilan perusteella disabled")))))

(deftest kustannusennuste-maarapaiva-paattely-eri-paivina-test
  "Testaa, että määräpäivä-päivänä vastaaminen on sallittu eri kellonaikoina.
   Kuvaa kellonajan merkitystä: esimerkiksi ilta 15. päivä on edelleen määräpäivä-päivä."
  (let [;; Määräpäivä on 15. päivä keskipäivällä
        maarapaiva (pvm/luo-pvm-aika 2025 10 15 12 0 0 0)
        tiedot-syotetty true
        ulkoinen-disabled false]
    
    (testing "Ennen määräpäivää aamulla - vastaaminen sallittu"
      (let [aamu (pvm/luo-pvm-aika 2025 10 14 9 0 0 0)
            tulos (lupaus-domain/kustannusennuste-maarapaiva-paattely aamu maarapaiva tiedot-syotetty ulkoinen-disabled)]
        (is (false? (:maarapaiva-mennyt-ohi? tulos)) "14.10. klo 09:00 - määräpäivä ei ole vielä ohitettu")
        (is (false? (:disabled? tulos)) "14.10. klo 09:00 - vastaaminen on sallittu")))
    
    (testing "Määräpäivä-päivänä aamulla - vastaaminen sallittu"
      (let [aamu (pvm/luo-pvm-aika 2025 10 15 8 30 0 0)
            tulos (lupaus-domain/kustannusennuste-maarapaiva-paattely aamu maarapaiva tiedot-syotetty ulkoinen-disabled)]
        (is (false? (:maarapaiva-mennyt-ohi? tulos)) "15.10. klo 08:30 - määräpäivä ei ole vielä ohitettu")
        (is (false? (:disabled? tulos)) "15.10. klo 08:30 - vastaaminen on sallittu")))
    
    (testing "Määräpäivä-päivänä illalla - vastaaminen sallittu"
      (let [ilta (pvm/luo-pvm-aika 2025 10 15 23 59 59 999)
            tulos (lupaus-domain/kustannusennuste-maarapaiva-paattely ilta maarapaiva tiedot-syotetty ulkoinen-disabled)]
        (is (false? (:maarapaiva-mennyt-ohi? tulos)) "15.10. klo 23:59:59 - määräpäivä ei ole vielä ohitettu")
        (is (false? (:disabled? tulos)) "15.10. klo 23:59:59 - vastaaminen on sallittu")))
    
    (testing "Seuraavana päivänä aamulla - määräpäivä jo ohitettu"
      (let [seuraava-aamu (pvm/luo-pvm-aika 2025 10 16 0 0 0 1)
            tulos (lupaus-domain/kustannusennuste-maarapaiva-paattely seuraava-aamu maarapaiva tiedot-syotetty ulkoinen-disabled)]
        (is (true? (:maarapaiva-mennyt-ohi? tulos)) "16.10. klo 00:00:00 - määräpäivä on ohitettu")
        (is (true? (:disabled? tulos)) "16.10. klo 00:00:00 - vastaaminen on estetty")))))


(deftest laske-pisteytyshoitovuosi-test
  "Testaa, että kustannusennuste-kuukauden pisteytyshoitovuosi lasketaan oikein.
   Offset-arvo määrittää siirtymän normaalista hoitovuoden laskennasta."
  
  (testing "Normaali pisteytys ilman offsettia (offset=0)"
    (testing "Lokakuu kuuluu HK1:lle"
      (is (= 2024 (lupaus-domain/laske-pisteytyshoitovuosi 2024 10 0))
          "Lokakuu 2024, offset=0 -> hoitovuosi 2024 (HK 2024-2025)"))
    
    (testing "Tammikuu kuuluu HK1:lle"
      (is (= 2024 (lupaus-domain/laske-pisteytyshoitovuosi 2025 1 0))
          "Tammikuu 2025, offset=0 -> hoitovuosi 2024 (HK 2024-2025)"))
    
    (testing "Syyskuu kuuluu HK1:lle"
      (is (= 2024 (lupaus-domain/laske-pisteytyshoitovuosi 2025 9 0))
          "Syyskuu 2025, offset=0 -> hoitovuosi 2024 (HK 2024-2025)"))
    
    (testing "Huhtikuu kuuluu HK1:lle"
      (is (= 2024 (lupaus-domain/laske-pisteytyshoitovuosi 2025 4 0))
          "Huhtikuu 2025, offset=0 -> hoitovuosi 2024 (HK 2024-2025)")))
  
  (testing "Pisteytys offsetilla +1 (esim. elokuu pisteytetään seuraavalle HK:lle)"
    (testing "Elokuu 2024, offset=1 -> pisteytetään HK2:lle (2025)"
      (is (= 2025 (lupaus-domain/laske-pisteytyshoitovuosi 2024 8 1))
          "Elokuu 2024, offset=1 -> hoitovuosi 2025 (HK 2025-2026)"))
    
    (testing "Lokakuu 2024, offset=1"
      (is (= 2025 (lupaus-domain/laske-pisteytyshoitovuosi 2024 10 1))
          "Lokakuu 2024, offset=1 -> hoitovuosi 2025"))
    
    (testing "Tammikuu 2025, offset=1"
      (is (= 2026 (lupaus-domain/laske-pisteytyshoitovuosi 2025 1 1))
          "Tammikuu 2025, offset=1 -> hoitovuosi 2026")))
  
  (testing "Pre-ehdot (validointi)"
    (testing "Kuukauden tulee olla 1-12"
      (is (thrown? java.lang.AssertionError (lupaus-domain/laske-pisteytyshoitovuosi 2024 13 0))
          "Kuukausi 13 ei ole validi")
      (is (thrown? java.lang.AssertionError (lupaus-domain/laske-pisteytyshoitovuosi 2024 0 0))
          "Kuukausi 0 ei ole validi"))
    
    (testing "Offset tulee olla >= 0"
      (is (thrown? java.lang.AssertionError (lupaus-domain/laske-pisteytyshoitovuosi 2024 8 -1))
          "Negatiivinen offset ei ole sallittu"))
    
    (testing "Parametrien tulee olla kokonaislukuja"
      (is (thrown? java.lang.AssertionError (lupaus-domain/laske-pisteytyshoitovuosi "2024" 8 0))
          "Vuosi String-tyyppi ei ole sallittu")))
  
  (testing "Post-ehto (palautusarvo on kokonaisluku)"
    (is (integer? (lupaus-domain/laske-pisteytyshoitovuosi 2024 10 0))
        "Palautusarvo on kokonaisluku")
    (is (integer? (lupaus-domain/laske-pisteytyshoitovuosi 2024 8 1))
        "Palautusarvo on kokonaisluku myös offsetilla")))


(deftest kustannusennuste-toteuma-offset-test
  "Testaa, että toteuman laskenta huomioi offset-logiikan.
   Elokuun pisteet kuuluvat seuraavalle hoitokaudelle (HK2), vaikka se
   kirjataan HK1:llä."
  
  (testing "Toteuma lasketaan oikein ilman offsettia"
    (let [lupaus-kuukaudet [{:kuukausi 10 :kustannusennuste {:lasketut-pisteet 8 :hoitovuosi 2024}}
                            {:kuukausi 1 :kustannusennuste {:lasketut-pisteet 6 :hoitovuosi 2024}}
                            {:kuukausi 4 :kustannusennuste {:lasketut-pisteet 10 :hoitovuosi 2024}}]
          ;; HK1 (hoitovuosi-nro=1, urakan-alkuvuosi=2024) -> alkuvuosi=2024
          toteuma (lupaus-domain/kustannusennuste->toteuma 
                    {:lupaus-kuukaudet lupaus-kuukaudet
                     :hoitovuosi-paattynyt? true
                     :hoitovuosi-nro 1
                     :urakan-alkuvuosi 2024})]
      (is (= 8 toteuma) 
          "HK1 toteuma = ROUND((8+6+10)/3) = ROUND(8) = 8")))
  
  (testing "Toteuma huomioi hoitovuosi-nron perusteella suodatuksen"
    (let [lupaus-kuukaudet [{:kuukausi 10 :kustannusennuste {:lasketut-pisteet 8 :hoitovuosi 2024}}   ; HK1
                            {:kuukausi 1 :kustannusennuste {:lasketut-pisteet 6 :hoitovuosi 2024}}    ; HK1
                            {:kuukausi 8 :kustannusennuste {:lasketut-pisteet 4 :hoitovuosi 2025}}]   ; HK2 (elokuu pisteytetään seuraavalle HK:lle!)
          ;; Laskettaessa HK2:n toteuma (hoitovuosi-nro=2, urakan-alkuvuosi=2024)
          ;; HK2:n alkuvuosi = 2024 + (2-1) = 2025
          toteuma-hk2 (lupaus-domain/kustannusennuste->toteuma 
                        {:lupaus-kuukaudet lupaus-kuukaudet
                         :hoitovuosi-paattynyt? true
                         :hoitovuosi-nro 2
                         :urakan-alkuvuosi 2024})]
      (is (= 4 toteuma-hk2) 
          "HK2 toteuma = 4 (vain elokuun pisteet, jotka pisteytetään HK2:lle)")))
  
  (testing "Toteuma lasketaan vain, kun hoitokausi on päättynyt"
    (let [lupaus-kuukaudet [{:kuukausi 10 :kustannusennuste {:lasketut-pisteet 8 :hoitovuosi 2024}}]
          ;; hoitovuosi-paattynyt? = false
          toteuma (lupaus-domain/kustannusennuste->toteuma 
                    {:lupaus-kuukaudet lupaus-kuukaudet
                     :hoitovuosi-paattynyt? false
                     :hoitovuosi-nro 1
                     :urakan-alkuvuosi 2024})]
      (is (nil? toteuma)
          "Toteuma on nil, kun hoitokausi ei ole päättynyt")))
  
  (testing "Toteuma on 0 kun ei ole pisteitä"
    (let [lupaus-kuukaudet [{:kuukausi 10 :kustannusennuste {:lasketut-pisteet nil :hoitovuosi 2024}}
                            {:kuukausi 1 :kustannusennuste {:lasketut-pisteet nil :hoitovuosi 2024}}]
          toteuma (lupaus-domain/kustannusennuste->toteuma 
                    {:lupaus-kuukaudet lupaus-kuukaudet
                     :hoitovuosi-paattynyt? true
                     :hoitovuosi-nro 1
                     :urakan-alkuvuosi 2024})]
      (is (= 0 toteuma)
          "Toteuma on 0, kun ei ole yhtään pisteitä")))
  
  (testing "Toteuma pyöristetään oikein"
    (let [lupaus-kuukaudet [{:kuukausi 10 :kustannusennuste {:lasketut-pisteet 8 :hoitovuosi 2024}}
                            {:kuukausi 1 :kustannusennuste {:lasketut-pisteet 7 :hoitovuosi 2024}}]
          toteuma (lupaus-domain/kustannusennuste->toteuma 
                    {:lupaus-kuukaudet lupaus-kuukaudet
                     :hoitovuosi-paattynyt? true
                     :hoitovuosi-nro 1
                     :urakan-alkuvuosi 2024})]
      (is (= 8 toteuma)
          "ROUND((8+7)/2) = ROUND(7.5) = 8"))))


(deftest laske-pisteytyshoitovuosi-suuri-offset-test
  "Testaa offset-logiikkaa suuremmilla arvoilla."
  
  (testing "Offset 2 (skenaario: pisteytetään kahden vuoden päähän)"
    (is (= 2026 (lupaus-domain/laske-pisteytyshoitovuosi 2024 10 2))
        "Lokakuu 2024, offset=2 -> hoitovuosi 2026"))
  
  (testing "Offset 3"
    (is (= 2027 (lupaus-domain/laske-pisteytyshoitovuosi 2024 8 3))
        "Elokuu 2024, offset=3 -> hoitovuosi 2027")))


(deftest kustannusennuste-toteuma-useita-hoitovuosia-test
  "Testaa toteuman laskentaa, kun samassa datassa on sekä HK1:n että HK2:n pisteitä." 
  (testing "HK1 ja HK2 pisteet samassa datassa - filtteröidään oikein HK2:lle"
    (let [lupaus-kuukaudet [
            ;; HK1 pisteet
            {:kuukausi 10 :kustannusennuste {:lasketut-pisteet 8 :hoitovuosi 2024}}
            {:kuukausi 1 :kustannusennuste {:lasketut-pisteet 6 :hoitovuosi 2024}}
            {:kuukausi 4 :kustannusennuste {:lasketut-pisteet 10 :hoitovuosi 2024}}
            
            ;; HK2 pisteet: elokuu offset=1 + muut seuraavalta HK:lta
            {:kuukausi 8 :kustannusennuste {:lasketut-pisteet 5 :hoitovuosi 2025}}
            {:kuukausi 10 :kustannusennuste {:lasketut-pisteet 7 :hoitovuosi 2025}}]
          
          ;; Laskettaessa HK1
          toteuma-hk1 (lupaus-domain/kustannusennuste->toteuma 
                        {:lupaus-kuukaudet lupaus-kuukaudet
                         :hoitovuosi-paattynyt? true
                         :hoitovuosi-nro 1
                         :urakan-alkuvuosi 2024})
          
          ;; Laskettaessa HK2
          toteuma-hk2 (lupaus-domain/kustannusennuste->toteuma 
                        {:lupaus-kuukaudet lupaus-kuukaudet
                         :hoitovuosi-paattynyt? true
                         :hoitovuosi-nro 2
                         :urakan-alkuvuosi 2024})]
      
      (is (= 8 toteuma-hk1)
          "HK1 toteuma = ROUND((8+6+10)/3) = 8 (ei ota HK2:n pisteitä mukaan)")
      
      (is (= 6 toteuma-hk2)
          "HK2 toteuma = ROUND((5+7)/2) = 6 (vain HK2 pisteet)")))
  
  (testing "Useita HK:ita per\u00e4kk\u00e4in"
    (let [lupaus-kuukaudet [
            ;; HK1: 2024-2025
            {:kuukausi 10 :kustannusennuste {:lasketut-pisteet 8 :hoitovuosi 2024}}
            ;; HK2: 2025-2026 (elokuun offset=1-logiikka)
            {:kuukausi 8 :kustannusennuste {:lasketut-pisteet 9 :hoitovuosi 2025}}
            ;; HK3: 2026-2027
            {:kuukausi 10 :kustannusennuste {:lasketut-pisteet 7 :hoitovuosi 2026}}]
          
          toteuma-hk1 (lupaus-domain/kustannusennuste->toteuma 
                        {:lupaus-kuukaudet lupaus-kuukaudet
                         :hoitovuosi-paattynyt? true
                         :hoitovuosi-nro 1
                         :urakan-alkuvuosi 2024})
          
          toteuma-hk2 (lupaus-domain/kustannusennuste->toteuma 
                        {:lupaus-kuukaudet lupaus-kuukaudet
                         :hoitovuosi-paattynyt? true
                         :hoitovuosi-nro 2
                         :urakan-alkuvuosi 2024})
          
          toteuma-hk3 (lupaus-domain/kustannusennuste->toteuma 
                        {:lupaus-kuukaudet lupaus-kuukaudet
                         :hoitovuosi-paattynyt? true
                         :hoitovuosi-nro 3
                         :urakan-alkuvuosi 2024})]
      
      (is (= 8 toteuma-hk1) "HK1: 8")
      (is (= 9 toteuma-hk2) "HK2: 9")
      (is (= 7 toteuma-hk3) "HK3: 7"))))

