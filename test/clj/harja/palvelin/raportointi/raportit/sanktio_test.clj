(ns harja.palvelin.raportointi.raportit.sanktio-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.raportointi.raportit.sanktio :as sanktio]))

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
    (is (= "Sakko" (get-in (last raportin-osat) [2 0 :otsikko])))))

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
                 (= "Muistutus" (get-in % [2 0 :otsikko]))
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
                                   (= :otsikko (first %))))
                     (map second)
                     set)]
    (is (contains? otsikot "Bonukset"))
    (is (contains? otsikot "Arvovähennykset"))))

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
