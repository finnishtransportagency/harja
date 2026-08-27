(ns harja.palvelin.raportointi.raportit.sanktio-test
  (:require [clojure.test :refer :all]
       [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.sanktio :as sanktio]))

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
                                (= "sakko" (get-in % [1 :sheet-nimi])))
                          %)
                   raportti)
        rivit (nth taulukko 3)]
    (is (= ["Sakko" 100] (:rivi (first rivit))))
    (is (= ["Yhteensä" 100] (:rivi (last rivit))))))
