(ns harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone :as kone]
            [harja.testi :refer :all]))

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
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 11 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 11 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))
    (is (= 11 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026))))
    (is (= 11 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027))))
    (is (= 11 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028))))))

(deftest mhu+-vuodelle-2025-palautaa-oikein
  (let [mhu-tyyppi "MHU+"
        urakan-alkuvuosi 2025
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 11 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))
    (is (= 11 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026))))
    (is (= 11 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027))))
    (is (= 11 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028))))
    (is (= 11 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2029))))))

(deftest mhu-vuodelle-2024-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2024
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 12 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 12 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))
    (is (= 12 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026))))
    (is (= 12 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027))))
    (is (= 12 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028))))))

(deftest mhu-vuodelle-2025-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2025
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 12 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))
    (is (= 12 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2026))))
    (is (= 12 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2027))))
    (is (= 12 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2028))))
    (is (= 12 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2029))))))

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
    (is (= 10 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))))

(deftest mhu-vuodelle-2021-palautaa-oikein
  (let [mhu-tyyppi "MHU"
        urakan-alkuvuosi 2021
        urakan-loppuvuosi (+ urakan-alkuvuosi 5)]
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2021))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2022))))
    (is (= 7 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2023))))
    (is (= 10 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2024))))
    (is (= 10 (count (kone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi 2025))))))


(deftest paatosmaarat-mhu-tyypilla-test
  (let [mhu-paatokset (kone/mahdolliset-paatokset-tyypilla "MHU" kone/paatostyypit)
        mhu+-paatokset (kone/mahdolliset-paatokset-tyypilla "MHU+" kone/paatostyypit)]
    (is (= 17 (count mhu-paatokset)))
    (is (= 11 (count mhu+-paatokset)))))

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
    (is (= 20 (count urakan-alkuvuosi-2024-paatokset)))
    (is (= 21 (count urakan-alkuvuosi-2025-paatokset)))))

(deftest paatosmaarat-hoitokauden-loppuvuodella-test
  (let [urakan-loppuvuosi-2019-paatokset (kone/mahdolliset-paatokset-urakan-loppuvuodella 2019 kone/paatostyypit)
        urakan-loppuvuosi-2020-paatokset (kone/mahdolliset-paatokset-urakan-loppuvuodella 2020 kone/paatostyypit)
        urakan-loppuvuosi-2021-paatokset (kone/mahdolliset-paatokset-urakan-loppuvuodella 2021 kone/paatostyypit)
        urakan-loppuvuosi-2022-paatokset (kone/mahdolliset-paatokset-urakan-loppuvuodella 2022 kone/paatostyypit)
        urakan-loppuvuosi-2023-paatokset (kone/mahdolliset-paatokset-urakan-loppuvuodella 2023 kone/paatostyypit)
        urakan-loppuvuosi-2024-paatokset (kone/mahdolliset-paatokset-urakan-loppuvuodella 2024 kone/paatostyypit)
        urakan-loppuvuosi-2025-paatokset (kone/mahdolliset-paatokset-urakan-loppuvuodella 2025 kone/paatostyypit)]
    (is (= 21 (count urakan-loppuvuosi-2019-paatokset)))
    (is (= 21 (count urakan-loppuvuosi-2020-paatokset)))
    (is (= 21 (count urakan-loppuvuosi-2021-paatokset)))
    (is (= 21 (count urakan-loppuvuosi-2022-paatokset)))
    (is (= 21 (count urakan-loppuvuosi-2023-paatokset)))
    (is (= 21 (count urakan-loppuvuosi-2024-paatokset)))
    (is (= 21 (count urakan-loppuvuosi-2025-paatokset)))))

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
    (is (= 17 (count nakyvyysvuosi-2024-paatokset)))
    (is (= 22 (count nakyvyysvuosi-2025-paatokset)))))

(deftest yhdista-mapit-test
  (let [;; pk viittaa päätöskoneeseen, ja db databaseen
        pk-paatokset [{:nimi "Lupaukset" :tyyppi "pk" }
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
  (let [urakkaid 1
        indeksi "MAKU 2015"
        paatokset [{:nimi "Lupaukset" :tyyppi "bonus" :jarjestys 1}
                   {:nimi "Lupaukset" :tyyppi "sanktio" :jarjestys 2}
                   {:nimi "Lupaukset" :tyyppi "taytetty" :jarjestys 3}]
        toteutuneet-pisteet 10
        luvatut-pisteet 10
        tarjous-tavoitehinta 100
        tavoitehinta 99
        paatokset-ei-kumpikaan (kone/valmistele-lupauspaatokset (:db jarjestelma) urakkaid paatokset toteutuneet-pisteet
                                 luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi)
        _ (is (= 1 (count paatokset-ei-kumpikaan)))
        _ (is (= "taytetty" (:tyyppi (first paatokset-ei-kumpikaan))))

        toteutuneet-pisteet 10
        luvatut-pisteet 15
        tarjous-tavoitehinta 100
        tavoitehinta 99
        paatokset-sanktio (kone/valmistele-lupauspaatokset (:db jarjestelma) urakkaid paatokset toteutuneet-pisteet
                            luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi)
        _ (is (= 1 (count paatokset-sanktio)))
        _ (is (= "sanktio" (:tyyppi (first paatokset-sanktio))))

        toteutuneet-pisteet 15
        luvatut-pisteet 10
        tarjous-tavoitehinta 100
        tavoitehinta 99
        paatokset-bonus (kone/valmistele-lupauspaatokset (:db jarjestelma) urakkaid paatokset toteutuneet-pisteet
                          luvatut-pisteet tavoitehinta tarjous-tavoitehinta indeksi)
        _ (is (= 1 (count paatokset-bonus)))
        _ (is (= "bonus" (:tyyppi (first paatokset-bonus))))]))

