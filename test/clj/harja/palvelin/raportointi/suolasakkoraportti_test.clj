(ns harja.palvelin.raportointi.suolasakkoraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
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
                                {:nimi :suolasakko
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Suolasakkoraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{1
                                                    10
                                                    11
                                                    12
                                                    13
                                                    2
                                                    3
                                                    4
                                                    5
                                                    6
                                                    7
                                                    8
                                                    9}
                      :otsikko "Oulun alueurakka 2014-2019, Suolasakkoraportti ajalta 01.10.2014 - 01.10.2015"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 10
                       :otsikko "Urakka"}
                      {:leveys 5
                       :otsikko "Keski­lämpö­tila"}
                      {:leveys 5
                       :otsikko "Pitkän aikavälin keski­lämpö­tila"}
                      {:leveys 5
                       :otsikko "Talvi­suolan max-määrä (t)"}
                      {:leveys 5
                       :otsikko "Sakko­raja (t)"}
                      {:leveys 4
                       :otsikko "Kerroin"}
                      {:leveys 5
                       :otsikko "Kohtuul­lis­tarkis­tettu sakko­raja (t)"}
                      {:leveys 5
                       :otsikko "Käytetty suola­määrä (t)"}
                      {:leveys 5
                       :otsikko "Suola­erotus (t)"}
                      {:leveys 4
                       :otsikko "Sakko € / tonni"}
                      {:leveys 6
                       :otsikko "Sakko €"}
                      {:leveys 5
                       :otsikko "Indeksi €"}
                      {:leveys 6
                       :otsikko "Indeksi­korotettu sakko €"}]
                     [["Oulun alueurakka 2014-2019"
                       "-6.20 °C"
                       "-9.00 °C"
                       800M
                       "840,00"
                       "1,0477"
                       "924,00"
                       1000M
                       76.0000M
                       "30,00"
                       "2 280,00"
                       "108,68"
                       "2 388,68"]
                       ["Yhteensä"
                        nil
                        nil
                        800M
                        nil
                        nil
                        924.0000M
                        1000M
                        76.0000M
                        nil
                        "2 280,00"
                        nil
                        "2 388,68"]]]]))))


(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :suolasakko
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Suolasakkoraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{1
                                                    10
                                                    11
                                                    12
                                                    13
                                                    2
                                                    3
                                                    4
                                                    5
                                                    6
                                                    7
                                                    8
                                                    9}
                      :otsikko "Pohjois-Pohjanmaa ja Kainuu, Suolasakkoraportti ajalta 01.10.2014 - 01.10.2015"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 10
                       :otsikko "Urakka"}
                      {:leveys 5
                       :otsikko "Keski­lämpö­tila"}
                      {:leveys 5
                       :otsikko "Pitkän aikavälin keski­lämpö­tila"}
                      {:leveys 5
                       :otsikko "Talvi­suolan max-määrä (t)"}
                      {:leveys 5
                       :otsikko "Sakko­raja (t)"}
                      {:leveys 4
                       :otsikko "Kerroin"}
                      {:leveys 5
                       :otsikko "Kohtuul­lis­tarkis­tettu sakko­raja (t)"}
                      {:leveys 5
                       :otsikko "Käytetty suola­määrä (t)"}
                      {:leveys 5
                       :otsikko "Suola­erotus (t)"}
                      {:leveys 4
                       :otsikko "Sakko € / tonni"}
                      {:leveys 6
                       :otsikko "Sakko €"}
                      {:leveys 5
                       :otsikko "Indeksi €"}
                      {:leveys 6
                       :otsikko "Indeksi­korotettu sakko €"}]
                     [["Oulun alueurakka 2014-2019"
                       "-6.20 °C"
                       "-9.00 °C"
                       800M
                       "840,00"
                       "1,0477"
                       "924,00"
                       1000M
                       76.0000M
                       "30,00"
                       "2 280,00"
                       "108,68"
                       "2 388,68"]
                       ["Kajaanin alueurakka 2014-2019"
                        "-6.00 °C"
                        "-8.80 °C"
                        800M
                        "840,00"
                        "1,0477"
                        "924,00"
                        1000M
                        76.0000M
                        "30,00"
                        "2 280,00"
                        "108,68"
                        "2 388,68"]
                       ["Yhteensä"
                        nil
                        nil
                        1600M
                        nil
                        nil
                        1848.0000M
                        2000M
                        152.0000M
                        nil
                        "4 560,00"
                        nil
                        "4 777,36"]]]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :suolasakko
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 1 1))
                                              :loppupvm (c/to-date (t/local-date 2015 12 31))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Suolasakkoraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{1
                                                    10
                                                    11
                                                    12
                                                    13
                                                    2
                                                    3
                                                    4
                                                    5
                                                    6
                                                    7
                                                    8
                                                    9}
                      :otsikko "KOKO MAA, Suolasakkoraportti ajalta 01.01.2014 - 31.12.2015"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 10
                       :otsikko "Urakka"}
                      {:leveys 5
                       :otsikko "Keski­lämpö­tila"}
                      {:leveys 5
                       :otsikko "Pitkän aikavälin keski­lämpö­tila"}
                      {:leveys 5
                       :otsikko "Talvi­suolan max-määrä (t)"}
                      {:leveys 5
                       :otsikko "Sakko­raja (t)"}
                      {:leveys 4
                       :otsikko "Kerroin"}
                      {:leveys 5
                       :otsikko "Kohtuul­lis­tarkis­tettu sakko­raja (t)"}
                      {:leveys 5
                       :otsikko "Käytetty suola­määrä (t)"}
                      {:leveys 5
                       :otsikko "Suola­erotus (t)"}
                      {:leveys 4
                       :otsikko "Sakko € / tonni"}
                      {:leveys 6
                       :otsikko "Sakko €"}
                      {:leveys 5
                       :otsikko "Indeksi €"}
                      {:leveys 6
                       :otsikko "Indeksi­korotettu sakko €"}]
                     [["Vantaan alueurakka 2014-2019"
                       "-6.00 °C"
                       "-8.80 °C"
                       800M
                       "840,00"
                       "1,0477"
                       "924,00"
                       4000M
                       3076.0000M
                       "30,00"
                       "92 280,00"
                       "4 398,68"
                       "96 678,68"]
                       ["Espoon alueurakka 2014-2019"
                        "-6.00 °C"
                        "-8.80 °C"
                        800M
                        "840,00"
                        "1,0477"
                        "924,00"
                        4000M
                        3076.0000M
                        "30,00"
                        "92 280,00"
                        "4 398,68"
                        "96 678,68"]
                       {:lihavoi? true
                        :rivi ["01 Uusimaa"
                               nil
                               nil
                               1600M
                               nil
                               nil
                               1848.0000M
                               8000M
                               6152.0000M
                               nil
                               "184 560,00"
                               nil
                               "193 357,36"]}
                       ["Oulun alueurakka 2014-2019"
                        "-6.20 °C"
                        "-9.00 °C"
                        800M
                        "840,00"
                        "1,0477"
                        "924,00"
                        4000M
                        3076.0000M
                        "30,00"
                        "92 280,00"
                        "4 398,68"
                        "96 678,68"]
                       ["Kajaanin alueurakka 2014-2019"
                        "-6.00 °C"
                        "-8.80 °C"
                        800M
                        "840,00"
                        "1,0477"
                        "924,00"
                        4000M
                        3076.0000M
                        "30,00"
                        "92 280,00"
                        "4 398,68"
                        "96 678,68"]
                       {:lihavoi? true
                        :rivi ["12 Pohjois-Pohjanmaa ja Kainuu"
                               nil
                               nil
                               1600M
                               nil
                               nil
                               1848.0000M
                               8000M
                               6152.0000M
                               nil
                               "184 560,00"
                               nil
                               "193 357,36"]}
                       ["Yhteensä"
                        nil
                        nil
                        3200M
                        nil
                        nil
                        3696.0000M
                        16000M
                        12304.0000M
                        nil
                        "369 120,00"
                        nil
                        "386 714,72"]]]]))))
