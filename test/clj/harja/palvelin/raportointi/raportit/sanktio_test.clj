(ns harja.palvelin.raportointi.raportit.sanktio-test
  (:require [clojure.test :refer :all]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.palvelin.raportointi.raportit.sanktio :as sanktio]
            [harja.palvelin.palvelut.laadunseuranta.laadunseuranta-tulosteet :as tulosteet])
  (:import (org.apache.poi.xssf.usermodel XSSFWorkbook)))

(deftest urakkatasoraportin-otsikko-noudattaa-yhteista-rakennetta
  (let [alkupvm #inst "2025-10-01T00:00:00.000-00:00"
        loppupvm #inst "2026-09-30T00:00:00.000-00:00"
        aikajakso (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))
        raportti (#'sanktio/koosta-urakkataso-runko
                  "Urakka"
                  alkupvm
                  loppupvm
                  []
                  []
                  []
                  []
                  []
                  false)]
    (is (= "Urakka" (get-in raportti [1 :nimi])))
    (is (= true (get-in raportti [1 :piilota-otsikko?])))
    (is (= [:otsikko-title "Sanktiot, bonukset ja arvonvähennykset"]
           (nth raportti 2)))
    (is (= [:teksti (str "Urakka | Aikaväli: " aikajakso)
            {:luokka "raportin-otsikkorivi"}]
           (nth raportti 3)))
    (is (= "Urakka" (get-in raportti [1 :urakan-nimi])))
    (is (= aikajakso (get-in raportti [1 :aikajakso])))
    (is (= :iso (get-in raportti [1 :otsikon-koko])))))

(deftest urakkatasoraportin-excel-yhteenveto-sailyttaa-otsikon
  (let [alkupvm #inst "2025-10-01T00:00:00.000-00:00"
        loppupvm #inst "2026-09-30T00:00:00.000-00:00"
        aikajakso (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))
        raportti (#'sanktio/koosta-urakkataso-runko
                  "Urakka"
                  alkupvm
                  loppupvm
                  []
                  []
                  []
                  []
                  []
                  false
                  false
                  :excel)
        workbook (XSSFWorkbook.)]
    (excel/muodosta-excel raportti workbook)
    (let [sheet (.getSheetAt workbook 0)
          tekstit (mapcat (fn [rivi]
                            (keep (fn [solu]
                                    (when (= org.apache.poi.ss.usermodel.CellType/STRING
                                             (.getCellType solu))
                                      (.getStringCellValue solu)))
                              (iterator-seq (.cellIterator rivi))))
                    (iterator-seq (.rowIterator sheet)))]
      (is (= "Yhteenveto" (.getSheetName sheet)))
      (is (some #(= "Sanktiot, bonukset ja arvonvähennykset" %) tekstit))
      (is (some #(= (str "Urakka | Aikaväli: " aikajakso) %) tekstit)))))

(deftest sanktiolajit-ja-tyypit-naytetaan-ilman-toteutuneita-arvoja
  (let [sanktiolajit [{:sanktiolaji_koodi "sakko"
                       :sanktiolaji_nimi "Sakko"
                       :sanktiolaji_jarjestys 2
                       :sanktiotyyppi_koodi "sakko"
                       :sanktiotyyppi_nimi "Sakko"}
                      {:sanktiolaji_koodi "muistutus"
                       :sanktiolaji_nimi "Muistutus"
                       :sanktiolaji_jarjestys 1
                       :sanktiotyyppi_koodi "ensimmainen"
                       :sanktiotyyppi_nimi "Ensimmäinen"}
                      {:sanktiolaji_koodi "muistutus"
                       :sanktiolaji_nimi "Muistutus"
                       :sanktiolaji_jarjestys 1
                       :sanktiotyyppi_koodi "toinen"
                       :sanktiotyyppi_nimi "Toinen"}]
        raportin-osat (vec (#'sanktio/koosta-sanktio-taulukot sanktiolajit {}))
        taulukko (second raportin-osat)
        rivit (nth taulukko 3)]
    (is (= [:otsikko "Sanktiot"] (first raportin-osat)))
    (is (:himmennetty? (first rivit)))
    (is (= ["Ensimmäinen" 0] (:rivi (first rivit))))
    (is (= ["Toinen" 0] (:rivi (second rivit))))
    (is (= ["Yhteensä" 0] (:rivi (last rivit))))
    (is (= "Tyyppi" (get-in (last raportin-osat) [2 0 :otsikko])))))

(deftest sanktiotyypin-koodi-nolla-toistaa-sanktiolajin-nimen
  (let [sanktiolajit [{:sanktiolaji_koodi "tenttikeskiarvo-sanktio"
                       :sanktiolaji_nimi "Vastuuhenkilön tenttipistemäärän alentuminen"
                       :sanktiolaji_jarjestys 1
                       :sanktiotyyppi_koodi 0
                       :sanktiotyyppi_nimi "Ei tarvita sanktiotyyppiä"}]
        raportin-osat (vec (#'sanktio/koosta-sanktio-taulukot sanktiolajit {}))
        taulukko (second raportin-osat)
        rivi (first (nth taulukko 3))]
    (is (= ["Vastuuhenkilön tenttipistemäärän alentuminen" 0]
           (:rivi rivi)))))

(deftest tulosteraportin-sanktiotyyppi-koodi-nolla-toistaa-sanktiolajin-nimen
  (let [raportti (tulosteet/sanktiot-ja-bonukset-raportti
                   nil
                   nil
                   "Urakka"
                   false
                   #{}
                   #{}
                   [{:laji :tenttikeskiarvo-sanktio
                     :tyyppi {:koodi 0
                              :nimi "Ei tarvita sanktiotyyppiä"}
                     :summa 100}])
        taulukko (last raportti)
        rivi (first (nth taulukko 3))]
    (is (= "Vastuuhenkilön tenttipistemäärän alentuminen"
           (nth rivi 2)))))

(deftest arvonvahennys-ei-kuulu-sanktiolajitaulukoihin
  (let [sanktiolajit [{:sanktiolaji_koodi "sakko"
                       :sanktiolaji_nimi "Sakko"
                       :sanktiolaji_jarjestys 1
                       :sanktiotyyppi_koodi "sakko"
                       :sanktiotyyppi_nimi "Sakko"}
                      {:sanktiolaji_koodi "arvonvahennyssanktio"
                       :sanktiolaji_nimi "Arvonvähennys"
                       :sanktiolaji_jarjestys 2
                       :sanktiotyyppi_koodi "arvonvahennys"
                       :sanktiotyyppi_nimi "Arvonvähennys"}]
        taulukot (->> (#'sanktio/koosta-sanktio-taulukot sanktiolajit {})
                   (filter #(and (vector? %)
                              (= :taulukko (first %))))
                   vec)]
    (is (= ["Sakko"] (mapv #(get-in % [1 :otsikko]) taulukot)))
    (is (not-any? #(= "Arvonvähennys" (get-in % [1 :otsikko])) taulukot))))

(deftest rahasarakkeet-nimetaan-taulukon-mukaan
  (let [raportti (#'sanktio/koosta-urakkataso-runko
                  "Urakka"
                  (java.util.Date.)
                  (java.util.Date.)
                  []
                  []
                  []
                  [{:sanktiolaji_koodi "sakko"
                    :sanktiolaji_nimi "Sakko"
                    :sanktiolaji_jarjestys 1
                    :sanktiotyyppi_koodi "sakko"
                    :sanktiotyyppi_nimi "Sakko"}]
                  [{:bonuslaji_koodi "bonus"
                    :bonuslaji_nimi "Bonus"
                    :bonuslaji_jarjestys 1}]
                  false)
        taulukot (filter #(and (vector? %)
                            (= :taulukko (first %)))
                   (tree-seq coll? seq raportti))
        rahasarakkeen-otsikko (fn [taulukon-otsikko]
                                (get-in (some #(when (= taulukon-otsikko (get-in % [1 :otsikko])) %)
                                          taulukot)
                                  [2 1 :otsikko]))]
    (is (= "Sanktio (€)" (rahasarakkeen-otsikko "Sakko")))
    (is (= "Bonus (€)" (rahasarakkeen-otsikko "Bonukset")))
    (is (= "Arvonvähennys (€)" (rahasarakkeen-otsikko "Arvonvähennykset")))))

(deftest urakkatasorunko-sailyttaa-profiilin-rakenteen-ilman-toteumia
  (let [raportti (#'sanktio/koosta-urakkataso-runko
                  "Urakka"
                  (java.util.Date.)
                  (java.util.Date.)
                  []
                  []
                  []
                  [{:sanktiolaji_koodi "muistutus"
                    :sanktiolaji_nimi "Muistutus"
                    :sanktiolaji_jarjestys 1
                    :sanktiotyyppi_koodi "kirjallinen"
                    :sanktiotyyppi_nimi "Kirjallinen"}]
                  []
                  false)]
    (is (some #(and (vector? %)
                 (= :otsikko (first %))
                 (= "Sanktiot" (second %)))
          raportti))
    (is (some #(and (vector? %)
                 (= :taulukko (first %))
                 (= "Tyyppi" (get-in % [2 0 :otsikko]))
                 (= ["Kirjallinen" 0]
                    (:rivi (first (nth % 3 1)))))
          raportti))))

(deftest bonus-ja-arvonvahennys-ovat-omia-osioitaan
  (let [raportti (#'sanktio/koosta-urakkataso-runko
                  "Urakka"
                  (java.util.Date.)
                  (java.util.Date.)
                  []
                  []
                  []
                  []
                  [{:bonuslaji_koodi "bonus"
                    :bonuslaji_nimi "Bonus"
                    :bonuslaji_jarjestys 1}]
                  false)
        otsikot (->> raportti
                  (filter #(and (vector? %)
                             (= :taulukko (first %))))
                  (map #(get-in % [1 :otsikko]))
                  set)]
    (is (contains? otsikot "Bonukset"))
    (is (contains? otsikot "Arvonvähennykset"))))

(deftest bonus-ja-arvonvahennys-otsikot-eivat-tuplannu
  (let [raportti (#'sanktio/koosta-urakkataso-runko
                  "Urakka"
                  (java.util.Date.)
                  (java.util.Date.)
                  []
                  []
                  []
                  []
                  [{:bonuslaji_koodi "bonus"
                    :bonuslaji_nimi "Bonus"
                    :bonuslaji_jarjestys 1}]
                  false)
        elementit raportti
        otsikon-esiintymat (fn [otsikko]
                             (count (filter #(or (= [:otsikko otsikko] %)
                                               (and (vector? %)
                                                 (= :taulukko (first %))
                                                 (= otsikko (get-in % [1 :otsikko]))))
                                      elementit)))]
    (is (= 1 (otsikon-esiintymat "Bonukset")))
    (is (= 1 (otsikon-esiintymat "Arvonvähennykset")))))

(deftest tyhja-arvonvahennystaulukko-sailyttaa-kategorian
  (let [taulukko (#'sanktio/koosta-arvonvahennys-taulukko [])]
    (is (= ["Arvonvähennys" 0]
           (first (second taulukko))))))

(deftest yllapidon-muistutus-tunnistetaan-lajikoodilla
  (let [raportti (#'sanktio/koosta-urakkataso-runko
                  "Ylläpito"
                  (java.util.Date.)
                  (java.util.Date.)
                  [{:id 1
                    :sakkoryhma "yllapidon_muistutus"
                    :sanktiolaji_koodi "yllapidon_muistutus"
                    :sanktiotyyppi_koodi "muistutus"
                    :sanktiotyyppi_nimi "Ylläpidon muistutus"
                    :yllapitoluokka :luokka-1
                    :summa 0
                    :suorasanktio false}]
                  []
                  []
                  []
                  []
                  true)
        yhteenveto (some #(when (and (vector? %)
                                  (= :sininen-laatikko (first %)))
                            %)
                     (tree-seq coll? seq raportti))
        muistutukset (some #(when (= "Muistutukset" (:avain %)) %)
                       (nth yhteenveto 2))]
    (is (= "1 kpl" (:arvo muistutukset)))))

(deftest urakkatasoerittely-ei-tuplaa-sama-sanktiota
  (let [sanktio {:sanktio_id 1
                 :sanktiolaji_koodi "sakko"
                 :sanktiolaji_nimi "Sakko"
                 :sanktiolaji_jarjestys 1
                 :sanktiotyyppi_koodi "sakko"
                 :sanktiotyyppi_nimi "Sakko"
                 :summa 100}
        raportti (#'sanktio/koosta-urakkataso-runko
                  "Urakka"
                  (java.util.Date.)
                  (java.util.Date.)
                  [sanktio sanktio]
                  []
                  []
                  [sanktio]
                  []
                  false)
        taulukko (some #(when (and (vector? %)
                                (= :taulukko (first %))
                                (= "Sakko" (get-in % [1 :otsikko])))
                          %)
                   raportti)
        rivit (nth taulukko 3)]
    (is (= ["Sakko" 100] (:rivi (first rivit))))
    (is (= ["Yhteensä" 100] (:rivi (last rivit))))))

(deftest urakkaerittely-tuotetaan-raportin-suorina-alkioina
  ;; Regressio: urakkaerittely palautettiin aiemmin muodossa
  ;; [:otsikko "Urakat" [:otsikko urakka] [:taulukko ...] ...], jolloin HTML- ja
  ;; PDF-muodostimet destrukturoivat vain [_ teksti] ja pudottivat taulukot
  ;; äänettömästi. Erittelyn alkioiden pitää olla raportin suoria sisaralkioita.
  (let [sanktio {:sanktio_id 1
                 :urakka_id 42
                 :urakan_nimi "Testiurakka"
                 :sanktiolaji_koodi "sakko"
                 :sanktiolaji_nimi "Sakko"
                 :sanktiolaji_jarjestys 1
                 :sanktiotyyppi_koodi "sakko"
                 :sanktiotyyppi_nimi "Sakko"
                 :summa 100}
        raportti (#'sanktio/koosta-urakkataso-runko
                  "Koko maa"
                  (java.util.Date.)
                  (java.util.Date.)
                  [sanktio]
                  []
                  []
                  [sanktio]
                  []
                  false
                  true)
        alkiot (vec (drop 2 raportti))]
    (is (every? #(and (vector? %) (keyword? (first %))) alkiot)
      "Kaikki raportin alkiot ovat raporttielementtejä")
    (is (some #(= [:otsikko "Urakat"] %) alkiot)
      "Urakat-otsikko on oma alkionsa")
    (is (some #(= [:otsikko "Testiurakka"] %) alkiot)
      "Urakan otsikko on oma alkionsa")
    (is (every? #(= 2 (count %))
          (filter #(= :otsikko (first %)) alkiot))
      "Otsikkoalkioissa ei ole kiedottuja lapsielementtejä")
    (is (some #(and (= :taulukko (first %))
                 (= "Testiurakka" (:sheet-nimi (second %))))
          alkiot)
      "Urakkaerittelyn taulukot ovat raportin suoria alkioita")))

(deftest arvonvahennys-lasketaan-yhteen-vain-kerran
  (let [arvonvahennys {:sanktio_id 42
                       :sanktiolaji_koodi "arvonvahennyssanktio"
                       :sanktiolaji_nimi "Arvonvähennys"
                       :summa 100}
        raportti (#'sanktio/koosta-urakkataso-runko
                  "Urakka"
                  (java.util.Date.)
                  (java.util.Date.)
                  []
                  []
                  [arvonvahennys arvonvahennys]
                  []
                  []
                  false)
        yhteenveto (some #(when (and (vector? %)
                                  (= :sininen-laatikko (first %)))
                            %)
                     (tree-seq coll? seq raportti))
        yhteensa (some #(when (= "Yhteensä" (:avain %)) %)
                   (nth yhteenveto 2))]
    (is (= 100 (:arvo yhteensa)))))

(deftest urakkaerittelyn-arvonvahennys-lasketaan-yhteen-vain-kerran
  (let [arvonvahennys {:sanktio_id 42
                       :urakka_id 7
                       :urakan_nimi "Urakka"
                       :sanktiolaji_koodi "arvonvahennyssanktio"
                       :sanktiolaji_nimi "Arvonvähennys"
                       :summa 100}
        raportti (#'sanktio/koosta-urakkataso-runko
                  "Koko maa"
                  (java.util.Date.)
                  (java.util.Date.)
                  []
                  []
                  [arvonvahennys arvonvahennys]
                  []
                  []
                  false
                  true)
        taulukko (some #(when (and (vector? %)
                                (= :taulukko (first %))
                                (= "Arvonvähennykset" (get-in % [1 :otsikko])))
                          %)
                   raportti)
        yhteensa (some #(when (= "Yhteensä" (first (:rivi %))) %)
                   (nth taulukko 3))]
    (is (= 100 (second (:rivi yhteensa))))))

(deftest yllapitoluokat-esitetaan-pk-luokkina
  (let [sanktiot [{:yllapitoluokka 4 :summa 100}
                  {:yllapitoluokka nil :summa 200}]
        taulukko (#'sanktio/koosta-yllapito-taulukko sanktiot)
        rivit (map :rivi (nth taulukko 3))]
    (is (some #(= ["PK-luokka 2a" 1 100] %) rivit))
    (is (some #(= ["Ei PK-luokkaa" 1 200] %) rivit))))

(deftest samannimiset-urakat-saavat-vakaat-valilehtinimet
  (let [alkupvm #inst "2025-10-01T00:00:00.000-00:00"
        loppupvm #inst "2030-09-30T00:00:00.000-00:00"
        sanktio (fn [urakka-id sanktio-id]
                  {:urakka_id urakka-id
                   :sanktio_id sanktio-id
                   :urakan_nimi "Sama urakka"
                   :urakan_alkupvm alkupvm
                   :urakan_loppupvm loppupvm
                   :sanktiolaji_koodi "sakko"
                   :sanktiolaji_nimi "Sakko"
                   :sanktiolaji_jarjestys 1
                   :sanktiotyyppi_koodi "sakko"
                   :sanktiotyyppi_nimi "Sakko"
                   :summa 100})
        sanktiolaji (fn [urakka-id]
                      {:urakka_id urakka-id
                       :sanktiolaji_koodi "sakko"
                       :sanktiolaji_nimi "Sakko"
                       :sanktiolaji_jarjestys 1
                       :sanktiotyyppi_koodi "sakko"
                       :sanktiotyyppi_nimi "Sakko"})
        raportti (#'sanktio/koosta-urakkataso-runko
                  "Koko maa"
                  alkupvm
                  loppupvm
                  [(sanktio 2 22) (sanktio 1 11)]
                  []
                  []
                  [(sanktiolaji 2) (sanktiolaji 1)]
                  []
                  false
                  true
                  :excel)
        sheet-nimet (->> raportti
                      (tree-seq coll? seq)
                      (filter #(and (vector? %)
                                 (= :taulukko (first %))))
                      (map #(get-in % [1 :sheet-nimi]))
                      distinct
                      vec)]
    (is (= ["Sama urakka 2025-2030 (1)"
            "Sama urakka 2025-2030 (2)"]
           sheet-nimet))))

(deftest tunnistamaton-yllapitosanktio-erotellaan-sakoista
  (let [tunnettu {:sanktio_id 1
                  :sanktiolaji_koodi "yllapidon_sakko"
                  :yllapitoluokka 1
                  :summa -100}
        tunnistamaton {:sanktio_id 2
                       :sanktiolaji_koodi nil
                       :sanktiotyyppi_nimi "Tuntematon"
                       :summa -50}
        rivin-haku (fn [taulukko otsikko]
                     (some #(let [rivi (or (:rivi %) %)]
                              (when (= otsikko (first rivi))
                                rivi))
                       (nth taulukko 3)))
        taulukot (#'sanktio/koosta-yllapidon-taulukot
                  [tunnettu tunnistamaton]
                  [])
        sakko-taulukko (first (filter #(= "Sakot ylläpitoluokittain"
                                          (get-in % [1 :otsikko]))
                                taulukot))
        tunnistamattomat-taulukko (first (filter #(= "Tunnistamattomat sanktiot"
                                                     (get-in % [1 :otsikko]))
                                           taulukot))]
    (is (= ["Yhteensä" 1 -100]
           (rivin-haku sakko-taulukko "Yhteensä")))
    (is (= ["Tuntematon" -50]
           (rivin-haku tunnistamattomat-taulukko "Tuntematon")))))
