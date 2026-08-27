(ns harja.palvelin.palvelut.valikatselmus.paatostyypit)


(def paatostyypit
  [{:nimi "Tavoitehinnan muutokset"
    :urakan_alkuvuosi 2019
    :nakyvyys_alkaen 2019
    :nakyvyys_asti 2024
    :hoitotyyppi #{"MHU"}
    :jarjestys 2
    :paatostyyppi "tavoitehinnan-muutokset"
    :avain :tavoitehinnan-muutokset
    :riippuu []}

   {:nimi "Tavoitehinnan muutokset"
    :urakan_alkuvuosi 2021
    :nakyvyys_alkaen 2021
    :nakyvyys_asti 2028
    :hoitotyyppi #{"MHU"}
    :jarjestys 2
    :paatostyyppi "tavoitehinnan-muutokset"
    :avain :tavoitehinnan-muutokset
    :riippuu []}

   {:nimi "Tavoitehinnan muutokset"
    :urakan_alkuvuosi 2024
    :nakyvyys_alkaen 2024
    :nakyvyys_asti 2024
    :hoitotyyppi #{"MHU+"}
    :jarjestys 2
    :paatostyyppi "tavoitehinnan-muutokset"
    :avain :tavoitehinnan-muutokset
    :riippuu []}

   {:nimi "Tavoitehinnan pysyvät muutokset"
    :urakan_alkuvuosi 2025
    :nakyvyys_alkaen 2025
    :hoitotyyppi #{"MHU" "MHU+"}
    :jarjestys 2
    :paatostyyppi "tavoitehinnan-pysyvat-muutokset"
    :avain :tavoitehinnan-muutokset
    :riippuu []}

   {:nimi "Hoitovuoden lopun indeksikorjaus"
    :tyyppi nil
    :urakan_alkuvuosi 2024
    :nakyvyys_alkaen 2024
    :hoitotyyppi #{"MHU" "MHU+"}
    :jarjestys 3
    :paatostyyppi "indeksikorjaus"
    :avain :indeksikorjaus
    :riippuu [{:avain :tavoitehinnan-muutokset}]}

   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta"
    :tyyppi "A"
    :urakan_alkuvuosi 2021
    :nakyvyys_alkaen 2024
    :nakyvyys_asti 2024
    :hoitotyyppi #{"MHU"}
    :jarjestys 4
    :paatostyyppi "hoitovuoden-lopun-hinta"
    :avain :hoitovuoden-lopun-hinta
    :riippuu [{:avain :tavoitehinnan-muutokset}]}

   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta"
    :tyyppi "B"
    :urakan_alkuvuosi 2024
    :nakyvyys_alkaen 2024
    :nakyvyys_asti 2024
    :hoitotyyppi #{"MHU"}
    :jarjestys 4
    :paatostyyppi "hoitovuoden-lopun-hinta"
    :avain :hoitovuoden-lopun-hinta
    :riippuu [{:avain :tavoitehinnan-muutokset}
              {:avain :indeksikorjaus}]}

   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta"
    :tyyppi "B"
    :urakan_alkuvuosi 2024
    :nakyvyys_alkaen 2024
    :hoitotyyppi #{"MHU+"}
    :jarjestys 4
    :paatostyyppi "hoitovuoden-lopun-hinta-v2"
    :avain :hoitovuoden-lopun-hinta
    :riippuu [{:avain :tavoitehinnan-muutokset}
              {:avain :indeksikorjaus}]}

   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta"
    :tyyppi "C"
    :urakan_alkuvuosi 2025
    :nakyvyys_alkaen 2025
    :hoitotyyppi #{"MHU"}
    :jarjestys 4
    :paatostyyppi "hoitovuoden-lopun-hinta-v2"
    :avain :hoitovuoden-lopun-hinta
    :riippuu [{:avain :tavoitehinnan-muutokset}
              {:avain :indeksikorjaus}]}

   {:nimi "Tavoitehinnan alitus"
    :urakan_alkuvuosi 2019
    :nakyvyys_alkaen 2019
    :hoitotyyppi #{"MHU"}
    :jarjestys 5
    :paatostyyppi "tavoitehinta"
    :avain :tavoitehinnan-alitus
    :riippuu [{:avain :hoitovuoden-lopun-hinta}]}

   {:nimi "Tavoitehinnan alitus"
    :urakan_alkuvuosi 2024
    :nakyvyys_alkaen 2024
    :hoitotyyppi #{"MHU+"}
    :jarjestys 5
    :paatostyyppi "tavoitehinta"
    :avain :tavoitehinnan-alitus
    :riippuu [{:avain :hoitovuoden-lopun-hinta}]}

   {:nimi "Tavoitehinnan ylitys"
    :tyyppi "A"
    :urakan_alkuvuosi 2019
    :nakyvyys_alkaen 2019
    :hoitotyyppi #{"MHU"}
    :jarjestys 6
    :paatostyyppi "tavoitehinta"
    :avain :tavoitehinnan-ylitys
    :riippuu [{:avain :hoitovuoden-lopun-hinta}]}

   {:nimi "Tavoitehinnan ylitys"
    :tyyppi "B"
    :urakan_alkuvuosi 2024
    :nakyvyys_alkaen 2019
    :hoitotyyppi #{"MHU" "MHU+"}
    :jarjestys 6
    :paatostyyppi "tavoitehinta"
    :avain :tavoitehinnan-ylitys
    :riippuu [{:avain :hoitovuoden-lopun-hinta}]}

   {:nimi "Kattohinnan ylitys"
    :urakan_alkuvuosi 2019
    :nakyvyys_alkaen 2019
    :hoitotyyppi #{"MHU"}
    :jarjestys 7
    :paatostyyppi "kattohinta"
    :avain :kattohinnan-ylitys
    :riippuu [{:avain :hoitovuoden-lopun-hinta}]}

   {:nimi "Kattohinnan ylitys"
    :urakan_alkuvuosi 2024
    :nakyvyys_alkaen 2024
    :hoitotyyppi #{"MHU+"}
    :jarjestys 7
    :paatostyyppi "kattohinta"
    :avain :kattohinnan-ylitys
    :riippuu [{:avain :hoitovuoden-lopun-hinta}]}

   {:nimi "Lupaukset"
    :tyyppi "bonus"
    :urakan_alkuvuosi 2019
    :nakyvyys_alkaen 2019
    :hoitotyyppi #{"MHU" "MHU+"}
    :jarjestys 8
    :paatostyyppi "lupaus"
    :avain :lupaus
    :riippuu [{:avain :hoitovuoden-lopun-hinta
               :urakan_alkuvuosi_alkaen 2025}]}

   {:nimi "Lupaukset"
    :tyyppi "sanktio"
    :urakan_alkuvuosi 2019
    :nakyvyys_alkaen 2019
    :hoitotyyppi #{"MHU" "MHU+"}
    :jarjestys 8
    :paatostyyppi "lupaus"
    :avain :lupaus
    :riippuu [{:avain :hoitovuoden-lopun-hinta
               :urakan_alkuvuosi_alkaen 2025}]}

   {:nimi "Lupaukset"
    :tyyppi "taytetty"
    :urakan_alkuvuosi 2019
    :nakyvyys_alkaen 2019
    :hoitotyyppi #{"MHU" "MHU+"}
    :jarjestys 8
    :paatostyyppi "lupaus"
    :avain :lupaus
    :riippuu [{:avain :hoitovuoden-lopun-hinta
               :urakan_alkuvuosi_alkaen 2025}]}

   {:nimi "Hoidonjohtopalkkion muutos"
    :urakan_alkuvuosi 2021
    :nakyvyys_alkaen 2024
    :hoitotyyppi #{"MHU"}
    :jarjestys 9
    :paatostyyppi "hoidonjohtopalkkio"
    :avain :hoidonjohtopalkkio
    :riippuu [{:avain :hoitovuoden-lopun-hinta}]}

   {:nimi "Hoidonjohtopalkkion muutos"
    :urakan_alkuvuosi 2024
    :nakyvyys_alkaen 2024
    :hoitotyyppi #{"MHU" "MHU+"}
    :jarjestys 9
    :paatostyyppi "hoidonjohtopalkkio"
    :avain :hoidonjohtopalkkio
    :riippuu [{:avain :hoitovuoden-lopun-hinta}]}

   {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit"
    :urakan_alkuvuosi 2020
    :nakyvyys_alkaen 2024
    :hoitotyyppi #{"MHU"}
    :jarjestys 10
    :paatostyyppi "raportti"
    :avain :raportti
    :riippuu []}

   {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit"
    :urakan_alkuvuosi 2024
    :nakyvyys_alkaen 2024
    :hoitotyyppi #{"MHU" "MHU+"}
    :jarjestys 10
    :paatostyyppi "raportti"
    :avain :raportti
    :riippuu []}])
