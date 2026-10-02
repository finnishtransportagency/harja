(ns harja.palvelin.ajastetut-tehtavat.urakka-parametrit-test
  "Varmistetaan, että urakan parametrit on tallentuneet tietokantaan oikein."
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.domain.urakka :as u]
            [harja.domain.sopimus :as sop]
            [harja.domain.organisaatio :as o]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clojure.spec.alpha :as s])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (harja.domain.roolit EiOikeutta)))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest varmista-urakka-parametrit
  (let [kittila19-urakka-id (hae-urakan-id-nimella "Kittilän MHU 2019-2024")
        kittila19-parametrit (first (q-map (format "SELECT * FROM urakka_parametrit WHERE urakkaid = %s" kittila19-urakka-id)))

        ii-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ii-parametrit (first (q-map (format "SELECT * FROM urakka_parametrit WHERE urakkaid = %s" ii-urakka-id)))

        ;; KOPIO POP MHU Suomussalmi 2024-2029
        suomussalmi-urakka-id (hae-urakan-id-nimella "KOPIO POP MHU Suomussalmi 2024-2029")
        suomussalmi-parametrit (first (q-map (format "SELECT * FROM urakka_parametrit WHERE urakkaid = %s" suomussalmi-urakka-id)))

        ;; Kajaani on MHU+ urakka - Edustaa siis MHU+ urakkaa, jolla on taas omat säätönsä
        kajaani-urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        kajaani-parametrit (first (q-map (format "SELECT * FROM urakka_parametrit WHERE urakkaid = %s" kajaani-urakka-id)))

        ;; Kittilän -25 urakka
        kittila25-urakka-id (hae-urakan-id-nimella "Kittilän MHU 2025-2030")
        kittila25-parametrit (first (q-map (format "SELECT * FROM urakka_parametrit WHERE urakkaid = %s" kittila25-urakka-id)))

        bonusprosentti-tasan-alle-2024 0.13M
        bonusprosentti-yli-2024 0.08M
        sanktioprosentti-tasan-alle-2024 0.33M
        sanktioprosentti-yli-2024 0.18M
        tavoitepalkkion-maksuprosentti-tasan-alle-2024 30M
        tavoitepalkkion-maksuprosentti-yli-2024 75M
        tavoitepakkion-maksimi 3M
        tavoitehinnan-ylityksen-urakoitsijan-maksuprosentti-tasan-alle-2024 30
        tavoitehinnan-ylityksen-tilaajan-maksuprosentti-tasan-alle-2024 70M
        tavoitehinnan-ylityksen-urakoitsijan-maksuprosentti-2024-mhuplus 50
        tavoitehinnan-ylityksen-tilaajan-maksuprosentti-2024-mhuplus 50M
        tavoitehinnan-ylityksen-urakoitsijan-maksuprosentti-yli-2024 75
        tavoitehinnan-ylityksen-tilaajan-maksuprosentti-yli-2024 25M
        kattohinnan-siirron-prosenttirajoitus-alle-2024 nil
        kattohinnan-siirron-prosenttirajoitus-tasan-2024 0.03M
        kattohinnan-siirron-prosenttirajoitus-yli-2024 0.03M
        muokkaa-kattohinta-kasin-tasan-alle-2020 true
        muokkaa-kattohinta-kasin-yli-2020 false
        hoitokauden-lopun-kattohinta-kerroin-tasan-alle-2020 nil
        hoitokauden-lopun-kattohinta-kerroin-tasan-alle-2024 1.1M
        hoitokauden-lopun-kattohinta-kerroin-yli-2024 1.2M
        lisaa-tavoitehintaan-hoitovuodenlopunindeksikorjaus-tasan-alle-2023 false
        lisaa-tavoitehintaan-hoitovuodenlopunindeksikorjaus-yli-2023 true
        maksuera-lahetys-sampo-kaikki true
        kustannussuunnitelma-lahetys-sampo-kaikki true
        laskutusraja-kaytossa-tasan-yli-2025 true
        laskutusraja-kaytossa-alle-2025 false
        muutosten-hallinta-tasan-yli-2025 true
        muutosten-hallinta-alle-2025 false
        hoitovuoden-alun-tavoitehinta-tasan-yli-2025 true
        hoitovuoden-alun-tavoitehinta-alle-2025 false

        _ (is (= (:lupauspaatoksen_bonusprosentti ii-parametrit) bonusprosentti-tasan-alle-2024))
        _ (is (= (:lupauspaatoksen_bonusprosentti kajaani-parametrit) bonusprosentti-yli-2024))
        _ (is (= (:lupauspaatoksen_sanktioprosentti ii-parametrit) sanktioprosentti-tasan-alle-2024))
        _ (is (= (:lupauspaatoksen_sanktioprosentti kajaani-parametrit) sanktioprosentti-yli-2024))

        _ (is (= (:tavoitepalkkion_maksuprosentti ii-parametrit) tavoitepalkkion-maksuprosentti-tasan-alle-2024))
        _ (is (= (:tavoitepalkkion_maksuprosentti kajaani-parametrit) tavoitepalkkion-maksuprosentti-yli-2024))

        _ (is (= (:tavoitepalkkion_maksimi ii-parametrit) tavoitepakkion-maksimi))

        _ (is (= (:tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti kajaani-parametrit) tavoitehinnan-ylityksen-urakoitsijan-maksuprosentti-2024-mhuplus))
        _ (is (= (:tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti kittila25-parametrit) tavoitehinnan-ylityksen-urakoitsijan-maksuprosentti-yli-2024))
        _ (is (= (:tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti ii-parametrit) tavoitehinnan-ylityksen-urakoitsijan-maksuprosentti-tasan-alle-2024))

        _ (is (= (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti kajaani-parametrit) tavoitehinnan-ylityksen-tilaajan-maksuprosentti-2024-mhuplus))
        _ (is (= (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti kittila25-parametrit) tavoitehinnan-ylityksen-tilaajan-maksuprosentti-yli-2024))
        _ (is (= (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti ii-parametrit) tavoitehinnan-ylityksen-tilaajan-maksuprosentti-tasan-alle-2024))

        _ (is (= (:kattohintaylityksen_siirron_prosenttirajoitus ii-parametrit) kattohinnan-siirron-prosenttirajoitus-alle-2024))
        _ (is (= (:kattohintaylityksen_siirron_prosenttirajoitus suomussalmi-parametrit) kattohinnan-siirron-prosenttirajoitus-tasan-2024))
        _ (is (= (:kattohintaylityksen_siirron_prosenttirajoitus kajaani-parametrit) kattohinnan-siirron-prosenttirajoitus-yli-2024))

        _ (is (= (:muokkaa_kattohinta_kasin kittila19-parametrit) muokkaa-kattohinta-kasin-tasan-alle-2020))
        _ (is (= (:muokkaa_kattohinta_kasin ii-parametrit) muokkaa-kattohinta-kasin-yli-2020))
        _ (is (= (:muokkaa_kattohinta_kasin kajaani-parametrit) muokkaa-kattohinta-kasin-yli-2020))

        _ (is (= (:hoitokauden_lopun_kattohinta_kerroin kajaani-parametrit) hoitokauden-lopun-kattohinta-kerroin-yli-2024))
        _ (is (= (:hoitokauden_lopun_kattohinta_kerroin ii-parametrit) hoitokauden-lopun-kattohinta-kerroin-tasan-alle-2024))
        _ (is (= (:hoitokauden_lopun_kattohinta_kerroin kittila19-parametrit) hoitokauden-lopun-kattohinta-kerroin-tasan-alle-2020))

        _ (is (= (:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus ii-parametrit) lisaa-tavoitehintaan-hoitovuodenlopunindeksikorjaus-tasan-alle-2023))
        _ (is (= (:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus kajaani-parametrit) lisaa-tavoitehintaan-hoitovuodenlopunindeksikorjaus-yli-2023))

        _ (is (= (:maksuera_lahetys_sampo kittila19-parametrit) maksuera-lahetys-sampo-kaikki))
        _ (is (= (:maksuera_lahetys_sampo ii-parametrit) maksuera-lahetys-sampo-kaikki))
        _ (is (= (:maksuera_lahetys_sampo suomussalmi-parametrit) maksuera-lahetys-sampo-kaikki))
        _ (is (= (:maksuera_lahetys_sampo kajaani-parametrit) maksuera-lahetys-sampo-kaikki))

        _ (is (= (:kustannussuunnitelma_lahetys_sampo kittila19-parametrit) kustannussuunnitelma-lahetys-sampo-kaikki))
        _ (is (= (:kustannussuunnitelma_lahetys_sampo ii-parametrit) kustannussuunnitelma-lahetys-sampo-kaikki))
        _ (is (= (:kustannussuunnitelma_lahetys_sampo suomussalmi-parametrit) kustannussuunnitelma-lahetys-sampo-kaikki))
        _ (is (= (:kustannussuunnitelma_lahetys_sampo kajaani-parametrit) kustannussuunnitelma-lahetys-sampo-kaikki))

        _ (is (= (:laskutusraja_kaytossa kajaani-parametrit) laskutusraja-kaytossa-tasan-yli-2025))
        _ (is (= (:laskutusraja_kaytossa kittila19-parametrit) laskutusraja-kaytossa-alle-2025))
        _ (is (= (:laskutusraja_kaytossa ii-parametrit) laskutusraja-kaytossa-alle-2025))
        _ (is (= (:laskutusraja_kaytossa suomussalmi-parametrit) laskutusraja-kaytossa-alle-2025))

        _ (is (= (:muutosten_hallinta kajaani-parametrit) muutosten-hallinta-tasan-yli-2025))
        _ (is (= (:muutosten_hallinta kittila19-parametrit) muutosten-hallinta-alle-2025))
        _ (is (= (:muutosten_hallinta ii-parametrit) muutosten-hallinta-alle-2025))
        _ (is (= (:muutosten_hallinta suomussalmi-parametrit) muutosten-hallinta-alle-2025))

        _ (is (= (:hoitovuoden_alun_tavoitehinta_kaytossa kajaani-parametrit) hoitovuoden-alun-tavoitehinta-tasan-yli-2025))
        _ (is (= (:hoitovuoden_alun_tavoitehinta_kaytossa kittila19-parametrit) hoitovuoden-alun-tavoitehinta-alle-2025))
        _ (is (= (:hoitovuoden_alun_tavoitehinta_kaytossa ii-parametrit) hoitovuoden-alun-tavoitehinta-alle-2025))
        _ (is (= (:hoitovuoden_alun_tavoitehinta_kaytossa suomussalmi-parametrit) hoitovuoden-alun-tavoitehinta-alle-2025))]))

