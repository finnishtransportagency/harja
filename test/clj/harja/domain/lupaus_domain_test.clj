(ns harja.domain.lupaus-domain-test
  (:require [clojure.test :refer :all]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]))

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

(deftest bonus-tai-sanktio-19-20-urakalle-test
  (is (= {:tavoite-taytetty true}
         (lupaus-domain/bonus-tai-sanktio-19-20-urakalle {:lupaus 100 :toteuma 100 :tavoitehinta 1000})))
  (is (= {:bonus 13.0}
         (lupaus-domain/bonus-tai-sanktio-19-20-urakalle {:lupaus 90 :toteuma 100 :tavoitehinta 1000})))
  (is (= {:sanktio -33.0}
         (lupaus-domain/bonus-tai-sanktio-19-20-urakalle {:lupaus 100 :toteuma 90 :tavoitehinta 1000}))
      "Legacy-polku odottaa sanktion negatiivisena, jotta indeksikorjaus laskee 2019/2020-urakoille oikein.")
  (is (= {:bonus 5200.0}
         (lupaus-domain/bonus-tai-sanktio-19-20-urakalle {:lupaus 76 :toteuma 78 :tavoitehinta 2000000})))
  (is (= {:sanktio -13200.0}
         (lupaus-domain/bonus-tai-sanktio-19-20-urakalle {:lupaus 76 :toteuma 74 :tavoitehinta 2000000}))
      "Legacy-sanktio säilyy negatiivisena vanhaa kuukausittaisten pisteiden indeksikorjauspolkua varten.")
  (is (nil? (lupaus-domain/bonus-tai-sanktio-19-20-urakalle {})))
  (is (nil? (lupaus-domain/bonus-tai-sanktio-19-20-urakalle nil))))

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

(deftest laske-lupauspaatos-bonus-tai-sanktio-test
  (testing "Tavoite täytetty kun pisteet täsmäävät"
    (is (= {:tavoite-taytetty true}
           (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
             {:toteutuneet-pisteet 100
              :luvatut-pisteet 100
              :tavoitehinta 1000000
              :sanktioprosentti 0.33
              :bonusprosentti 0.13}))))

  (testing "Bonus lasketaan oikein kun toteutuneet > luvatut"
    ;; Bonus = (bonusprosentti / 100) × tavoitehinta × (toteutuneet - luvatut)
    ;; (0.13 / 100) × 1000000 × (105 - 100) = 0.0013 × 1000000 × 5 = 6500
    (is (= {:lupausbonus 6500.0}
           (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
             {:toteutuneet-pisteet 105
              :luvatut-pisteet 100
              :tavoitehinta 1000000
              :sanktioprosentti 0.33
              :bonusprosentti 0.13}))))

  (testing "Sanktio lasketaan oikein kun toteutuneet < luvatut"
    ;; Sanktio = (sanktioprosentti / 100) × tavoitehinta × (luvatut - toteutuneet)
    ;; (0.33 / 100) × 1000000 × (100 - 95) = 0.0033 × 1000000 × 5 = 16500
    (is (= {:lupaussanktio 16500.0}
           (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
             {:toteutuneet-pisteet 95
              :luvatut-pisteet 100
              :tavoitehinta 1000000
              :sanktioprosentti 0.33
              :bonusprosentti 0.13}))))

  (testing "Todelliset laskentaesimerkit välikatselmuksesta"
    ;; Esimerkki 1: Bonus 2 pisteellä, tavoitehinta 2M€
    ;; (0.13 / 100) × 2000000 × 2 = 5200
    (is (= {:lupausbonus 5200.0}
           (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
             {:toteutuneet-pisteet 78
              :luvatut-pisteet 76
              :tavoitehinta 2000000
              :sanktioprosentti 0.33
              :bonusprosentti 0.13})))

    ;; Esimerkki 2: Sanktio 2 pisteellä, tavoitehinta 2M€
    ;; (0.33 / 100) × 2000000 × 2 = 13200
    (is (= {:lupaussanktio 13200.0}
           (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
             {:toteutuneet-pisteet 74
              :luvatut-pisteet 76
              :tavoitehinta 2000000
              :sanktioprosentti 0.33
              :bonusprosentti 0.13})))

    ;; Esimerkki 3: 2025 alkaen (alhaisempi sanktio%)
    ;; (0.18 / 100) × 2000000 × 2 = 7200
    (is (= {:lupaussanktio 7200.0}
           (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
             {:toteutuneet-pisteet 74
              :luvatut-pisteet 76
              :tavoitehinta 2000000
              :sanktioprosentti 0.18
              :bonusprosentti 0.13}))))

  (testing "Palauttaa nil kun pakolliset parametrit puuttuvat"
    (is (nil? (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio {})))
    (is (nil? (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio nil)))
    (is (nil? (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
                {:toteutuneet-pisteet 100
                 :luvatut-pisteet 100})))
    (is (nil? (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
                {:tavoitehinta 1000000
                 :sanktioprosentti 0.33
                 :bonusprosentti 0.13}))))

  (testing "Palauttaa nil kun tavoitehinta on nolla tai negatiivinen"
    (is (nil? (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
                {:toteutuneet-pisteet 100
                 :luvatut-pisteet 100
                 :tavoitehinta 0
                 :sanktioprosentti 0.33
                 :bonusprosentti 0.13})))
    (is (nil? (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
                {:toteutuneet-pisteet 100
                 :luvatut-pisteet 100
                 :tavoitehinta -1000
                 :sanktioprosentti 0.33
                 :bonusprosentti 0.13})))))

(deftest paatos->bonus-tai-sanktio-test
  (testing "Muuntaa tietokannasta haetun päätöksen API-muotoon oikein"
    (is (= {:bonus 5200M}
           (lupaus-domain/paatos->bonus-tai-sanktio
             {:tyyppi "bonus"
              :lupausbonus 5200M
              :lupaussanktio nil}))
        "Bonus palautetaan positiivisena")
    
    (is (= {:sanktio 13200M}
           (lupaus-domain/paatos->bonus-tai-sanktio
             {:tyyppi "sanktio"
              :lupausbonus nil
              :lupaussanktio 13200M}))
        "Sanktio palautetaan positiivisena API-muodossa")
    
    (is (nil? (lupaus-domain/paatos->bonus-tai-sanktio
                {:tyyppi "tuntematon"
                 :lupausbonus 100M
                 :lupaussanktio 200M}))
        "Palauttaa nil tuntemattomalle tyypille")

    (is (= {:tavoite-taytetty true}
         (lupaus-domain/paatos->bonus-tai-sanktio
         {:tyyppi "taytetty"
          :lupausbonus nil
          :lupaussanktio nil}))
      "Tavoite täytetty muunnetaan API-muotoon oikein")
    
    (is (nil? (lupaus-domain/paatos->bonus-tai-sanktio nil))
        "Palauttaa nil kun päätös on nil")))


