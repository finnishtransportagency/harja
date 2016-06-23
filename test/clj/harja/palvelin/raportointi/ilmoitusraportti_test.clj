(ns harja.palvelin.raportointi.ilmoitusraportti-test
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
                                {:nimi :ilmoitusraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Ilmoitusraportti"}
                    [:taulukko
                     {:otsikko "Oulun alueurakka 2014-2019, Ilmoitusraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Ilmoitusraportti"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 31
                       :otsikko "Urakka"}
                      {:leveys 23
                       :otsikko "TPP (Toimenpide­pyyntö)"}
                      {:leveys 23
                       :otsikko "TUR (Tiedoksi)"}
                      {:leveys 23
                       :otsikko "URK (Kysely)"}]
                     [{:otsikko "Pohjois-Pohjanmaa"}
                       ["Oulun alueurakka 2014-2019"
                        10
                        7
                        3]]]
                    [:pylvaat
                     {:legend ["TPP"
                               "TUR"
                               "URK"]
                      :otsikko "Ilmoitukset kuukausittain 01.10.2014-01.10.2015"
                      :piilota-arvo? #{0}}
                     [["2014/10"
                       [2
                        nil
                        nil]]
                      ["2014/11"
                       [nil
                        2
                        nil]]
                      ["2014/12"
                       [1
                        nil
                        nil]]
                      ["2015/01"
                       [1
                        1
                        1]]
                      ["2015/02"
                       [1
                        1
                        nil]]
                      ["2015/03"
                       [3
                        nil
                        nil]]
                      ["2015/04"
                       [nil
                        1
                        nil]]
                      ["2015/05"
                       []]
                      ["2015/06"
                       [nil
                        1
                        2]]
                      ["2015/07"
                       []]
                      ["2015/08"
                       [1
                        nil
                        nil]]
                      ["2015/09"
                       [1
                        1
                        nil]]
                      ["2015/10"
                       []]]]
                    [:taulukko
                     {:otsikko "Ilmoitukset asiakaspalauteluokittain"}
                     [{:leveys 6
                       :otsikko "Asiakaspalauteluokka"}
                      {:leveys 2
                       :otsikko "TPP (Toimenpidepyyntö)"}
                      {:leveys 2
                       :otsikko "TUR (Tiedoksi)"}
                      {:leveys 2
                       :otsikko "URK (Kysely)"}
                      {:leveys 2
                       :otsikko "Yhteensä"}]
                     [["Auraus ja sohjonpoisto"
                       10
                       0
                       3
                       13]
                      ["Puhtaanapito ja kalusteiden hoito"
                       0
                       7
                       0
                       7]]]]))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :ilmoitusraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Ilmoitusraportti"}
                    [:taulukko
                     {:otsikko "Pohjois-Pohjanmaa, Ilmoitusraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Ilmoitusraportti"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 31
                       :otsikko "Urakka"}
                      {:leveys 23
                       :otsikko "TPP (Toimenpide­pyyntö)"}
                      {:leveys 23
                       :otsikko "TUR (Tiedoksi)"}
                      {:leveys 23
                       :otsikko "URK (Kysely)"}]
                     [{:otsikko "Pohjois-Pohjanmaa"}
                      ["Oulun alueurakka 2014-2019"
                       10
                       7
                       3]
                      ["Yhteensä"
                        10
                        7
                        3]]]
                    [:pylvaat
                     {:legend ["TPP"
                               "TUR"
                               "URK"]
                      :otsikko "Ilmoitukset kuukausittain 01.10.2014-01.10.2015"
                      :piilota-arvo? #{0}}
                     [["2014/10"
                       [2
                        nil
                        nil]]
                      ["2014/11"
                       [nil
                        2
                        nil]]
                      ["2014/12"
                       [1
                        nil
                        nil]]
                      ["2015/01"
                       [1
                        1
                        1]]
                      ["2015/02"
                       [1
                        1
                        nil]]
                      ["2015/03"
                       [3
                        nil
                        nil]]
                      ["2015/04"
                       [nil
                        1
                        nil]]
                      ["2015/05"
                       []]
                      ["2015/06"
                       [nil
                        1
                        2]]
                      ["2015/07"
                       []]
                      ["2015/08"
                       [1
                        nil
                        nil]]
                      ["2015/09"
                       [1
                        1
                        nil]]
                      ["2015/10"
                       []]]]
                    [:taulukko
                     {:otsikko "Ilmoitukset asiakaspalauteluokittain"}
                     [{:leveys 6
                       :otsikko "Asiakaspalauteluokka"}
                      {:leveys 2
                       :otsikko "TPP (Toimenpidepyyntö)"}
                      {:leveys 2
                       :otsikko "TUR (Tiedoksi)"}
                      {:leveys 2
                       :otsikko "URK (Kysely)"}
                      {:leveys 2
                       :otsikko "Yhteensä"}]
                     [["Auraus ja sohjonpoisto"
                       10
                       0
                       3
                       13]
                      ["Puhtaanapito ja kalusteiden hoito"
                       0
                       7
                       0
                       7]]]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :ilmoitusraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Ilmoitusraportti"}
                    [:taulukko
                     {:otsikko "KOKO MAA, Ilmoitusraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Ilmoitusraportti"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 31
                       :otsikko "Urakka"}
                      {:leveys 23
                       :otsikko "TPP (Toimenpide­pyyntö)"}
                      {:leveys 23
                       :otsikko "TUR (Tiedoksi)"}
                      {:leveys 23
                       :otsikko "URK (Kysely)"}]
                     [{:otsikko "Pohjois-Pohjanmaa"}
                      ["Oulun alueurakka 2014-2019"
                       10
                       7
                       3]
                      ["Pohjois-Pohjanmaa yhteensä"
                        10
                        7
                        3]
                      ["Yhteensä"
                        10
                        7
                        3]]]
                    [:pylvaat
                     {:legend ["TPP"
                               "TUR"
                               "URK"]
                      :otsikko "Ilmoitukset kuukausittain 01.10.2014-01.10.2015"
                      :piilota-arvo? #{0}}
                     [["2014/10"
                       [2
                        nil
                        nil]]
                      ["2014/11"
                       [nil
                        2
                        nil]]
                      ["2014/12"
                       [1
                        nil
                        nil]]
                      ["2015/01"
                       [1
                        1
                        1]]
                      ["2015/02"
                       [1
                        1
                        nil]]
                      ["2015/03"
                       [3
                        nil
                        nil]]
                      ["2015/04"
                       [nil
                        1
                        nil]]
                      ["2015/05"
                       []]
                      ["2015/06"
                       [nil
                        1
                        2]]
                      ["2015/07"
                       []]
                      ["2015/08"
                       [1
                        nil
                        nil]]
                      ["2015/09"
                       [1
                        1
                        nil]]
                      ["2015/10"
                       []]]]
                    [:taulukko
                     {:otsikko "Ilmoitukset asiakaspalauteluokittain"}
                     [{:leveys 6
                       :otsikko "Asiakaspalauteluokka"}
                      {:leveys 2
                       :otsikko "TPP (Toimenpidepyyntö)"}
                      {:leveys 2
                       :otsikko "TUR (Tiedoksi)"}
                      {:leveys 2
                       :otsikko "URK (Kysely)"}
                      {:leveys 2
                       :otsikko "Yhteensä"}]
                     [["Auraus ja sohjonpoisto"
                       10
                       0
                       3
                       13]
                      ["Puhtaanapito ja kalusteiden hoito"
                       0
                       7
                       0
                       7]]]]))))