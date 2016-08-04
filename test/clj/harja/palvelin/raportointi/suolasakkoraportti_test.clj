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
                                {:nimi       :suolasakko
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Suolasakkoraportti"
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
                      :otsikko                    "Oulun alueurakka 2014-2019, Suolasakkoraportti ajalta 01.10.2014 - 01.10.2015"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys  10
                       :otsikko "Urakka"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Keski­lämpö­tila"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Pitkän aikavälin keski­lämpö­tila"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Talvi­suolan max-määrä (t)"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Sakko­raja (t)"}
                      {:fmt     :numero
                       :leveys  4
                       :otsikko "Kerroin"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Kohtuul­lis­tarkis­tettu sakko­raja (t)"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Käytetty suola­määrä (t)"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Suola­erotus (t)"}
                      {:fmt     :raha
                       :leveys  4
                       :otsikko "Sakko € / tonni"}
                      {:fmt     :raha
                       :leveys  6
                       :otsikko "Sakko €"}
                      {:fmt     :raha
                       :leveys  5
                       :otsikko "Indeksi €"}
                      {:fmt     :raha
                       :leveys  6
                       :otsikko "Indeksi­korotettu sakko €"}]
                     '(["Oulun alueurakka 2014-2019"
                        -6.20M
                        -9.00M
                        800M
                        840.0
                        1.04766666666666666667M
                        924.0000M
                        1000M
                        76.0000M
                        30.0M
                        2280.00000M
                        108.6800000000000000076000000M
                        2388.6800000000000000076000000M]
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
                         2280.00000M
                         nil
                         2388.6800000000000000076000000M])]]))))


(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :suolasakko
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2014 10 1))
                                                      :loppupvm     (c/to-date (t/local-date 2015 10 1))
                                                      :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Suolasakkoraportti"
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
                      :otsikko                    "Pohjois-Pohjanmaa ja Kainuu, Suolasakkoraportti ajalta 01.10.2014 - 01.10.2015"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys  10
                       :otsikko "Urakka"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Keski­lämpö­tila"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Pitkän aikavälin keski­lämpö­tila"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Talvi­suolan max-määrä (t)"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Sakko­raja (t)"}
                      {:fmt     :numero
                       :leveys  4
                       :otsikko "Kerroin"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Kohtuul­lis­tarkis­tettu sakko­raja (t)"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Käytetty suola­määrä (t)"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Suola­erotus (t)"}
                      {:fmt     :raha
                       :leveys  4
                       :otsikko "Sakko € / tonni"}
                      {:fmt     :raha
                       :leveys  6
                       :otsikko "Sakko €"}
                      {:fmt     :raha
                       :leveys  5
                       :otsikko "Indeksi €"}
                      {:fmt     :raha
                       :leveys  6
                       :otsikko "Indeksi­korotettu sakko €"}]
                     '(["Oulun alueurakka 2014-2019"
                        -6.20M
                        -9.00M
                        800M
                        840.0
                        1.04766666666666666667M
                        924.0000M
                        1000M
                        76.0000M
                        30.0M
                        2280.00000M
                        108.6800000000000000076000000M
                        2388.6800000000000000076000000M]
                        ["Kajaanin alueurakka 2014-2019"
                         -6.00M
                         -8.80M
                         800M
                         840.0
                         1.04766666666666666667M
                         924.0000M
                         1000M
                         76.0000M
                         30.0M
                         2280.00000M
                         108.6800000000000000076000000M
                         2388.6800000000000000076000000M]
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
                         4560.00000M
                         nil
                         4777.3600000000000000152000000M])]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :suolasakko
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2014 1 1))
                                              :loppupvm     (c/to-date (t/local-date 2015 12 31))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Suolasakkoraportti"
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
                      :otsikko                    "KOKO MAA, Suolasakkoraportti ajalta 01.01.2014 - 31.12.2015"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys  10
                       :otsikko "Urakka"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Keski­lämpö­tila"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Pitkän aikavälin keski­lämpö­tila"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Talvi­suolan max-määrä (t)"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Sakko­raja (t)"}
                      {:fmt     :numero
                       :leveys  4
                       :otsikko "Kerroin"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Kohtuul­lis­tarkis­tettu sakko­raja (t)"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Käytetty suola­määrä (t)"}
                      {:fmt     :numero
                       :leveys  5
                       :otsikko "Suola­erotus (t)"}
                      {:fmt     :raha
                       :leveys  4
                       :otsikko "Sakko € / tonni"}
                      {:fmt     :raha
                       :leveys  6
                       :otsikko "Sakko €"}
                      {:fmt     :raha
                       :leveys  5
                       :otsikko "Indeksi €"}
                      {:fmt     :raha
                       :leveys  6
                       :otsikko "Indeksi­korotettu sakko €"}]
                     '(["Vantaan alueurakka 2014-2019"
                       -6.00M
                       -8.80M
                       800M
                       840.0
                       1.04766666666666666667M
                       924.0000M
                       4000M
                       3076.0000M
                       30.0M
                       92280.00000M
                       4398.6800000000000003076000000M
                       96678.6800000000000003076000000M]
                       ["Espoon alueurakka 2014-2019"
                        -6.00M
                        -8.80M
                        800M
                        840.0
                        1.04766666666666666667M
                        924.0000M
                        4000M
                        3076.0000M
                        30.0M
                        92280.00000M
                        4398.6800000000000003076000000M
                        96678.6800000000000003076000000M]
                       {:lihavoi? true
                        :rivi     ["01 Uusimaa"
                                   nil
                                   nil
                                   1600M
                                   nil
                                   nil
                                   1848.0000M
                                   8000M
                                   6152.0000M
                                   nil
                                   184560.00000M
                                   nil
                                   193357.3600000000000006152000000M]}
                       ["Oulun alueurakka 2014-2019"
                        -6.20M
                        -9.00M
                        800M
                        840.0
                        1.04766666666666666667M
                        924.0000M
                        4000M
                        3076.0000M
                        30.0M
                        92280.00000M
                        4398.6800000000000003076000000M
                        96678.6800000000000003076000000M]
                       ["Kajaanin alueurakka 2014-2019"
                        -6.00M
                        -8.80M
                        800M
                        840.0
                        1.04766666666666666667M
                        924.0000M
                        4000M
                        3076.0000M
                        30.0M
                        92280.00000M
                        4398.6800000000000003076000000M
                        96678.6800000000000003076000000M]
                       {:lihavoi? true
                        :rivi     ["12 Pohjois-Pohjanmaa ja Kainuu"
                                   nil
                                   nil
                                   1600M
                                   nil
                                   nil
                                   1848.0000M
                                   8000M
                                   6152.0000M
                                   nil
                                   184560.00000M
                                   nil
                                   193357.3600000000000006152000000M]}
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
                        369120.00000M
                        nil
                        386714.7200000000000012304000000M])]]))))
