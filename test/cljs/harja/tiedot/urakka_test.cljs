(ns harja.tiedot.urakka-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [harja.tiedot.urakka :as urakka]))

(def +sanktio-konfiguraatio+
  {:sanktio-lajit [{:laji :muistutus
                    :nimi "Muistutus"
                    :jarjestys 1
                    :sanktiotyypit [{:id 10 :koodi 13 :nimi "Tyyppi A"}
                                    {:id 11 :koodi 14 :nimi "Tyyppi B"}]}
                   {:laji :A
                    :nimi "Sakko"
                    :jarjestys 2
                    :sanktiotyypit [{:id 12 :koodi 17 :nimi "Tyyppi C"}]}]})

(deftest sanktio-konfiguraation-adapteri-palauttaa-lajit-ja-tyypit
  (testing "Lajit tulevat resolverin jarjestyksessa"
    (is (= [:muistutus :A]
           (urakka/sanktio-konfiguraation-lajit +sanktio-konfiguraatio+))))

  (testing "Lajin nimi luetaan resolverin profiilidatasta"
    (is (= "Sakko"
           (urakka/sanktio-konfiguraation-lajin-nimi +sanktio-konfiguraatio+ :A))))

  (testing "Sanktiotyypit tulevat suoraan resolverin lajiriviltä"
    (is (= [{:id 10 :koodi 13 :nimi "Tyyppi A"}
            {:id 11 :koodi 14 :nimi "Tyyppi B"}]
           (urakka/sanktio-konfiguraation-sanktiotyypit +sanktio-konfiguraatio+ :muistutus))))

  (testing "Tuntematon laji ei palauta tyyppeja"
    (is (= []
           (urakka/sanktio-konfiguraation-sanktiotyypit +sanktio-konfiguraatio+ :tuntematon)))))

(deftest sanktio-konfiguraation-tila-erottelee-latauksen-virheen-ja-tyhjan
  (testing "Haku kaynnissa erotetaan omaksi tilakseen"
    (is (= :haku-kaynnissa
           (urakka/sanktio-konfiguraation-tila nil true))))

  (testing "Virhevastaus ei nayta tyhjaa konfiguraatiota"
    (is (= :haku-epaonnistui
           (urakka/sanktio-konfiguraation-tila {:virhe "virhe"} false))))

  (testing "Tyhja tai puuttuva konfiguraatio palauttaa ei-konfiguraatiota-tilan"
    (is (= :ei-konfiguraatiota
           (urakka/sanktio-konfiguraation-tila nil false)))
    (is (= :ei-konfiguraatiota
           (urakka/sanktio-konfiguraation-tila {} false))))

  (testing "Sallitut lajit tunnistetaan valmiiksi konfiguraatioksi"
    (is (= :valmis
           (urakka/sanktio-konfiguraation-tila +sanktio-konfiguraatio+ false)))))

(deftest uuden-sanktion-oletuslaji-tulee-konfiguraatiosta
  (testing "Hoito- ja MHU-urakoilla kaytetaan ensimmaista sallittua lajia"
    (is (= :muistutus
           (urakka/oletus-uuden-sanktion-laji :hoito [:muistutus :A])))
    (is (= :B
           (urakka/oletus-uuden-sanktion-laji :teiden-hoito [:B :C]))))

  (testing "Kun konfiguraatiota ei ole, hoitourakan oletusvalinta jaa tyhjaksi"
    (is (nil?
          (urakka/oletus-uuden-sanktion-laji :hoito []))))

  (testing "Muiden urakkatyyppien olemassa oleva oletuskayttaytyminen säilyy"
    (is (= :vesivayla_sakko
           (urakka/oletus-uuden-sanktion-laji :vesivayla-hoito [])))
    (is (= :yllapidon_sakko
           (urakka/oletus-uuden-sanktion-laji :paallystys [])))))
