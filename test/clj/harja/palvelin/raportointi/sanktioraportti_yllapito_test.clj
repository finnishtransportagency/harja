(ns harja.palvelin.raportointi.sanktioraportti-yllapito-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.testiapurit :as apurit]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yhteiset :as yhteiset]
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

(defn muistutus-solu [arvo]
  [:arvo-ja-yksikko {:arvo arvo :yksikko " kpl" :fmt? false}])

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :sanktioraportti-yllapito
                                 :konteksti "urakka"
                                 :urakka-id (hae-muhoksen-paallystysurakan-id)
                                 :parametrit {:alkupvm (pvm/->pvm "1.1.2017")
                                              :loppupvm (pvm/->pvm "31.1.2017")
                                              :urakkatyyppi :paallystys}})
        nurkkasumma (last (last (last (last (butlast vastaus)))))]
    (is (vector? vastaus))
    (is (= [:teksti "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."] (last vastaus)))
    (is (=marginaalissa? nurkkasumma -2500.00))
    (let [otsikko "Muhoksen päällystysurakka, Sakko- ja bonusraportti tammikuussa 2017"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko ""}
                                          {:otsikko "Muhoksen päällystysurakka"})
      (apurit/tarkista-taulukko-rivit taulukko
                                      {:otsikko "Ylläpitoluokka 1"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 0)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ 2000M]

                                      {:otsikko "Ylläpitoluokka 2"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 1)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ 0]


                                      {:otsikko "Ylläpitoluokka 3"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 1)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ -3000M]


                                      {:otsikko "Ei ylläpitoluokkaa"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 0)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ -1500M]


                                      {:otsikko "Yhteensä"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 2)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ -2500M]))))


(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :sanktioraportti-yllapito
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (pvm/->pvm "1.1.2017")
                                              :loppupvm (pvm/->pvm "31.12.2017")
                                              :urakkatyyppi :paallystys}})
        nurkkasumma (last (last (last (last (butlast vastaus)))))]
    (is (vector? vastaus))
    (is (= [:teksti "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."] (last vastaus)))
    (is (=marginaalissa? nurkkasumma -2500.00))
    (let [otsikko "Pohjois-Pohjanmaa, Sakko- ja bonusraportti ajalta 01.01.2017 - 31.12.2017"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko ""}
                                          {:otsikko "Muhoksen päällystysurakka"}
                                          {:otsikko "Oulun päällystyksen palvelusopimus"}
                                          {:otsikko "YHA-päällystysurakka"}
                                          {:otsikko "YHA-päällystysurakka (sidottu)"}
                                          {:otsikko "Yh\u00ADteen\u00ADsä"})
      (apurit/tarkista-taulukko-rivit taulukko
                                      {:otsikko "Ylläpitoluokka 1"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ 2000M 0 0 0 2000M]


                                      {:otsikko "Ylläpitoluokka 2"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 1) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 1)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ 0 0 0 0 0]


                                      {:otsikko "Ylläpitoluokka 3"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 1) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 1)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ -3000M 0 0 0 -3000M]


                                      {:otsikko "Ei ylläpitoluokkaa"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ -1500M 0 0 0 -1500M]


                                      {:otsikko "Yhteensä"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 2) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 2)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ -2500M 0 0 0 -2500M]))))




(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :sanktioraportti-yllapito
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (pvm/->pvm "1.1.2017")
                                              :loppupvm (pvm/->pvm "31.12.2017")
                                              :urakkatyyppi :paallystys}})
        nurkkasumma (last (last (last (last (butlast vastaus)))))]
    (is (vector? vastaus))
    (is (= [:teksti "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."] (last vastaus)))
    (is (=marginaalissa? nurkkasumma -5500.00M))
    (let [otsikko "KOKO MAA, Sakko- ja bonusraportti ajalta 01.01.2017 - 31.12.2017"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko ""}
                                          {:otsikko "01 Uusimaa"}
                                          {:otsikko "02 Varsinais-Suomi"}
                                          {:otsikko "03 Kaakkois-Suomi"}
                                          {:otsikko "04 Pirkanmaa"}
                                          {:otsikko "08 Pohjois-Savo"}
                                          {:otsikko "09 Keski-Suomi"}
                                          {:otsikko "10 Etelä-Pohjanmaa"}
                                          {:otsikko "12 Pohjois-Pohjanmaa"}
                                          {:otsikko "14 Lappi"}
                                          {:otsikko "Yh\u00ADteen\u00ADsä"})
      (apurit/tarkista-taulukko-rivit taulukko
                                      {:otsikko "Ylläpitoluokka 1"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ 0 0 0 0 0 0 0 2000M  0 2000M]

                                      {:otsikko "Ylläpitoluokka 2"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 1) (muistutus-solu 0) (muistutus-solu 1)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ 0 0 0 0 0 0 0 0  0 0]

                                      {:otsikko "Ylläpitoluokka 3"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 1) (muistutus-solu 0) (muistutus-solu 1)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ 0 0 0 0 0 0 0 -3000M  0 -3000M]

                                      {:otsikko "Ei ylläpitoluokkaa"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ 0 -3000M 0 0 0 0 0 -1500M  0 -4500M]

                                      {:otsikko "Yhteensä"}
                                      [yhteiset/+muistutusrivin-nimi-yllapito+ (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 0) (muistutus-solu 2) (muistutus-solu 0) (muistutus-solu 2)]
                                      [yhteiset/+sakkorivin-nimi-yllapito+ 0 -3000M 0 0 0 0 0 -2500M  0 -5500M]))))
