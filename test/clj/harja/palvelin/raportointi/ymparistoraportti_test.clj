(ns harja.palvelin.raportointi.ymparistoraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.urakat :as urk-q]
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
                                {:nimi :ymparistoraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 9 30))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Ympäristöraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{1
                                                    10
                                                    11
                                                    12
                                                    13
                                                    14
                                                    15
                                                    2
                                                    3
                                                    4
                                                    5
                                                    6
                                                    7
                                                    8
                                                    9}
                      :otsikko "Oulun alueurakka 2014-2019, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
                      :sheet-nimi "Ympäristöraportti"}
                     [{:leveys "16%"
                       :otsikko "Materiaali"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "10/15"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "11/15"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "12/15"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "01/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "02/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "03/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "04/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "05/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "06/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "07/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "08/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "09/16"}
                      {:excel [:summa-vasen
                               1]
                       :fmt :numero
                       :jos-tyhja "-"
                       :leveys "8%"
                       :otsikko "Määrä yhteensä"}
                      {:fmt :prosentti
                       :jos-tyhja "-"
                       :leveys "8%"
                       :otsikko "Tot-%"}
                      {:fmt :numero
                       :jos-tyhja "-"
                       :leveys "8%"
                       :otsikko "Maksimi­määrä"}]
                     [{:lihavoi? true
                       :rivi ["Talvisuola"
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              0
                              nil
                              nil]}
                       {:lihavoi? true
                        :rivi ["Talvisuolaliuos CaCl2"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Talvisuolaliuos NaCl"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Erityisalueet NaCl"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Erityisalueet NaCl-liuos"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Kaliumformiaatti"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Hiekoitushiekan suola"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Kesäsuola (pölynsidonta)"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Kesäsuola (sorateiden kevätkunnostus)"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Hiekoitushiekka"
                               nil
                               nil
                               nil
                               nil
                               nil
                               500M
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               500M
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Jätteet kaatopaikalle"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Murskeet"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Rikkaruohojen torjunta-aineet"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Erityisalueet CaCl2-liuos"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}]]]))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi      :ymparistoraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm            (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm           (c/to-date (t/local-date 2016 9 30))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Ympäristöraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{1
                                                    10
                                                    11
                                                    12
                                                    13
                                                    14
                                                    15
                                                    2
                                                    3
                                                    4
                                                    5
                                                    6
                                                    7
                                                    8
                                                    9}
                      :otsikko "Pohjois-Pohjanmaa, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
                      :sheet-nimi "Ympäristöraportti"}
                     [{:leveys "16%"
                       :otsikko "Materiaali"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "10/15"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "11/15"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "12/15"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "01/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "02/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "03/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "04/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "05/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "06/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "07/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "08/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "09/16"}
                      {:excel [:summa-vasen
                               1]
                       :fmt :numero
                       :jos-tyhja "-"
                       :leveys "8%"
                       :otsikko "Määrä yhteensä"}
                      {:fmt :prosentti
                       :jos-tyhja "-"
                       :leveys "8%"
                       :otsikko "Tot-%"}
                      {:fmt :numero
                       :jos-tyhja "-"
                       :leveys "8%"
                       :otsikko "Maksimi­määrä"}]
                     [{:lihavoi? true
                       :rivi ["Talvisuola"
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              0
                              nil
                              nil]}
                       {:lihavoi? true
                        :rivi ["Talvisuolaliuos CaCl2"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Talvisuolaliuos NaCl"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Erityisalueet NaCl"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Erityisalueet NaCl-liuos"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Kaliumformiaatti"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Hiekoitushiekan suola"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Kesäsuola (pölynsidonta)"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Kesäsuola (sorateiden kevätkunnostus)"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Hiekoitushiekka"
                               nil
                               nil
                               nil
                               nil
                               nil
                               500M
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               500M
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Jätteet kaatopaikalle"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Murskeet"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Rikkaruohojen torjunta-aineet"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Erityisalueet CaCl2-liuos"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}]]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi      :ymparistoraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 9 30))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Ympäristöraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{1
                                                    10
                                                    11
                                                    12
                                                    13
                                                    14
                                                    15
                                                    2
                                                    3
                                                    4
                                                    5
                                                    6
                                                    7
                                                    8
                                                    9}
                      :otsikko "KOKO MAA, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
                      :sheet-nimi "Ympäristöraportti"}
                     [{:leveys "16%"
                       :otsikko "Materiaali"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "10/15"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "11/15"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "12/15"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "01/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "02/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "03/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "04/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "05/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "06/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "07/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "08/16"}
                      {:fmt :numero
                       :leveys "5%"
                       :otsikko "09/16"}
                      {:excel [:summa-vasen
                               1]
                       :fmt :numero
                       :jos-tyhja "-"
                       :leveys "8%"
                       :otsikko "Määrä yhteensä"}
                      {:fmt :prosentti
                       :jos-tyhja "-"
                       :leveys "8%"
                       :otsikko "Tot-%"}
                      {:fmt :numero
                       :jos-tyhja "-"
                       :leveys "8%"
                       :otsikko "Maksimi­määrä"}]
                     [{:lihavoi? true
                       :rivi ["Talvisuola"
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              nil
                              0
                              nil
                              nil]}
                       {:lihavoi? true
                        :rivi ["Talvisuolaliuos CaCl2"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Talvisuolaliuos NaCl"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Erityisalueet NaCl"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Erityisalueet NaCl-liuos"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Kaliumformiaatti"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Hiekoitushiekan suola"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Kesäsuola (pölynsidonta)"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Kesäsuola (sorateiden kevätkunnostus)"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Hiekoitushiekka"
                               nil
                               nil
                               nil
                               nil
                               nil
                               500M
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               500M
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Jätteet kaatopaikalle"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Murskeet"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Rikkaruohojen torjunta-aineet"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}
                       {:lihavoi? true
                        :rivi ["Erityisalueet CaCl2-liuos"
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               nil
                               0
                               nil
                               nil]}]]]))))