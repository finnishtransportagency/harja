(ns harja.palvelin.palvelut.suunnittelu.tarjous-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.yleiset :refer :all]
            [harja.palvelin.palvelut.suunnittelu.apurit :as apurit]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as uusi-kust-kyselyt]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhmat-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodi-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]))

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
          :uusi-kustannussuunnitelma (component/using
                                       (kust-palvelu/->UusiKustannussuunnitelmaPalvelu)
                                       [:http-palvelin :db])
          :tarjous (component/using
                     (tarjous-palvelu/->Tarjous)
                     [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

;; Helpperit

(defn generoi-toimenkuville-vuosisummat [toimenkuvat vuodet]
  {:tarjous (mapv
              (fn [toimenkuva]
                (let [hoitovuosittaiset-arvot (vec
                                                (remove nil?
                                                  (map-indexed
                                                    ;; Generoidaan satunnaiset summat
                                                    (fn [indeksi vuosi]
                                                      (cond
                                                        (and (= 0 indeksi) (= "valmistelukausi ennen urakka-ajan alkua" (str/lower-case (:toimenkuva toimenkuva)))) {:vuosi (:vuosi vuosi) :summa (round2 1 (rand-int 1000))}
                                                        (and (< 0 indeksi) (= "valmistelukausi ennen urakka-ajan alkua" (str/lower-case (:toimenkuva toimenkuva)))) nil
                                                        :else {:vuosi (:vuosi vuosi) :summa (round2 1 (rand-int 1000))}))
                                                    vuodet)))
                      yhteensa (reduce + (map #(:summa % 0) hoitovuosittaiset-arvot))
                      toimenkuva (merge toimenkuva
                                   {:hoitovuosittaiset-arvot hoitovuosittaiset-arvot
                                    :yhteensa yhteensa})]
                  toimenkuva))
              toimenkuvat)})

(defn lisaa-ja-generoi-toimenkuvat [db urakka-id urakan-alkuvuosi tietomallitarjous hoitovuosittaiset-arvot]
  (let [;; Lisää tarjoukselle urakkavuoden mukaiset toimenkuvat
        kaikki-urakan-toimenkuvat (tarjous-kyselyt/hae-urakan-toimenkuvat db urakka-id urakan-alkuvuosi hoitovuosittaiset-arvot)

        tarjous (generoi-toimenkuville-vuosisummat kaikki-urakan-toimenkuvat hoitovuosittaiset-arvot)
        tarjousrivit (concat (:tarjous tietomallitarjous) (:tarjous tarjous))
        ;; Poistetaan yhteensä rivi
        tarjousrivit (filter #(not= "yhteensa" (:osio %)) tarjousrivit)
        ;; Lisätään uusi yhteensä rivi
        tarjous (tarjous-kyselyt/lisaa-yhteenvetorivi-tarjoukseen {:tarjous tarjousrivit})]
    tarjous))

(defn ota-toimenkuvat-ja-poista-id [tarjous]
  (map #(dissoc % :id) ;; Id ei aina testeissä oikein täsmää
    (filter (fn [rivi]
              (= (:osio rivi) "johto-ja-hallintokorvaus"))
      (:tarjous tarjous))))

(deftest tallenna-yksinkertainen-tarjous-tietokantaan-onnistuneesti
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        vahvistetut-vuodet #{}
        tehtavaryhma-erillishankinnat (first (tehtavaryhmat-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        tietomalli (apurit/paivita-tarjoustietomallin-idt apurit/tarjous-tietomalli-2019 tehtavaryhma-erillishankinnat tehtava-hoidonjohtopalkkio)
        vuosittaiset-tarjoushinnat (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tietomalli vahvistetut-vuodet)
        tarjoukset-tietokannasta (q-map (format "SELECT * from tarjous WHERE urakka_id = %s" urakka-id))]
    (is (= (count tarjoukset-tietokannasta) (count vuosittaiset-tarjoushinnat)))))

(deftest tallenna-rahavaraukset-tarjoukselle-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        vahvistetut-vuodet #{}
        vuosittaiset-tarjoushinnat (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tarjous vahvistetut-vuodet)
        tarjoukset-tietokannasta (q-map (format "SELECT * from tarjous WHERE urakka_id = %s" urakka-id))
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
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        ;; Tallenna tarjous kantaan
        vahvistetut-vuodet #{}
        vuosittaiset-tarjoushinnat (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tarjous vahvistetut-vuodet)
        ;; Hae tarjous tietokannasta
        tarjousdb (tarjous-kyselyt/hae-tarjous db urakka-id)
        tarjous-rivit-tietokannasta (filter #(contains? #{"tavoitehintaiset-rahavaraukset"} (:osio %)) (:tarjous tarjousdb))]

    (is (= (count tarjous-rivit-tietokannasta) (count rahavaraukset)) "Rahavarausten lisäksi tarjous palauttaa yhteenvetorivin.")))

(deftest tallenna-laajat-kustannukset-ja-hae-kustannukset-tarjoukselle-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset db {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)

        ;; Lisätään rahavarausten lisäksi myös muita kustannuksia
        tarjous (update tarjous :tarjous (fn [rivit]
                                           (vec (concat rivit
                                                  [{:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 380 :rahavaraus-id nil
                                                    :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                                                   {:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                                    :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                                                   {:nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 3061 :tehtavaryhma-id nil :rahavaraus-id nil
                                                    :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}]))))
        tehtavaryhma-erillishankinnat (first (tehtavaryhmat-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        tarjous (apurit/paivita-tarjoustietomallin-idt tarjous tehtavaryhma-erillishankinnat tehtava-hoidonjohtopalkkio)
        ;; Tallenna tarjous kantaan
        vahvistetut-vuodet #{}
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tarjous vahvistetut-vuodet)
        ;; Hae tarjous tietokannasta
        tarjousdb (tarjous-kyselyt/hae-tarjous db urakka-id)
        tarjoukset-tietokannasta (filter #(contains? #{"tavoitehintaiset-rahavaraukset" "erillishankinnat" "hoidonjohtopalkkio" "hankintakustannukset"} (:osio %)) (:tarjous tarjousdb))]
    ;; Varmistetaan, että tietokannasta löytyy oikea määrä rivejä
    (is (= (count tarjoukset-tietokannasta) (+ 3 (count rahavaraukset))))))

(deftest tallenna-hankintoja-tarjoukselle-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        ;; Muodostetaan hankintakustannuksia, joilla voi testata tallennuksia
        hankinnat [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}
                   {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 380 :rahavaraus-id nil}
                   {:nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 3061 :tehtavaryhma-id nil :rahavaraus-id nil}]
        tehtavaryhma-erillishankinnat (first (tehtavaryhmat-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        hankinnat (:tarjous (apurit/paivita-tarjoustietomallin-idt {:tarjous hankinnat} tehtavaryhma-erillishankinnat tehtava-hoidonjohtopalkkio))

        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
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

        vahvistetut-vuodet #{}
        vuosittaiset-tarjoushinnat (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tarjous vahvistetut-vuodet)
        tarjoukset-tietokannasta (q-map (format "SELECT * from tarjous WHERE urakka_id = %s" urakka-id))
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
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        ;; Muodostetaan johto-ja-hallinto-kustannuksia, joilla voi testata tallennuksia
        ;; Muodostetaan hankintakustannuksia, joilla voi testata tallennuksia
        johto-ja-hallintokorvaukset [{:toimenkuva "valmistelukausi ennen urakka-ajan alkua" :nimi "Valmistelukausi ennen urakka-ajan alkua", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 10 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}
                                     {:toimenkuva "vastuunalainen työnjohtaja" :nimi "Vastuunalainen työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 2 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}
                                     {:toimenkuva "päätoiminen apulainen / työnjohtaja" :nimi "Päätoiminen apulainen / työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 4 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}]


        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
        tarjous (generoi-toimenkuville-vuosisummat johto-ja-hallintokorvaukset vuodet)

        vahvistetut-vuodet #{}
        vuosittaiset-tarjoushinnat (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tarjous vahvistetut-vuodet)
        tarjoukset-tietokannasta (q-map (format "SELECT * from tarjous WHERE urakka_id = %s" urakka-id))
        tietokantajohto-ja-hallintokorvaukset (q-map (format "SELECT * from tarjous_johto_ja_hallintokorvaus
                                                 WHERE osio = 'johto-ja-hallintokorvaus'
                                                   AND urakka_id = %s" urakka-id))]

    (is (= (count tarjoukset-tietokannasta) (count vuosittaiset-tarjoushinnat)))
    (is (= (count tietokantajohto-ja-hallintokorvaukset) 11) "Tietokannasta löytyy johto-ja-hallintokorvaukset jokaiselle vuodelle. Paitsi valmistelukausi ennen urakka-ajan alkua on vain yhdessä vuodessa.")))

(deftest tallenna-ja-hae-johto-ja-hallintokorvaukset-tarjoukselle-2019-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        kayttaja-id (:id +kayttaja-jvh+)

        ;; Muodostetaan johto-ja-hallinto-kustannuksia, joilla voi testata tallennuksia
        ;; Muodostetaan hankintakustannuksia, joilla voi testata tallennuksia
        muokattavat-jjh-korvaukset [{:nimi "Sopimusvastaava" :toimenkuva "sopimusvastaava" :osio "johto-ja-hallintokorvaus" :toimenkuva-id 1 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}
                                    {:nimi "Vastuunalainen työnjohtaja", :toimenkuva "Vastuunalainen työnjohtaja" :osio "johto-ja-hallintokorvaus" :toimenkuva-id 2 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}
                                    {:nimi "Viherhoidosta vastaava henkilö", :toimenkuva "viherhoidosta vastaava henkilö" :osio "johto-ja-hallintokorvaus" :toimenkuva-id 3 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil}]

        tarjous (generoi-toimenkuville-vuosisummat muokattavat-jjh-korvaukset hoitovuosittaiset-arvot)
        vahvistetut-vuodet #{}
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tarjous vahvistetut-vuodet)
        tarjous-tietokannasta (:tarjous (tarjous-kyselyt/hae-tarjous db urakka-id))

        ;; Haetaan default toimenkuvat tietokannasta
        urakan-toimenkuvat (tarjous-kyselyt/hae-urakan-toimenkuvat db urakka-id urakan-alkuvuosi hoitovuosittaiset-arvot)

        toimenkuvat-tarjouksesta (filter #(contains? #{"johto-ja-hallintokorvaus"} (:osio %)) tarjous-tietokannasta)]

    (is (= (count toimenkuvat-tarjouksesta) (count urakan-toimenkuvat)))))

(deftest tallenna-ja-hae-johto-ja-hallintokorvaukset-tarjoukselle-2025-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        kayttaja-id (:id +kayttaja-jvh+)

        kaikki-urakan-toimenkuvat (tarjous-kyselyt/hae-urakan-toimenkuvat db urakka-id urakan-alkuvuosi hoitovuosittaiset-arvot)

        tarjous (generoi-toimenkuville-vuosisummat kaikki-urakan-toimenkuvat hoitovuosittaiset-arvot)
        vahvistetut-vuodet #{}
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tarjous vahvistetut-vuodet)
        tarjous-tietokannasta (:tarjous (tarjous-kyselyt/hae-tarjous db urakka-id))

        ;; Haetaan default toimenkuvat tietokannasta
        urakan-toimenkuvat (tarjous-kyselyt/hae-urakan-toimenkuvat db urakka-id (pvm/vuosi (:alkupvm urakan-tiedot)) hoitovuosittaiset-arvot)

        toimenkuvat-tarjouksesta (filter #(contains? #{"johto-ja-hallintokorvaus"} (:osio %)) tarjous-tietokannasta)]

    (is (= (count toimenkuvat-tarjouksesta) (count urakan-toimenkuvat)))
    (is (= (:hoitovuosittaiset-arvot (first toimenkuvat-tarjouksesta)) (:hoitovuosittaiset-arvot (first urakan-toimenkuvat))))))

(deftest muokkaa-johto-ja-hallintokorvaukset-tarjoukselle-2025-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        kayttaja-id (:id +kayttaja-jvh+)

        kaikki-urakan-toimenkuvat (tarjous-kyselyt/hae-urakan-toimenkuvat db urakka-id urakan-alkuvuosi hoitovuosittaiset-arvot)

        tarjous (generoi-toimenkuville-vuosisummat kaikki-urakan-toimenkuvat hoitovuosittaiset-arvot)
        vahvistetut-vuodet #{}
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tarjous vahvistetut-vuodet)
        tarjous-tietokannasta (:tarjous (tarjous-kyselyt/hae-tarjous db urakka-id))

        ;; Haetaan default toimenkuvat tietokannasta
        urakan-toimenkuvat (tarjous-kyselyt/hae-urakan-toimenkuvat db urakka-id (pvm/vuosi (:alkupvm urakan-tiedot)) hoitovuosittaiset-arvot)

        toimenkuvat-tarjouksesta (filter #(contains? #{"johto-ja-hallintokorvaus"} (:osio %)) tarjous-tietokannasta)

        ;; Varmistetaan, että tallennuksen jälkeen summat ovat oikein
        _ (is (= (count toimenkuvat-tarjouksesta) (count urakan-toimenkuvat)))
        _ (is (= (:hoitovuosittaiset-arvot (second toimenkuvat-tarjouksesta)) (:hoitovuosittaiset-arvot (second urakan-toimenkuvat))))

        ;; Muokataan toisen toimenkuvan summia - Ensimmäinen on vain ennen urakkakauden alkua, joten se on vähä poikkeus
        vuosisumma 999.0
        muokatut-toimenkuvat (mapv (fn [toimenkuva]
                                     (if (= (:toimenkuva-id toimenkuva) (:toimenkuva-id (second toimenkuvat-tarjouksesta)))
                                       (assoc toimenkuva :hoitovuosittaiset-arvot (mapv (fn [vuosi]
                                                                                          (if (= (:vuosi vuosi) urakan-alkuvuosi)
                                                                                            (assoc vuosi :summa vuosisumma)
                                                                                            vuosi))
                                                                                    (:hoitovuosittaiset-arvot toimenkuva)))
                                       toimenkuva))
                               toimenkuvat-tarjouksesta)

        ;; Poista vanhat toimenkuvat
        muokattu-tarjous (remove #(contains? #{"johto-ja-hallintokorvaus"} (:osio %)) tarjous-tietokannasta)
        ;; Päivitä tilalle muokatut
        muokattu-tarjous (vec (concat muokattu-tarjous muokatut-toimenkuvat))

        ;; Tallennetaan muokatut toimenkuvat
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id {:tarjous muokattu-tarjous} vahvistetut-vuodet)
        muokattu-tarjous-tietokannasta (:tarjous (tarjous-kyselyt/hae-tarjous db urakka-id))

        muokatut-toimenkuvat-tarjouksesta (filter #(contains? #{"johto-ja-hallintokorvaus"} (:osio %)) muokattu-tarjous-tietokannasta)
        _ (is (= vuosisumma (:summa (first (:hoitovuosittaiset-arvot (second muokatut-toimenkuvat-tarjouksesta))))))]))

(deftest poista-toimenkuva-2025-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)

        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        kayttaja-id (:id +kayttaja-jvh+)

        kaikki-urakan-toimenkuvat (tarjous-kyselyt/hae-urakan-toimenkuvat db urakka-id urakan-alkuvuosi hoitovuosittaiset-arvot)

        tarjous (generoi-toimenkuville-vuosisummat kaikki-urakan-toimenkuvat hoitovuosittaiset-arvot)
        vahvistetut-vuodet #{}
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tarjous vahvistetut-vuodet)
        tarjous-tietokannasta (:tarjous (tarjous-kyselyt/hae-tarjous db urakka-id))
        toimenkuvat-tarjouksesta (filter #(contains? #{"johto-ja-hallintokorvaus"} (:osio %)) tarjous-tietokannasta)
        ;; Merkitään yksi poistetuksi
        muokatut-toimenkuvat (mapv (fn [toimenkuva]
                                     (if (= (:toimenkuva-id toimenkuva) (:toimenkuva-id (second toimenkuvat-tarjouksesta)))
                                       (assoc toimenkuva :poistettu true)
                                       toimenkuva))
                               toimenkuvat-tarjouksesta)
        ;; Poista vanhat toimenkuvat
        muokattu-tarjous (remove #(contains? #{"johto-ja-hallintokorvaus"} (:osio %)) tarjous-tietokannasta)
        ;; Päivitä tilalle muokatut
        muokattu-tarjous (vec (concat muokattu-tarjous muokatut-toimenkuvat))
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id {:tarjous muokattu-tarjous} vahvistetut-vuodet)
        muokattu-tarjous-tietokannasta (:tarjous (tarjous-kyselyt/hae-tarjous db urakka-id))
        muokatut-toimenkuvat-tarjouksesta (filter #(contains? #{"johto-ja-hallintokorvaus"} (:osio %)) muokattu-tarjous-tietokannasta)]

    ;; Toimenkuvia on poiston jälkeen yksi vähemmän
    (is (= (dec (count toimenkuvat-tarjouksesta)) (count muokatut-toimenkuvat-tarjouksesta)))))


;; Rajanpintatestit
(deftest tallenna-tarjous-2021-urakalle-rajapinnasta-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        tietomallitarjous (assoc apurit/tarjous-tietomalli-2019 :urakka-id urakka-id)
        tehtavaryhma-erillishankinnat (first (tehtavaryhmat-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        tietomallitarjous (apurit/paivita-tarjoustietomallin-idt tietomallitarjous tehtavaryhma-erillishankinnat tehtava-hoidonjohtopalkkio)
        ;; Lisätään urakalle kuuluvat toimenkuvat ja niille generoidut summat
        tietomallitarjous (merge
                            {:urakka-id urakka-id}
                            (lisaa-ja-generoi-toimenkuvat db urakka-id urakan-alkuvuosi tietomallitarjous hoitovuosittaiset-arvot))
        hankinta-osiot #{"hankintakustannukset", "erillishankinnat", "hoidonjohtopalkkio", "tavoitehintaiset-rahavaraukset"}
        hoidonjohtopalkkio-tietomallista (filter #(= (:osio %) "hoidonjohtopalkkio") (:tarjous tietomallitarjous))
        hankinnat-tietomallista (filter #(contains? hankinta-osiot (:osio %)) (:tarjous tietomallitarjous))
        toimenkuvat-tietomallista (map #(dissoc % :id :jarjestys) ;; Id ei aina testeissä oikein täsmää
                                    (filter (fn [rivi]
                                              (= (:osio rivi) "johto-ja-hallintokorvaus"))
                                      (:tarjous tietomallitarjous)))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ tietomallitarjous)
        ;; Id ja järjestys ei aina testeissä oikein täsmää
        hoidonjohtopalkkio-vastauksesta (map #(dissoc % :id :jarjestys) (filter #(= (:osio %) "hoidonjohtopalkkio") (:tarjous vastaus)))
        hankinnat-vastauksesta (map #(dissoc % :id :jarjestys) (filter #(contains? hankinta-osiot (:osio %)) (:tarjous vastaus)))
        toimenkuvat-vastauksesta (map #(dissoc % :id :jarjestys)
                                   (filter (fn [rivi]
                                             (= (:osio rivi) "johto-ja-hallintokorvaus"))
                                     (:tarjous vastaus)))]
    (is (= hoidonjohtopalkkio-tietomallista hoidonjohtopalkkio-vastauksesta))
    (is (= toimenkuvat-tietomallista toimenkuvat-vastauksesta))
    (is (= hankinnat-tietomallista hankinnat-vastauksesta))))

(deftest tallenna-tarjous-2025-urakalle-rajapinnasta-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)

        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        tietomallitarjous (assoc apurit/tarjous-tietomalli-2025 :urakka-id urakka-id)
        tehtavaryhma-erillishankinnat (first (tehtavaryhmat-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        tietomallitarjous (apurit/paivita-tarjoustietomallin-idt tietomallitarjous tehtavaryhma-erillishankinnat tehtava-hoidonjohtopalkkio)
        ;; Lisätään urakalle kuuluvat toimenkuvat ja niille generoidut summat
        tietomallitarjous (merge
                            {:urakka-id urakka-id}
                            (lisaa-ja-generoi-toimenkuvat db urakka-id urakan-alkuvuosi tietomallitarjous hoitovuosittaiset-arvot))
        hankinta-osiot #{"hankintakustannukset", "erillishankinnat", "hoidonjohtopalkkio", "tavoitehintaiset-rahavaraukset"}
        hoidonjohtopalkkio-tietomallista (filter #(= (:osio %) "hoidonjohtopalkkio") (:tarjous tietomallitarjous))
        hankinnat-tietomallista (filter #(contains? hankinta-osiot (:osio %)) (:tarjous tietomallitarjous))
        toimenkuvat-tietomallista (ota-toimenkuvat-ja-poista-id tietomallitarjous)

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ tietomallitarjous)
        hoidonjohtopalkkio-vastauksesta (map #(dissoc % :id :jarjestys) (filter #(= (:osio %) "hoidonjohtopalkkio") (:tarjous vastaus)))
        hankinnat-vastauksesta (map #(dissoc % :id :jarjestys) (filter #(contains? hankinta-osiot (:osio %)) (:tarjous vastaus)))
        toimenkuvat-vastauksesta (ota-toimenkuvat-ja-poista-id vastaus)]

    (is (= hoidonjohtopalkkio-tietomallista hoidonjohtopalkkio-vastauksesta))
    (is (= toimenkuvat-tietomallista toimenkuvat-vastauksesta))
    (is (= hankinnat-tietomallista hankinnat-vastauksesta))))

(deftest hae-tarjouksen-tiedot
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :hae-tarjouksen-tiedot +kayttaja-jvh+ {:urakka-id urakka-id})]
    (is (= urakka-id (:urakka-id vastaus)))
    (is (< 0 (count (:kaikki-toimenkuvat vastaus))))
    (is (= 1.2M (:kattohintakerroin vastaus)))
    (is (= false (:muokkaa-kattohinta-kasin vastaus)))
    (is (= #{} (:vahvistetut-vuodet vastaus)))
    (is (< 0 (count (:tarjous vastaus))))))

(deftest tallenna-tarjous-2025-urakalle-nimettomilla-toimenkuvailla-rajapinnasta-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        tietomallitarjous (assoc apurit/tarjous-tietomalli-2025 :urakka-id urakka-id)
        ;; Lisätään urakalle kuuluvat toimenkuvat ja niille generoidut summat
        tietomallitarjous (merge
                            {:urakka-id urakka-id}
                            (lisaa-ja-generoi-toimenkuvat db urakka-id urakan-alkuvuosi tietomallitarjous hoitovuosittaiset-arvot))
        hankinta-osiot #{"hankintakustannukset", "erillishankinnat", "hoidonjohtopalkkio", "tavoitehintaiset-rahavaraukset"}
        hoidonjohtopalkkio-tietomallista (filter #(= (:osio %) "hoidonjohtopalkkio") (:tarjous tietomallitarjous))
        hankinnat-tietomallista (filter #(contains? hankinta-osiot (:osio %)) (:tarjous tietomallitarjous))
        toimenkuvat-tietomallista (ota-toimenkuvat-ja-poista-id tietomallitarjous)

        ;; Lisätään tarjoukselle uusi toimenkuva, jolla ei ole nimeä
        uudet-toimenkuvat '({:toimenkuva "uusi toimenkuva", :nimi "", :toimenkuva-id -1, :jarjestys 99, :maksukausi "vuosi", :osio "johto-ja-hallintokorvaus",
                             :hoitovuosittaiset-arvot [{:vuosi 2025, :summa 10.00M} {:vuosi 2026, :summa 20.00M} {:vuosi 2027, :summa 30.00M}
                                                       {:vuosi 2028, :summa 40.00M} {:vuosi 2029, :summa 50.00M}]})

        ;; Generoi summat suodatetuille toimenkuville
        uudet-toimenkuvat (:tarjous (generoi-toimenkuville-vuosisummat uudet-toimenkuvat hoitovuosittaiset-arvot))
        ;; Yhdistä tietomallitarjouksen toimenkuvat ja uudistetut toimenkuvat
        tarjousrivit (:tarjous tietomallitarjous)
        tarjousrivit (vec (concat tarjousrivit uudet-toimenkuvat))
        tarjousrivit (sort-by (fn [rivi] (get tarjous-kyselyt/osiojarjestys (:osio rivi))) tarjousrivit)
        tallennettava-tarjous {:urakka-id urakka-id
                               :tarjous tarjousrivit}
        ;; Tallennetaan tarjous
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ tallennettava-tarjous)
        hoidonjohtopalkkio-vastauksesta (map #(dissoc % :id :jarjestys) (filter #(= (:osio %) "hoidonjohtopalkkio") (:tarjous vastaus)))
        hankinnat-vastauksesta (map #(dissoc % :id :jarjestys) (filter #(contains? hankinta-osiot (:osio %)) (:tarjous vastaus)))
        toimenkuvat-vastauksesta (ota-toimenkuvat-ja-poista-id vastaus)]

    (is (= hoidonjohtopalkkio-tietomallista hoidonjohtopalkkio-vastauksesta))
    (is (= toimenkuvat-tietomallista toimenkuvat-vastauksesta))
    (is (= hankinnat-tietomallista hankinnat-vastauksesta))))

(deftest muokkaa-tarjouksen-kilpailutettavat-hankinnat-2021-rajapinnasta-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        tarjous (assoc apurit/tarjous-tietomalli-2019 :urakka-id urakka-id)
        tehtavaryhma-erillishankinnat (first (tehtavaryhmat-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        tarjous (apurit/paivita-tarjoustietomallin-idt tarjous tehtavaryhma-erillishankinnat tehtava-hoidonjohtopalkkio)
        ;; Lisätään urakalle kuuluvat toimenkuvat ja niille generoidut summat
        tarjous (merge
                  {:urakka-id urakka-id}
                  (lisaa-ja-generoi-toimenkuvat db urakka-id urakan-alkuvuosi tarjous hoitovuosittaiset-arvot))
        tietomalli-rahavaraukset (filter #(= (:osio %) "tavoitehintaiset-rahavaraukset") (:tarjous tarjous))
        tietomalli-toimenkuvat (ota-toimenkuvat-ja-poista-id tarjous)
        uusi-summa 500M ;; Uusi summa vuodelle Kilpailitettaville hankinnoille

        ;; Tallennetaan tarjous ensin
        ensivastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ tarjous)
        ensivastaus-kustannus (first (:tarjous ensivastaus))
        ensivastaus-rahavaraukset (map #(dissoc % :id :jarjestys) (filter #(= (:osio %) "tavoitehintaiset-rahavaraukset") (:tarjous ensivastaus)))
        ensivastaus-toimenkuvat (ota-toimenkuvat-ja-poista-id ensivastaus)

        ;; Muokataan tarjousta
        muokattu-tarjous (assoc-in tarjous [:tarjous 0 :hoitovuosittaiset-arvot 0 :summa] uusi-summa)
        muokkausvastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ muokattu-tarjous)
        muokkausvastaus-kustannus (first (:tarjous muokkausvastaus))]

    (is (= 10.00 (get-in ensivastaus-kustannus [:hoitovuosittaiset-arvot 2 :summa])) "Vuoden 2023 summa on 10")
    (is (= uusi-summa (bigdec (get-in muokkausvastaus-kustannus [:hoitovuosittaiset-arvot 0 :summa]))))
    ;; Rahavaraukset ovat samat
    (is (= tietomalli-rahavaraukset ensivastaus-rahavaraukset))
    (is (= tietomalli-toimenkuvat ensivastaus-toimenkuvat))))

(deftest muokkaa-tarjouksen-kilpailutettavat-hankinnat-2025-rajapinnasta-onnistuu
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        tarjous (assoc apurit/tarjous-tietomalli-2019 :urakka-id urakka-id)
        tehtavaryhma-erillishankinnat (first (tehtavaryhmat-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        tarjous (apurit/paivita-tarjoustietomallin-idt tarjous tehtavaryhma-erillishankinnat tehtava-hoidonjohtopalkkio)
        ;; Lisätään urakalle kuuluvat toimenkuvat ja niille generoidut summat
        tarjous (merge
                  {:urakka-id urakka-id}
                  (lisaa-ja-generoi-toimenkuvat db urakka-id urakan-alkuvuosi tarjous hoitovuosittaiset-arvot))
        tietomalli-rahavaraukset (filter #(= (:osio %) "tavoitehintaiset-rahavaraukset") (:tarjous tarjous))
        tietomalli-toimenkuvat (ota-toimenkuvat-ja-poista-id tarjous)
        uusi-summa 500M ;; Uusi summa vuodelle Kilpailitettaville hankinnoille

        ;; Tallennetaan tarjous ensin
        ensivastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ tarjous)
        ensivastaus-kustannus (first (:tarjous ensivastaus))
        ensivastaus-rahavaraukset (map #(dissoc % :id :jarjestys) (filter #(= (:osio %) "tavoitehintaiset-rahavaraukset") (:tarjous ensivastaus)))
        ensivastaus-toimenkuvat (ota-toimenkuvat-ja-poista-id ensivastaus)

        ;; Muokataan tarjousta
        muokattu-tarjous (assoc-in tarjous [:tarjous 0 :hoitovuosittaiset-arvot 0 :summa] uusi-summa)
        muokkausvastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ muokattu-tarjous)
        muokkausvastaus-kustannus (first (:tarjous muokkausvastaus))]

    (is (= 10.00 (get-in ensivastaus-kustannus [:hoitovuosittaiset-arvot 2 :summa])) "Vuoden 2027 summa on 10")
    (is (= uusi-summa (bigdec (get-in muokkausvastaus-kustannus [:hoitovuosittaiset-arvot 0 :summa]))))
    ;; Rahavaraukset ovat samat
    (is (= tietomalli-rahavaraukset ensivastaus-rahavaraukset))
    (is (= tietomalli-toimenkuvat ensivastaus-toimenkuvat))))

(deftest muokkaa-tarjouksen-tietoja-kun-kustannussuunnitelma-on-vahvistettu
  (let [db (:db jarjestelma)
        hoitovuoden-alkuvuosi 2021 ;; Tarvitaan kustannussuunnitelmalle
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        vuodet (range urakan-alkuvuosi (pvm/vuosi (:loppupvm urakan-tiedot)))
        hoitovuosittaiset-arvot (mapv (fn [vuosi] {:vuosi vuosi :summa 0.00M}) vuodet)
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        tarjous (assoc apurit/tarjous-tietomalli-2019 :urakka-id urakka-id)
        tehtavaryhma-erillishankinnat (first (tehtavaryhmat-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        tarjous (apurit/paivita-tarjoustietomallin-idt tarjous tehtavaryhma-erillishankinnat tehtava-hoidonjohtopalkkio)
        ;; Lisätään urakalle kuuluvat toimenkuvat ja niille generoidut summat
        tarjous (merge
                  {:urakka-id urakka-id}
                  (lisaa-ja-generoi-toimenkuvat db urakka-id urakan-alkuvuosi tarjous hoitovuosittaiset-arvot))
        tietomalli-rahavaraukset (filter #(= (:osio %) "tavoitehintaiset-rahavaraukset") (:tarjous tarjous))
        tietomalli-toimenkuvat (ota-toimenkuvat-ja-poista-id tarjous)
        uusi-summa 500M ;; Uusi summa vuodelle Kilpailitettaville hankinnoille
        uusi-summa2 1500M ;; Uusi summa vuodelle Kilpailitettaville hankinnoille

        ;; Tallennetaan tarjous ensin
        ensivastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ tarjous)
        ensivastaus-kustannus (first (:tarjous ensivastaus))
        ensivastaus-rahavaraukset (map #(dissoc % :id :jarjestys) (filter #(= (:osio %) "tavoitehintaiset-rahavaraukset") (:tarjous ensivastaus)))
        ensivastaus-toimenkuvat (ota-toimenkuvat-ja-poista-id ensivastaus)

        ;; Vahvistetaan kustannussuunnitelma
        ;; Lisätään ensin kilpailutettavat hankinnat
        ;; ;; Poista yhteenvetorivi ennen tallennusta
        hankinnat-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            hoitovuoden-alkuvuosi (:toimenpiteet hankinnat-tietomalli))
        ;; Lisätään erillishankinnat
        _ (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:erillishankinnat apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään hoidonjohtopalkkiot
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään johto- ja hallintokorvaukset
        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019) hoitovuoden-alkuvuosi)

        ;; Varmista, että kustannussuunnitelmaa ei ole vielä vahvistettu
        kustannussuunnitelma (kutsu-palvelua (:http-palvelin jarjestelma) :hae-kustannussuunnitelman-tiedot
                               +kayttaja-jvh+
                               {:urakka-id urakka-id
                                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})
        _ (is (false? (get-in kustannussuunnitelma [:kustannussuunnitelma :vahvistettu?])) "Kustannussuunnitelman pitäisi olla vahvistamaton ennen vahvistusta")

        ;; Muokataan tarjousta
        muokattu-tarjous (assoc-in tarjous [:tarjous 0 :hoitovuosittaiset-arvot 0 :summa] uusi-summa)
        muokkausvastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ muokattu-tarjous)
        ;; Tämä onnistuu, koska kustannussuunnitelma ei ole vielä vahvistettu
        muokkausvastaus-tarjous (first (:tarjous muokkausvastaus))
        _ (is (= 10.00 (get-in ensivastaus-kustannus [:hoitovuosittaiset-arvot 2 :summa])) "Vuoden 2023 summa on 10")
        _ (is (= uusi-summa (bigdec (get-in muokkausvastaus-tarjous [:hoitovuosittaiset-arvot 0 :summa]))))
        ;; Rahavaraukset ovat samat
        _ (is (= tietomalli-rahavaraukset ensivastaus-rahavaraukset))
        _ (is (= tietomalli-toimenkuvat ensivastaus-toimenkuvat))

        ;; Vahvisteatan 2021 vuoden kustannussuuunnitelma
        kustannussuunnitelma-vahvistettu (try
                                           (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                             :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                                                                                             :vahvista? true})
                                           (catch Exception e
                                             (println "Tapahtui virhe:" (.getMessage e))
                                             {:error (.getMessage e)}))
        _ (is (true? (get-in kustannussuunnitelma-vahvistettu [:kustannussuunnitelma :vahvistettu?])) "Kustannussuunnitelman pitäisi olla vahvistettu.")

        ;; Muokataan tarjousta lisää - 2021 vuosi on vahvistettu joten siihen tehdyt muokkaukset eivät mene läpi
        muokattu-tarjous (-> tarjous
                           (assoc-in [:tarjous 0 :hoitovuosittaiset-arvot 0 :summa] 1234M) ;; Vuodelle 2021
                           (assoc-in [:tarjous 1 :hoitovuosittaiset-arvot 0 :summa] 1234M)) ;; Vuodelle 2022
        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tarjouksen-tiedot +kayttaja-jvh+ muokattu-tarjous)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))]
    (is (not= (get-in tarjous [:tarjous 0 :hoitovuosittaiset-arvot 0 :summa]) uusi-summa2) "2021 vuoden summaksi asetettin 1234M, mutta se ei mennyt päivityksessä läpi.")
    (is (not= (get-in tarjous [:tarjous 1 :hoitovuosittaiset-arvot 0 :summa]) 1234M) "2022 summaksi asetettin 1234M.")))
