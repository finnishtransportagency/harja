(ns harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone)

(def paatostyypit
  [{:nimi "Lupaukset" :tyyppi "bonus" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 1}
   {:nimi "Lupaukset" :tyyppi "sakko" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 2}
   {:nimi "Lupaukset" :tyyppi "ei-bonus-ei-sakko" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 3}
   {:nimi "Tavoitehinnan muutokset" :tyyppi "1b" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 4}
   {:nimi "Tavoitehinnan muutokset" :tyyppi "1a" :urakan_alkuvuosi 2021 :nakyvyys_alkaen 2021 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 5}
   {:nimi "Hoitovuoden lopun indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "A" :urakan_alkuvuosi 2020 :nakyvyys_alkaen 2025 :hoitotyyppi #{"MHU"} :jarjestys 7}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "B" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8}
   {:nimi "Tavoitehinnan alitus" :tyyppi nil :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 9}
   {:nimi "Tavoitehinnan Ylitys" :tyyppi "A" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 10}
   {:nimi "Tavoitehinnan Ylitys" :tyyppi "B" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 11}
   {:nimi "Kattohinnan ylitys" :tyyppi nil :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 12}
   {:nimi "Hoidonjohtopalkkion muutos" :tyyppi nil :urakan_alkuvuosi 2020 :nakyvyys_alkaen 2025 :hoitotyyppi #{"MHU"} :jarjestys 13}
   {:nimi "Hoidonjohtopalkkion muutos" :tyyppi nil :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 14}
   {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :tyyppi nil :urakan_alkuvuosi 2020 :nakyvyys_alkaen 2025 :hoitotyyppi #{"MHU"} :jarjestys 15}
   {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :tyyppi nil :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 16}])

(defn mahdolliset-paatokset-tyypilla [mhu-tyyppi paatokset]
  (filter #(contains? (:hoitotyyppi %) mhu-tyyppi) paatokset))

(defn mahdolliset-paatokset-urakan-alkuvuodella [urakan-alkuvuosi paatokset]
  (filter #(<= (:urakan_alkuvuosi %) urakan-alkuvuosi) paatokset))

(defn mahdolliset-paatokset-nakyvyys-vuodella [kuluva-vuosi paatokset]
  (filter #(<= (:nakyvyys_alkaen %) kuluva-vuosi) paatokset))

(defn paatosnakyvyyskone [mhu-tyyppi urakan-alkuvuosi kuluva-hoitovuosi]
  (let [paatokset paatostyypit
        mahdollset-tyypilla (mahdolliset-paatokset-tyypilla mhu-tyyppi paatokset)
        mahdolliset-aloitusvuodella (mahdolliset-paatokset-urakan-alkuvuodella urakan-alkuvuosi mahdollset-tyypilla)
        mahdolliset-kuluvalle-vuodelle (mahdolliset-paatokset-nakyvyys-vuodella kuluva-hoitovuosi mahdolliset-aloitusvuodella)]
    mahdolliset-kuluvalle-vuodelle))
