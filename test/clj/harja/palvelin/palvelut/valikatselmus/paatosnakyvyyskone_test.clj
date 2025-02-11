(ns harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone :as kone]
            [harja.testi :refer :all]))

;; Varmistetaan, että kone palauttaa jotain
(deftest palauttaa-jotain
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2020
        kuluva-hoitovuosi 2024
        tulos (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi kuluva-hoitovuosi)]
    (is (not (nil? tulos)))))

;; Varmista, että mhu ja mhu+ urakat saa oikean urakkatyypin
(deftest urakan-hoitotyyppi-test
  (let [vaativa-hoitourakka-f false
        vaativa-hoitourakka-t true]
    (is (= "MHU" (kone/urakan-hoitotyyppi vaativa-hoitourakka-f)))
    (is (= "MHU+" (kone/urakan-hoitotyyppi vaativa-hoitourakka-t)))))

(deftest mhu+-vuodelle-2023-palautaa-oikein
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2023]
    (is (= 3 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2023))))
    (is (= 3 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2024))))
    (is (= 3 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2025))))
    (is (= 3 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2026))))
    (is (= 3 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2027))))))

(deftest mhu+-vuodelle-2024-palautaa-oikein
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2024]
    (is (= 8 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2024))))
    (is (= 8 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2025))))
    (is (= 8 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2026))))
    (is (= 8 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2027))))
    (is (= 8 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2028))))))

(deftest mhu-vuodelle-2019-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2019]
    (is (= 7 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2019))))
    (is (= 7 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2020))))
    (is (= 7 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2021))))
    (is (= 7 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2022))))
    (is (= 7 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2023))))))

(deftest mhu-vuodelle-2020-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2020]
    (is (= 7 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2020))))
    (is (= 7 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2021))))
    (is (= 7 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2022))))
    (is (= 7 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2023))))
    (is (= 7 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2024))))))

(deftest mhu-vuodelle-2021-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2021]
    (is (= 8 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2021))))
    (is (= 8 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2022))))
    (is (= 8 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2023))))
    (is (= 8 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2024))))
    (is (= 11 (count (kone/paatosnakyvyyskone mhu-tyyppi urakan-alkuvuosi 2025))))))


(deftest paatosmaarat-mhu-tyypilla-test
  (let [mhu-paatokset (kone/mahdolliset-paatokset-tyypilla "MHU" kone/paatostyypit)
        mhu+-paatokset (kone/mahdolliset-paatokset-tyypilla "MHU+" kone/paatostyypit)]
    (is (= 16 (count mhu-paatokset)))
    (is (= 8 (count mhu+-paatokset)))))

(deftest paatosmaarat-hoitokauden-alkuvuosilla-test
  (let [urakan-alkuvuosi-2019-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2019 kone/paatostyypit)
        urakan-alkuvuosi-2020-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2020 kone/paatostyypit)
        urakan-alkuvuosi-2021-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2021 kone/paatostyypit)
        urakan-alkuvuosi-2022-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2022 kone/paatostyypit)
        urakan-alkuvuosi-2023-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2023 kone/paatostyypit)
        urakan-alkuvuosi-2024-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2024 kone/paatostyypit)
        urakan-alkuvuosi-2025-paatokset (kone/mahdolliset-paatokset-urakan-alkuvuodella 2025 kone/paatostyypit)]
    (is (= 7 (count urakan-alkuvuosi-2019-paatokset)))
    (is (= 10 (count urakan-alkuvuosi-2020-paatokset)))
    (is (= 11 (count urakan-alkuvuosi-2021-paatokset)))
    (is (= 11 (count urakan-alkuvuosi-2022-paatokset)))
    (is (= 11 (count urakan-alkuvuosi-2023-paatokset)))
    (is (= 16 (count urakan-alkuvuosi-2024-paatokset)))
    (is (= 16 (count urakan-alkuvuosi-2025-paatokset)))))

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
    (is (= 13 (count nakyvyysvuosi-2024-paatokset)))
    (is (= 16 (count nakyvyysvuosi-2025-paatokset)))))
