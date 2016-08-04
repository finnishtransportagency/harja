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
                                {:nimi :erilliskustannukset
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:toimenpide-id nil
                                              :alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Erilliskustannusten raportti"}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{4
                                                    5}
                      :otsikko "Oulun alueurakka 2014-2019, Erilliskustannusten raportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Erilliskustannusten raportti"
                      :viimeinen-rivi-yhteenveto? true}
                     '({:leveys  7
                       :otsikko "Pvm"}
                       {:leveys  7
                        :otsikko "Sop. nro"}
                       {:leveys  12
                        :otsikko "Toimenpide"}
                       {:leveys  7
                        :otsikko "Tyyppi"}
                       {:fmt     :raha
                        :leveys  6
                        :otsikko "Summa €"}
                       {:fmt     :raha
                        :leveys  6
                        :otsikko "Ind.korotus €"})
                     '(["15.09.2015"
                       "2H16339/01"
                       "Oulu Talvihoito TP 2014-2019"
                       "Muu"
                       1000M
                       16.9166932652410000M]
                       ["15.08.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "As.tyyt.­bonus"
                        1000M
                        4.06958187041174593000M]
                       ["01.08.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
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
                        -2.23428024257899745000M]
                       ["15.05.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        -1000M
                        -7.3412065113310000M]
                       ("Yhteensä"
                         ""
                         ""
                         ""
                         4000M
                         28.32748164698374848000M))]]))))

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
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Erilliskustannusten raportti"}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{5
                                                    6}
                      :otsikko "Pohjois-Pohjanmaa ja Kainuu, Erilliskustannusten raportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Erilliskustannusten raportti"
                      :viimeinen-rivi-yhteenveto? true}
                     '({:leveys  10
                      :otsikko "Urakka"}
                     {:leveys  7
                      :otsikko "Pvm"}
                     {:leveys  7
                      :otsikko "Sop. nro"}
                     {:leveys  12
                      :otsikko "Toimenpide"}
                     {:leveys  7
                      :otsikko "Tyyppi"}
                     {:fmt     :raha
                      :leveys  6
                      :otsikko "Summa €"}
                     {:fmt     :raha
                      :leveys  6
                      :otsikko "Ind.korotus €"})
                     '(["Kajaanin alueurakka 2014-2019"
                       "15.09.2015"
                       "7A26339/05"
                       "Kajaani Talvihoito TP 2014-2019"
                       "Muu"
                       1000M
                       16.9166932652410000M]
                       ["Kajaanin alueurakka 2014-2019"
                        "15.08.2015"
                        "7A26339/05"
                        "Kajaani Talvihoito TP 2014-2019"
                        "As.tyyt.­bonus"
                        1000M
                        4.06958187041174593000M]
                       ["Kajaanin alueurakka 2014-2019"
                        "01.08.2015"
                        "7A26339/05"
                        "Kajaani Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
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
                        -2.23428024257899745000M]
                       ["Kajaanin alueurakka 2014-2019"
                        "15.05.2015"
                        "7A26339/05"
                        "Kajaani Talvihoito TP 2014-2019"
                        "Muu"
                        -1000M
                        -7.3412065113310000M]
                       ["Oulun alueurakka 2014-2019"
                        "15.09.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
                       ["Oulun alueurakka 2014-2019"
                        "15.08.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "As.tyyt.­bonus"
                        1000M
                        4.06958187041174593000M]
                       ["Oulun alueurakka 2014-2019"
                        "01.08.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
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
                        -2.23428024257899745000M]
                       ["Oulun alueurakka 2014-2019"
                        "15.05.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        -1000M
                        -7.3412065113310000M]
                       ("Yhteensä"
                         ""
                         ""
                         ""
                         ""
                         8000M
                         56.65496329396749696000M))]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :erilliskustannukset
                                 :konteksti "koko maa"
                                 :parametrit {:toimenpide-id nil
                                              :alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Erilliskustannusten raportti"}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{5
                                                    6}
                      :otsikko "KOKO MAA, Erilliskustannusten raportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Erilliskustannusten raportti"
                      :viimeinen-rivi-yhteenveto? true}
                     '({:leveys  10
                       :otsikko "Urakka"}
                       {:leveys  7
                        :otsikko "Pvm"}
                       {:leveys  7
                        :otsikko "Sop. nro"}
                       {:leveys  12
                        :otsikko "Toimenpide"}
                       {:leveys  7
                        :otsikko "Tyyppi"}
                       {:fmt     :raha
                        :leveys  6
                        :otsikko "Summa €"}
                       {:fmt     :raha
                        :leveys  6
                        :otsikko "Ind.korotus €"})
                     '(["Espoon alueurakka 2014-2019"
                       "15.09.2015"
                       "7eS6339/05"
                       "Espoo Talvihoito TP 2014-2019"
                       "Muu"
                       1000M
                       16.9166932652410000M]
                       ["Espoon alueurakka 2014-2019"
                        "15.08.2015"
                        "7eS6339/05"
                        "Espoo Talvihoito TP 2014-2019"
                        "As.tyyt.­bonus"
                        1000M
                        4.06958187041174593000M]
                       ["Espoon alueurakka 2014-2019"
                        "01.08.2015"
                        "7eS6339/05"
                        "Espoo Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
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
                        -2.23428024257899745000M]
                       ["Espoon alueurakka 2014-2019"
                        "15.05.2015"
                        "7eS6339/05"
                        "Espoo Talvihoito TP 2014-2019"
                        "Muu"
                        -1000M
                        -7.3412065113310000M]
                       ["Vantaan alueurakka 2014-2019"
                        "15.09.2015"
                        "7V26339/05"
                        "Vantaa Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
                       ["Vantaan alueurakka 2014-2019"
                        "15.08.2015"
                        "7V26339/05"
                        "Vantaa Talvihoito TP 2014-2019"
                        "As.tyyt.­bonus"
                        1000M
                        4.06958187041174593000M]
                       ["Vantaan alueurakka 2014-2019"
                        "01.08.2015"
                        "7V26339/05"
                        "Vantaa Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
                       ["Vantaan alueurakka 2014-2019"
                        "15.07.2015"
                        "7V26339/05"
                        "Vantaa Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        0]
                       ["Vantaan alueurakka 2014-2019"
                        "15.06.2015"
                        "7V26339/05"
                        "Vantaa Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        -2.23428024257899745000M]
                       ["Vantaan alueurakka 2014-2019"
                        "15.05.2015"
                        "7V26339/05"
                        "Vantaa Talvihoito TP 2014-2019"
                        "Muu"
                        -1000M
                        -7.3412065113310000M]
                       ["Kajaanin alueurakka 2014-2019"
                        "15.09.2015"
                        "7A26339/05"
                        "Kajaani Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
                       ["Kajaanin alueurakka 2014-2019"
                        "15.08.2015"
                        "7A26339/05"
                        "Kajaani Talvihoito TP 2014-2019"
                        "As.tyyt.­bonus"
                        1000M
                        4.06958187041174593000M]
                       ["Kajaanin alueurakka 2014-2019"
                        "01.08.2015"
                        "7A26339/05"
                        "Kajaani Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
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
                        -2.23428024257899745000M]
                       ["Kajaanin alueurakka 2014-2019"
                        "15.05.2015"
                        "7A26339/05"
                        "Kajaani Talvihoito TP 2014-2019"
                        "Muu"
                        -1000M
                        -7.3412065113310000M]
                       ["Oulun alueurakka 2014-2019"
                        "15.09.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
                       ["Oulun alueurakka 2014-2019"
                        "15.08.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "As.tyyt.­bonus"
                        1000M
                        4.06958187041174593000M]
                       ["Oulun alueurakka 2014-2019"
                        "01.08.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        1000M
                        16.9166932652410000M]
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
                        -2.23428024257899745000M]
                       ["Oulun alueurakka 2014-2019"
                        "15.05.2015"
                        "2H16339/01"
                        "Oulu Talvihoito TP 2014-2019"
                        "Muu"
                        -1000M
                        -7.3412065113310000M]
                       ("Yhteensä"
                         ""
                         ""
                         ""
                         ""
                         16000M
                         113.30992658793499392000M))]]))))