(ns harja.palvelin.palvelut.suunnittelu.apurit)

(def tarjous-tietomalli-2019 {:tarjous [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                                        ;; Rahavaraukset
                                        {:nimi "Äkilliset hoitotyöt", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 1
                                         :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                                        {:nimi "Vahinkojen korjaukset", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 2
                                         :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                                        {:nimi "Tilaajan rahavaraus kannustinjärjestelmään", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 3
                                         :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}

                                        ;; Erillishankinnat
                                        {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 28 :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}

                                        ;; Johto ja hallintokorvaukset eli toimenkuvat - tulevat tietokannasta urakkavuoden mukaan, ei yritetä syöttää tässä

                                        ;; Hoidonjohtopalkkio
                                        {:nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 3061 :tehtavaryhma-id nil :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                                        ;; Yhteensä rivi
                                        {:nimi "Yhteensä tavoitehinta", :osio "yhteensa"
                                         :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 90.00} {:vuosi 2024 :summa 180.00} {:vuosi 2029 :summa 270.00}], :yhteensa 540.00}]})

(def tarjous-tietomalli-2025 {:tarjous [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.00} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}] :yhteensa 60.00}
                                        ;; Rahavaraukset
                                        {:nimi "Äkilliset hoitotyöt", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 1
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.00} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}] :yhteensa 60.00}
                                        {:nimi "Vahinkojen korjaukset", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 2
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.00} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}] :yhteensa 60.00}
                                        {:nimi "Tilaajan rahavaraus kannustinjärjestelmään", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 3
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.00} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}] :yhteensa 60.00}

                                        ;; Erillishankinnat
                                        {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 28 :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.00} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}], :yhteensa 60.00}

                                        ;; Johto ja hallintokorvaukset eli toimenkuvat - tulevat tietokannasta urakkavuoden mukaan, ei yritetä syöttää tässä

                                        ;; Hoidonjohtopalkkio
                                        {:nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 3061 :tehtavaryhma-id nil :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.00} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}], :yhteensa 60.00}
                                        ;; Yhteensä rivi
                                        {:nimi "Yhteensä tavoitehinta", :osio "yhteensa"
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.00} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 90.00} {:vuosi 2028 :summa 180.00} {:vuosi 2029 :summa 270.00}], :yhteensa 540.00}]})

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

(defn poista-yhteenvetorivi-toimenpiteilta [tietomalli]
  {:toimenpiteet (filter #(not= (:nimi %) "Yhteensä") (:toimenpiteet tietomalli))})

(def hankinnat-tietomalli {:toimenpiteet [{:nimi "Talvihoito laaja TPI", :osio "hankintakustannukset" :toimenpideinstanssi-id 90 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "Liikenneympäristön hoito laaja TPI", :osio "hankintakustannukset" :toimenpideinstanssi-id 91 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "Soratien hoito laaja TPI", :osio "hankintakustannukset" :toimenpideinstanssi-id 92 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "Päällysteiden paikkaus (hoidon ylläpito)", :osio "hankintakustannukset" :toimenpideinstanssi-id 93 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "MHU Ylläpito", :osio "hankintakustannukset" :toimenpideinstanssi-id 94 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "MHU Korvausinvestointi", :osio "hankintakustannukset" :toimenpideinstanssi-id 95 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "Yhteensä", :osio "hankintakustannukset" :toimenpideinstanssi-id 0 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 700 :alkukausi-indeksikorjattu 777 :loppukausi 2100 :loppukausi-indeksikorjattu 2331 :yhteensa 2800 :yhteensa-indeksikorjattu 3108}]})

(defn paivita-hankintojen-toimenpideinstanssi-id
  "Kovakoodatussa tietomallissa annetaan toimenpideinstanssi-id kovakoodattuna. Se ei täsmää kaikille urakoille.
  Vaihdetaan siis id sen mukaan, mitä toimenpiteissä on annettu"
  [hankinnat toimenpiteet]
  (let [hankintarivit (:toimenpiteet hankinnat)
        hankintarivit (mapv (fn [hankintarivi]
                              (let [r (keep
                                        (fn [toimenpide]
                                          (if (= (:nimi toimenpide) (:nimi hankintarivi))
                                            (assoc hankintarivi :toimenpideinstanssi-id (:toimenpideinstanssi-id toimenpide))
                                            nil))
                                        toimenpiteet)]
                                (first r)))
                        hankintarivit)]
    {:toimenpiteet hankintarivit}))

(def erillishankinnat-tietomalli {:erillishankinnat [{:summa 1000 :summa_indeksikorjattu 1111 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 10 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Lokakuu 2024"}
                                                     {:summa 2000 :summa_indeksikorjattu 2222 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 11 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Marraskuu 2024"}
                                                     {:summa 3000 :summa_indeksikorjattu 3333 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 12 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Joulukuu 2024"}
                                                     {:summa 4000 :summa_indeksikorjattu 4444 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 1 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Tammikuu 2025"}
                                                     {:summa 5000 :summa_indeksikorjattu 5555 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 2 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Helmikuu 2025"}
                                                     {:summa 6000 :summa_indeksikorjattu 6666 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 3 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Maaliskuu 2025"}
                                                     {:summa 7000 :summa_indeksikorjattu 7777 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 4 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Huhtikuu 2025"}
                                                     {:summa 8000 :summa_indeksikorjattu 8888 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 5 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Toukokuu 2025"}
                                                     {:summa 9000 :summa_indeksikorjattu 9999 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 6 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Kesäkuu 2025"}
                                                     {:summa 10000 :summa_indeksikorjattu 11111 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 7 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Heinäkuu 2025"}
                                                     {:summa 11000 :summa_indeksikorjattu 12121 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 8 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Elokuu 2025"}
                                                     {:summa 12000 :summa_indeksikorjattu 13333 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 9 :sopimus 1 :tehtavaryhma 28 :kalenterikuukausi "Syyskuu 2025"}]})

(def hoidonjohtopalkkiot-tietomalli {:hoidonjohtopalkkiot [{:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 10 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Lokakuu 2024"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 11 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Marraskuu 2024"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 12 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Joulukuu 2024"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 1 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Tammikuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 2 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Helmikuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 3 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Maaliskuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 4 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Huhtikuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 5 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Toukokuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 6 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Kesäkuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 7 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Heinäkuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 8 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Elokuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 9 :sopimus 1 :tehtava 3061 :kalenterikuukausi "Syyskuu 2025"}]})

(def johto-ja-hallinto-tietomalli-2019 {:johto-ja-hallintokorvaukset-2019
                                        [{:id 1 :toimenkuva "sopimusvastaava"
                                          :kuukaudet [{:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2024 :kuukausi 10 :kalenterikuukausi "Lokakuu 2024"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2024 :kuukausi 11 :kalenterikuukausi "Marraskuu 2024"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2024 :kuukausi 12 :kalenterikuukausi "Joulukuu 2024"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 1 :kalenterikuukausi "Tammikuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 2 :kalenterikuukausi "Helmikuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 3 :kalenterikuukausi "Maaliskuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 4 :kalenterikuukausi "Huhtikuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 5 :kalenterikuukausi "Toukokuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 6 :kalenterikuukausi "Kesäkuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 7 :kalenterikuukausi "Heinäkuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 8 :kalenterikuukausi "Elokuu 2025"}
                                                      {:toimenkuva-id 1 :toimenkuva "sopimusvastaava" :tunnit 1 :tuntipalkka 85.2 :vuosi 2025 :kuukausi 9 :kalenterikuukausi "Syyskuu 2025"}]}]})
