(ns harja.palvelin.palvelut.suunnittelu.apurit
  (:require [clojure.string :as str]
            [harja.testi :refer :all]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as uusi-kust-kyselyt]))

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
                                        {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 380 :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}

                                        ;; Johto ja hallintokorvaukset eli toimenkuvat - tulevat tietokannasta urakkavuoden mukaan, ei yritetä syöttää tässä

                                        ;; Hoidonjohtopalkkio
                                        {:nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 26665 :tehtavaryhma-id nil :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                                        ;; Yhteensä rivi
                                        {:nimi "Yhteensä tavoitehinta", :osio "yhteensa"
                                         :hoitovuosittaiset-arvot [{:vuosi 2021 :summa 0.00} {:vuosi 2022 :summa 0.00} {:vuosi 2023 :summa 90.00} {:vuosi 2024 :summa 180.00} {:vuosi 2029 :summa 270.00}], :yhteensa 540.00}]})

(def tarjous-tietomalli-2025 {:tarjous [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.0} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}] :yhteensa 60.00}
                                        ;; Rahavaraukset
                                        {:nimi "Äkilliset hoitotyöt", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 1
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.0} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}] :yhteensa 60.00}
                                        {:nimi "Vahinkojen korjaukset", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 2
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.0} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}] :yhteensa 60.00}
                                        {:nimi "Tilaajan rahavaraus kannustinjärjestelmään", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 3
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.0} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}] :yhteensa 60.00}

                                        ;; Erillishankinnat
                                        {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 380 :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.0} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}], :yhteensa 60.00}

                                        ;; Johto ja hallintokorvaukset eli toimenkuvat - tulevat tietokannasta urakkavuoden mukaan, ei yritetä syöttää tässä

                                        ;; Hoidonjohtopalkkio
                                        {:nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio" :toimenkuva-id nil :tehtava-id 26665 :tehtavaryhma-id nil :rahavaraus-id nil
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.0} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 10.00} {:vuosi 2028 :summa 20.00} {:vuosi 2029 :summa 30.00}], :yhteensa 60.00}
                                        ;; Yhteensä rivi
                                        {:nimi "Yhteensä tavoitehinta", :osio "yhteensa"
                                         :hoitovuosittaiset-arvot [{:vuosi 2025 :summa 0.0} {:vuosi 2026 :summa 0.00} {:vuosi 2027 :summa 90.00} {:vuosi 2028 :summa 180.00} {:vuosi 2029 :summa 270.00}], :yhteensa 540.00}]})

(defn paivita-tarjoustietomallin-idt
  "Kovakoodatussa tietomallissa annetaan tehtavaryhma-id kovakoodattuna. Se ei täsmää kaikille urakoille.
  Vaihdetaan siis id."
  [tietomalli erillishankinta hoidonjohtopalkkio]
  (let [tarjousrivit (:tarjous tietomalli)
        rivit (mapv (fn [rivi]
                      (cond
                        (= (:nimi rivi) "Erillishankinnat")
                        (assoc rivi :tehtavaryhma-id (:id erillishankinta))
                        (= (:nimi rivi) "Hoidonjohtopalkkio")
                        (assoc rivi :tehtava-id (:id hoidonjohtopalkkio))
                        :else rivi))
                tarjousrivit)]
    {:tarjous rivit}))

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

(def hankinnat-tietomalli {:toimenpiteet [{:nimi "TALVIHOITO" :apunimi "talvi" :osio "hankintakustannukset" :toimenpideinstanssi-id 87 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "LIIKENNEYMPÄRISTÖN HOITO" :apunimi "ympäristö" :osio "hankintakustannukset" :toimenpideinstanssi-id 88 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "SORATEIDEN HOITO" :apunimi "sora" :osio "hankintakustannukset" :toimenpideinstanssi-id 89 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "PÄÄLLYSTEIDEN PAIKKAUS" :apunimi "paikkaus" :osio "hankintakustannukset" :toimenpideinstanssi-id 90 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "YLLÄPITO" :apunimi "mhu ylläpito" :osio "hankintakustannukset" :toimenpideinstanssi-id 91 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "KORVAUSINVESTOINTI" :apunimi "korvaus" :osio "hankintakustannukset" :toimenpideinstanssi-id 92 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 100 :alkukausi-indeksikorjattu 111 :loppukausi 300 :loppukausi-indeksikorjattu 333 :yhteensa 400 :yhteensa-indeksikorjattu 444}
                                          {:nimi "Yhteensä" :apunimi "yhteensä" :osio "hankintakustannukset" :toimenpideinstanssi-id 0 :pysyvat-muutokset "Ei muutoksia"
                                           :alkukausi 700 :alkukausi-indeksikorjattu 777 :loppukausi 2100 :loppukausi-indeksikorjattu 2331 :yhteensa 2800 :yhteensa-indeksikorjattu 3108}]})

(defn paivita-hankintojen-toimenpideinstanssi-id
  "Kovakoodatussa tietomallissa annetaan toimenpideinstanssi-id kovakoodattuna. Se ei täsmää kaikille urakoille.
  Vaihdetaan siis id sen mukaan, mitä toimenpiteissä on annettu"
  [hankinnat toimenpiteet]
  (let [hankintarivit (:toimenpiteet hankinnat)
        hankintarivit (keep (fn [hankintarivi]
                              (let [r (keep
                                        (fn [toimenpide]
                                          (if (str/includes? (str/lower-case (:nimi toimenpide)) (str/lower-case (:apunimi hankintarivi)))
                                            (assoc hankintarivi :toimenpideinstanssi-id (:toimenpideinstanssi-id toimenpide))
                                            nil))
                                        toimenpiteet)]
                                (first r)))
                        hankintarivit)]
    {:toimenpiteet hankintarivit}))

(def johto-ja-hallinto-tietomalli-2025 {:johto-ja-hallintokorvaukset-2025 [{:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2024 :kuukausi 10 :kalenterikuukausi "Lokakuu 2024"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2024 :kuukausi 11 :kalenterikuukausi "Marraskuu 2024"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2024 :kuukausi 12 :kalenterikuukausi "Joulukuu 2024"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 1 :kalenterikuukausi "Tammikuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 2 :kalenterikuukausi "Helmikuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 3 :kalenterikuukausi "Maaliskuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 4 :kalenterikuukausi "Huhtikuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 5 :kalenterikuukausi "Toukokuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 6 :kalenterikuukausi "Kesäkuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 7 :kalenterikuukausi "Heinäkuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 8 :kalenterikuukausi "Elokuu 2025"}
                                                                           {:summa 58 :summa_indeksikorjattu 85.2 :vuosi 2025 :kuukausi 9 :kalenterikuukausi "Syyskuu 2025"}]})

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

(def hoidonjohtopalkkiot-tietomalli {:hoidonjohtopalkkiot [{:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 10 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Lokakuu 2024"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 11 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Marraskuu 2024"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2024 :kuukausi 12 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Joulukuu 2024"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 1 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Tammikuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 2 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Helmikuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 3 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Maaliskuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 4 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Huhtikuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 5 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Toukokuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 6 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Kesäkuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 7 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Heinäkuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 8 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Elokuu 2025"}
                                                           {:summa 5 :summa_indeksikorjattu 6.5 :toimenpideinstanssi 96 :vuosi 2025 :kuukausi 9 :sopimus 1 :tehtava 26665 :kalenterikuukausi "Syyskuu 2025"}]})

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

(defn generoi-tarjous-tasmaa-kustannuksia
  "Generoi tarjouksen, joka täsmää kustista.
  Uusi validointi vaatii, että tarjous täsmää kustannussuunnitelman kanssa."
  [urakka
   erillishankinnat-yht
   hoidonjohtopalkkiot-yht
   johto-ja-hallintokorvaukset-yht]
  {:tarjous [{:yhteensa 0, :maksukausi nil, :jarjestys 0, :koskematon true, :rahavaraus-id nil, :toimenkuva-id nil, :nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset", :poistettu nil,
              :hoitovuosittaiset-arvot (list {:vuosi 2026, :summa 0} {:vuosi 2029, :summa 0} {:vuosi 2028, :summa 0} {:vuosi 2025, :summa 2000} {:vuosi 2027, :summa 0}), :tehtavaryhma-id nil, :tehtava-id nil}
             {:yhteensa 0, :maksukausi nil, :jarjestys 1, :koskematon true, :rahavaraus-id 1, :toimenkuva-id nil, :nimi "Äkilliset hoitotyöt", :osio "tavoitehintaiset-rahavaraukset", :poistettu nil,
              :hoitovuosittaiset-arvot (list {:vuosi 2026, :summa 0} {:vuosi 2029, :summa 0} {:vuosi 2028, :summa 0} {:vuosi 2025, :summa 2000} {:vuosi 2027, :summa 0}), :tehtavaryhma-id nil, :tehtava-id nil}
             {:yhteensa 0, :maksukausi nil, :jarjestys 2, :koskematon true, :rahavaraus-id 2, :toimenkuva-id nil, :nimi "Vahinkojen korjaukset", :osio "tavoitehintaiset-rahavaraukset", :poistettu nil,
              :hoitovuosittaiset-arvot (list {:vuosi 2026, :summa 0} {:vuosi 2029, :summa 0} {:vuosi 2028, :summa 0} {:vuosi 2025, :summa 2000} {:vuosi 2027, :summa 0}), :tehtavaryhma-id nil, :tehtava-id nil}
             {:yhteensa 0, :maksukausi nil, :jarjestys 3, :rahavaraus-id 3, :toimenkuva-id nil, :nimi "Tilaajan rahavaraus kannustinjärjestelmään", :osio "tavoitehintaiset-rahavaraukset", :poistettu nil,
              :hoitovuosittaiset-arvot (list {:vuosi 2026, :summa 0} {:vuosi 2029, :summa 0} {:vuosi 2028, :summa 0} {:vuosi 2025, :summa 2000} {:vuosi 2027, :summa 0}), :tehtavaryhma-id nil, :tehtava-id nil}
             {:yhteensa 0, :maksukausi nil, :eperhoitovuosi erillishankinnat-yht, :jarjestys 4, :rahavaraus-id nil, :toimenkuva-id nil, :nimi "Erillishankinnat", :osio "erillishankinnat", :poistettu nil,
              :hoitovuosittaiset-arvot (list {:vuosi 2026, :summa erillishankinnat-yht} {:vuosi 2025, :summa erillishankinnat-yht} {:vuosi 2024, :summa erillishankinnat-yht} {:vuosi 2029, :summa 0} {:vuosi 2028, :summa 0} {:vuosi 2027, :summa 0}), :tehtavaryhma-id 380, :tehtava-id nil}
             {:yhteensa 0, :maksukausi nil, :eperhoitovuosi hoidonjohtopalkkiot-yht, :jarjestys 5, :rahavaraus-id nil, :toimenkuva-id nil, :nimi "Hoidonjohtopalkkio", :osio "hoidonjohtopalkkio", :poistettu nil,
              :hoitovuosittaiset-arvot (list {:vuosi 2026, :summa hoidonjohtopalkkiot-yht} {:vuosi 2025, :summa hoidonjohtopalkkiot-yht} {:vuosi 2024, :summa hoidonjohtopalkkiot-yht} {:vuosi 2029, :summa 0} {:vuosi 2028, :summa 0} {:vuosi 2027, :summa 0}), :tehtavaryhma-id nil, :tehtava-id 19434}
             {:yhteensa 0, :maksukausi "vuosi", :jarjestys 2, :rahavaraus-id nil, :toimenkuva-id 33, :nimi "Vastuunalainen työnjohtaja", :osio "johto-ja-hallintokorvaus", :poistettu nil,
              :hoitovuosittaiset-arvot (list {:vuosi 2026, :summa johto-ja-hallintokorvaukset-yht} {:vuosi 2025, :summa johto-ja-hallintokorvaukset-yht} {:vuosi 2024, :summa johto-ja-hallintokorvaukset-yht} {:vuosi 2029, :summa 0} {:vuosi 2028, :summa 0} {:vuosi 2027, :summa 0}), :tehtavaryhma-id nil, :tehtava-id nil}], :urakka-id urakka})


(defn tallenna-kustannussuunnitelma-ja-tarjous!
  "Tallentaa kustannussuunnitelman ja tarjouksen testikäyttöön.
   Palauttaa urakan id:n."
  [db kayttaja urakka-id hoitovuoden-alkuvuosi johto-ja-hallinto-tietomalli]
  ;; Kilpailutettavat hankinnat
  (let [h-tietomalli (poista-yhteenvetorivi-toimenpiteilta hankinnat-tietomalli)
        toimenpiteet (uusi-kust-kyselyt/hae-urakan-toimenpiteet db {:urakkaid urakka-id})
        h-tietomalli (paivita-hankintojen-toimenpideinstanssi-id h-tietomalli toimenpiteet)
        erillishankinnat-yht (apply + (map :summa (:erillishankinnat erillishankinnat-tietomalli)))
        hoidonjohto-yht (apply + (map :summa (:hoidonjohtopalkkiot hoidonjohtopalkkiot-tietomalli)))
        jjh-yht (apply + (map :summa johto-ja-hallinto-tietomalli))
        tarjous (generoi-tarjous-tasmaa-kustannuksia
                  urakka-id
                  erillishankinnat-yht
                  hoidonjohto-yht
                  jjh-yht)
        vahvistetut-vuodet #{}]
    (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat db kayttaja urakka-id
      hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
    ;; Erillishankinnat
    (uusi-kust-kyselyt/tallenna-erillishankinnat db kayttaja urakka-id
      (:erillishankinnat erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
    ;; Hoidonjohtopalkkiot
    (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot db kayttaja urakka-id
      (:hoidonjohtopalkkiot hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
    ;; Johto- ja hallintokorvaukset
    (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset db kayttaja urakka-id
      johto-ja-hallinto-tietomalli hoitovuoden-alkuvuosi)
    ;; Tarjous
    (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id (:id kayttaja) tarjous vahvistetut-vuodet)))

(defn poista-tarjoukset-tietokannasta!
  "Poistaa kaikki tarjouksessa olevat rivit tietokannasta, jotta testit voidaan ajaa uudestaan."
  [urakkaid]
  ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
  (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s" urakkaid))
  (u (format "DELETE FROM tarjous_kustannukset WHERE urakka_id = %s" urakkaid))
  (u (format "DELETE FROM tarjous_johto_ja_hallintokorvaus WHERE urakka_id = %s" urakkaid))
  (u (format "DELETE FROM tarjous WHERE urakka_id = %s" urakkaid)))
