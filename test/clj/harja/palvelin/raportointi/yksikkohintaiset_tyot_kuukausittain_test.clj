(ns harja.palvelin.raportointi.yksikkohintaiset-tyot-kuukausittain-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.urakat :as urk-q]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi :refer :all]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain :as raportti]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pdf-vienti (component/using
                                      (pdf-vienti/luo-pdf-vienti)
                                      [:http-palvelin])
                        :raportointi (component/using
                                       (raportointi/luo-raportointi)
                                       [:db :pdf-vienti])
                        :raportit (component/using
                                    (raportit/->Raportit)
                                    [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :yks-hint-kuukausiraportti
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm     (c/to-date (t/local-date 2016 9 30))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:orientaatio :landscape
                     :nimi        "Yksikköhintaiset työt kuukausittain"}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{10
                                                    11
                                                    12
                                                    13
                                                    14
                                                    15
                                                    16
                                                    2
                                                    3
                                                    4
                                                    5
                                                    6
                                                    7
                                                    8
                                                    9}
                      :otsikko                    "Oulun alueurakka 2014-2019, Yksikköhintaiset työt kuukausittain ajalta 01.10.2015 - 30.09.2016"
                      :sheet-nimi                 "Yksikköhintaiset työt kuukausittain"
                      :tyhja                      nil}
                     '({:leveys  10
                        :otsikko "Tehtävä"}
                        {:leveys  5
                         :otsikko "Yk­sik­kö"}
                        {:leveys             5
                         :otsikko            "10 / 15"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "11 / 15"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "12 / 15"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "01 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "02 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "03 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "04 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "05 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "06 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "07 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "08 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "09 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys  7
                         :otsikko "Mää­rä yh­teen­sä"
                         :fmt     :numero})
                     ['("Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen"
                        "m2"
                        0
                        0
                        0
                        667M
                        0
                        0
                        0
                        0
                        0
                        0
                        0
                        0
                        667M)
                      '("Pensaiden täydennysistutus"
                         "m2"
                         0
                         0
                         0
                         668M
                         0
                         0
                         0
                         0
                         0
                         0
                         0
                         0
                         668M)]]
                     [:teksti
                      "Suunnittelutiedot näytetään vain haettaessa urakan tiedot hoitokaudelta tai sen osalta."]]))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :yks-hint-kuukausiraportti
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2015 10 1))
                                                      :loppupvm     (c/to-date (t/local-date 2016 9 30))
                                                      :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Yksikköhintaiset työt kuukausittain"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{10
                                                    11
                                                    12
                                                    13
                                                    14
                                                    15
                                                    16
                                                    2
                                                    3
                                                    4
                                                    5
                                                    6
                                                    7
                                                    8
                                                    9}
                      :otsikko "Pohjois-Pohjanmaa, Yksikköhintaiset työt kuukausittain ajalta 01.10.2015 - 30.09.2016"
                      :tyhja                      nil
                      :sheet-nimi                 "Yksikköhintaiset työt kuukausittain"}
                     '({:otsikko "Tehtävä"
                        :leveys  10}
                        {:otsikko "Yk­sik­kö"
                         :leveys  5}
                        {:leveys             5
                         :otsikko            "10 / 15"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "11 / 15"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "12 / 15"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "01 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "02 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "03 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "04 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "05 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "06 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "07 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "08 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "09 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys  7
                         :otsikko "Mää­rä yh­teen­sä"
                         :fmt     :numero})
                     ['("Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen"
                         "m2"
                         0
                         0
                         0
                         667M
                         0
                         0
                         0
                         0
                         0
                         0
                         0
                         0
                         667M)
                      '("Pensaiden täydennysistutus"
                         "m2"
                         0
                         0
                         0
                         668M
                         0
                         0
                         0
                         0
                         0
                         0
                         0
                         0
                         668M)
                      ]]
                    nil]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :yks-hint-kuukausiraportti
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm     (c/to-date (t/local-date 2016 9 30))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Yksikköhintaiset työt kuukausittain"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{10
                                                    11
                                                    12
                                                    13
                                                    14
                                                    15
                                                    16
                                                    2
                                                    3
                                                    4
                                                    5
                                                    6
                                                    7
                                                    8
                                                    9}
                      :otsikko                    "KOKO MAA, Yksikköhintaiset työt kuukausittain ajalta 01.10.2015 - 30.09.2016"
                      :sheet-nimi                 "Yksikköhintaiset työt kuukausittain"
                      :tyhja                      nil}
                     '({:leveys  10
                        :otsikko "Tehtävä"}
                        {:leveys  5
                         :otsikko "Yk­sik­kö"}
                        {:leveys             5
                         :otsikko            "10 / 15"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "11 / 15"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "12 / 15"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "01 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "02 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "03 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "04 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "05 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "06 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "07 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "08 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys             5
                         :otsikko            "09 / 16"
                         :otsikkorivi-luokka "grid-kk-sarake"
                         :fmt                :numero}
                        {:leveys  7
                         :otsikko "Mää­rä yh­teen­sä"
                         :fmt     :numero})
                     ['("Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen"
                         "m2"
                         0
                         0
                         0
                         667M
                         0
                         0
                         0
                         0
                         0
                         0
                         0
                         0
                         667M)
                      '("Pensaiden täydennysistutus"
                         "m2"
                         0
                         0
                         0
                         668M
                         0
                         0
                         0
                         0
                         0
                         0
                         0
                         0
                         668M)]]
                    nil]))))

(deftest kuukausittaisten-summien-yhdistaminen-toimii-urakan-yhdelle-tehtavalle
  (let [rivit [{:kuukausi 10 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 1}
               {:kuukausi 11 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 2}
               {:kuukausi 12 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 3}]
        vastaus (raportti/muodosta-raportin-rivit rivit false)]
    (is (= 1 (count vastaus)))
    (is (= (get (first vastaus) "10 / 05") 1))
    (is (= (get (first vastaus) "11 / 05") 2))
    (is (= (get (first vastaus) "12 / 05") 3))))

(deftest kuukausittaisten-summien-yhdistaminen-toimii-urakan-usealle-tehtavalle
  (let [rivit [{:kuukausi 10 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 1}
               {:kuukausi 11 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 2}
               {:kuukausi 11 :vuosi 2005 :nimi "Suolaus" :yksikko "kg" :suunniteltu_maara 1 :toteutunut_maara 3}]
        vastaus (raportti/muodosta-raportin-rivit rivit false)]
    (is (= 2 (count vastaus)))
    (let [auraus (first (filter #(= (:nimi %) "Auraus") vastaus))
          suolaus (first (filter #(= (:nimi %) "Suolaus") vastaus))]
      (is (= (get auraus "10 / 05") 1))
      (is (= (get auraus "11 / 05") 2))
      (is (= (get suolaus "11 / 05") 3)))))

(deftest kuukausittaisten-summien-yhdistaminen-toimii-urakoittain-usealle-tehtavalle
  (let [rivit [{:kuukausi 10 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 1 :urakka_id 1 :urakka_nimi "Sepon urakka"}
               {:kuukausi 11 :vuosi 2005 :nimi "Suolaus" :yksikko "kg" :suunniteltu_maara 1 :toteutunut_maara 2 :urakka_id 1 :urakka_nimi "Sepon urakka"}
               {:kuukausi 12 :vuosi 2005 :nimi "Suolaus" :yksikko "kg" :suunniteltu_maara 1 :toteutunut_maara 666 :urakka_id 1 :urakka_nimi "Sepon urakka"}
               {:kuukausi 12 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 3 :urakka_id 2 :urakka_nimi "Paavon urakka"}
               {:kuukausi 12 :vuosi 2006 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 123 :urakka_id 2 :urakka_nimi "Paavon urakka"}]
        vastaus (raportti/muodosta-raportin-rivit rivit true)]
    (is (= 3 (count vastaus)))
    (let [sepon-auraus (first (filter #(and (= (:nimi %) "Auraus")
                                            (= (:urakka_nimi %) "Sepon urakka"))
                                      vastaus))
          sepon-suolaus (first (filter #(and (= (:nimi %) "Suolaus")
                                             (= (:urakka_nimi %) "Sepon urakka"))
                                       vastaus))
          paavon-auraus (first (filter #(and (= (:nimi %) "Auraus")
                                             (= (:urakka_nimi %) "Paavon urakka"))
                                       vastaus))]
      (is (= (get sepon-auraus "10 / 05") 1))
      (is (= (get sepon-suolaus "11 / 05") 2))
      (is (= (get sepon-suolaus "12 / 05") 666))
      (is (= (get paavon-auraus "12 / 05") 3))
      (is (= (get paavon-auraus "12 / 06") 123)))))