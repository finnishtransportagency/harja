(ns harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.tyokalut.yleiset :as yleiset]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.valikatselmus.apurit :as apurit]
            [harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone :as kone]
            [harja.palvelin.palvelut.valikatselmus.paatostyypit :refer [paatostyypit]]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; Varmistetaan, että kone palauttaa jotain
(deftest palauttaa-jotain
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2020
        urakan-loppuvuosi 2025
        kuluva-hoitovuosi 2024
        tulos (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi)]
    (is (not (nil? tulos)))))

;; Varmista, että mhu ja mhu+ urakat saa oikean urakkatyypin
(deftest urakan-hoitotyyppi-test
  (let [vaativa-hoitourakka-f false
        vaativa-hoitourakka-t true]
    (is (= "MHU" (apurit/urakan-hoitotyyppi vaativa-hoitourakka-f)))
    (is (= "MHU+" (apurit/urakan-hoitotyyppi vaativa-hoitourakka-t)))))

;; 2023 ei ole MHU+ urakoita käynnissä ja mitään ei löydy
(deftest mhu+-vuodelle-2023-palautaa-oikein
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2023
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 3 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2023))))
    (is (= 3 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 3 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))
    (is (= 3 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026))))
    (is (= 3 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027))))))

(deftest mhu+-vuodelle-2021-ei-saisi-palauttaa-mitaan-test
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2021
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 0 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2017))))
    (is (= 0 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2018))))
    (is (= 3 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2019))))
    (is (= 3 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2020))))
    (is (= 3 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2021))))))

(deftest mhu+-vuodelle-2024-palautaa-oikein
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2024
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        odotettu-lista '({:hoitotyyppi #{"MHU+"} :jarjestys 2 :nakyvyys_alkaen 2024 :nakyvyys_asti 2024 :nimi "Tavoitehinnan muutokset" :paatostyyppi "tavoitehinnan-muutokset" :urakan_alkuvuosi 2024 :avain :tavoitehinnan-muutokset :riippuu []}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 3 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun indeksikorjaus" :paatostyyppi "indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024 :avain :indeksikorjaus :riippuu [{:avain :tavoitehinnan-muutokset}]}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 4 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun tavoite- ja kattohinta" :paatostyyppi "hoitovuoden-lopun-hinta-v2" :tyyppi "B" :urakan_alkuvuosi 2024 :avain :hoitovuoden-lopun-hinta :riippuu [{:avain :tavoitehinnan-muutokset} {:avain :indeksikorjaus}]}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 5 :nakyvyys_alkaen 2024 :nimi "Tavoitehinnan alitus" :paatostyyppi "tavoitehinta" :urakan_alkuvuosi 2024 :avain :tavoitehinnan-alitus :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan ylitys" :paatostyyppi "tavoitehinta" :tyyppi "B" :urakan_alkuvuosi 2024 :avain :tavoitehinnan-ylitys :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 7 :nakyvyys_alkaen 2024 :nimi "Kattohinnan ylitys" :paatostyyppi "kattohinta" :urakan_alkuvuosi 2024 :avain :kattohinnan-ylitys :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "bonus" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "sanktio" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "taytetty" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 9 :nakyvyys_alkaen 2024 :nimi "Hoidonjohtopalkkion muutos" :paatostyyppi "hoidonjohtopalkkio" :urakan_alkuvuosi 2024 :avain :hoidonjohtopalkkio :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 10 :nakyvyys_alkaen 2024 :nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :paatostyyppi "raportti" :urakan_alkuvuosi 2024 :avain :raportti :riippuu []})]
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028)))))

(deftest mhu+-vuodelle-2025-palautaa-oikein
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2025
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        odotettu-lista '({:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 2 :nakyvyys_alkaen 2025 :nimi "Tavoitehinnan pysyvät muutokset" :paatostyyppi "tavoitehinnan-pysyvat-muutokset" :urakan_alkuvuosi 2025 :avain :tavoitehinnan-muutokset :riippuu []}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 3 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun indeksikorjaus" :paatostyyppi "indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024 :avain :indeksikorjaus :riippuu [{:avain :tavoitehinnan-muutokset}]}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 4 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun tavoite- ja kattohinta" :paatostyyppi "hoitovuoden-lopun-hinta-v2" :tyyppi "B" :urakan_alkuvuosi 2024 :avain :hoitovuoden-lopun-hinta :riippuu [{:avain :tavoitehinnan-muutokset} {:avain :indeksikorjaus}]}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 5 :nakyvyys_alkaen 2024 :nimi "Tavoitehinnan alitus" :paatostyyppi "tavoitehinta" :urakan_alkuvuosi 2024 :avain :tavoitehinnan-alitus :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan ylitys" :paatostyyppi "tavoitehinta" :tyyppi "B" :urakan_alkuvuosi 2024 :avain :tavoitehinnan-ylitys :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 7 :nakyvyys_alkaen 2024 :nimi "Kattohinnan ylitys" :paatostyyppi "kattohinta" :urakan_alkuvuosi 2024 :avain :kattohinnan-ylitys :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "bonus" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "sanktio" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "taytetty" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 9 :nakyvyys_alkaen 2024 :nimi "Hoidonjohtopalkkion muutos" :paatostyyppi "hoidonjohtopalkkio" :urakan_alkuvuosi 2024 :avain :hoidonjohtopalkkio :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 10 :nakyvyys_alkaen 2024 :nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :paatostyyppi "raportti" :urakan_alkuvuosi 2024 :avain :raportti :riippuu []})]
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2029)))))

(deftest mhu-vuodelle-2025-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2025
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        odotettu-lista '({:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 2 :nakyvyys_alkaen 2025 :nimi "Tavoitehinnan pysyvät muutokset" :paatostyyppi "tavoitehinnan-pysyvat-muutokset" :urakan_alkuvuosi 2025 :avain :tavoitehinnan-muutokset :riippuu []}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 3 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun indeksikorjaus" :paatostyyppi "indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024 :avain :indeksikorjaus :riippuu [{:avain :tavoitehinnan-muutokset}]}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 4 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun tavoite- ja kattohinta" :paatostyyppi "hoitovuoden-lopun-hinta-v2" :tyyppi "B" :urakan_alkuvuosi 2024 :avain :hoitovuoden-lopun-hinta :riippuu [{:avain :tavoitehinnan-muutokset} {:avain :indeksikorjaus}]}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 5 :nakyvyys_alkaen 2024 :nimi "Tavoitehinnan alitus" :paatostyyppi "tavoitehinta" :urakan_alkuvuosi 2024 :avain :tavoitehinnan-alitus :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan ylitys" :paatostyyppi "tavoitehinta" :tyyppi "B" :urakan_alkuvuosi 2024 :avain :tavoitehinnan-ylitys :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 7 :nakyvyys_alkaen 2024 :nimi "Kattohinnan ylitys" :paatostyyppi "kattohinta" :urakan_alkuvuosi 2024 :avain :kattohinnan-ylitys :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "bonus" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "sanktio" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "taytetty" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 9 :nakyvyys_alkaen 2024 :nimi "Hoidonjohtopalkkion muutos" :paatostyyppi "hoidonjohtopalkkio" :urakan_alkuvuosi 2024 :avain :hoidonjohtopalkkio :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 10 :nakyvyys_alkaen 2024 :nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :paatostyyppi "raportti" :urakan_alkuvuosi 2024 :avain :raportti :riippuu []})]
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028)))
    (is (= odotettu-lista (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2029)))))

(deftest mhu-vuodelle-2024-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2024
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 13 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 13 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))
    (is (= 13 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026))))
    (is (= 13 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027))))
    (is (= 13 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028))))))

(deftest mhu-vuodelle-2024-toimii-kun-paallekaiset-filtteroity
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2024
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024) nil nil nil)
        lupausmaara (filter #(= "lupaus" (:paatostyyppi %)) filtteroidyt-paatokset)
        ;; Lupauksia on vain yksi
        _ (is (= 1 (count lupausmaara)))
        ;; "Hoitovuoden lopun tavoite- ja kattohinta" - päätöksiä on vain yksi
        hoitovuoden-lopun-hinta-maara (filter #(= "hoitovuoden-lopun-hinta" (:paatostyyppi %)) filtteroidyt-paatokset)
        _ (is (= 1 (count hoitovuoden-lopun-hinta-maara)))]
    (is (= 6 (count (kone/filtteroi-mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024) nil nil nil))))
    (is (= 6 (count (kone/filtteroi-mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025) nil nil nil))))
    (is (= 6 (count (kone/filtteroi-mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026) nil nil nil))))
    (is (= 6 (count (kone/filtteroi-mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027) nil nil nil))))
    (is (= 6 (count (kone/filtteroi-mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028) nil nil nil))))))

(deftest hintapaatosten-filtterointi-toimii
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2024
        urakan-loppuvuosi (+ urakan-alkuvuosi 7)
        ;; Toteutuneita kustannuksia, tavoitehintaa tai kattohintaa ei määritellä
        filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024) nil nil nil)
        kattohintamaara1 (filter #(= "kattohinta" (:paatostyyppi %)) filtteroidyt-paatokset)
        tavoitehintamaara1 (filter #(= "tavoitehinta" (:paatostyyppi %)) filtteroidyt-paatokset)
        _ (is (= 0 (count kattohintamaara1)))
        _ (is (= 0 (count tavoitehintamaara1)))
        ;; Toteutuneet kustannukset ylittää sekä tavoite että kattohinnan
        filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024) 10 8 9)
        kattohintamaara2 (filter #(= "kattohinta" (:paatostyyppi %)) filtteroidyt-paatokset)
        tavoitehintamaara2 (filter #(= "tavoitehinta" (:paatostyyppi %)) filtteroidyt-paatokset)
        ;; Lupauksia on vain yksi
        _ (is (= 1 (count kattohintamaara2)))
        _ (is (= 1 (count tavoitehintamaara2)))
        ;; "Hoitovuoden lopun tavoite- ja kattohinta" - päätöksiä on vain yksi
        hoitovuoden-lopun-hinta-maara (filter #(= "hoitovuoden-lopun-hinta" (:paatostyyppi %)) filtteroidyt-paatokset)
        _ (is (= 1 (count hoitovuoden-lopun-hinta-maara)))]))

(deftest mhu-vuodelle-2024-palautaa-oikein-kun-6v-urakka
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2024
        urakan-loppuvuosi (+ urakan-alkuvuosi 6)]
    (is (= 13 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 13 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))
    (is (= 13 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026))))
    (is (= 13 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027))))
    (is (= 13 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028))))))

(deftest mhu-vuodelle-2025-palautaa-oikein
  (let [odotetut-kaikki-paatokset '({:hoitotyyppi #{"MHU"} :jarjestys 2 :nakyvyys_alkaen 2021 :nakyvyys_asti 2028 :nimi "Tavoitehinnan muutokset" :paatostyyppi "tavoitehinnan-muutokset" :urakan_alkuvuosi 2021 :avain :tavoitehinnan-muutokset :riippuu []}
                                    {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 2 :nakyvyys_alkaen 2025 :nimi "Tavoitehinnan pysyvät muutokset" :paatostyyppi "tavoitehinnan-pysyvat-muutokset" :urakan_alkuvuosi 2025 :avain :tavoitehinnan-muutokset :riippuu []}
                                    {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 3 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun indeksikorjaus" :paatostyyppi "indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024 :avain :indeksikorjaus :riippuu [{:avain :tavoitehinnan-muutokset}]}
                                    {:hoitotyyppi #{"MHU"} :jarjestys 4 :nakyvyys_alkaen 2025 :nimi "Hoitovuoden lopun tavoite- ja kattohinta" :paatostyyppi "hoitovuoden-lopun-hinta-v2" :tyyppi "C" :urakan_alkuvuosi 2025 :avain :hoitovuoden-lopun-hinta :riippuu [{:avain :tavoitehinnan-muutokset} {:avain :indeksikorjaus}]}
                                    {:hoitotyyppi #{"MHU"} :jarjestys 5 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan alitus" :paatostyyppi "tavoitehinta" :urakan_alkuvuosi 2019 :avain :tavoitehinnan-alitus :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                                    {:hoitotyyppi #{"MHU"} :jarjestys 6 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan ylitys" :paatostyyppi "tavoitehinta" :tyyppi "A" :urakan_alkuvuosi 2019 :avain :tavoitehinnan-ylitys :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                                    {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan ylitys" :paatostyyppi "tavoitehinta" :tyyppi "B" :urakan_alkuvuosi 2024 :avain :tavoitehinnan-ylitys :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                                    {:hoitotyyppi #{"MHU"} :jarjestys 7 :nakyvyys_alkaen 2019 :nimi "Kattohinnan ylitys" :paatostyyppi "kattohinta" :urakan_alkuvuosi 2019 :avain :kattohinnan-ylitys :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                                    {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "bonus" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                                    {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "sanktio" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                                    {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "taytetty" :urakan_alkuvuosi 2019 :avain :lupaus :riippuu [{:avain :hoitovuoden-lopun-hinta :urakan_alkuvuosi_alkaen 2025}]}
                                    {:hoitotyyppi #{"MHU"} :jarjestys 9 :nakyvyys_alkaen 2024 :nimi "Hoidonjohtopalkkion muutos" :paatostyyppi "hoidonjohtopalkkio" :urakan_alkuvuosi 2021 :avain :hoidonjohtopalkkio :riippuu [{:avain :hoitovuoden-lopun-hinta}]}
                                    {:hoitotyyppi #{"MHU"} :jarjestys 10 :nakyvyys_alkaen 2024 :nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :paatostyyppi "raportti" :urakan_alkuvuosi 2020 :avain :raportti :riippuu []})
        odotetut-filtteroidyt-paatokset [{:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "tavoitehinnan-pysyvat-muutokset", :jarjestys 2, :riippuu [], :nimi "Tavoitehinnan pysyvät muutokset", :urakan_alkuvuosi 2025, :avain :tavoitehinnan-muutokset, :nakyvyys_alkaen 2025}
                                         {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "indeksikorjaus", :jarjestys 3, :riippuu [{:avain :tavoitehinnan-muutokset}], :nimi "Hoitovuoden lopun indeksikorjaus", :urakan_alkuvuosi 2024, :avain :indeksikorjaus, :tyyppi nil, :nakyvyys_alkaen 2024}
                                         {:hoitotyyppi #{"MHU"}, :paatostyyppi "hoitovuoden-lopun-hinta-v2", :jarjestys 4, :riippuu [{:avain :tavoitehinnan-muutokset} {:avain :indeksikorjaus}], :nimi "Hoitovuoden lopun tavoite- ja kattohinta", :urakan_alkuvuosi 2025, :avain :hoitovuoden-lopun-hinta, :tyyppi "C", :nakyvyys_alkaen 2025}
                                         {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "lupaus", :jarjestys 8, :riippuu [{:avain :hoitovuoden-lopun-hinta, :urakan_alkuvuosi_alkaen 2025}], :nimi "Lupaukset", :urakan_alkuvuosi 2019, :avain :lupaus, :tyyppi "taytetty", :nakyvyys_alkaen 2019}
                                         {:hoitotyyppi #{"MHU"}, :paatostyyppi "hoidonjohtopalkkio", :jarjestys 9, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Hoidonjohtopalkkion muutos", :urakan_alkuvuosi 2021, :avain :hoidonjohtopalkkio, :nakyvyys_alkaen 2024}
                                         {:hoitotyyppi #{"MHU"}, :paatostyyppi "raportti", :jarjestys 10, :riippuu [], :nimi "Välikatselmuspöytäkirjaan liitettävät raportit", :urakan_alkuvuosi 2020, :avain :raportti, :nakyvyys_alkaen 2024}]
        mhu-tyyppi "MHU"
        urakan-alkuvuosi 2025
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        paatokset-25 (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)
        filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset paatokset-25 nil nil nil)
        hoitovuoden-lopun-hinta-maara (filter #(= "hoitovuoden-lopun-hinta-v2" (:paatostyyppi %)) filtteroidyt-paatokset)
        _ (is (= 1 (count hoitovuoden-lopun-hinta-maara)))]
    (is (= odotetut-kaikki-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)))
    (is (= odotetut-filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025) nil nil nil)))
    (is (= odotetut-kaikki-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026)))
    (is (= odotetut-filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026) nil nil nil)))
    (is (= odotetut-kaikki-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027)))
    (is (= odotetut-kaikki-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028)))
    (is (= odotetut-kaikki-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2029)))))

(deftest mhu-vuodelle-2019-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2019
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2019))))
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2020))))
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2021))))
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2022))))
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2023))))))

(deftest mhu-vuodelle-2020-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2020
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2020))))
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2021))))
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2022))))
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2023))))
    (is (= 8 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))))

(deftest mhu-vuodelle-2021-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2021
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2021))))
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2022))))
    (is (= 7 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2023))))
    (is (= 10 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 10 (count (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))))

(deftest mhu-2021-vuodelle-2024
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2021
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024)]
    ;; Hoitovuoden lopun tavoite- ja kattohintaan tuli yksittäinen speksimuutos, niin varmistetaan sen toiminta
    (is (= 1 (count (filter
                      #(= "Hoitovuoden lopun tavoite- ja kattohinta" (:nimi %))
                      paatokset))))))


(deftest paatosmaarat-mhu-tyypilla-test
  (let [mhu-paatokset (apurit/mahdolliset-paatokset-tyypilla "MHU" paatostyypit)
        mhu+-paatokset (apurit/mahdolliset-paatokset-tyypilla "MHU+" paatostyypit)]
    (is (= 18 (count mhu-paatokset)))
    (is (= 12 (count mhu+-paatokset)))))

(deftest paatosmaarat-hoitokauden-alkuvuosilla-test
  (let [urakan-alkuvuosi-2019-paatokset (apurit/mahdolliset-paatokset-urakan-alkuvuodella 2019 paatostyypit)
        urakan-alkuvuosi-2020-paatokset (apurit/mahdolliset-paatokset-urakan-alkuvuodella 2020 paatostyypit)
        urakan-alkuvuosi-2021-paatokset (apurit/mahdolliset-paatokset-urakan-alkuvuodella 2021 paatostyypit)
        urakan-alkuvuosi-2022-paatokset (apurit/mahdolliset-paatokset-urakan-alkuvuodella 2022 paatostyypit)
        urakan-alkuvuosi-2023-paatokset (apurit/mahdolliset-paatokset-urakan-alkuvuodella 2023 paatostyypit)
        urakan-alkuvuosi-2024-paatokset (apurit/mahdolliset-paatokset-urakan-alkuvuodella 2024 paatostyypit)
        urakan-alkuvuosi-2025-paatokset (apurit/mahdolliset-paatokset-urakan-alkuvuodella 2025 paatostyypit)]
    (is (= 7 (count urakan-alkuvuosi-2019-paatokset)))
    (is (= 8 (count urakan-alkuvuosi-2020-paatokset)))
    (is (= 11 (count urakan-alkuvuosi-2021-paatokset)))
    (is (= 11 (count urakan-alkuvuosi-2022-paatokset)))
    (is (= 11 (count urakan-alkuvuosi-2023-paatokset)))
    (is (= 20 (count urakan-alkuvuosi-2024-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2025-paatokset)))))

(deftest paatosmaarat-nakyvyys-asti-test
  (let [urakan-alkuvuosi-2019-paatokset (apurit/mahdolliset-paatokset-nakyvyys-asti 2019 paatostyypit)
        urakan-alkuvuosi-2020-paatokset (apurit/mahdolliset-paatokset-nakyvyys-asti 2020 paatostyypit)
        urakan-alkuvuosi-2021-paatokset (apurit/mahdolliset-paatokset-nakyvyys-asti 2021 paatostyypit)
        urakan-alkuvuosi-2022-paatokset (apurit/mahdolliset-paatokset-nakyvyys-asti 2022 paatostyypit)
        urakan-alkuvuosi-2023-paatokset (apurit/mahdolliset-paatokset-nakyvyys-asti 2023 paatostyypit)
        urakan-alkuvuosi-2024-paatokset (apurit/mahdolliset-paatokset-nakyvyys-asti 2024 paatostyypit)
        urakan-alkuvuosi-2025-paatokset (apurit/mahdolliset-paatokset-nakyvyys-asti 2025 paatostyypit)]
    (is (= 22 (count urakan-alkuvuosi-2019-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2020-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2021-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2022-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2023-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2024-paatokset)))
    (is (= 18 (count urakan-alkuvuosi-2025-paatokset)))))

(deftest paatosmaarat-nakyvyys-vuodesta-test
  (let [nakyvyysvuosi-2019-paatokset (apurit/mahdolliset-paatokset-nakyvyys-vuodella 2019 paatostyypit)
        nakyvyysvuosi-2020-paatokset (apurit/mahdolliset-paatokset-nakyvyys-vuodella 2020 paatostyypit)
        nakyvyysvuosi-2021-paatokset (apurit/mahdolliset-paatokset-nakyvyys-vuodella 2021 paatostyypit)
        nakyvyysvuosi-2022-paatokset (apurit/mahdolliset-paatokset-nakyvyys-vuodella 2022 paatostyypit)
        nakyvyysvuosi-2023-paatokset (apurit/mahdolliset-paatokset-nakyvyys-vuodella 2023 paatostyypit)
        nakyvyysvuosi-2024-paatokset (apurit/mahdolliset-paatokset-nakyvyys-vuodella 2024 paatostyypit)
        nakyvyysvuosi-2025-paatokset (apurit/mahdolliset-paatokset-nakyvyys-vuodella 2025 paatostyypit)]
    (is (= 8 (count nakyvyysvuosi-2019-paatokset)))
    (is (= 8 (count nakyvyysvuosi-2020-paatokset)))
    (is (= 9 (count nakyvyysvuosi-2021-paatokset)))
    (is (= 9 (count nakyvyysvuosi-2022-paatokset)))
    (is (= 9 (count nakyvyysvuosi-2023-paatokset)))
    (is (= 20 (count nakyvyysvuosi-2024-paatokset)))
    (is (= 22 (count nakyvyysvuosi-2025-paatokset)))))

(deftest yhdista-mapit-test
  (let [;; pk viittaa päätöskoneeseen, ja db databaseen
        pk-paatokset [{:nimi "Lupaukset" :tyyppi "pk"}
                      {:nimi "Tavoitehinnan muutokset" :tyyppi "pk"}
                      {:nimi "Hoitovuoden lopun indeksikorjaus" :tyyppi "pk"}
                      {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "pk"}
                      {:nimi "Tavoitehinnan alitus" :tyyppi "pk"}
                      {:nimi "Tavoitehinnan ylitys" :tyyppi "pk"}
                      {:nimi "Kattohinnan ylitys" :tyyppi "pk"}
                      {:nimi "Hoidonjohtopalkkion muutos" :tyyppi "pk"}
                      {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :tyyppi "pk"}]
        db-paatokset [{:nimi "Lupaukset" :tyyppi "db"}
                      {:nimi "Tavoitehinnan muutokset" :tyyppi "db"}
                      {:nimi "Hoitovuoden lopun indeksikorjaus" :tyyppi "db"}
                      {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "db"}
                      {:nimi "Tavoitehinnan alitus" :tyyppi "db"}
                      {:nimi "Tavoitehinnan ylitys" :tyyppi "db"}
                      {:nimi "Kattohinnan ylitys" :tyyppi "db"}
                      {:nimi "Hoidonjohtopalkkion muutos" :tyyppi "db"}
                      {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :tyyppi "db"}]
        yhdistetyt-paatokset (yleiset/yhdista-mapit :nimi pk-paatokset db-paatokset)
        yksi-db-paatos (conj [] (first db-paatokset))
        yksi-tietokannasta (yleiset/yhdista-mapit :nimi pk-paatokset yksi-db-paatos)]
    (is (= 9 (count yhdistetyt-paatokset)))
    (is (= "db" (:tyyppi (first yhdistetyt-paatokset))))
    (is (= "db" (:tyyppi (last yhdistetyt-paatokset))))

    ;; Yksi tietokannasta
    (is (= 9 (count yksi-tietokannasta)))
    (is (= "db" (:tyyppi (first yksi-tietokannasta))))
    (is (= "pk" (:tyyppi (last yksi-tietokannasta))))))

(deftest valmistele-lupauspaatokset-test
  (let [urakkaid (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-tiedot (first (urakat-kyselyt/hae-urakan-tiedot (:db jarjestelma) urakkaid))
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        urakan-loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
        indeksi "MAKU 2015"
        valittu-hoitovuosi 2024
        paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}
                   {:nimi "Lupaukset" :tyyppi "sanktio" :jarjestys 2}
                   {:nimi "Lupaukset" :tyyppi "taytetty" :jarjestys 3}]
        toteutuneet-pisteet 10
        luvatut-pisteet 10
        tarjous-tavoitehinta 100
        tavoitehinta 99
        mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset "mhu" urakan-alkuvuosi urakan-loppuvuosi valittu-hoitovuosi)
        tietokanta-paatokset (paatos-kyselyt/hae-paatokset db mahdolliset-paatokset urakkaid valittu-hoitovuosi)
        paatokset-ei-kumpikaan (kone/valmistele-lupauspaatokset (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset
                                 toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi
                                 tietokanta-paatokset urakan-alkuvuosi urakan-parametrit)
        _ (is (= 1 (count paatokset-ei-kumpikaan)))
        _ (is (= "taytetty" (:tyyppi (first paatokset-ei-kumpikaan))))

        toteutuneet-pisteet 10
        luvatut-pisteet 15
        tarjous-tavoitehinta 100
        tavoitehinta 99
        paatokset-sanktio (kone/valmistele-lupauspaatokset (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset
                            toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi
                            tietokanta-paatokset urakan-alkuvuosi urakan-parametrit)
        _ (is (= 1 (count paatokset-sanktio)))
        _ (is (= "sanktio" (:tyyppi (first paatokset-sanktio))))

        toteutuneet-pisteet 15
        luvatut-pisteet 10
        tarjous-tavoitehinta 100
        tavoitehinta 99
        paatokset-bonus (kone/valmistele-lupauspaatokset (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset toteutuneet-pisteet
                          luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi tietokanta-paatokset urakan-alkuvuosi urakan-parametrit)
        _ (is (= 1 (count paatokset-bonus)))
        _ (is (= "bonus" (:tyyppi (first paatokset-bonus))))]))

(deftest valmistele-lupauspaatokset-laskenta-yhdenmukaisuus-test
  (testing "Varmistetaan, että lupausbonus/sanktio lasketaan yhteisen domain-funktion mukaisesti"
    (let [urakkaid (hae-urakan-id-nimella "Iin MHU 2021-2026")
          urakan-tiedot (first (urakat-kyselyt/hae-urakan-tiedot (:db jarjestelma) urakkaid))
          urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
          urakan-loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
          indeksi "MAKU 2015"
          valittu-hoitovuosi 2024
          paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}
                     {:nimi "Lupaukset" :tyyppi "sanktio" :jarjestys 2}
                     {:nimi "Lupaukset" :tyyppi "taytetty" :jarjestys 3}]
          toteutuneet-pisteet 15
          luvatut-pisteet 10
          tarjous-tavoitehinta 100000M ;; 100 000 €
          tavoitehinta 99000M
          mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset "mhu" urakan-alkuvuosi urakan-loppuvuosi valittu-hoitovuosi)
          tietokanta-paatokset (paatos-kyselyt/hae-paatokset db mahdolliset-paatokset urakkaid valittu-hoitovuosi)
          ;; Haetaan urakan parametrit bonus/sanktioprosenteille
          urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
          bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
          sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)

          ;; Lasketaan odotettu bonussumma yhteisellä funktiolla
          yhteinen-tulos (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
                           {:toteutuneet-pisteet toteutuneet-pisteet
                            :luvatut-pisteet luvatut-pisteet
                            :tavoitehinta tarjous-tavoitehinta
                            :sanktioprosentti sanktioprosentti
                            :bonusprosentti bonusprosentti})
          odotettu-bonus (:lupausbonus yhteinen-tulos)

          ;; Valmistele lupauspaatos
          valmistellut-paatokset (kone/valmistele-lupauspaatokset
                                   (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset
                                   toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi
                                   tietokanta-paatokset urakan-alkuvuosi urakan-parametrit)
          lupauspaatos (first valmistellut-paatokset)]

      (is (= 1 (count valmistellut-paatokset)) "Vain yksi päätös palautetaan")
      (is (= "bonus" (:tyyppi lupauspaatos)) "Päätös on bonuspäätös")
      (is (= odotettu-bonus (:lupausbonus lupauspaatos))
        "Lupausbonus vastaa yhteistä laskentaa"))))

(deftest valmistele-lupauspaatokset-sanktio-laskenta-yhdenmukaisuus-test
  (testing "Varmistetaan, että lupaussanktio lasketaan yhteisen domain-funktion mukaisesti"
    (let [urakkaid (hae-urakan-id-nimella "Iin MHU 2021-2026")
          urakan-tiedot (first (urakat-kyselyt/hae-urakan-tiedot (:db jarjestelma) urakkaid))
          urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
          urakan-loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
          indeksi "MAKU 2015"
          valittu-hoitovuosi 2024
          paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}
                     {:nimi "Lupaukset" :tyyppi "sanktio" :jarjestys 2}
                     {:nimi "Lupaukset" :tyyppi "taytetty" :jarjestys 3}]
          toteutuneet-pisteet 10
          luvatut-pisteet 15
          tarjous-tavoitehinta 100000M ;; 100 000 €
          tavoitehinta 99000M
          ;; Haetaan urakan parametrit bonus/sanktioprosenteille
          urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
          bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
          sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)

          ;; Lasketaan odotettu sanktiosumma yhteisellä funktiolla
          yhteinen-tulos (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
                           {:toteutuneet-pisteet toteutuneet-pisteet
                            :luvatut-pisteet luvatut-pisteet
                            :tavoitehinta tarjous-tavoitehinta
                            :sanktioprosentti sanktioprosentti
                            :bonusprosentti bonusprosentti})
          odotettu-sanktio (:lupaussanktio yhteinen-tulos)

          ;; Valmistele lupauspaatos
          mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset "mhu" urakan-alkuvuosi urakan-loppuvuosi valittu-hoitovuosi)
          tietokanta-paatokset (paatos-kyselyt/hae-paatokset db mahdolliset-paatokset urakkaid valittu-hoitovuosi)
          valmistellut-paatokset (kone/valmistele-lupauspaatokset
                                   (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset
                                   toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi
                                   tietokanta-paatokset urakan-alkuvuosi urakan-parametrit)
          lupauspaatos (first valmistellut-paatokset)]

      (is (= 1 (count valmistellut-paatokset)) "Vain yksi päätös palautetaan")
      (is (= "sanktio" (:tyyppi lupauspaatos)) "Päätös on sanktioon johtava päätös")
      (is (= odotettu-sanktio (:lupaussanktio lupauspaatos))
        "Lupaussanktio vastaa yhteistä laskentaa"))))


(deftest valmistele-lupauspaatokset-puuttuvat-prosentit-test
  (testing "Varmistetaan, että puuttuvilla bonus/sanktioprosenteilla palautetaan virheellinen Lupaukset-päätös"
    (let [urakkaid (hae-urakan-id-nimella "Iin MHU 2021-2026")
          urakan-tiedot (first (urakat-kyselyt/hae-urakan-tiedot (:db jarjestelma) urakkaid))
          urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
          urakan-loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
          indeksi "MAKU 2015"
          valittu-hoitovuosi 2024
          paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}
                     {:nimi "Lupaukset" :tyyppi "sanktio" :jarjestys 2}
                     {:nimi "Lupaukset" :tyyppi "taytetty" :jarjestys 3}]
          toteutuneet-pisteet 15
          luvatut-pisteet 10
          tarjous-tavoitehinta 100000M
          tavoitehinta 99000M
          mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset "mhu" urakan-alkuvuosi urakan-loppuvuosi valittu-hoitovuosi)
          tietokanta-paatokset (paatos-kyselyt/hae-paatokset db mahdolliset-paatokset urakkaid valittu-hoitovuosi)
          ;; Stubataan urakan parametrit niin että bonus- ja sanktioprosentit puuttuvat (nil)
          urakan-parametrit {:lupauspaatoksen_bonusprosentti nil
                             :lupauspaatoksen_sanktioprosentti nil}]
      (with-redefs [urakat-kyselyt/hae-urakan-parametrit
                    (fn [_db _params]
                      [urakan-parametrit])]
        (let [valmistellut-paatokset (kone/valmistele-lupauspaatokset
                                       (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset
                                       toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi
                                       tietokanta-paatokset urakan-alkuvuosi urakan-parametrit)
              lupauspaatos (first valmistellut-paatokset)]

          (is (= 1 (count valmistellut-paatokset)) "Vain yksi päätös palautetaan")
          (is (= "Lupaukset" (:nimi lupauspaatos)) "Päätöksen nimi on Lupaukset")
          (is (some? (:virheet lupauspaatos)) "Päätöksessä on virhe")
          (is (str/includes? (:virheet lupauspaatos) "prosentit")
            "Virheviesti mainitsee puuttuvat prosentit"))))))

(deftest valmistele-tavoitehinnan-pysyva-muutospaatos
  (let [urakkaid (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-tiedot (first (urakat-kyselyt/hae-urakan-tiedot (:db jarjestelma) urakkaid))
        kuluva-hoitovuosi 2025
        mahdolliset-paatokset [{:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "tavoitehinnan-pysyvat-muutokset", :jarjestys 2, :riippuu [], :nimi "Tavoitehinnan pysyvät muutokset", :urakan_alkuvuosi 2025, :avain :tavoitehinnan-muutokset, :nakyvyys_alkaen 2025}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "indeksikorjaus", :jarjestys 3, :riippuu [{:avain :tavoitehinnan-muutokset}], :nimi "Hoitovuoden lopun indeksikorjaus", :urakan_alkuvuosi 2024, :avain :indeksikorjaus, :tyyppi nil, :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+"}, :paatostyyppi "hoitovuoden-lopun-hinta-v2", :jarjestys 4, :riippuu [{:avain :tavoitehinnan-muutokset} {:avain :indeksikorjaus}], :nimi "Hoitovuoden lopun tavoite- ja kattohinta", :urakan_alkuvuosi 2024, :avain :hoitovuoden-lopun-hinta, :tyyppi "B", :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+"}, :paatostyyppi "tavoitehinta", :jarjestys 5, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Tavoitehinnan alitus", :urakan_alkuvuosi 2024, :avain :tavoitehinnan-alitus, :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "tavoitehinta", :jarjestys 6, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Tavoitehinnan ylitys", :urakan_alkuvuosi 2024, :avain :tavoitehinnan-ylitys, :tyyppi "B", :nakyvyys_alkaen 2019}
                               {:hoitotyyppi #{"MHU+"}, :paatostyyppi "kattohinta", :jarjestys 7, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Kattohinnan ylitys", :urakan_alkuvuosi 2024, :avain :kattohinnan-ylitys, :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :hoitovuosi-kesken? false, :paatostyyppi "lupaus", :jarjestys 8, :virheet ["Toteutuneet pisteet täyttämättä." "Hoitovuoden lopun tavoite- ja kattohinta -päätöstä ei ole vahvistettu."], :lupaussanktio nil, :toteutuneet_pisteet nil, :tarjous_tavoitehinta 1988273.5M, :riippuu [{:avain :hoitovuoden-lopun-hinta, :urakan_alkuvuosi_alkaen 2025}], :tavoitehinta 2091663.722M, :bonusprosentti 0.08M, :nimi "Lupaukset", :urakan_alkuvuosi 2019, :luvatut_pisteet 80, :indeksi "MAKU 2020", :avain :lupaus, :sanktioprosentti 0.18M, :tyyppi "taytetty", :lupausbonus nil, :indeksikorotus nil, :nakyvyys_alkaen 2019}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "hoidonjohtopalkkio", :jarjestys 9, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Hoidonjohtopalkkion muutos", :urakan_alkuvuosi 2024, :avain :hoidonjohtopalkkio, :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "raportti", :jarjestys 10, :riippuu [], :nimi "Välikatselmuspöytäkirjaan liitettävät raportit", :urakan_alkuvuosi 2024, :avain :raportti, :nakyvyys_alkaen 2024}]
        kirjallisesti-sovitut-muutokset 8800
        pysyvat-muutokset 10000
        muutostyo-muutokset 300
        jjh-muutokset -1500
        tehtava-ja-maaramuutos-summa 0
        rahavarausmuutos-summa -31560
        toteumiin-perustuvat-muutokset (+ tehtava-ja-maaramuutos-summa rahavarausmuutos-summa)
        thv-arvonvahennykset-yht -3400
        tavoitehinna-muutokset-yhteensa (+ kirjallisesti-sovitut-muutokset toteumiin-perustuvat-muutokset
                                          thv-arvonvahennykset-yht)
        tavhin-pysyva-muutospaatos (first
                                     (filter #(= (:nimi %) "Tavoitehinnan pysyvät muutokset")
                                       (kone/valmistele-tavoitehinnan-pysyva-muutospaatos true mahdolliset-paatokset kuluva-hoitovuosi
                                         kirjallisesti-sovitut-muutokset pysyvat-muutokset muutostyo-muutokset
                                         jjh-muutokset tehtava-ja-maaramuutos-summa rahavarausmuutos-summa thv-arvonvahennykset-yht)))

        _ (is (= (:kirjallisesti_sovitut_muutokset tavhin-pysyva-muutospaatos) kirjallisesti-sovitut-muutokset))
        _ (is (= (:pysyvat_muutokset tavhin-pysyva-muutospaatos) pysyvat-muutokset))
        _ (is (= (:johto_ja_hallintakorvaus_muutokset tavhin-pysyva-muutospaatos) jjh-muutokset))
        _ (is (= (:muutostyo_muutokset tavhin-pysyva-muutospaatos) muutostyo-muutokset))
        _ (is (= (:toteumiin_perustuvat_muutokset tavhin-pysyva-muutospaatos) (+ tehtava-ja-maaramuutos-summa rahavarausmuutos-summa)))
        _ (is (= (:rahavarausten_muutokset tavhin-pysyva-muutospaatos) rahavarausmuutos-summa))
        _ (is (= (:tehtava_ja_maaratoteumamuutokset tavhin-pysyva-muutospaatos) tehtava-ja-maaramuutos-summa))
        _ (is (= (:arvonvahennysten_muutokset tavhin-pysyva-muutospaatos) thv-arvonvahennykset-yht))
        _ (is (= (:tavoitehinnan_muutokset_yhteensa tavhin-pysyva-muutospaatos) tavoitehinna-muutokset-yhteensa))
        _ (is (= (:hoitovuosi-kesken? tavhin-pysyva-muutospaatos) false))
        _ (is (= (:virheet tavhin-pysyva-muutospaatos) nil))]))

(deftest valmistele-hv-lopun-tavoite-ja-kattohinta-ei-indeksipaatosta-testi-toimii
  (let [urakan-alkuvuosi 2025
        ;indeksipaatos-tehty? false
        valittu-hoitovuosi 2025
        paatos-nimi "Hoitovuoden lopun tavoite- ja kattohinta"
        hoitovuoden-alun-indeksikorjattu-tavoitehinta 2091663.72M
        tavoitehinnan-oikaisut nil
        taman-vuoden-muutokset-summa -26160.00M
        thv-arvonvahennykset-yht -3400M
        hintamuutos (+ taman-vuoden-muutokset-summa thv-arvonvahennykset-yht)
        tavoitehinnan-muutokset (+ taman-vuoden-muutokset-summa thv-arvonvahennykset-yht)
        hoitokauden-lopun-indeksikorjaus 0 #_  33466.62M          ;; Jos indeksikorjausta ei ole tehty, niin tätä ei lisätä, koska se on jo tietokannasta löytyvässä summassa
        hoitovuoden-lopun-tavoitehinta (+ (or hoitovuoden-alun-indeksikorjattu-tavoitehinta 0) (or hintamuutos 0) (or hoitokauden-lopun-indeksikorjaus 0))
        _ (println "hoitovuoden-alun-indeksikorjattu-tavoitehinta" hoitovuoden-alun-indeksikorjattu-tavoitehinta)
        _ (println "hintamuutos" hintamuutos)
        _ (println "hoitokauden-lopun-indeksikorjaus" hoitokauden-lopun-indeksikorjaus)
        _ (println "hoitovuoden-lopun-tavoitehinta" hoitovuoden-lopun-tavoitehinta)
        kattohintakerroin 1.2
        hoitovuoden-lopun-kattohinta-ennen-indeksia-ja-muutoksia (* hoitovuoden-alun-indeksikorjattu-tavoitehinta kattohintakerroin)
        hoitovuoden-lopun-kattohinta (* (+ hoitovuoden-alun-indeksikorjattu-tavoitehinta hintamuutos hoitokauden-lopun-indeksikorjaus) kattohintakerroin)
        lisaa-hoitokauden-lopun-indeksikorjaus true
        tietokanta-paatokset [{:nimi "Tavoitehinnan pysyvät muutokset" :id 1}
                              ;{:nimi "Hoitovuoden lopun indeksikorjaus" :id 2} -- Koska tätä ei ole tehty, niin ei lisätä indeksiä summaan
                              ]

        mahdolliset-paatokset [{:muutostyo_muutokset 300M, :hoitotyyppi #{"MHU+" "MHU"}, :hoitovuosi-kesken? false, :paatostyyppi "tavoitehinnan-pysyvat-muutokset", :jarjestys 2, :rahavarausten_muutokset -31560.00000M, :tehtava_ja_maaratoteumamuutokset 0.0, :virheet nil, :riippuu [], :toteumiin_perustuvat_muutokset -31560.0, :nimi "Tavoitehinnan pysyvät muutokset", :urakan_alkuvuosi 2025, :pysyvat_muutokset 10000M, :arvonvahennysten_muutokset -3400M, :avain :tavoitehinnan-muutokset, :kirjallisesti_sovitut_muutokset 8800M, :tavoitehinnan_muutokset_yhteensa -26160.0, :nakyvyys_alkaen 2025, :johto_ja_hallintakorvaus_muutokset -1500M}
                               {:alkuperaisen_pisteluvun_kuukausi "elokuu 2025", :hoitotyyppi #{"MHU+" "MHU"}, :kuukausien_keskiarvo 164.54999999999998, :tavoitehinnan_muutokset -22760.0, :paatostyyppi "indeksikorjaus", :indeksikorotuksen_prosenttiosuus 1.6, :jarjestys 3, :virheet ["Kustannussuunnitelma vahvistamatta." "Hoitokauden indeksiluvuissa puutteita."], :pistelukujen_muutos 5.8, :hoitokauden_kuukaudet '({:kuukausi "2025 Lokakuu", :indeksiluku 160.8M} {:kuukausi "2025 Marraskuu", :indeksiluku 161.9M} {:kuukausi "2025 Joulukuu", :indeksiluku 163.0M} {:kuukausi "2026 Tammikuu", :indeksiluku 161.1M} {:kuukausi "2026 Helmikuu", :indeksiluku 162.2M} {:kuukausi "2026 Maaliskuu", :indeksiluku 163.3M} {:kuukausi "2026 Huhtikuu", :indeksiluku 164.3M} {:kuukausi "2026 Toukokuu", :indeksiluku 165.4M} {:kuukausi "2026 Kesäkuu", :indeksiluku 166.5M} {:kuukausi "2026 Heinäkuu", :indeksiluku 167.6M} {:kuukausi "2026 Elokuu", :indeksiluku 168.7M} {:kuukausi "2026 Syyskuu", :indeksiluku 169.8M}), :pistelukujen_muutos_prosentteina 3.6, :riippuu [{:avain :tavoitehinnan-muutokset}], :nimi "Hoitovuoden lopun indeksikorjaus", :urakan_alkuvuosi 2024, :avain :indeksikorjaus, :hv_alun_indkorj_tavoitehinta 2091663.722M, :alkuperainen_pisteluku 158.7M, :hv_lopun_tavoitehinta_ennen_indkorj 2068903.722, :tyyppi nil, :hoitokauden_lopun_indeksikorjaus 33466.619552000004, :puuttuvat_kuukaudet (), :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+"}, :paatostyyppi "hoitovuoden-lopun-hinta-v2", :jarjestys 4, :riippuu [{:avain :tavoitehinnan-muutokset} {:avain :indeksikorjaus}], :nimi "Hoitovuoden lopun tavoite- ja kattohinta", :urakan_alkuvuosi 2024, :avain :hoitovuoden-lopun-hinta, :tyyppi "B", :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+"}, :paatostyyppi "tavoitehinta", :jarjestys 5, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Tavoitehinnan alitus", :urakan_alkuvuosi 2024, :avain :tavoitehinnan-alitus, :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "tavoitehinta", :jarjestys 6, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Tavoitehinnan ylitys", :urakan_alkuvuosi 2024, :avain :tavoitehinnan-ylitys, :tyyppi "B", :nakyvyys_alkaen 2019}
                               {:hoitotyyppi #{"MHU+"}, :paatostyyppi "kattohinta", :jarjestys 7, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Kattohinnan ylitys", :urakan_alkuvuosi 2024, :avain :kattohinnan-ylitys, :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :hoitovuosi-kesken? false, :paatostyyppi "lupaus", :jarjestys 8, :virheet ["Toteutuneet pisteet täyttämättä." "Hoitovuoden lopun tavoite- ja kattohinta -päätöstä ei ole vahvistettu."], :lupaussanktio nil, :toteutuneet_pisteet nil, :tarjous_tavoitehinta 1988273.5M, :riippuu [{:avain :hoitovuoden-lopun-hinta, :urakan_alkuvuosi_alkaen 2025}], :tavoitehinta 2091663.722M, :bonusprosentti 0.08M, :nimi "Lupaukset", :urakan_alkuvuosi 2019, :luvatut_pisteet 80, :indeksi "MAKU 2020", :avain :lupaus, :sanktioprosentti 0.18M, :tyyppi "taytetty", :lupausbonus nil, :indeksikorotus nil, :nakyvyys_alkaen 2019}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "hoidonjohtopalkkio", :jarjestys 9, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Hoidonjohtopalkkion muutos", :urakan_alkuvuosi 2024, :avain :hoidonjohtopalkkio, :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "raportti", :jarjestys 10, :riippuu [], :nimi "Välikatselmuspöytäkirjaan liitettävät raportit", :urakan_alkuvuosi 2024, :avain :raportti, :nakyvyys_alkaen 2024}]

        tavoitehinta-vahvistettu? true
        urakan-parametrit {:hoitokauden_lopun_kattohinta_kerroin 1.2
                           :muutosten_hallinta true}
        paatos (first (filter #(= (:nimi %) paatos-nimi)
                        (kone/valmistele-hv-lopun-tavoite-ja-kattohinta
                          true urakan-alkuvuosi valittu-hoitovuosi mahdolliset-paatokset hoitovuoden-alun-indeksikorjattu-tavoitehinta
                          tavoitehinnan-oikaisut taman-vuoden-muutokset-summa thv-arvonvahennykset-yht
                          hoitovuoden-lopun-kattohinta-ennen-indeksia-ja-muutoksia kattohintakerroin hoitokauden-lopun-indeksikorjaus
                          lisaa-hoitokauden-lopun-indeksikorjaus tietokanta-paatokset
                          tavoitehinta-vahvistettu? urakan-parametrit)))
        _ (println "paatos :: :tavoitehinta_jalkeen" (:tavoitehinta_jalkeen paatos) "erotus" (- hoitovuoden-lopun-tavoitehinta (:tavoitehinta_jalkeen paatos)))]
    (is (= paatos-nimi (:nimi paatos)) )
    (is (= hoitovuoden-alun-indeksikorjattu-tavoitehinta (:tavoitehinta_ennen paatos)) )
    (is (= hoitovuoden-lopun-tavoitehinta (:tavoitehinta_jalkeen paatos)) )
    (is (= tavoitehinnan-muutokset (:tavoitehinnan_muutokset paatos)) )
    (is (= hoitokauden-lopun-indeksikorjaus (:hoitokauden_lopun_indeksikorjaus paatos)) )
    (is (= (bigdec hoitovuoden-lopun-kattohinta) (bigdec (:kattohinta paatos))) )
    (is (= kattohintakerroin (:kattohintakerroin paatos)) )))

(deftest valmistele-hv-lopun-tavoite-ja-kattohinta-indeksipaatos-mukana-test
  (let [urakan-alkuvuosi 2025
        indeksipaatos-tehty? true
        valittu-hoitovuosi 2025
        paatos-nimi "Hoitovuoden lopun tavoite- ja kattohinta"
        hoitovuoden-alun-indeksikorjattu-tavoitehinta 2091663.72M
        tavoitehinnan-oikaisut nil
        taman-vuoden-muutokset-summa -26160.00M
        thv-arvonvahennykset-yht -3400M
        hintamuutos (+ taman-vuoden-muutokset-summa thv-arvonvahennykset-yht)
        tavoitehinnan-muutokset (+ taman-vuoden-muutokset-summa thv-arvonvahennykset-yht)
        hoitokauden-lopun-indeksikorjaus 33466.62M          ;; Jos indeksikorjausta ei ole tehty, niin tätä ei lisätä, koska se on jo tietokannasta löytyvässä summassa
        hoitovuoden-lopun-tavoitehinta (+ (or hoitovuoden-alun-indeksikorjattu-tavoitehinta 0) (or hintamuutos 0) (or hoitokauden-lopun-indeksikorjaus 0))
        kattohintakerroin 1.2M
        hoitovuoden-lopun-kattohinta-ennen-indeksia-ja-muutoksia (* hoitovuoden-alun-indeksikorjattu-tavoitehinta kattohintakerroin)
        hoitovuoden-lopun-kattohinta (* (+ hoitovuoden-alun-indeksikorjattu-tavoitehinta hintamuutos (if indeksipaatos-tehty? 0 hoitokauden-lopun-indeksikorjaus)) kattohintakerroin)
        lisaa-hoitokauden-lopun-indeksikorjaus true
        tietokanta-paatokset [{:nimi "Tavoitehinnan pysyvät muutokset" :id 1}
                              {:nimi "Hoitovuoden lopun indeksikorjaus" :id 2}
                              {:nimi "Hoitovuoden lopun indeksikorjaus" :id 3 }]

        mahdolliset-paatokset [{:muutostyo_muutokset 300M, :hoitotyyppi #{"MHU+" "MHU"}, :hoitovuosi-kesken? false, :paatostyyppi "tavoitehinnan-pysyvat-muutokset", :jarjestys 2, :rahavarausten_muutokset -31560.00000M, :tehtava_ja_maaratoteumamuutokset 0.0, :virheet nil, :riippuu [], :toteumiin_perustuvat_muutokset -31560.0, :nimi "Tavoitehinnan pysyvät muutokset", :urakan_alkuvuosi 2025, :pysyvat_muutokset 10000M, :arvonvahennysten_muutokset -3400M, :avain :tavoitehinnan-muutokset, :kirjallisesti_sovitut_muutokset 8800M, :tavoitehinnan_muutokset_yhteensa -26160.0, :nakyvyys_alkaen 2025, :johto_ja_hallintakorvaus_muutokset -1500M}
                               {:alkuperaisen_pisteluvun_kuukausi "elokuu 2025", :hoitotyyppi #{"MHU+" "MHU"}, :kuukausien_keskiarvo 164.54999999999998, :tavoitehinnan_muutokset -22760.0, :paatostyyppi "indeksikorjaus", :indeksikorotuksen_prosenttiosuus 1.6, :jarjestys 3, :virheet ["Kustannussuunnitelma vahvistamatta." "Hoitokauden indeksiluvuissa puutteita."], :pistelukujen_muutos 5.8, :hoitokauden_kuukaudet '({:kuukausi "2025 Lokakuu", :indeksiluku 160.8M} {:kuukausi "2025 Marraskuu", :indeksiluku 161.9M} {:kuukausi "2025 Joulukuu", :indeksiluku 163.0M} {:kuukausi "2026 Tammikuu", :indeksiluku 161.1M} {:kuukausi "2026 Helmikuu", :indeksiluku 162.2M} {:kuukausi "2026 Maaliskuu", :indeksiluku 163.3M} {:kuukausi "2026 Huhtikuu", :indeksiluku 164.3M} {:kuukausi "2026 Toukokuu", :indeksiluku 165.4M} {:kuukausi "2026 Kesäkuu", :indeksiluku 166.5M} {:kuukausi "2026 Heinäkuu", :indeksiluku 167.6M} {:kuukausi "2026 Elokuu", :indeksiluku 168.7M} {:kuukausi "2026 Syyskuu", :indeksiluku 169.8M}), :pistelukujen_muutos_prosentteina 3.6, :riippuu [{:avain :tavoitehinnan-muutokset}], :nimi "Hoitovuoden lopun indeksikorjaus", :urakan_alkuvuosi 2024, :avain :indeksikorjaus, :hv_alun_indkorj_tavoitehinta 2091663.722M, :alkuperainen_pisteluku 158.7M, :hv_lopun_tavoitehinta_ennen_indkorj 2068903.722, :tyyppi nil, :hoitokauden_lopun_indeksikorjaus 33466.619552000004, :puuttuvat_kuukaudet (), :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+"}, :paatostyyppi "hoitovuoden-lopun-hinta-v2", :jarjestys 4, :riippuu [{:avain :tavoitehinnan-muutokset} {:avain :indeksikorjaus}], :nimi "Hoitovuoden lopun tavoite- ja kattohinta", :urakan_alkuvuosi 2024, :avain :hoitovuoden-lopun-hinta, :tyyppi "B", :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+"}, :paatostyyppi "tavoitehinta", :jarjestys 5, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Tavoitehinnan alitus", :urakan_alkuvuosi 2024, :avain :tavoitehinnan-alitus, :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "tavoitehinta", :jarjestys 6, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Tavoitehinnan ylitys", :urakan_alkuvuosi 2024, :avain :tavoitehinnan-ylitys, :tyyppi "B", :nakyvyys_alkaen 2019}
                               {:hoitotyyppi #{"MHU+"}, :paatostyyppi "kattohinta", :jarjestys 7, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Kattohinnan ylitys", :urakan_alkuvuosi 2024, :avain :kattohinnan-ylitys, :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :hoitovuosi-kesken? false, :paatostyyppi "lupaus", :jarjestys 8, :virheet ["Toteutuneet pisteet täyttämättä." "Hoitovuoden lopun tavoite- ja kattohinta -päätöstä ei ole vahvistettu."], :lupaussanktio nil, :toteutuneet_pisteet nil, :tarjous_tavoitehinta 1988273.5M, :riippuu [{:avain :hoitovuoden-lopun-hinta, :urakan_alkuvuosi_alkaen 2025}], :tavoitehinta 2091663.722M, :bonusprosentti 0.08M, :nimi "Lupaukset", :urakan_alkuvuosi 2019, :luvatut_pisteet 80, :indeksi "MAKU 2020", :avain :lupaus, :sanktioprosentti 0.18M, :tyyppi "taytetty", :lupausbonus nil, :indeksikorotus nil, :nakyvyys_alkaen 2019}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "hoidonjohtopalkkio", :jarjestys 9, :riippuu [{:avain :hoitovuoden-lopun-hinta}], :nimi "Hoidonjohtopalkkion muutos", :urakan_alkuvuosi 2024, :avain :hoidonjohtopalkkio, :nakyvyys_alkaen 2024}
                               {:hoitotyyppi #{"MHU+" "MHU"}, :paatostyyppi "raportti", :jarjestys 10, :riippuu [], :nimi "Välikatselmuspöytäkirjaan liitettävät raportit", :urakan_alkuvuosi 2024, :avain :raportti, :nakyvyys_alkaen 2024}]

        tavoitehinta-vahvistettu? true
        urakan-parametrit {:hoitokauden_lopun_kattohinta_kerroin 1.2
                           :muutosten_hallinta true}
        paatos (first (filter #(= (:nimi %) paatos-nimi)
                        (kone/valmistele-hv-lopun-tavoite-ja-kattohinta
                          true urakan-alkuvuosi valittu-hoitovuosi mahdolliset-paatokset hoitovuoden-alun-indeksikorjattu-tavoitehinta
                          tavoitehinnan-oikaisut taman-vuoden-muutokset-summa thv-arvonvahennykset-yht
                          hoitovuoden-lopun-kattohinta-ennen-indeksia-ja-muutoksia kattohintakerroin hoitokauden-lopun-indeksikorjaus
                          lisaa-hoitokauden-lopun-indeksikorjaus tietokanta-paatokset
                          tavoitehinta-vahvistettu? urakan-parametrit)))]
    (is (= paatos-nimi (:nimi paatos)) )
    (is (= hoitovuoden-alun-indeksikorjattu-tavoitehinta (:tavoitehinta_ennen paatos)) )
    (is (= hoitovuoden-lopun-tavoitehinta (:tavoitehinta_jalkeen paatos)) )
    (is (= tavoitehinnan-muutokset (:tavoitehinnan_muutokset paatos)) )
    (is (= hoitokauden-lopun-indeksikorjaus (:hoitokauden_lopun_indeksikorjaus paatos)) )
    (is (= hoitovuoden-lopun-kattohinta (bigdec (:kattohinta paatos))) )
    (is (= kattohintakerroin (:kattohintakerroin paatos)))))

(deftest valmistele-tavoitehinnan-ylityspaatos-kun-kattohinta-ei-ylity-toimii
  (let [urakkaid (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit (:db jarjestelma) urakkaid))
        urakan-alkuvuosi 2025
        urakan-loppuvuosi 2030
        kuluva-hoitovuosi 2025
        mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset "MHU" urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi)
        tietokanta-paatokset []
        paatos-nimi "Tavoitehinnan ylitys"

        hoitovuoden-lopun-tavoitehinta 2098970.34
        hoitovuoden-lopun-kattohinta-ennen-indeksia-ja-muutoksia (* hoitovuoden-lopun-tavoitehinta 1.2)
        ylityksen-maara 100
        toteutuneet-kustannukset (+ hoitovuoden-lopun-tavoitehinta ylityksen-maara)
        tavoitehinta-vahvistettu? false

        vastaus (kone/valmistele-tavoitehinnan-ylityspaatos
                  false urakkaid mahdolliset-paatokset urakan-alkuvuosi
                  urakan-loppuvuosi kuluva-hoitovuosi hoitovuoden-lopun-tavoitehinta
                  hoitovuoden-lopun-kattohinta-ennen-indeksia-ja-muutoksia toteutuneet-kustannukset tietokanta-paatokset
                  tavoitehinta-vahvistettu? urakan-parametrit)
        paatos (first (filter #(= (:nimi %) paatos-nimi) vastaus))
        ]
    (is (= paatos-nimi (:nimi paatos)))
    (is (= (bigdec ylityksen-maara) (bigdec (:ylityksen_maara paatos))))
    (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset paatos)))
    (is (= false (:viimeinen_hoitokausi paatos)))
    (is (= nil (:virheet paatos)))))

(deftest valmistele-tavoitehinnan-ylityspaatos-kun-kattohinta-ylittyy-toimii
  (let [urakkaid (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit (:db jarjestelma) urakkaid))
        urakan-alkuvuosi 2025
        urakan-loppuvuosi 2030
        kuluva-hoitovuosi 2025
        mahdolliset-paatokset (apurit/kaikki-mahdolliset-paatokset "MHU" urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi)
        tietokanta-paatokset []
        paatos-nimi "Tavoitehinnan ylitys"

        hoitovuoden-lopun-tavoitehinta 2098970.34
        hoitovuoden-lopun-kattohinta-ennen-indeksia-ja-muutoksia (* hoitovuoden-lopun-tavoitehinta 1.2)
        ylityksen-maara 100
        toteutuneet-kustannukset (+ hoitovuoden-lopun-tavoitehinta ylityksen-maara)
        tavoitehinta-vahvistettu? false

        vastaus (kone/valmistele-tavoitehinnan-ylityspaatos
                  false urakkaid mahdolliset-paatokset urakan-alkuvuosi
                  urakan-loppuvuosi kuluva-hoitovuosi hoitovuoden-lopun-tavoitehinta
                  hoitovuoden-lopun-kattohinta-ennen-indeksia-ja-muutoksia toteutuneet-kustannukset tietokanta-paatokset
                  tavoitehinta-vahvistettu? urakan-parametrit)
        paatos (first (filter #(= (:nimi %) paatos-nimi) vastaus))
        ]
    (is (= paatos-nimi (:nimi paatos)))
    (is (= (bigdec ylityksen-maara) (bigdec (:ylityksen_maara paatos))))
    (is (= toteutuneet-kustannukset (:toteutuneet_kustannukset paatos)))
    (is (= false (:viimeinen_hoitokausi paatos)))
    (is (= nil (:virheet paatos)))))
