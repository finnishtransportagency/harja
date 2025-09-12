(ns harja.palvelin.palvelut.suunnittelu.apurit)

(def tarjous-tietomalli {:tarjous [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
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

                                   ;; Johto ja hallintokorvaukset eli toimenkuvat
                                   {:nimi "Vastuunalainen työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 2 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                    :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                                   {:nimi "Apulainen/työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 4 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                    :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                                   {:nimi "Valmistelukausi ennen urakka-ajan alkua", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 10 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                    :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}

                                   ;; Hoidonjohtopalkkio
                                   {:nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 3061 :tehtavaryhma-id nil :rahavaraus-id nil
                                    :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                                   ;; Yhteensä rivi
                                   {:nimi "Yhteensä tavoitehinta", :osio "yhteensa"
                                    :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 90.00} {:vuosi 2024 :summa 180.00} {:vuosi 2025 :summa 270.00}], :yhteensa 540.00}]})

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
