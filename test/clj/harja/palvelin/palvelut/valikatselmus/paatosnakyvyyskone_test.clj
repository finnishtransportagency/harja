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
  (let [odotetut-paatokset '({:hoitotyyppi #{"MHU"} :jarjestys 2 :nakyvyys_alkaen 2021 :nakyvyys_asti 2028 :nimi "Tavoitehinnan muutokset" :paatostyyppi "tavoitehinnan-muutokset" :urakan_alkuvuosi 2021 :avain :tavoitehinnan-muutokset :riippuu []}
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
        mhu-tyyppi "MHU"
        urakan-alkuvuosi 2025
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        paatokset-25 (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)
        filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset paatokset-25 nil nil nil)
        hoitovuoden-lopun-hinta-maara (filter #(= "hoitovuoden-lopun-hinta-v2" (:paatostyyppi %)) filtteroidyt-paatokset)
        _ (is (= 1 (count hoitovuoden-lopun-hinta-maara)))]
    (is (= odotetut-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)))
    (is (= odotetut-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026)))
    (is (= odotetut-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027)))
    (is (= odotetut-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028)))
    (is (= odotetut-paatokset (apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2029)))))

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
        yhdistetyt-paatokset (yleiset/yhdista-mapit pk-paatokset db-paatokset)
        yksi-db-paatos (conj [] (first db-paatokset))
        yksi-tietokannasta (yleiset/yhdista-mapit pk-paatokset yksi-db-paatos)]
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
                                 tietokanta-paatokset urakan-alkuvuosi)
        _ (is (= 1 (count paatokset-ei-kumpikaan)))
        _ (is (= "taytetty" (:tyyppi (first paatokset-ei-kumpikaan))))

        toteutuneet-pisteet 10
        luvatut-pisteet 15
        tarjous-tavoitehinta 100
        tavoitehinta 99
        paatokset-sanktio (kone/valmistele-lupauspaatokset (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset
                            toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi
                            tietokanta-paatokset urakan-alkuvuosi)
        _ (is (= 1 (count paatokset-sanktio)))
        _ (is (= "sanktio" (:tyyppi (first paatokset-sanktio))))

        toteutuneet-pisteet 15
        luvatut-pisteet 10
        tarjous-tavoitehinta 100
        tavoitehinta 99
        paatokset-bonus (kone/valmistele-lupauspaatokset (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset toteutuneet-pisteet
                          luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi tietokanta-paatokset urakan-alkuvuosi)
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
                                   tietokanta-paatokset urakan-alkuvuosi)
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
                                   tietokanta-paatokset urakan-alkuvuosi)
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
          tietokanta-paatokset (paatos-kyselyt/hae-paatokset db mahdolliset-paatokset urakkaid valittu-hoitovuosi)]

      ;; Stubataan urakan parametrit niin että bonus- ja sanktioprosentit puuttuvat (nil)
      (with-redefs [urakat-kyselyt/hae-urakan-parametrit
                    (fn [_db _params]
                      [{:lupauspaatoksen_bonusprosentti nil
                        :lupauspaatoksen_sanktioprosentti nil}])]
        (let [valmistellut-paatokset (kone/valmistele-lupauspaatokset
                                       (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset
                                       toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi
                                       tietokanta-paatokset urakan-alkuvuosi)
              lupauspaatos (first valmistellut-paatokset)]

          (is (= 1 (count valmistellut-paatokset)) "Vain yksi päätös palautetaan")
          (is (= "Lupaukset" (:nimi lupauspaatos)) "Päätöksen nimi on Lupaukset")
          (is (some? (:virheet lupauspaatos)) "Päätöksessä on virhe")
          (is (str/includes? (:virheet lupauspaatos) "prosentit")
            "Virheviesti mainitsee puuttuvat prosentit"))))))
