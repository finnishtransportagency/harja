(ns harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone :as kone]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmukset]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.domain.lupaus-domain :as lupaus-domain]))

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
        tulos (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi)]
    (is (not (nil? tulos)))))

;; Varmista, että mhu ja mhu+ urakat saa oikean urakkatyypin
(deftest urakan-hoitotyyppi-test
  (let [vaativa-hoitourakka-f false
        vaativa-hoitourakka-t true]
    (is (= "MHU" (kone/urakan-hoitotyyppi vaativa-hoitourakka-f)))
    (is (= "MHU+" (kone/urakan-hoitotyyppi vaativa-hoitourakka-t)))))

;; 2023 ei ole MHU+ urakoita käynnissä ja mitään ei löydy
(deftest mhu+-vuodelle-2023-palautaa-oikein
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2023
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 3 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2023))))
    (is (= 3 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 3 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))
    (is (= 3 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026))))
    (is (= 3 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027))))))

(deftest mhu+-vuodelle-2021-ei-saisi-palauttaa-mitaan-test
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2021
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 0 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2017))))
    (is (= 0 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2018))))
    (is (= 3 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2019))))
    (is (= 3 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2020))))
    (is (= 3 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2021))))))

(deftest mhu+-vuodelle-2024-palautaa-oikein
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2024
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        odotettu-lista '({:hoitotyyppi #{"MHU+"} :jarjestys 2 :nakyvyys_alkaen 2024 :nakyvyys_asti 2024 :nimi "Tavoitehinnan muutokset" :paatostyyppi "tavoitehinnan-muutokset" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 3 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun indeksikorjaus" :paatostyyppi "indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 4 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun tavoite- ja kattohinta" :paatostyyppi "hoitovuoden-lopun-hinta-v2" :tyyppi "B" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 5 :nakyvyys_alkaen 2024 :nimi "Tavoitehinnan alitus" :paatostyyppi "tavoitehinta" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan ylitys" :paatostyyppi "tavoitehinta" :tyyppi "B" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 7 :nakyvyys_alkaen 2024 :nimi "Kattohinnan ylitys" :paatostyyppi "kattohinta" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "bonus" :urakan_alkuvuosi 2019}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "sanktio" :urakan_alkuvuosi 2019}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "taytetty" :urakan_alkuvuosi 2019}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 9 :nakyvyys_alkaen 2024 :nimi "Hoidonjohtopalkkion muutos" :paatostyyppi "hoidonjohtopalkkio" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 10 :nakyvyys_alkaen 2024 :nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :paatostyyppi "raportti" :urakan_alkuvuosi 2024})]
    (is (= odotettu-lista (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024)))
    (is (= odotettu-lista (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)))
    (is (= odotettu-lista (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026)))
    (is (= odotettu-lista (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027)))
    (is (= odotettu-lista (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028)))))

(deftest mhu+-vuodelle-2025-palautaa-oikein
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2025
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        odotettu-lista '({:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 2 :nakyvyys_alkaen 2025 :nimi "Tavoitehinnan pysyvät muutokset" :paatostyyppi "tavoitehinnan-pysyvat-muutokset" :urakan_alkuvuosi 2025}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 3 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun indeksikorjaus" :paatostyyppi "indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 4 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun tavoite- ja kattohinta" :paatostyyppi "hoitovuoden-lopun-hinta-v2" :tyyppi "B" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 5 :nakyvyys_alkaen 2024 :nimi "Tavoitehinnan alitus" :paatostyyppi "tavoitehinta" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan ylitys" :paatostyyppi "tavoitehinta" :tyyppi "B" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU+"} :jarjestys 7 :nakyvyys_alkaen 2024 :nimi "Kattohinnan ylitys" :paatostyyppi "kattohinta" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "bonus" :urakan_alkuvuosi 2019}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "sanktio" :urakan_alkuvuosi 2019}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "taytetty" :urakan_alkuvuosi 2019}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 9 :nakyvyys_alkaen 2024 :nimi "Hoidonjohtopalkkion muutos" :paatostyyppi "hoidonjohtopalkkio" :urakan_alkuvuosi 2024}
                         {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 10 :nakyvyys_alkaen 2024 :nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :paatostyyppi "raportti" :urakan_alkuvuosi 2024})]
    (is (= odotettu-lista (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)))
    (is (= odotettu-lista (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026)))
    (is (= odotettu-lista (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027)))
    (is (= odotettu-lista (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028)))
    (is (= odotettu-lista (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2029)))))

(deftest mhu-vuodelle-2024-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2024
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 13 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 13 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))
    (is (= 13 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026))))
    (is (= 13 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027))))
    (is (= 13 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028))))))

(deftest mhu-vuodelle-2024-toimii-kun-paallekaiset-filtteroity
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2024
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024) nil nil nil)
        lupausmaara (filter #(= "lupaus" (:paatostyyppi %)) filtteroidyt-paatokset)
        ;; Lupauksia on vain yksi
        _ (is (= 1 (count lupausmaara)))
        ;; "Hoitovuoden lopun tavoite- ja kattohinta" - päätöksiä on vain yksi
        hoitovuoden-lopun-hinta-maara (filter #(= "hoitovuoden-lopun-hinta" (:paatostyyppi %)) filtteroidyt-paatokset)
        _ (is (= 1 (count hoitovuoden-lopun-hinta-maara)))]
    (is (= 6 (count (kone/filtteroi-mahdolliset-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024) nil nil nil))))
    (is (= 6 (count (kone/filtteroi-mahdolliset-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025) nil nil nil))))
    (is (= 6 (count (kone/filtteroi-mahdolliset-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026) nil nil nil))))
    (is (= 6 (count (kone/filtteroi-mahdolliset-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027) nil nil nil))))
    (is (= 6 (count (kone/filtteroi-mahdolliset-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028) nil nil nil))))))

(deftest hintapaatosten-filtterointi-toimii
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2024
        urakan-loppuvuosi (+ urakan-alkuvuosi 7)
        ;; Toteutuneita kustannuksia, tavoitehintaa tai kattohintaa ei määritellä
        filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024) nil nil nil)
        kattohintamaara1 (filter #(= "kattohinta" (:paatostyyppi %)) filtteroidyt-paatokset)
        tavoitehintamaara1 (filter #(= "tavoitehinta" (:paatostyyppi %)) filtteroidyt-paatokset)
        _ (is (= 0 (count kattohintamaara1)))
        _ (is (= 0 (count tavoitehintamaara1)))
        ;; Toteutuneet kustannukset ylittää sekä tavoite että kattohinnan
        filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024) 10 8 9)
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
    (is (= 13 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 13 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))
    (is (= 13 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026))))
    (is (= 13 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027))))
    (is (= 13 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028))))))

(deftest mhu-vuodelle-2025-palautaa-oikein
  (let [odotetut-paatokset '({:hoitotyyppi #{"MHU"} :jarjestys 2 :nakyvyys_alkaen 2021 :nakyvyys_asti 2028 :nimi "Tavoitehinnan muutokset" :paatostyyppi "tavoitehinnan-muutokset" :urakan_alkuvuosi 2021}
                             {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 2 :nakyvyys_alkaen 2025 :nimi "Tavoitehinnan pysyvät muutokset" :paatostyyppi "tavoitehinnan-pysyvat-muutokset" :urakan_alkuvuosi 2025}
                             {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 3 :nakyvyys_alkaen 2024 :nimi "Hoitovuoden lopun indeksikorjaus" :paatostyyppi "indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024}
                             {:hoitotyyppi #{"MHU"} :jarjestys 4 :nakyvyys_alkaen 2025 :nimi "Hoitovuoden lopun tavoite- ja kattohinta" :paatostyyppi "hoitovuoden-lopun-hinta-v2" :tyyppi "C" :urakan_alkuvuosi 2025}
                             {:hoitotyyppi #{"MHU"} :jarjestys 5 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan alitus" :paatostyyppi "tavoitehinta" :urakan_alkuvuosi 2019}
                             {:hoitotyyppi #{"MHU"} :jarjestys 6 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan ylitys" :paatostyyppi "tavoitehinta" :tyyppi "A" :urakan_alkuvuosi 2019}
                             {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6 :nakyvyys_alkaen 2019 :nimi "Tavoitehinnan ylitys" :paatostyyppi "tavoitehinta" :tyyppi "B" :urakan_alkuvuosi 2024}
                             {:hoitotyyppi #{"MHU"} :jarjestys 7 :nakyvyys_alkaen 2019 :nimi "Kattohinnan ylitys" :paatostyyppi "kattohinta" :urakan_alkuvuosi 2019}
                             {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "bonus" :urakan_alkuvuosi 2019}
                             {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "sanktio" :urakan_alkuvuosi 2019}
                             {:hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :nakyvyys_alkaen 2019 :nimi "Lupaukset" :paatostyyppi "lupaus" :tyyppi "taytetty" :urakan_alkuvuosi 2019}
                             {:hoitotyyppi #{"MHU"} :jarjestys 9 :nakyvyys_alkaen 2024 :nimi "Hoidonjohtopalkkion muutos" :paatostyyppi "hoidonjohtopalkkio" :urakan_alkuvuosi 2021}
                             {:hoitotyyppi #{"MHU"} :jarjestys 10 :nakyvyys_alkaen 2024 :nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :paatostyyppi "raportti" :urakan_alkuvuosi 2020})
        mhu-tyyppi "MHU"
        urakan-alkuvuosi 2025
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        paatokset-25 (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)
        filtteroidyt-paatokset (kone/filtteroi-mahdolliset-paatokset paatokset-25 nil nil nil)
        hoitovuoden-lopun-hinta-maara (filter #(= "hoitovuoden-lopun-hinta-v2" (:paatostyyppi %)) filtteroidyt-paatokset)
        _ (is (= 1 (count hoitovuoden-lopun-hinta-maara)))]
    (is (= odotetut-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025)))
    (is (= odotetut-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026)))
    (is (= odotetut-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027)))
    (is (= odotetut-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028)))
    (is (= odotetut-paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2029)))))

(deftest mhu-vuodelle-2019-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2019
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2019))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2020))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2021))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2022))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2023))))))

(deftest mhu-vuodelle-2020-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2020
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2020))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2021))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2022))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2023))))
    (is (= 8 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))))

(deftest mhu-vuodelle-2021-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2021
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2021))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2022))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2023))))
    (is (= 10 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 10 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))))

(deftest mhu-2021-vuodelle-2024
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2021
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)
        paatokset (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024)]
    ;; Hoitovuoden lopun tavoite- ja kattohintaan tuli yksittäinen speksimuutos, niin varmistetaan sen toiminta
    (is (= 1 (count (filter
                      #(= "Hoitovuoden lopun tavoite- ja kattohinta" (:nimi %))
                      paatokset))))))


(deftest paatosmaarat-mhu-tyypilla-test
  (let [mhu-paatokset (kone/mahdolliset-paatokset-tyypilla "MHU" kone/paatostyypit)
        mhu+-paatokset (kone/mahdolliset-paatokset-tyypilla "MHU+" kone/paatostyypit)]
    (is (= 18 (count mhu-paatokset)))
    (is (= 12 (count mhu+-paatokset)))))

(deftest paatosmaarat-hoitokauden-alkuvuosilla-test
  (let [urakan-alkuvuosi-2019-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2019 kone/paatostyypit)
        urakan-alkuvuosi-2020-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2020 kone/paatostyypit)
        urakan-alkuvuosi-2021-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2021 kone/paatostyypit)
        urakan-alkuvuosi-2022-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2022 kone/paatostyypit)
        urakan-alkuvuosi-2023-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2023 kone/paatostyypit)
        urakan-alkuvuosi-2024-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2024 kone/paatostyypit)
        urakan-alkuvuosi-2025-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2025 kone/paatostyypit)]
    (is (= 7 (count urakan-alkuvuosi-2019-paatokset)))
    (is (= 8 (count urakan-alkuvuosi-2020-paatokset)))
    (is (= 11 (count urakan-alkuvuosi-2021-paatokset)))
    (is (= 11 (count urakan-alkuvuosi-2022-paatokset)))
    (is (= 11 (count urakan-alkuvuosi-2023-paatokset)))
    (is (= 20 (count urakan-alkuvuosi-2024-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2025-paatokset)))))

(deftest paatosmaarat-nakyvyys-asti-test
  (let [urakan-alkuvuosi-2019-paatokset (kone/mahdolliset-paatokset-nakyvyys-asti 2019 kone/paatostyypit)
        urakan-alkuvuosi-2020-paatokset (kone/mahdolliset-paatokset-nakyvyys-asti 2020 kone/paatostyypit)
        urakan-alkuvuosi-2021-paatokset (kone/mahdolliset-paatokset-nakyvyys-asti 2021 kone/paatostyypit)
        urakan-alkuvuosi-2022-paatokset (kone/mahdolliset-paatokset-nakyvyys-asti 2022 kone/paatostyypit)
        urakan-alkuvuosi-2023-paatokset (kone/mahdolliset-paatokset-nakyvyys-asti 2023 kone/paatostyypit)
        urakan-alkuvuosi-2024-paatokset (kone/mahdolliset-paatokset-nakyvyys-asti 2024 kone/paatostyypit)
        urakan-alkuvuosi-2025-paatokset (kone/mahdolliset-paatokset-nakyvyys-asti 2025 kone/paatostyypit)]
    (is (= 22 (count urakan-alkuvuosi-2019-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2020-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2021-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2022-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2023-paatokset)))
    (is (= 22 (count urakan-alkuvuosi-2024-paatokset)))
    (is (= 18 (count urakan-alkuvuosi-2025-paatokset)))))

(deftest paatosmaarat-nakyvyys-vuodesta-test
  (let [nakyvyysvuosi-2019-paatokset (kone/mahdolliset-paatokset-nakyvyys-vuodella 2019 kone/paatostyypit)
        nakyvyysvuosi-2020-paatokset (kone/mahdolliset-paatokset-nakyvyys-vuodella 2020 kone/paatostyypit)
        nakyvyysvuosi-2021-paatokset (kone/mahdolliset-paatokset-nakyvyys-vuodella 2021 kone/paatostyypit)
        nakyvyysvuosi-2022-paatokset (kone/mahdolliset-paatokset-nakyvyys-vuodella 2022 kone/paatostyypit)
        nakyvyysvuosi-2023-paatokset (kone/mahdolliset-paatokset-nakyvyys-vuodella 2023 kone/paatostyypit)
        nakyvyysvuosi-2024-paatokset (kone/mahdolliset-paatokset-nakyvyys-vuodella 2024 kone/paatostyypit)
        nakyvyysvuosi-2025-paatokset (kone/mahdolliset-paatokset-nakyvyys-vuodella 2025 kone/paatostyypit)]
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
        yhdistetyt-paatokset (kone/yhdista-mapit pk-paatokset db-paatokset)
        yksi-db-paatos (conj [] (first db-paatokset))
        yksi-tietokannasta (kone/yhdista-mapit pk-paatokset yksi-db-paatos)]
    (is (= 9 (count yhdistetyt-paatokset)))
    (is (= "db" (:tyyppi (first yhdistetyt-paatokset))))
    (is (= "db" (:tyyppi (last yhdistetyt-paatokset))))

    ;; Yksi tietokannasta
    (is (= 9 (count yksi-tietokannasta)))
    (is (= "db" (:tyyppi (first yksi-tietokannasta))))
    (is (= "pk" (:tyyppi (last yksi-tietokannasta))))))

(deftest valmistele-lupauspaatokset-test
  (let [urakkaid 36
        indeksi "MAKU 2015"
        valittu-hoitovuosi 2024
        paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}
                   {:nimi "Lupaukset" :tyyppi "sanktio" :jarjestys 2}
                   {:nimi "Lupaukset" :tyyppi "taytetty" :jarjestys 3}]
        toteutuneet-pisteet 10
        luvatut-pisteet 10
        tarjous-tavoitehinta 100
        tavoitehinta 99
        paatokset-ei-kumpikaan (kone/valmistele-lupauspaatokset (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset
                                 toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi)
        _ (is (= 1 (count paatokset-ei-kumpikaan)))
        _ (is (= "taytetty" (:tyyppi (first paatokset-ei-kumpikaan))))

        toteutuneet-pisteet 10
        luvatut-pisteet 15
        tarjous-tavoitehinta 100
        tavoitehinta 99
        paatokset-sanktio (kone/valmistele-lupauspaatokset (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset
                            toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi)
        _ (is (= 1 (count paatokset-sanktio)))
        _ (is (= "sanktio" (:tyyppi (first paatokset-sanktio))))

        toteutuneet-pisteet 15
        luvatut-pisteet 10
        tarjous-tavoitehinta 100
        tavoitehinta 99
        paatokset-bonus (kone/valmistele-lupauspaatokset (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset toteutuneet-pisteet
                          luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi)
        _ (is (= 1 (count paatokset-bonus)))
        _ (is (= "bonus" (:tyyppi (first paatokset-bonus))))]))

(deftest valmistele-lupauspaatokset-laskenta-yhdenmukaisuus-test
  (testing "Varmistetaan, että lupausbonus/sanktio lasketaan yhteisen domain-funktion mukaisesti"
    (let [urakkaid 36
          indeksi "MAKU 2015"
          valittu-hoitovuosi 2024
          paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}
                     {:nimi "Lupaukset" :tyyppi "sanktio" :jarjestys 2}
                     {:nimi "Lupaukset" :tyyppi "taytetty" :jarjestys 3}]
          toteutuneet-pisteet 15
          luvatut-pisteet 10
          tarjous-tavoitehinta 100000M ;; 100 000 €
          tavoitehinta 99000M
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
                                   toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi)
          lupauspaatos (first valmistellut-paatokset)]
      
      (is (= 1 (count valmistellut-paatokset)) "Vain yksi päätös palautetaan")
      (is (= "bonus" (:tyyppi lupauspaatos)) "Päätös on bonuspäätös")
      (is (= odotettu-bonus (:lupausbonus lupauspaatos))
          "Lupausbonus vastaa yhteistä laskentaa"))))

(deftest valmistele-lupauspaatokset-sanktio-laskenta-yhdenmukaisuus-test
  (testing "Varmistetaan, että lupaussanktio lasketaan yhteisen domain-funktion mukaisesti"
    (let [urakkaid 36
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
          valmistellut-paatokset (kone/valmistele-lupauspaatokset 
                                   (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset 
                                   toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi)
          lupauspaatos (first valmistellut-paatokset)]
      
      (is (= 1 (count valmistellut-paatokset)) "Vain yksi päätös palautetaan")
      (is (= "sanktio" (:tyyppi lupauspaatos)) "Päätös on sanktioon johtava päätös")
      (is (= odotettu-sanktio (:lupaussanktio lupauspaatos)) 
          "Lupaussanktio vastaa yhteistä laskentaa"))))


(deftest valmistele-lupauspaatokset-puuttuvat-prosentit-test
  (testing "Varmistetaan, että puuttuvilla bonus/sanktioprosenteilla palautetaan virheellinen Lupaukset-päätös"
    (let [urakkaid 36
          indeksi "MAKU 2015"
          valittu-hoitovuosi 2024
          paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}
                     {:nimi "Lupaukset" :tyyppi "sanktio" :jarjestys 2}
                     {:nimi "Lupaukset" :tyyppi "taytetty" :jarjestys 3}]
          toteutuneet-pisteet 15
          luvatut-pisteet 10
          tarjous-tavoitehinta 100000M
          tavoitehinta 99000M]
      
      ;; Stubataan urakan parametrit niin että bonus- ja sanktioprosentit puuttuvat (nil)
      (with-redefs [urakat-kyselyt/hae-urakan-parametrit 
                    (fn [db params] 
                      [{:lupauspaatoksen_bonusprosentti nil
                        :lupauspaatoksen_sanktioprosentti nil}])]
        (let [valmistellut-paatokset (kone/valmistele-lupauspaatokset 
                                       (:db jarjestelma) false valittu-hoitovuosi urakkaid paatokset 
                                       toteutuneet-pisteet luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi)
              lupauspaatos (first valmistellut-paatokset)]
          
          (is (= 1 (count valmistellut-paatokset)) "Vain yksi päätös palautetaan")
          (is (= "Lupaukset" (:nimi lupauspaatos)) "Päätöksen nimi on Lupaukset")
          (is (some? (:virhe lupauspaatos)) "Päätöksessä on virhe")
          (is (str/includes? (:virhe lupauspaatos) "prosentit") 
              "Virheviesti mainitsee puuttuvat prosentit")
          ;; Varmistetaan että päätös EI ole taytetty-tyyppinen (vanha bugi)
          (is (not= "taytetty" (:tyyppi lupauspaatos)) 
              "Päätös ei saa olla taytetty-tyyppinen kun prosentit puuttuvat"))))))

