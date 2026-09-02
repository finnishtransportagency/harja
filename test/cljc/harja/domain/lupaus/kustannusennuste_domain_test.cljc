(ns harja.domain.lupaus.kustannusennuste-domain-test
  (:require [clojure.test :refer [deftest testing is]]
            [harja.domain.lupaus.kustannusennuste-domain :as kustannusennuste-domain]
            [harja.pvm :as pvm]))

(deftest kustannusennuste?-test
  (testing "Tunnistaa kustannusennuste-lupauksen"
    (is (true? (kustannusennuste-domain/kustannusennuste? {:lupaustyyppi "kustannusennuste"})))
    (is (false? (kustannusennuste-domain/kustannusennuste? {:lupaustyyppi "yksittainen"})))
    (is (false? (kustannusennuste-domain/kustannusennuste? {:lupaustyyppi "kysely"})))
    (is (false? (kustannusennuste-domain/kustannusennuste? {:lupaustyyppi "monivalinta"})))))

(deftest kustannusennuste->ennuste-test
  (testing "Palauttaa 0 kun ei ole dataa"
    (is (= 0 (kustannusennuste-domain/kustannusennuste->ennuste {:lupaus-kuukaudet []})))
    (is (= 0 (kustannusennuste-domain/kustannusennuste->ennuste {:lupaus-kuukaudet nil}))))
  
  (testing "Palauttaa viimeisimmän pistemäärän kun on dataa"
    (is (= 50 (kustannusennuste-domain/kustannusennuste->ennuste 
                {:lupaus-kuukaudet [{:kuukausi 10 :kustannusennuste {:pisteet 50}}]})))
    (is (= 75 (kustannusennuste-domain/kustannusennuste->ennuste 
                {:lupaus-kuukaudet [{:kuukausi 10 :kustannusennuste {:pisteet 50}}
                                    {:kuukausi 11 :kustannusennuste {:pisteet 75}}]}))))
  
  (testing "Ohittaa kuukaudet ilman pistemäärää"
    (is (= 50 (kustannusennuste-domain/kustannusennuste->ennuste 
                {:lupaus-kuukaudet [{:kuukausi 10 :kustannusennuste {:pisteet 50}}
                                    {:kuukausi 11 :kustannusennuste {}}
                                    {:kuukausi 12}]})))))

(deftest kustannusennuste->toteuma-test
  (testing "Palauttaa nil kun ei ole lopputilannetta"
    (is (nil? (kustannusennuste-domain/kustannusennuste->toteuma {})))
    (is (nil? (kustannusennuste-domain/kustannusennuste->toteuma {:lopputilanne nil}))))
  
  (testing "Palauttaa pyöristetyn keskiarvon kun on lopputilanne"
    (is (= 50 (kustannusennuste-domain/kustannusennuste->toteuma 
                {:lopputilanne {:kustannusennuste_keskiarvo_pisteet 50.0}})))
    (is (= 51 (kustannusennuste-domain/kustannusennuste->toteuma 
                {:lopputilanne {:kustannusennuste_keskiarvo_pisteet 50.5}})))
    (is (= 50 (kustannusennuste-domain/kustannusennuste->toteuma 
                {:lopputilanne {:kustannusennuste_keskiarvo_pisteet 50.4}})))))

(deftest validoi-kustannusennuste-syotteet-test
  (testing "Validoi onnistuneesti kelvollisilla arvoilla"
    (let [syotteet {:ennustettu-tavoitehinta 100000
                    :toteutunut-tavoitehinta 95000
                    :ennustettu-kustannus 90000
                    :toteutunut-kustannus 88000
                    :hoitovuoden-alun-tavoitehinta 100000}
          tulos (kustannusennuste-domain/validoi-kustannusennuste-syotteet syotteet)]
      (is (:ok tulos))))
  
  (testing "Hylkää jos arvot eivät ole numeroita"
    (let [syotteet {:ennustettu-tavoitehinta "ei numero"
                    :toteutunut-tavoitehinta 95000
                    :ennustettu-kustannus 90000
                    :toteutunut-kustannus 88000
                    :hoitovuoden-alun-tavoitehinta 100000}
          tulos (kustannusennuste-domain/validoi-kustannusennuste-syotteet syotteet)]
      (is (:virhe tulos))
      (is (= "Kaikki arvot on oltava numeroita" (:virhe tulos)))))
  
  (testing "Hylkää jos hoitovuoden tavoitehinta on nolla"
    (let [syotteet {:ennustettu-tavoitehinta 100000
                    :toteutunut-tavoitehinta 95000
                    :ennustettu-kustannus 90000
                    :toteutunut-kustannus 88000
                    :hoitovuoden-alun-tavoitehinta 0}
          tulos (kustannusennuste-domain/validoi-kustannusennuste-syotteet syotteet)]
      (is (:virhe tulos))
      (is (= "Hoitovuoden tavoitehinta ei voi olla nolla (nollajako)" (:virhe tulos)))))
  
  (testing "Hylkää jos hoitovuoden tavoitehinta on negatiivinen"
    (let [syotteet {:ennustettu-tavoitehinta 100000
                    :toteutunut-tavoitehinta 95000
                    :ennustettu-kustannus 90000
                    :toteutunut-kustannus 88000
                    :hoitovuoden-alun-tavoitehinta -100000}
          tulos (kustannusennuste-domain/validoi-kustannusennuste-syotteet syotteet)]
      (is (:virhe tulos))
      (is (= "Hoitovuoden tavoitehinta ei voi olla negatiivinen" (:virhe tulos))))))

(deftest turvallinen-jako-test
  (testing "Normaali jako"
    (is (= 2.0 (kustannusennuste-domain/turvallinen-jako 10 5)))
    (is (= 0.5 (kustannusennuste-domain/turvallinen-jako 5 10))))
  
  (testing "Jako nollalla palauttaa 0.0"
    (is (= 0.0 (kustannusennuste-domain/turvallinen-jako 10 0)))
    (is (= 0.0 (kustannusennuste-domain/turvallinen-jako 0 0)))))

(deftest laske-pisteytyshoitovuosi-test
  (testing "Normaali pisteytys ilman offsettia (offset=0)"
    (testing "Lokakuu kuuluu HK1:lle"
      (is (= 2024 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2024 10 0))
          "Lokakuu 2024, offset=0 -> hoitovuosi 2024 (HK 2024-2025)"))
    
    (testing "Tammikuu kuuluu HK1:lle"
      (is (= 2024 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2025 1 0))
          "Tammikuu 2025, offset=0 -> hoitovuosi 2024 (HK 2024-2025)"))
    
    (testing "Elokuu kuuluu HK1:lle"
      (is (= 2024 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2025 8 0))
          "Elokuu 2025, offset=0 -> hoitovuosi 2024 (HK 2024-2025)"))
    
    (testing "Syyskuu kuuluu HK1:lle"
      (is (= 2024 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2025 9 0))
          "Syyskuu 2025, offset=0 -> hoitovuosi 2024 (HK 2024-2025)")))
  
  (testing "Pisteytys offsetilla (offset=1)"
    (testing "Lokakuu siirtyy HK2:lle"
      (is (= 2025 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2024 10 1))
          "Lokakuu 2024, offset=1 -> hoitovuosi 2025 (HK 2025-2026)"))
    
    (testing "Elokuu siirtyy HK2:lle"
      (is (= 2025 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2024 8 1))
          "Elokuu 2024, offset=1 -> hoitovuosi 2025 (HK 2025-2026)"))
    
    (testing "Tammikuu siirtyy HK2:lle"
      (is (= 2026 (kustannusennuste-domain/laske-pisteytyshoitovuosi 2025 1 1))
          "Tammikuu 2025, offset=1 -> hoitovuosi 2026 (HK 2026-2027)"))))

(deftest laske-kustannusennusteen-tarkkuus-test
  (testing "Laskee tarkkuuden oikein täydellisellä ennusteella"
    (let [syotteet {:ennustettu-tavoitehinta 100000
                    :toteutunut-tavoitehinta 100000
                    :ennustettu-kustannus 90000
                    :toteutunut-kustannus 90000
                    :hoitovuoden-alun-tavoitehinta 100000}
          tulos (kustannusennuste-domain/laske-kustannusennusteen-tarkkuus syotteet)]
      (is (:tarkkuus-prosentti tulos))
      (is (= 0.0 (:tarkkuus-prosentti tulos)))
      (is (:laskentakaava-versio tulos))
      (is (:laskentakaava-teksti tulos))
      (is (:laskentakaava-parametrit tulos))
      (is (:laskentakaava-vaiheet tulos))))
  
  (testing "Laskee tarkkuuden oikein kun ennuste ylittää toteutuman"
    (let [syotteet {:ennustettu-tavoitehinta 100000
                    :toteutunut-tavoitehinta 95000
                    :ennustettu-kustannus 90000
                    :toteutunut-kustannus 88000
                    :hoitovuoden-alun-tavoitehinta 100000}
          tulos (kustannusennuste-domain/laske-kustannusennusteen-tarkkuus syotteet)]
      (is (:tarkkuus-prosentti tulos))
      (is (number? (:tarkkuus-prosentti tulos)))))
  
  (testing "Palauttaa virheen jos validointi epäonnistuu"
    (let [syotteet {:ennustettu-tavoitehinta 100000
                    :toteutunut-tavoitehinta 95000
                    :ennustettu-kustannus 90000
                    :toteutunut-kustannus 88000
                    :hoitovuoden-alun-tavoitehinta 0}
          tulos (kustannusennuste-domain/laske-kustannusennusteen-tarkkuus syotteet)]
      (is (:virhe tulos)))))

(deftest kustannusennuste-maarapaiva-paattely-test
  (testing "Ennen määräpäivää - vastaaminen sallittu, ei read-only"
    (let [maarapaiva (pvm/luo-pvm 2025 10 15)
          nykyhetki (pvm/luo-pvm 2025 10 14)
          tulos (kustannusennuste-domain/kustannusennuste-maarapaiva-paattely 
                  nykyhetki maarapaiva true false)]
      (is (false? (:maarapaiva-mennyt-ohi? tulos)) "Määräpäivä ei ole vielä ohitettu")
      (is (false? (:ei-maarapaivan-kuukausi? tulos)) "Kuukausi on oikea")
      (is (false? (:kayta-readonly-nakymaa? tulos)) "Ei käytetä read-only näkymää")
      (is (false? (:disabled? tulos)) "Vastaaminen on sallittu")))
  
  (testing "Määräpäivänä - vastaaminen sallittu, ei read-only"
    (let [maarapaiva (pvm/luo-pvm 2025 10 15)
          nykyhetki (pvm/luo-pvm 2025 10 15)
          tulos (kustannusennuste-domain/kustannusennuste-maarapaiva-paattely 
                  nykyhetki maarapaiva true false)]
      (is (false? (:maarapaiva-mennyt-ohi? tulos)) "Määräpäivänä ei ole vielä ohitettu")
      (is (false? (:ei-maarapaivan-kuukausi? tulos)) "Kuukausi on oikea")
      (is (false? (:kayta-readonly-nakymaa? tulos)) "Ei käytetä read-only näkymää")
      (is (false? (:disabled? tulos)) "Vastaaminen on sallittu")))
  
  (testing "Määräpäivän jälkeen + tiedot syötetty ajoissa = read-only"
    (let [maarapaiva (pvm/luo-pvm 2025 10 15)
          nykyhetki (pvm/luo-pvm 2025 10 16)
          tulos (kustannusennuste-domain/kustannusennuste-maarapaiva-paattely 
                  nykyhetki maarapaiva true false)]
      (is (true? (:maarapaiva-mennyt-ohi? tulos)) "Määräpäivä on ohitettu")
      (is (false? (:ei-maarapaivan-kuukausi? tulos)) "Kuukausi on oikea")
      (is (true? (:kayta-readonly-nakymaa? tulos)) "Käytetään read-only näkymää")
      (is (true? (:disabled? tulos)) "Vastaaminen on estetty")))
  
  (testing "Määräpäivän jälkeen + tiedot EI syötetty ajoissa = varoitus"
    (let [maarapaiva (pvm/luo-pvm 2025 10 15)
          nykyhetki (pvm/luo-pvm 2025 10 16)
          tulos (kustannusennuste-domain/kustannusennuste-maarapaiva-paattely 
                  nykyhetki maarapaiva false false)]
      (is (true? (:maarapaiva-mennyt-ohi? tulos)) "Määräpäivä on ohitettu")
      (is (false? (:ei-maarapaivan-kuukausi? tulos)) "Kuukausi on oikea")
      (is (false? (:kayta-readonly-nakymaa? tulos)) "Ei read-only (tiedot puuttuvat)")
      (is (true? (:disabled? tulos)) "Vastaaminen on estetty (määräpäivä ohitettu)")))
  
  (testing "Väärässä kuukaudessa - vastaaminen estetty"
    (let [maarapaiva (pvm/luo-pvm 2025 10 15)
          nykyhetki (pvm/luo-pvm 2025 11 15)
          tulos (kustannusennuste-domain/kustannusennuste-maarapaiva-paattely 
                  nykyhetki maarapaiva true false)]
      (is (true? (:maarapaiva-mennyt-ohi? tulos)) "Määräpäivä on ohitettu")
      (is (true? (:ei-maarapaivan-kuukausi? tulos)) "Kuukausi on väärä")
      (is (true? (:kayta-readonly-nakymaa? tulos)) "Read-only koska määräpäivä ohitettu JA tiedot syötetty")
      (is (true? (:disabled? tulos)) "Vastaaminen on estetty")))
  
  (testing "Ulkoisen disabled-tilan yhdistäminen"
    (let [maarapaiva (pvm/luo-pvm 2025 10 15)
          nykyhetki (pvm/luo-pvm 2025 10 14)
          tulos (kustannusennuste-domain/kustannusennuste-maarapaiva-paattely 
                  nykyhetki maarapaiva true true)]
      (is (true? (:disabled? tulos)) "Ulkoisen disabled-tilan perusteella disabled"))))
