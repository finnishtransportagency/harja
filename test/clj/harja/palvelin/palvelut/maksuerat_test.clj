(ns harja.palvelin.palvelut.maksuerat-test
  (:require [clojure.test :refer :all]
            [harja.pvm :as pvm]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.maksuerat :refer :all]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-urakan-maksuerat (component/using
                                                (->Maksuerat)
                                                [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))


(deftest urakan-maksuerat-haettu-oikein-urakalle-1
  (let [maksuerat (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-urakan-maksuerat +kayttaja-jvh+ @oulun-alueurakan-2005-2010-id)]
    (is (= 16 (count maksuerat)))
    (is (= (count (filter #(= :kokonaishintainen (:tyyppi (:maksuera %))) maksuerat)) 2))
    (is (= (count (filter #(= :yksikkohintainen (:tyyppi (:maksuera %))) maksuerat)) 2))
    (is (= (count (filter #(= :bonus (:tyyppi (:maksuera %))) maksuerat)) 2))
    (is (= (count (filter #(= :akillinen-hoitotyo (:tyyppi (:maksuera %))) maksuerat)) 2))
    (is (= (count (filter #(= :lisatyo (:tyyppi (:maksuera %))) maksuerat)) 2))
    (is (= (count (filter #(= :sakko (:tyyppi (:maksuera %))) maksuerat)) 2))
    (is (= (count (filter #(= :indeksi (:tyyppi (:maksuera %))) maksuerat)) 2))
    (is (= (count (filter #(= :muu (:tyyppi (:maksuera %))) maksuerat)) 2))))

(deftest urakan-maksuerat-haettu-oikein-urakalle-Ii-MHU-ennen-2022
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Varmistetaan, että alihankintabonus tulee hoidon johto maksuerään
        sopimus-id (hae-iin-maanteiden-hoitourakan-2021-2026-sopimus-id)
        tpi-hoidonjohto (hae-toimenpideinstanssi-id urakka-id "23151") ;; MHU Hoidon johto
        ;; Tässä on vaarana, että kovakoodatut päivämäärät eivät toimi enää tulevaisuudessa.
        ;; Päivämäärillä haetaan sitä, että 1.10.2022 jälkeen tulevat alihankintabonukset menevät MHU Ylläpito toimenpideinstanssille
        ;; Ja tuota päivää aiemmat menevät MHU Hoidon johto toimenpideinstanssille.
        pvm-2021 (pvm/->pvm "15.01.2022")
        bonus_summa 1000M
        ;; Poistetaan kaikki bonukset ja sanktiot urakalta
        _ (poista-bonukset-ja-sanktiot-aikavalilta urakka-id (pvm/->pvm "01.10.2021") (pvm/->pvm "30.09.2026"))

        ;; Luodaan alihankintabonus vuodelle 2021 - kaikki bonukset menevät hoidon johto toimenpideinstanssille
        _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
               sopimus-id tpi-hoidonjohto pvm-2021 bonus_summa urakka-id "alihankintabonus"))
        ;; Luodaan asiakastyytyvaisyysbonus vuodelle 2021
        _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
               sopimus-id tpi-hoidonjohto pvm-2021 bonus_summa urakka-id "asiakastyytyvaisyysbonus"))
        maksuerat (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-urakan-maksuerat +kayttaja-jvh+ urakka-id)

        ;; Kokonaishintaiset
        kokonaishintaiset-hoidonjohto (first (filter (fn [rivi]
                                                       (= "Iin MHU 2021-2026 MHU ja HJU Hoidon johto" (get-in rivi [:toimenpideinstanssi :nimi])))
                                               (filter #(= :kokonaishintainen (:tyyppi (:maksuera %))) maksuerat)))
        ;; Varmistetaan, että molemmat bonukset kuuluvat hoidon johdon toimenpideinstanssin alle, koska ne on luotu ennen 1.10.2022
        _ (println "kokonaishintaiset-hoidonjohto: " kokonaishintaiset-hoidonjohto)
        _ (is (= 2000.000M (get-in kokonaishintaiset-hoidonjohto [:maksuera :summa])))]
    (is (= 7 (count maksuerat)))
    (is (= (count (filter #(= :kokonaishintainen (:tyyppi (:maksuera %))) maksuerat)) 7))))

(deftest urakan-maksuerat-haettu-oikein-urakalle-Ii-MHU-jalkeen-2022
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ;; Varmistetaan, että alihankintabonus ei tule enää hoidon johto maksuerään, vaan MHU ylläpitoon
        ;; MHU Ylläpito maksuerään pitäisi tulla matkaan alihankintabonus, mutta ei asiakastyytyväisyysbonusta
        sopimus-id (hae-iin-maanteiden-hoitourakan-2021-2026-sopimus-id)
        tpi-yllapito (hae-toimenpideinstanssi-id urakka-id "20191") ;; MHU Ylläpito
        tpi-hoidonjohto (hae-toimenpideinstanssi-id urakka-id "23151") ;; MHU Hoidon johto
        ;; Tässä on vaarana, että kovakoodatut päivämäärät eivät toimi enää tulevaisuudessa.
        ;; Päivämäärillä haetaan sitä, että 1.10.2022 jälkeen tulevat alihankintabonukset menevät MHU Ylläpito toimenpideinstanssille
        ;; Ja tuota päivää aiemmat menevät MHU Hoidon johto toimenpideinstanssille.
        pvm-2022 (pvm/->pvm "15.01.2023")
        bonus_summa 1000M
        ;; Poistetaan kaikki bonukset ja sanktiot urakalta
        _ (poista-bonukset-ja-sanktiot-aikavalilta urakka-id (pvm/->pvm "01.10.2021") (pvm/->pvm "30.09.2026"))
        ;; Luodaan alihankintabonus vuodelle 2022
        _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
               sopimus-id tpi-yllapito pvm-2022 bonus_summa urakka-id "alihankintabonus"))
        ;; Luodaan asiakastyytyvaisyysbonus vuodelle 2022
        _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
               sopimus-id tpi-hoidonjohto pvm-2022 bonus_summa urakka-id "asiakastyytyvaisyysbonus"))

        maksuerat (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-urakan-maksuerat +kayttaja-jvh+ urakka-id)

        hoidonjohto (first (filter (fn [rivi]
                                     (= "Iin MHU 2021-2026 MHU ja HJU Hoidon johto" (get-in rivi [:toimenpideinstanssi :nimi])))
                             (filter #(= :kokonaishintainen (:tyyppi (:maksuera %))) maksuerat)))
        yllapito (first (filter (fn [rivi]
                                  (= "Iin MHU 2021-2026 MHU Ylläpito TP" (get-in rivi [:toimenpideinstanssi :nimi])))
                          (filter #(= :kokonaishintainen (:tyyppi (:maksuera %))) maksuerat)))
        ;; Varmistetaan, että molemmat bonukset kuuluvat hoidon johdon toimenpideinstanssin alle, koska ne on luotu ennen 1.10.2022
        _ (is (= 1000.000M (get-in hoidonjohto [:maksuera :summa])))
        _ (is (= 1000.000M (get-in yllapito [:maksuera :summa])))]))
