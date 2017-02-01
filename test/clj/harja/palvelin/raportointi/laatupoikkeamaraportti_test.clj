(ns harja.palvelin.raportointi.laatupoikkeamaraportti-test
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
                                {:nimi :laatupoikkeamaraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Laatupoikkeamaraportti"
                     :orientaatio :landscape}
                    nil
                    [:taulukko
                     {:otsikko "Oulun alueurakka 2014-2019, Laatupoikkeamaraportti ajalta 01.10.2015 - 01.10.2016"
                      :sheet-nimi "Laatupoikkeamaraportti"
                      :tyhja nil}
                     [{:fmt :pvm
                       :leveys 15
                       :otsikko "Päi­vä­mää­rä"}
                      {:leveys 20
                       :otsikko "Koh­de"}
                      {:leveys 10
                       :otsikko "Te­ki­jä"}
                      {:leveys 35
                       :otsikko "Ku­vaus"}
                      {:leveys 25
                       :otsikko "Liit­teet"
                       :tyyppi :liite}]
                     [["17.10.2015"
                       "Testikohde"
                       "urakoitsija"
                       "Palava autonromu jätetty keskelle tietä!!"
                       [:liitteet
                        []]]
                       ["16.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Roskakori kaatunut"
                        [:liitteet
                         []]]
                       ["15.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Aidassa reikä"
                        [:liitteet
                         []]]
                       ["14.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Testihavainto 5"
                        [:liitteet
                         []]]
                       ["12.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Testihavainto 4"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 2"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 7"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 5"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 1"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 666"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 667"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Testihavainto 3"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 4"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 6"
                        [:liitteet
                         []]]]]]))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :laatupoikkeamaraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Laatupoikkeamaraportti"
                     :orientaatio :landscape}
                    nil
                    [:taulukko
                     {:otsikko "Pohjois-Pohjanmaa, Laatupoikkeamaraportti ajalta 01.10.2015 - 01.10.2016"
                      :sheet-nimi "Laatupoikkeamaraportti"
                      :tyhja nil}
                     [{:fmt :pvm
                       :leveys 15
                       :otsikko "Päi­vä­mää­rä"}
                      {:leveys 20
                       :otsikko "Koh­de"}
                      {:leveys 10
                       :otsikko "Te­ki­jä"}
                      {:leveys 35
                       :otsikko "Ku­vaus"}
                      {:leveys 25
                       :otsikko "Liit­teet"
                       :tyyppi :liite}]
                     [{:otsikko "Oulun alueurakka 2014-2019"}
                       ["17.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Palava autonromu jätetty keskelle tietä!!"
                        [:liitteet
                         []]]
                       ["16.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Roskakori kaatunut"
                        [:liitteet
                         []]]
                       ["15.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Aidassa reikä"
                        [:liitteet
                         []]]
                       ["14.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Testihavainto 5"
                        [:liitteet
                         []]]
                       ["12.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Testihavainto 4"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 2"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 7"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 5"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 1"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 666"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 667"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Testihavainto 3"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 4"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 6"
                        [:liitteet
                         []]]]]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :laatupoikkeamaraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Laatupoikkeamaraportti"
                     :orientaatio :landscape}
                    nil
                    [:taulukko
                     {:otsikko "KOKO MAA, Laatupoikkeamaraportti ajalta 01.10.2015 - 01.10.2016"
                      :sheet-nimi "Laatupoikkeamaraportti"
                      :tyhja nil}
                     [{:fmt :pvm
                       :leveys 15
                       :otsikko "Päi­vä­mää­rä"}
                      {:leveys 20
                       :otsikko "Koh­de"}
                      {:leveys 10
                       :otsikko "Te­ki­jä"}
                      {:leveys 35
                       :otsikko "Ku­vaus"}
                      {:leveys 25
                       :otsikko "Liit­teet"
                       :tyyppi :liite}]
                     [{:otsikko "Oulun alueurakka 2014-2019"}
                       ["17.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Palava autonromu jätetty keskelle tietä!!"
                        [:liitteet
                         []]]
                       ["16.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Roskakori kaatunut"
                        [:liitteet
                         []]]
                       ["15.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Aidassa reikä"
                        [:liitteet
                         []]]
                       ["14.10.2015"
                        "Testikohde"
                        "urakoitsija"
                        "Testihavainto 5"
                        [:liitteet
                         []]]
                       ["12.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Testihavainto 4"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 2"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 7"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 5"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 1"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 666"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 667"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Testihavainto 3"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 4"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 6"
                        [:liitteet
                         []]]
                       {:otsikko "Vantaan alueurakka 2009-2019"}
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 9990"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 999"
                        [:liitteet
                         []]]
                       {:otsikko "Espoon alueurakka 2014-2019"}
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 3424"
                        [:liitteet
                         []]]
                       ["11.10.2015"
                        "Testikohde"
                        "tilaaja"
                        "Sanktion sisältävä laatupoikkeama 6767"
                        [:liitteet
                         []]]]]]))))