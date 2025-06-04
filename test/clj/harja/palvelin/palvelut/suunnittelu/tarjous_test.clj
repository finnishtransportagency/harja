(ns harja.palvelin.palvelut.suunnittelu.tarjous-test
  (:require [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.yleiset :refer :all]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :budjetoidut-tyot (component/using
                              (bs/->Budjettisuunnittelu)
                              [:http-palvelin :db])
          :tarjous (component/using
                     (tarjous-palvelu/->Tarjous)
                     [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(def tarjous-tietomalli {:tarjous [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                                   ;; Rahavaraukset
                                   {:nimi "Äkilliset hoitotyöt", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 1
                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                                   {:nimi "Vahinkojen korjaukset", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 2
                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                                   {:nimi "Tilaajan rahavaraus kannustinjärjestelmään", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 3
                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}

                                   ;; Erillishankinnat
                                   {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 28 :rahavaraus-id nil
                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}

                                   ;; Johto ja hallintokorvaukset eli toimenkuvat
                                   {:nimi "Vastuunalainen työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 2 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                                   {:nimi "Apulainen/työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 4 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                                   {:nimi "Valmistelukausi ennen urakka-ajan alkua", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 10 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}

                                   ;; Hoidonjohtopalkkio
                                   {:nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 3061 :tehtavaryhma-id nil :rahavaraus-id nil
                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                                   ;; Yhteensä rivi
                                   {:nimi "Yhteensä tavoitehinta", :osio "yhteensa"
                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 90.00} {:vuosi 2024 :summa 180.00} {:vuosi 2025 :summa 270.00}], :yhteensa 540.00}]})

(defn muodosta-tarjous-rahavarauksista [rahavaraukset vuodet]
  {:tarjous (mapv
              (fn [rahavaraus]
                {:nimi (:nimi rahavaraus)
                 :osio "tavoitehintaiset-rahavaraukset"
                 :toimenkuva-id nil
                 :tehtava-id nil
                 :tehtavaryhma-id nil
                 :rahavaraus-id (:id rahavaraus)
                 :hoitovuosittaiset-arvot (mapv
                                            (fn [vuosi]
                                              {:vuosi (:vuosi vuosi) :summa (rand-int 1000)}) ;; Generoidaan satunnaiset summat
                                            vuodet)})
              rahavaraukset)})

(defn muodosta-tarjous-toimenkuvista [toimenkuvat vuodet]
  {:tarjous (mapv
              (fn [toimenkuva]
                {:nimi (:nimi toimenkuva)
                 :osio (:osio toimenkuva)
                 :toimenkuva-id (:toimenkuva-id toimenkuva)
                 :tehtava-id (:tehtava-id toimenkuva)
                 :tehtavaryhma-id (:tehtavaryhma-id toimenkuva)
                 :rahavaraus-id (:rahavaraus-id toimenkuva)
                 :hoitovuosittaiset-arvot (mapv
                                            (fn [vuosi]
                                              {:vuosi (:vuosi vuosi) :summa (rand-int 1000)}) ;; Generoidaan satunnaiset summat
                                            vuodet)})
              toimenkuvat)})

(deftest tallenna-yksinkertainen-tarjous-tietokantaan-onnistuneesti
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1
        vuosittaiset-tarjoushinnat (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id kattohintakerroin tarjous-tietomalli)
        tarjoukset-tietokannasta (q-map "SELECT * from tarjous")]
    (is (= (count tarjoukset-tietokannasta) (count vuosittaiset-tarjoushinnat)))))

(deftest tallenna-rahavaraukset-tarjoukselle-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista tarjous-tietomalli)
        tarjous (muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        vuosittaiset-tarjoushinnat (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id kattohintakerroin tarjous)
        tarjoukset-tietokannasta (q-map "SELECT * from tarjous")
        tietokantarahavaraukset (q-map (format "SELECT * from tarjous_kustannukset
                                                 WHERE osio = 'tavoitehintaiset-rahavaraukset'
                                                   AND urakka_id = %s" urakka-id))]

    (is (= (count tarjoukset-tietokannasta) (count vuosittaiset-tarjoushinnat)))
    (is (= (count tietokantarahavaraukset) (* (count vuodet) (count rahavaraukset))) "Tietokannasta löytyy rahavaraukset jokaiselle vuodelle.")))

(deftest tallenna-rahavaraukset-ja-hae-kustannukset-tarjoukselle-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista tarjous-tietomalli)
        tarjous (muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        ;; Tallenna tarjous kantaan
        vuosittaiset-tarjoushinnat (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id kattohintakerroin tarjous)
        ;; Hae tarjous tietokannasta
        tarjous-rivit-tietokannasta (:tarjous (tarjous-kyselyt/hae-tarjous db urakka-id))]

    (is (= (count (butlast tarjous-rivit-tietokannasta)) (count rahavaraukset)) "Rahavarausten lisäksi tarjous palauttaa yhteenvetorivin.")))

(deftest tallenna-laajat-kustannukset-ja-hae-kustannukset-tarjoukselle-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista tarjous-tietomalli)
        tarjous (muodosta-tarjous-rahavarauksista rahavaraukset vuodet)

        ;; Lisätään rahavarausten lisäksi myös muita kustannuksia
        tarjous (update tarjous :tarjous (fn [rivit]
                                           (vec (concat rivit
                                                  [{:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 28 :rahavaraus-id nil
                                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                                                   {:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                                                   {:nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 3061 :tehtavaryhma-id nil :rahavaraus-id nil
                                                    :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}]))))
        ;; Tallenna tarjous kantaan
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id kattohintakerroin tarjous)
        ;; Hae tarjous tietokannasta
        tarjoukset-tietokannasta (:tarjous (tarjous-kyselyt/hae-tarjous db urakka-id))]
    ;; Varmistetaan, että tietokannasta löytyy oikea määrä rivejä
    (is (= (count (butlast tarjoukset-tietokannasta)) (+ 3 (count rahavaraukset))))))

(deftest tallenna-hankintoja-tarjoukselle-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Muodostetaan hankintakustannuksia, joilla voi testata tallennuksia
        hankinnat [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}
                   {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 28 :rahavaraus-id nil}
                   {:nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 3061 :tehtavaryhma-id nil :rahavaraus-id nil}]

        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista tarjous-tietomalli)
        tarjous {:tarjous (mapv
                            (fn [hankinta]
                              {:nimi (:nimi hankinta)
                               :osio (:osio hankinta)
                               :toimenkuva-id (:toimenkuva-id hankinta)
                               :tehtava-id (:tehtava-id hankinta)
                               :tehtavaryhma-id (:tehtavaryhma-id hankinta)
                               :rahavaraus-id (:rahavaraus-id hankinta)
                               :hoitovuosittaiset-arvot (mapv
                                                          (fn [vuosi]
                                                            {:vuosi (:vuosi vuosi) :summa (rand-int 1000)}) ;; Generoidaan satunnaiset summat
                                                          vuodet)})
                            hankinnat)}

        vuosittaiset-tarjoushinnat (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id kattohintakerroin tarjous)
        tarjoukset-tietokannasta (q-map "SELECT * from tarjous")
        tietokantakustannukset (q-map (format "SELECT * from tarjous_kustannukset
                                                 WHERE osio IN ('hankintakustannukset', 'erillishankinnat', 'hoidonjohtopalkkio')
                                                   AND urakka_id = %s" urakka-id))]

    (is (= (count tarjoukset-tietokannasta) (count vuosittaiset-tarjoushinnat)))
    (is (= (count tietokantakustannukset) (* (count vuodet) (count hankinnat))) "Tietokannasta löytyy hankinnat jokaiselle vuodelle.")))

(deftest tallenna-johto-ja-hallintokorvaukset-tarjoukselle-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Muodostetaan johto-ja-hallinto-kustannuksia, joilla voi testata tallennuksia
        ;; Muodostetaan hankintakustannuksia, joilla voi testata tallennuksia
        johto-ja-hallintokorvaukset [{:nimi "Valmistelukausi ennen urakka-ajan alkua", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 10 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}
                                     {:nimi "Vastuunalainen työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 2 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}
                                     {:nimi "Päätoiminen apulainen / työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 4 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}]


        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista tarjous-tietomalli)
        tarjous (muodosta-tarjous-toimenkuvista johto-ja-hallintokorvaukset vuodet)

        vuosittaiset-tarjoushinnat (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id kattohintakerroin tarjous)
        tarjoukset-tietokannasta (q-map "SELECT * from tarjous")
        tietokantajohto-ja-hallintokorvaukset (q-map (format "SELECT * from tarjous_johto_ja_hallintokorvaus
                                                 WHERE osio = 'johto-ja-hallintokorvaus'
                                                   AND urakka_id = %s" urakka-id))]

    (is (= (count tarjoukset-tietokannasta) (count vuosittaiset-tarjoushinnat)))
    (is (= (count tietokantajohto-ja-hallintokorvaukset) (* (count vuodet) (count johto-ja-hallintokorvaukset))) "Tietokannasta löytyy johto-ja-hallintokorvaukset jokaiselle vuodelle.")))

(deftest tallenna-ja-hae-johto-ja-hallintokorvaukset-tarjoukselle-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Muodostetaan johto-ja-hallinto-kustannuksia, joilla voi testata tallennuksia
        ;; Muodostetaan hankintakustannuksia, joilla voi testata tallennuksia
        johto-ja-hallintokorvaukset [{:nimi "Valmistelukausi ennen urakka-ajan alkua", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 10 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}
                                     {:nimi "Vastuunalainen työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 2 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}
                                     {:nimi "Päätoiminen apulainen / työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 4 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}]


        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista tarjous-tietomalli)
        tarjous (muodosta-tarjous-toimenkuvista johto-ja-hallintokorvaukset vuodet)

        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id kattohintakerroin tarjous)
        tarjoukset-tietokannasta (:tarjous (tarjous-kyselyt/hae-tarjous db urakka-id))]

    (is (= (count (butlast tarjoukset-tietokannasta)) (count johto-ja-hallintokorvaukset)))))

;; Rajanpintatestit
(deftest tallenna-tarjous-rajapinnasta-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tarjous (assoc tarjous-tietomalli :urakka-id urakka-id)
        ;; Testi failaa, jos tämän kommentoinnin ottaa pois.
        ;tarjous (assoc-in tarjous [:tarjous 0 :hoitovuosittaiset-arvot 0 :summa] 100M) ;; Muutetaan ensimmäisen rivin summa
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ tarjous)]
    (is (= (:tarjous vastaus) (:tarjous tarjous-tietomalli)))))

(deftest muokkaa-tarjous-rajapinnasta-onnistuu
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        tarjous (assoc tarjous-tietomalli :urakka-id urakka-id)
        uusi-summa 500M
        ;; Tallennetaan tarjous ensin
        ensivastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ tarjous)
        ensivastaus-kustannus (first (:tarjous ensivastaus))

        ;; Muokataan tarjousta
        muokattu-tarjous (assoc-in tarjous [:tarjous 0 :hoitovuosittaiset-arvot 0 :summa] uusi-summa)
        muokkausvastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ muokattu-tarjous)
        muokkausvastaus-kustannus (first (:tarjous muokkausvastaus))]
    (is (= 10.00 (get-in ensivastaus-kustannus [:hoitovuosittaiset-arvot 0 :summa])))
    (is (= uusi-summa (bigdec (get-in muokkausvastaus-kustannus [:hoitovuosittaiset-arvot 0 :summa]))))
    ;; Ensivastauksen rivimäärä on sama
    (is (= (count (:tarjous tarjous-tietomalli)) (count (:tarjous ensivastaus))))
    ;; Muokkausvastauksen rivimäärä on sama
    (is (= (count (:tarjous tarjous-tietomalli)) (count (:tarjous muokkausvastaus))))))
