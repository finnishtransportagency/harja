(ns harja.palvelin.raportointi.turvallisuusraportti-test
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
                                {:nimi :turvallisuus
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Turvallisuusraportti"}
                    [:taulukko
                     {:otsikko "Oulun alueurakka 2014-2019, Turvallisuusraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Turvallisuusraportti"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:otsikko "Tyyppi"}
                      {:otsikko "Määrä"}]
                     [["Työtapaturma"
                       1]
                       ["Vaaratilanne"
                        0]
                       ["Turvallisuushavainto"
                        0]
                       ["Muu"
                        0]
                       ["Yksittäisiä ilmoituksia yhteensä"
                        1]]]
                    nil
                    [:pylvaat
                     {:legend ["Työtapaturmat"
                               "Vaaratilanteet"
                               "Turvallisuushavainnot"
                               "Muut"]
                      :otsikko "Turvallisuuspoikkeamat kuukausittain 01.10.2014-01.10.2015"
                      :piilota-arvo? #{0}}
                     [["2014/10"
                       []]
                      ["2014/11"
                       []]
                      ["2014/12"
                       []]
                      ["2015/01"
                       []]
                      ["2015/02"
                       []]
                      ["2015/03"
                       []]
                      ["2015/04"
                       []]
                      ["2015/05"
                       []]
                      ["2015/06"
                       []]
                      ["2015/07"
                       []]
                      ["2015/08"
                       []]
                      ["2015/09"
                       []]
                      ["2015/10"
                       [1
                        nil
                        nil
                        nil]]]]
                    [:taulukko
                     {:otsikko "Turvallisuuspoikkeamat listana: 1 kpl"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 14
                       :otsikko "Pvm"}
                      {:leveys 24
                       :otsikko "Tyyppi"}
                      {:leveys 15
                       :otsikko "Vakavuusaste"}
                      {:leveys 14
                       :otsikko "Ammatti"}
                      {:leveys 14
                       :otsikko "Työ­tehtävä"}
                      {:leveys 9
                       :otsikko "Sairaala­vuoro­kaudet"}
                      {:leveys 9
                       :otsikko "Sairaus­poissa­olo­päivät"}]
                     [["1.10.2015 0:20"
                       "Ty­ö­ta­pa­tur­ma"
                       "Vakava"
                       "Porari"
                       "Lastaus"
                       1
                       7]
                       ["Yhteensä"
                        ""
                        ""
                        ""
                        ""
                        1
                        7]]]]))))


(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :turvallisuus
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Turvallisuusraportti"}
                    [:taulukko
                     {:otsikko "Pohjois-Pohjanmaa ja Kainuu, Turvallisuusraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Turvallisuusraportti"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:otsikko "Tyyppi"}
                      {:otsikko "Määrä"}]
                     [["Työtapaturma"
                       1]
                       ["Vaaratilanne"
                        0]
                       ["Turvallisuushavainto"
                        0]
                       ["Muu"
                        0]
                       ["Yksittäisiä ilmoituksia yhteensä"
                        1]]]
                    nil
                    [:pylvaat
                     {:legend ["Työtapaturmat"
                               "Vaaratilanteet"
                               "Turvallisuushavainnot"
                               "Muut"]
                      :otsikko "Turvallisuuspoikkeamat kuukausittain 01.10.2014-01.10.2015"
                      :piilota-arvo? #{0}}
                     [["2014/10"
                       []]
                      ["2014/11"
                       []]
                      ["2014/12"
                       []]
                      ["2015/01"
                       []]
                      ["2015/02"
                       []]
                      ["2015/03"
                       []]
                      ["2015/04"
                       []]
                      ["2015/05"
                       []]
                      ["2015/06"
                       []]
                      ["2015/07"
                       []]
                      ["2015/08"
                       []]
                      ["2015/09"
                       []]
                      ["2015/10"
                       [1
                        nil
                        nil
                        nil]]]]
                    [:taulukko
                     {:otsikko "Turvallisuuspoikkeamat listana: 1 kpl"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 14
                       :otsikko "Pvm"}
                      {:leveys 24
                       :otsikko "Tyyppi"}
                      {:leveys 15
                       :otsikko "Vakavuusaste"}
                      {:leveys 14
                       :otsikko "Ammatti"}
                      {:leveys 14
                       :otsikko "Työ­tehtävä"}
                      {:leveys 9
                       :otsikko "Sairaala­vuoro­kaudet"}
                      {:leveys 9
                       :otsikko "Sairaus­poissa­olo­päivät"}]
                     [["1.10.2015 0:20"
                       "Ty­ö­ta­pa­tur­ma"
                       "Vakava"
                       "Porari"
                       "Lastaus"
                       1
                       7]
                       ["Yhteensä"
                        ""
                        ""
                        ""
                        ""
                        1
                        7]]]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :turvallisuus
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 1 1))
                                              :loppupvm (c/to-date (t/local-date 2015 12 31))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Turvallisuusraportti"}
                    [:taulukko
                     {:otsikko "KOKO MAA, Turvallisuusraportti ajalta 01.01.2014 - 31.12.2015"
                      :sheet-nimi "Turvallisuusraportti"
                      :viimeinen-rivi-yhteenveto? false}
                     [{:otsikko "Hallintayksikkö"}
                      {:otsikko "Työtapaturmat"}
                      {:otsikko "Vaaratilanteet"}
                      {:otsikko "Turvallisuushavainnot"}
                      {:otsikko "Muut"}]
                     [["Uusimaa"
                       0
                       0
                       0
                       0]
                       ["Varsinais-Suomi"
                        0
                        0
                        0
                        0]
                       ["Kaakkois-Suomi"
                        0
                        0
                        0
                        0]
                       ["Pirkanmaa"
                        0
                        0
                        0
                        0]
                       ["Pohjois-Savo"
                        0
                        0
                        0
                        0]
                       ["Keski-Suomi"
                        0
                        0
                        0
                        0]
                       ["Etelä-Pohjanmaa"
                        0
                        0
                        0
                        0]
                       ["Pohjois-Pohjanmaa ja Kainuu"
                        2
                        1
                        3
                        1]
                       ["Lappi"
                        0
                        0
                        0
                        0]]]
                    [:taulukko
                     {:otsikko "Turvallisuuspoikkeamat vakavuusasteittain"}
                     [{:otsikko "Hallintayksikkö"}
                      {:otsikko "Lievät"}
                      {:otsikko "Vakavat"}]
                     [["Uusimaa"
                       0
                       0]
                       ["Varsinais-Suomi"
                        0
                        0]
                       ["Kaakkois-Suomi"
                        0
                        0]
                       ["Pirkanmaa"
                        0
                        0]
                       ["Pohjois-Savo"
                        0
                        0]
                       ["Keski-Suomi"
                        0
                        0]
                       ["Etelä-Pohjanmaa"
                        0
                        0]
                       ["Pohjois-Pohjanmaa ja Kainuu"
                        1
                        4]
                       ["Lappi"
                        0
                        0]]]
                    [:pylvaat
                     {:legend ["Työtapaturmat"
                               "Vaaratilanteet"
                               "Turvallisuushavainnot"
                               "Muut"]
                      :otsikko "Turvallisuuspoikkeamat kuukausittain 01.01.2014-31.12.2015"
                      :piilota-arvo? #{0}}
                     [["2014/01"
                       []]
                      ["2014/02"
                       []]
                      ["2014/03"
                       []]
                      ["2014/04"
                       []]
                      ["2014/05"
                       []]
                      ["2014/06"
                       []]
                      ["2014/07"
                       []]
                      ["2014/08"
                       []]
                      ["2014/09"
                       []]
                      ["2014/10"
                       []]
                      ["2014/11"
                       []]
                      ["2014/12"
                       []]
                      ["2015/01"
                       []]
                      ["2015/02"
                       []]
                      ["2015/03"
                       []]
                      ["2015/04"
                       []]
                      ["2015/05"
                       []]
                      ["2015/06"
                       []]
                      ["2015/07"
                       []]
                      ["2015/08"
                       []]
                      ["2015/09"
                       []]
                      ["2015/10"
                       [2
                        1
                        1
                        1]]
                      ["2015/11"
                       [nil
                        nil
                        2
                        nil]]
                      ["2015/12"
                       []]]]
                    nil]))))
