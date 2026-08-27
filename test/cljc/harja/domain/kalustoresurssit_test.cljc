(ns harja.domain.kalustoresurssit-test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.domain.kalustoresurssit :as kalustoresurssit]))

(deftest hoitoluokka->ryhma-mappaa-oikein
  (testing "Ise-Ib -ryhmän luokat"
    (is (= "ise-ib" (kalustoresurssit/hoitoluokka->ryhma "Ise")))
    (is (= "ise-ib" (kalustoresurssit/hoitoluokka->ryhma "Is")))
    (is (= "ise-ib" (kalustoresurssit/hoitoluokka->ryhma "I")))
    (is (= "ise-ib" (kalustoresurssit/hoitoluokka->ryhma "Ib"))))
  (testing "Ic-III -ryhmän luokat"
    (is (= "ic-iii" (kalustoresurssit/hoitoluokka->ryhma "Ic")))
    (is (= "ic-iii" (kalustoresurssit/hoitoluokka->ryhma "II")))
    (is (= "ic-iii" (kalustoresurssit/hoitoluokka->ryhma "III"))))
  (testing "K1, K2 ja L -ryhmän luokat"
    (is (= "k1-k2-l" (kalustoresurssit/hoitoluokka->ryhma "L")))
    (is (= "k1-k2-l" (kalustoresurssit/hoitoluokka->ryhma "K1")))
    (is (= "k1-k2-l" (kalustoresurssit/hoitoluokka->ryhma "K2"))))
  (testing "Huoltoaukot-luokat eivät kuulu mihinkään ryhmään"
    (is (nil? (kalustoresurssit/hoitoluokka->ryhma "Talvihoito")))
    (is (nil? (kalustoresurssit/hoitoluokka->ryhma "Hoito osin")))
    (is (nil? (kalustoresurssit/hoitoluokka->ryhma "Ei talvihoitoa")))))

(deftest reitin-hallitseva-ryhma-toimii
  (testing "Reitin hallitseva ryhmä on se, jota on pituudeltaan eniten"
    (is (= "ise-ib"
          (kalustoresurssit/reitin-hallitseva-ryhma
            [{:hoitoluokka "Ise" :laskettu_pituus 80}
             {:hoitoluokka "Ic" :laskettu_pituus 20}]))))
  (testing "Saman ryhmän eri luokat lasketaan yhteen"
    (is (= "ise-ib"
          (kalustoresurssit/reitin-hallitseva-ryhma
            [{:hoitoluokka "Ise" :laskettu_pituus 30}
             {:hoitoluokka "Ib" :laskettu_pituus 30}
             {:hoitoluokka "Ic" :laskettu_pituus 50}]))))
  (testing "Tasapelissä valitaan ensimmäinen hoitoluokkaryhmien järjestyksessä (ise-ib)"
    (is (= "ise-ib"
          (kalustoresurssit/reitin-hallitseva-ryhma
            [{:hoitoluokka "Ic" :laskettu_pituus 50}
             {:hoitoluokka "Ise" :laskettu_pituus 50}]))))
  (testing "Tasapelissä ic-iii voittaa k1-k2-l:n"
    (is (= "ic-iii"
          (kalustoresurssit/reitin-hallitseva-ryhma
            [{:hoitoluokka "K1" :laskettu_pituus 50}
             {:hoitoluokka "Ic" :laskettu_pituus 50}]))))
  (testing "Huoltoaukot-luokkia ei huomioida"
    (is (= "k1-k2-l"
          (kalustoresurssit/reitin-hallitseva-ryhma
            [{:hoitoluokka "Talvihoito" :laskettu_pituus 90}
             {:hoitoluokka "K1" :laskettu_pituus 10}]))))
  (testing "nil kun reitillä ei ole yhtään tarjouksessa esiintyvää hoitoluokkaryhmää"
    (is (nil? (kalustoresurssit/reitin-hallitseva-ryhma
                [{:hoitoluokka "Talvihoito" :laskettu_pituus 10}])))
    (is (nil? (kalustoresurssit/reitin-hallitseva-ryhma [])))))

(deftest reitin-kalusto-kpl-summaa-kaikki-kalustotyypit
  (is (= 2 (kalustoresurssit/reitin-kalusto-kpl {:tr_maara 1 :ka_maara 1 :kup_maara nil})))
  (is (= 6 (kalustoresurssit/reitin-kalusto-kpl {:tr_maara 1 :ka_maara 2 :kup_maara 3})))
  (is (= 0 (kalustoresurssit/reitin-kalusto-kpl {}))))

(deftest reittien-kalusto-ryhmittain-kohdistaa-hallitsevalle-ryhmalle
  (testing "Yhden reitin koko kalusto kohdistuu hallitsevalle ryhmälle"
    (is (= {"ise-ib" 2}
          (kalustoresurssit/reittien-kalusto-ryhmittain
            [{:tr_maara 1 :ka_maara 1
              :reitit [{:hoitoluokka "Ise" :laskettu_pituus 80}
                       {:hoitoluokka "Ic" :laskettu_pituus 20}]}]))))
  (testing "Usean reitin kalusto summautuu ryhmittäin"
    (is (= {"ise-ib" 2 "k1-k2-l" 3}
          (kalustoresurssit/reittien-kalusto-ryhmittain
            [{:tr_maara 1 :ka_maara 1
              :reitit [{:hoitoluokka "Ise" :laskettu_pituus 80}
                       {:hoitoluokka "Ic" :laskettu_pituus 20}]}
             {:tr_maara 3
              :reitit [{:hoitoluokka "K1" :laskettu_pituus 100}]}])))))

(deftest kokoa-kalustoyhteenveto-rakentaa-rivit-kaytossa-oleville-ryhmille
  (testing "Rivit muodostetaan vain käytössä olevista hoitoluokkaryhmistä (R1-esimerkki)"
    (let [luvatut [{:hoitoluokkaryhma "ise-ib" :maara 5}
                   {:hoitoluokkaryhma "ic-iii" :maara 3}]
          reitit [{:tr_maara 1 :ka_maara 1
                   :reitit [{:hoitoluokka "Ise" :laskettu_pituus 80}
                            {:hoitoluokka "Ic" :laskettu_pituus 20}]}]]
      (is (= [{:hoitoluokkaryhma "ise-ib" :nimi "Ise–Ib" :luvattu 5 :suunniteltu 2}
              {:hoitoluokkaryhma "ic-iii" :nimi "Ic–III" :luvattu 3 :suunniteltu 0}]
            (kalustoresurssit/kokoa-kalustoyhteenveto luvatut reitit)))))
  (testing "Käyttämättömät hoitoluokkaryhmät jätetään pois vaikka reiteillä olisi kalustoa"
    (let [luvatut [{:hoitoluokkaryhma "ise-ib" :maara 4}]
          reitit [{:tr_maara 2
                   :reitit [{:hoitoluokka "K1" :laskettu_pituus 50}]}]]
      (is (= [{:hoitoluokkaryhma "ise-ib" :nimi "Ise–Ib" :luvattu 4 :suunniteltu 0}]
            (kalustoresurssit/kokoa-kalustoyhteenveto luvatut reitit)))))
  (testing "Tyhjä yhteenveto kun urakalla ei ole kirjattuja kalustoresursseja"
    (is (= [] (kalustoresurssit/kokoa-kalustoyhteenveto [] [])))))
