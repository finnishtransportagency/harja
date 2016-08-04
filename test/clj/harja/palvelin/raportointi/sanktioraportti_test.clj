(ns harja.palvelin.raportointi.sanktioraportti-test
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
                                {:nimi       :sanktioraportti
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2011 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti {:nimi        "Sanktioiden yhteenveto"
                               :orientaatio :landscape}
                    [:taulukko {:sheet-nimi                 "Sanktioiden yhteenveto"
                                :otsikko                    "Oulun alueurakka 2014-2019, Sanktioiden yhteenveto ajalta 01.10.2011 - 01.10.2016"
                                :oikealle-tasattavat-kentat #{1}}
                     [{:otsikko "", :leveys 12} {:fmt :numero :otsikko "Oulun alueurakka 2014-2019", :leveys 15}]
                     [{:otsikko "Talvihoito"}
                      ["Muistutukset (kpl)"
                       1]
                      ["Sakko A (€)"
                       1000M]
                      ["- Päätiet (€)"
                       1000M]
                      ["- Muut tiet (€)"
                       0]
                      ["Sakko B (€)"
                       666.666M]
                      ["- Päätiet (€)"
                       666.666M]
                      ["- Muut tiet (€)"
                       0]
                      ["Talvihoito, sakot yht. (€)"
                       1666.666M]
                      ["Talvihoito, indeksit yht. (€)"
                       1674.11359782955633631133610M]
                      {:otsikko "Muut tuotteet"}
                      ["Muistutukset (kpl)"
                       1]
                      ["Sakko A (€)"
                       0]
                      ["- Liikenneymp. hoito (€)"
                       0]
                      ["- Sorateiden hoito (€)"
                       0]
                      ["Sakko B (€)"
                       111M]
                      ["- Liikenneymp. hoito (€)"
                       110M]
                      ["- Sorateiden hoito (€)"
                       0]
                      ["Muut tuotteet, sakot yht. (€)"
                       111M]
                      ["Muut tuotteet, indeksit yht. (€)"
                       111.49601021385253753935M]
                      {:otsikko "Ryhmä C"}
                      ["Ryhmä C, sakot yht. (€)"
                       123M]
                      ["Ryhmä C, indeksit yht. (€)"
                       123.54963293967443348955M]
                      {:otsikko "Yhteensä"}
                      ["Muistutukset yht. (kpl)"
                       2]
                      ["Indeksit yht. (€)"
                       1909.15924098308330734023610M]
                      ["Kaikki sakot yht. (€)"
                       1900.666M]
                      ["Kaikki yht. (€)"
                       3809.82524098308330734023610M]]]]))))


(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :sanktioraportti
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2011 10 1))
                                                      :loppupvm     (c/to-date (t/local-date 2016 10 1))
                                                      :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Sanktioiden yhteenveto"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{1
                                                    2
                                                    3
                                                    4
                                                    5}
                      :otsikko                    "Pohjois-Pohjanmaa ja Kainuu, Sanktioiden yhteenveto ajalta 01.10.2011 - 01.10.2016"
                      :sheet-nimi                 "Sanktioiden yhteenveto"}
                     [{:leveys  12
                       :otsikko ""}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "Kajaanin alueurakka 2014-2019"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "Oulun alueurakka 2005-2012"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "Oulun alueurakka 2014-2019"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "Pudasjärven alueurakka 2007-2012"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "Yh­teen­sä"}]
                     [{:otsikko "Talvihoito"}
                      ["Muistutukset (kpl)"
                       0
                       0
                       1
                       1
                       2]
                      ["Sakko A (€)"
                       0
                       0
                       1000M
                       10000M
                       11000M]
                      ["- Päätiet (€)"
                       0
                       0
                       1000M
                       10000M
                       11000M]
                      ["- Muut tiet (€)"
                       0
                       0
                       0
                       0
                       0]
                      ["Sakko B (€)"
                       0
                       0
                       666.666M
                       6660M
                       7326.666M]
                      ["- Päätiet (€)"
                       0
                       0
                       666.666M
                       6660M
                       7326.666M]
                      ["- Muut tiet (€)"
                       0
                       0
                       0
                       0
                       0]
                      ["Talvihoito, sakot yht. (€)"
                       0
                       0
                       1666.666M
                       16660M
                       18326.666M]
                      ["Talvihoito, indeksit yht. (€)"
                       0
                       0
                       1674.11359782955633631133610M
                       0
                       1674.11359782955633631133610M]
                      {:otsikko "Muut tuotteet"}
                      ["Muistutukset (kpl)"
                       0
                       0
                       1
                       1
                       2]
                      ["Sakko A (€)"
                       0
                       0
                       0
                       0
                       0]
                      ["- Liikenneymp. hoito (€)"
                       0
                       0
                       0
                       0
                       0]
                      ["- Sorateiden hoito (€)"
                       0
                       0
                       0
                       0
                       0]
                      ["Sakko B (€)"
                       0
                       0
                       111M
                       1110M
                       1221M]
                      ["- Liikenneymp. hoito (€)"
                       0
                       0
                       110M
                       1100M
                       1210M]
                      ["- Sorateiden hoito (€)"
                       0
                       0
                       0
                       0
                       0]
                      ["Muut tuotteet, sakot yht. (€)"
                       0
                       0
                       111M
                       1110M
                       1221M]
                      ["Muut tuotteet, indeksit yht. (€)"
                       0
                       0
                       111.49601021385253753935M
                       0
                       111.49601021385253753935M]
                      {:otsikko "Ryhmä C"}
                      ["Ryhmä C, sakot yht. (€)"
                       0
                       0
                       123M
                       1230M
                       1353M]
                      ["Ryhmä C, indeksit yht. (€)"
                       0
                       0
                       123.54963293967443348955M
                       0
                       123.54963293967443348955M]
                      {:otsikko "Yhteensä"}
                      ["Muistutukset yht. (kpl)"
                       0
                       0
                       2
                       2
                       4]
                      ["Indeksit yht. (€)"
                       0
                       0
                       1909.15924098308330734023610M
                       0
                       1909.15924098308330734023610M]
                      ["Kaikki sakot yht. (€)"
                       0
                       0
                       1900.666M
                       19000M
                       20900.666M]
                      ["Kaikki yht. (€)"
                       0
                       0
                       3809.82524098308330734023610M
                       19000M
                       22809.82524098308330734023610M]]]]))))

(deftest raportin-suoritus-hallintayksikolle-toimii-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :sanktioraportti
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2015 1 1))
                                                      :loppupvm     (c/to-date (t/local-date 2015 12 31))
                                                      :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Sanktioiden yhteenveto"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{1
                                                    2
                                                    3}
                      :otsikko                    "Pohjois-Pohjanmaa ja Kainuu, Sanktioiden yhteenveto ajalta 01.01.2015 - 31.12.2015"
                      :sheet-nimi                 "Sanktioiden yhteenveto"}
                     [{:leveys  12
                       :otsikko ""}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "Kajaanin alueurakka 2014-2019"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "Oulun alueurakka 2014-2019"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "Yh­teen­sä"}]
                     [{:otsikko "Talvihoito"}
                      ["Muistutukset (kpl)"
                       0
                       1
                       1]
                      ["Sakko A (€)"
                       0
                       1000M
                       1000M]
                      ["- Päätiet (€)"
                       0
                       1000M
                       1000M]
                      ["- Muut tiet (€)"
                       0
                       0
                       0]
                      ["Sakko B (€)"
                       0
                       666.666M
                       666.666M]
                      ["- Päätiet (€)"
                       0
                       666.666M
                       666.666M]
                      ["- Muut tiet (€)"
                       0
                       0
                       0]
                      ["Talvihoito, sakot yht. (€)"
                       0
                       1666.666M
                       1666.666M]
                      ["Talvihoito, indeksit yht. (€)"
                       0
                       1674.11359782955633631133610M
                       1674.11359782955633631133610M]
                      {:otsikko "Muut tuotteet"}
                      ["Muistutukset (kpl)"
                       0
                       1
                       1]
                      ["Sakko A (€)"
                       0
                       0
                       0]
                      ["- Liikenneymp. hoito (€)"
                       0
                       0
                       0]
                      ["- Sorateiden hoito (€)"
                       0
                       0
                       0]
                      ["Sakko B (€)"
                       0
                       111M
                       111M]
                      ["- Liikenneymp. hoito (€)"
                       0
                       110M
                       110M]
                      ["- Sorateiden hoito (€)"
                       0
                       0
                       0]
                      ["Muut tuotteet, sakot yht. (€)"
                       0
                       111M
                       111M]
                      ["Muut tuotteet, indeksit yht. (€)"
                       0
                       111.49601021385253753935M
                       111.49601021385253753935M]
                      {:otsikko "Ryhmä C"}
                      ["Ryhmä C, sakot yht. (€)"
                       0
                       123M
                       123M]
                      ["Ryhmä C, indeksit yht. (€)"
                       0
                       123.54963293967443348955M
                       123.54963293967443348955M]
                      {:otsikko "Yhteensä"}
                      ["Muistutukset yht. (kpl)"
                       0
                       2
                       2]
                      ["Indeksit yht. (€)"
                       0
                       1909.15924098308330734023610M
                       1909.15924098308330734023610M]
                      ["Kaikki sakot yht. (€)"
                       0
                       1900.666M
                       1900.666M]
                      ["Kaikki yht. (€)"
                       0
                       3809.82524098308330734023610M
                       3809.82524098308330734023610M]]]]))))


(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :sanktioraportti
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2015 1 1))
                                              :loppupvm     (c/to-date (t/local-date 2015 12 31))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Sanktioiden yhteenveto"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{1
                                                    10
                                                    2
                                                    3
                                                    4
                                                    5
                                                    6
                                                    7
                                                    8
                                                    9}
                      :otsikko                    "KOKO MAA, Sanktioiden yhteenveto ajalta 01.01.2015 - 31.12.2015"
                      :sheet-nimi                 "Sanktioiden yhteenveto"}
                     [{:leveys  12
                       :otsikko ""}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "01 Uusimaa"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "02 Varsinais-Suomi"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "03 Kaakkois-Suomi"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "04 Pirkanmaa"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "08 Pohjois-Savo"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "09 Keski-Suomi"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "10 Etelä-Pohjanmaa"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "12 Pohjois-Pohjanmaa ja Kainuu"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "14 Lappi"}
                      {:fmt     :numero
                       :leveys  15
                       :otsikko "Yh­teen­sä"}]
                     [{:otsikko "Talvihoito"}
                      ["Muistutukset (kpl)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       1
                       0
                       1]
                      ["Sakko A (€)"
                       3.5M
                       0
                       0
                       0
                       0
                       0
                       0
                       1000M
                       0
                       1003.5M]
                      ["- Päätiet (€)"
                       3.5M
                       0
                       0
                       0
                       0
                       0
                       0
                       1000M
                       0
                       1003.5M]
                      ["- Muut tiet (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0]
                      ["Sakko B (€)"
                       3.5M
                       0
                       0
                       0
                       0
                       0
                       0
                       666.666M
                       0
                       670.166M]
                      ["- Päätiet (€)"
                       3.5M
                       0
                       0
                       0
                       0
                       0
                       0
                       666.666M
                       0
                       670.166M]
                      ["- Muut tiet (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0]
                      ["Talvihoito, sakot yht. (€)"
                       7.0M
                       0
                       0
                       0
                       0
                       0
                       0
                       1666.666M
                       0
                       1673.666M]
                      ["Talvihoito, indeksit yht. (€)"
                       7.031279923396105970950M
                       0
                       0
                       0
                       0
                       0
                       0
                       1674.11359782955633631133610M
                       0
                       1681.14487775295244228228610M]
                      {:otsikko "Muut tuotteet"}
                      ["Muistutukset (kpl)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       1
                       0
                       1]
                      ["Sakko A (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0]
                      ["- Liikenneymp. hoito (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0]
                      ["- Sorateiden hoito (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0]
                      ["Sakko B (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       111M
                       0
                       111M]
                      ["- Liikenneymp. hoito (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       110M
                       0
                       110M]
                      ["- Sorateiden hoito (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0]
                      ["Muut tuotteet, sakot yht. (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       111M
                       0
                       111M]
                      ["Muut tuotteet, indeksit yht. (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       111.49601021385253753935M
                       0
                       111.49601021385253753935M]
                      {:otsikko "Ryhmä C"}
                      ["Ryhmä C, sakot yht. (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       123M
                       0
                       123M]
                      ["Ryhmä C, indeksit yht. (€)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       123.54963293967443348955M
                       0
                       123.54963293967443348955M]
                      {:otsikko "Yhteensä"}
                      ["Muistutukset yht. (kpl)"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       2
                       0
                       2]
                      ["Indeksit yht. (€)"
                       7.031279923396105970950M
                       0
                       0
                       0
                       0
                       0
                       0
                       1909.15924098308330734023610M
                       0
                       1916.19052090647941331118610M]
                      ["Kaikki sakot yht. (€)"
                       7.0M
                       0
                       0
                       0
                       0
                       0
                       0
                       1900.666M
                       0
                       1907.666M]
                      ["Kaikki yht. (€)"
                       14.031279923396105970950M
                       0
                       0
                       0
                       0
                       0
                       0
                       3809.82524098308330734023610M
                       0
                       3823.85652090647941331118610M]]]]))))
