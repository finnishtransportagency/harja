(ns harja.palvelin.raportointi.materiaaliraportti-test
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
                                {:nimi :materiaaliraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Materiaaliraportti"}
                    [:taulukko
                     {:otsikko "Oulun alueurakka 2014-2019, Materiaaliraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Materiaaliraportti"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:otsikko "Urakka"}
                      {:fmt :numero
                       :otsikko "Talvisuolaliuos NaCl (t)"}]
                     [["Oulun alueurakka 2014-2019"
                       1000M]
                       ["Yhteensä"
                        1000M]]]]))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :materiaaliraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Materiaaliraportti"}
                    [:taulukko
                     {:otsikko "Pohjois-Pohjanmaa ja Kainuu, Materiaaliraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Materiaaliraportti"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:otsikko "Urakka"}
                      {:fmt :numero
                       :otsikko "Talvisuolaliuos NaCl (t)"}]
                     [["Oulun alueurakka 2014-2019"
                       1000M]
                       ["Kajaanin alueurakka 2014-2019"
                        1000M]
                       ["Yhteensä"
                        2000M]]]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :materiaaliraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Materiaaliraportti"}
                    [:taulukko
                     {:otsikko "KOKO MAA, Materiaaliraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Materiaaliraportti"
                      :viimeinen-rivi-yhteenveto? true}
                     [{:otsikko "Urakka"}
                      {:fmt :numero
                       :otsikko "Talvisuolaliuos NaCl (t)"}]
                     [["Uusimaa"
                       2000M]
                       ["Pohjois-Pohjanmaa ja Kainuu"
                        2000M]
                       ["Yhteensä"
                        4000M]]]]))))