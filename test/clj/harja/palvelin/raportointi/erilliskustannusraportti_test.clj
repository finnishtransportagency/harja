(ns harja.palvelin.raportointi.erilliskustannusraportti-test
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
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.raportit.erilliskustannukset :as ek-raportti]))

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
                                {:nimi :erilliskustannukset
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:toimenpide-id nil
                                              :alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Erilliskustannusten raportti", :rajoita-pdf-rivimaara nil}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{4
                                                    5}
                      :otsikko "Oulun alueurakka 2014-2019, Erilliskustannusten raportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Erilliskustannusten raportti"
                      :viimeinen-rivi-yhteenveto? true}
                     '({:leveys 7
                        :otsikko "Pvm"}
                       {:leveys 7
                        :otsikko "Sop. nro"}
                       {:leveys 12
                        :otsikko "Toimenpide"}
                       {:leveys 7
                        :otsikko "Tyyppi"}
                       {:fmt :raha
                        :leveys 6
                        :otsikko "Summa"}
                       {:fmt :raha
                        :leveys 6
                        :otsikko "Ind.korotus"})
                     '(["15.09.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        17.2413793103448000M]
                       ["15.08.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "As.tyyt.­bonus"
                        1000M
                        0M]
                       ["15.08.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        17.2413793103448000M]
                       ["15.07.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        0]
                       ["15.06.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        -1.91570881226053640000M]
                       ["15.05.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        -1000M
                        -7.6628352490421000M]
                       ("Yhteensä"
                         ""
                         ""
                         ""
                         4000M
                         24.90421455938696360000M))]]))))

(deftest raportin-suoritus-mhu-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :erilliskustannukset
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                 :parametrit {:toimenpide-id nil
                                              :urakkatyyppi "teiden-hoito"
                                              :alkupvm (c/to-date (t/local-date 2019 10 1))
                                              :loppupvm (c/to-date (t/local-date 2022 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Erilliskustannusten raportti", :rajoita-pdf-rivimaara nil}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{4
                                                    5}
                      :otsikko "Oulun MHU 2019-2024, Erilliskustannusten raportti ajalta 01.10.2019 - 01.10.2022"
                      :sheet-nimi "Erilliskustannusten raportti"
                      :viimeinen-rivi-yhteenveto? true}
                     '({:leveys 7
                        :otsikko "Pvm"}
                       {:leveys 7
                        :otsikko "Sop. nro"}
                       {:leveys 12
                        :otsikko "Toimenpide"}
                       {:leveys 7
                        :otsikko "Tyyppi"}
                       {:fmt :raha
                        :leveys 6
                        :otsikko "Summa"}
                       {:fmt :raha
                        :leveys 6
                        :otsikko "Ind.korotus"})
                     '(["15.03.2020"
                        "666-TES"
                        "Oulu MHU Hallinnolliset toimenpiteet TP"
                        "Lupaus­bonus"
                        500M
                        40.500M]
                       ["15.03.2020"
                        "666-TES"
                        "Oulu MHU Hallinnolliset toimenpiteet TP"
                        "Alihankinta­bonus"
                        500M
                        0]
                       ["15.10.2019"
                        "666-TES"
                        "Oulu MHU Hallinnolliset toimenpiteet TP"
                        "Muu bonus"
                        1000M
                        0]
                       ["15.10.2019"
                        "666-TES"
                        "Oulu MHU Hallinnolliset toimenpiteet TP"
                        "Lupaus­bonus"
                        1000M
                        81.000M]
                       ["15.10.2019"
                        "666-TES"
                        "Oulu MHU Hallinnolliset toimenpiteet TP"
                        "Alihankinta­bonus"
                        1000M
                        0]
                       ["15.10.2019"
                        "666-TES"
                        "Oulu MHU Hallinnolliset toimenpiteet TP"
                        "As.tyyt.­bonus"
                        1000M
                        81.000M]
                       ("Yhteensä"
                         ""
                         ""
                         ""
                         5000M
                         202.500M))]]))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :erilliskustannukset
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:toimenpide-id nil
                                              :alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus
           [:raportti
            {:nimi "Erilliskustannusten raportti", :rajoita-pdf-rivimaara nil}
            [:taulukko
             {:oikealle-tasattavat-kentat #{5
                                            6}
              :otsikko "Pohjois-Pohjanmaa, Erilliskustannusten raportti ajalta 01.10.2014 - 01.10.2015"
              :sheet-nimi "Erilliskustannusten raportti"
              :viimeinen-rivi-yhteenveto? true}
             '({:leveys 10
                :otsikko "Urakka"}
               {:leveys 7
                :otsikko "Pvm"}
               {:leveys 7
                :otsikko "Sop. nro"}
               {:leveys 12
                :otsikko "Toimenpide"}
               {:leveys 7
                :otsikko "Tyyppi"}
               {:fmt :raha
                :leveys 6
                :otsikko "Summa"}
               {:fmt :raha
                :leveys 6
                :otsikko "Ind.korotus"})
             '(["Kajaanin alueurakka 2014-2019"
                "15.09.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "Muu"
                1000M
                17.2413793103448000M]
               ["Kajaanin alueurakka 2014-2019"
                "15.08.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "As.tyyt.­bonus"
                1000M
                0M]
               ["Kajaanin alueurakka 2014-2019"
                "01.08.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "Muu"
                1000M
                17.2413793103448000M]
               ["Kajaanin alueurakka 2014-2019"
                "15.07.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "Muu"
                1000M
                0]
               ["Kajaanin alueurakka 2014-2019"
                "15.06.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "Muu"
                1000M
                -1.91570881226053640000M]
               ["Kajaanin alueurakka 2014-2019"
                "15.05.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "Muu"
                -1000M
                -7.6628352490421000M]
               ["Oulun alueurakka 2014-2019"
                "15.09.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "Muu"
                1000M
                17.2413793103448000M]
               ["Oulun alueurakka 2014-2019"
                "15.08.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "As.tyyt.­bonus"
                1000M
                0M]
               ["Oulun alueurakka 2014-2019"
                "15.08.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "Muu"
                1000M
                17.2413793103448000M]
               ["Oulun alueurakka 2014-2019"
                "15.07.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "Muu"
                1000M
                0]
               ["Oulun alueurakka 2014-2019"
                "15.06.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "Muu"
                1000M
                -1.91570881226053640000M]
               ["Oulun alueurakka 2014-2019"
                "15.05.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "Muu"
                -1000M
                -7.6628352490421000M]
               ("Yhteensä"
                 ""
                 ""
                 ""
                 ""
                 8000M
                 49.80842911877392720000M))]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :erilliskustannukset
                                 :konteksti "koko maa"
                                 :parametrit {:toimenpide-id nil
                                              :alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus
           [:raportti
            {:nimi "Erilliskustannusten raportti", :rajoita-pdf-rivimaara nil}
            [:taulukko
             {:oikealle-tasattavat-kentat #{5
                                            6}
              :otsikko "KOKO MAA, Erilliskustannusten raportti ajalta 01.10.2014 - 01.10.2015"
              :sheet-nimi "Erilliskustannusten raportti"
              :viimeinen-rivi-yhteenveto? true}
             '({:leveys 10
                :otsikko "Urakka"}
               {:leveys 7
                :otsikko "Pvm"}
               {:leveys 7
                :otsikko "Sop. nro"}
               {:leveys 12
                :otsikko "Toimenpide"}
               {:leveys 7
                :otsikko "Tyyppi"}
               {:fmt :raha
                :leveys 6
                :otsikko "Summa"}
               {:fmt :raha
                :leveys 6
                :otsikko "Ind.korotus"})
             '(["Espoon alueurakka 2014-2019"
                "15.09.2015"
                "7eS6339/05"
                "Espoo Talvihoito TP 2014-2019"
                "Muu"
                1000M
                17.2413793103448000M]
               ["Espoon alueurakka 2014-2019"
                "15.08.2015"
                "7eS6339/05"
                "Espoo Talvihoito TP 2014-2019"
                "As.tyyt.­bonus"
                1000M
                0M]
               ["Espoon alueurakka 2014-2019"
                "15.08.2015"
                "7eS6339/05"
                "Espoo Talvihoito TP 2014-2019"
                "Muu"
                1000M
                17.2413793103448000M]
               ["Espoon alueurakka 2014-2019"
                "15.07.2015"
                "7eS6339/05"
                "Espoo Talvihoito TP 2014-2019"
                "Muu"
                1000M
                0]
               ["Espoon alueurakka 2014-2019"
                "15.06.2015"
                "7eS6339/05"
                "Espoo Talvihoito TP 2014-2019"
                "Muu"
                1000M
                -1.91570881226053640000M]
               ["Espoon alueurakka 2014-2019"
                "15.05.2015"
                "7eS6339/05"
                "Espoo Talvihoito TP 2014-2019"
                "Muu"
                -1000M
                -7.6628352490421000M]
               ["Vantaan alueurakka 2009-2019"
                "15.09.2015"
                "00LZM-0033600"
                "Vantaa Talvihoito TP 2014-2019"
                "Muu"
                1000M
                -108.31234256926952141000M]
               ["Vantaan alueurakka 2009-2019"
                "15.08.2015"
                "00LZM-0033600"
                "Vantaa Talvihoito TP 2014-2019"
                "As.tyyt.­bonus"
                1000M
                0M]
               ["Vantaan alueurakka 2009-2019"
                "01.08.2015"
                "00LZM-0033600"
                "Vantaa Talvihoito TP 2014-2019"
                "Muu"
                1000M
                -108.31234256926952141000M]
               ["Vantaan alueurakka 2009-2019"
                "15.07.2015"
                "00LZM-0033600"
                "Vantaa Talvihoito TP 2014-2019"
                "Muu"
                1000M
                0]
               ["Vantaan alueurakka 2009-2019"
                "15.06.2015"
                "00LZM-0033600"
                "Vantaa Talvihoito TP 2014-2019"
                "Muu"
                1000M
                -125.10495382031905961000M]
               ["Vantaan alueurakka 2009-2019"
                "15.05.2015"
                "00LZM-0033600"
                "Vantaa Talvihoito TP 2014-2019"
                "Muu"
                -1000M
                116.70864819479429051000M]
               ["Kajaanin alueurakka 2014-2019"
                "15.09.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "Muu"
                1000M
                17.2413793103448000M]
               ["Kajaanin alueurakka 2014-2019"
                "15.08.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "As.tyyt.­bonus"
                1000M
                0M]
               ["Kajaanin alueurakka 2014-2019"
                "01.08.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "Muu"
                1000M
                17.2413793103448000M]
               ["Kajaanin alueurakka 2014-2019"
                "15.07.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "Muu"
                1000M
                0]
               ["Kajaanin alueurakka 2014-2019"
                "15.06.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "Muu"
                1000M
                -1.91570881226053640000M]
               ["Kajaanin alueurakka 2014-2019"
                "15.05.2015"
                "7A26339/05"
                "Kajaani Talvihoito TP 2014-2019"
                "Muu"
                -1000M
                -7.6628352490421000M]
               ["Oulun alueurakka 2014-2019"
                "15.09.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "Muu"
                1000M
                17.2413793103448000M]
               ["Oulun alueurakka 2014-2019"
                "15.08.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "As.tyyt.­bonus"
                1000M
                0M]
               ["Oulun alueurakka 2014-2019"
                "15.08.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "Muu"
                1000M
                17.2413793103448000M]
               ["Oulun alueurakka 2014-2019"
                "15.07.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "Muu"
                1000M
                0]
               ["Oulun alueurakka 2014-2019"
                "15.06.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "Muu"
                1000M
                -1.91570881226053640000M]
               ["Oulun alueurakka 2014-2019"
                "15.05.2015"
                "2H16339/01"
                "Oulu Talvihoito TP 2014-2019"
                "Muu"
                -1000M
                -7.6628352490421000M]
               ("Yhteensä"
                 ""
                 ""
                 ""
                 ""
                 16000M
                 -150.30834708590292112000M))]]))))

(deftest erilliskustannusten-tyypit
  (is (= (ek-raportti/erilliskustannuksen-nimi "alihankintabonus") "Alihankinta\u00ADbonus"))
  (is (= (ek-raportti/erilliskustannuksen-nimi "asiakastyytyvaisyysbonus") "As.tyyt.\u00ADbonus"))
  (is (= (ek-raportti/erilliskustannuksen-nimi "lupausbonus") "Lupaus\u00ADbonus"))
  (is (= (ek-raportti/erilliskustannuksen-nimi "muu") "Muu"))
  (is (= (ek-raportti/erilliskustannuksen-nimi "tavoitepalkkio") "Tavoite\u00ADpalkkio"))
  (is (= (ek-raportti/erilliskustannuksen-nimi "kissajakameli") "Tuntematon kustannustyyppi")))
