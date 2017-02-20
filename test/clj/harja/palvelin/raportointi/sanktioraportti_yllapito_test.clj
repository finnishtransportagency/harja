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
                                {:nimi :sanktioraportti-yllapito
                                 :konteksti "urakka"
                                 :urakka-id (hae-muhoksen-paallystysurakan-id)
                                 :parametrit {:alkupvm (pvm/->pvm "1.1.2017")
                                              :loppupvm (pvm/->pvm "31.1.2017")}})
        nurkkasumma (last (last (last (last vastaus))))]
    (is (vector? vastaus))
    (is (=marginaalissa? nurkkasumma 2500.00))
    (let [otsikko "Muhoksen päällystysurakka, Sanktioraportti tammikuussa 2017"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko ""}
                                          {:otsikko "Muhoksen päällystysurakka"})
      (apurit/tarkista-taulukko-rivit taulukko
                                      {:otsikko "Ylläpitoluokka 1"}
                                      ["Muistutukset yht. (kpl)" "0 kpl"]
                                      ["Indeksit yht. (€)" 0]
                                      ["Kaikki sakot yht. (€)" -2000M]
                                      ["Kaikki yht. (€)" -2000M]

                                      {:otsikko "Ylläpitoluokka 2"}
                                      ["Muistutukset yht. (kpl)" "1 kpl"]
                                      ["Indeksit yht. (€)" 0]
                                      ["Kaikki sakot yht. (€)" 0]
                                      ["Kaikki yht. (€)" 0]

                                      {:otsikko "Ylläpitoluokka 3"}
                                      ["Muistutukset yht. (kpl)" "1 kpl"]
                                      ["Indeksit yht. (€)" 0]
                                      ["Kaikki sakot yht. (€)" 3000M]
                                      ["Kaikki yht. (€)" 3000M]

                                      {:otsikko "Ei ylläpitoluokkaa"}
                                      ["Muistutukset yht. (kpl)" "0 kpl"]
                                      ["Indeksit yht. (€)" 0]
                                      ["Kaikki sakot yht. (€)" 1500M]
                                      ["Kaikki yht. (€)" 1500M]

                                      {:otsikko "Yhteensä"}
                                      ["Muistutukset yht. (kpl)" "2 kpl"]
                                      ["Indeksit yht. (€)" 0]
                                      ["Kaikki sakot yht. (€)" 2500M]
                                      ["Kaikki yht. (€)" 2500M]))))


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
        nurkkasumma (last (last (last (last vastaus))))]
    (is (vector? vastaus))
    (is (=marginaalissa? nurkkasumma 2500.00))
    (let [otsikko "Pohjois-Pohjanmaa, Sanktioraportti ajalta 01.01.2017 - 31.12.2017"
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
                                      ["Muistutukset yht. (kpl)" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl"]
                                      ["Indeksit yht. (€)" 0 0 0 0 0]
                                      ["Kaikki sakot yht. (€)" -2000M 0 0 0 -2000M]
                                      ["Kaikki yht. (€)" -2000M 0 0 0 -2000M]

                                      {:otsikko "Ylläpitoluokka 2"}
                                      ["Muistutukset yht. (kpl)" "1 kpl" "0 kpl" "0 kpl" "0 kpl" "1 kpl"]
                                      ["Indeksit yht. (€)" 0 0 0 0 0]
                                      ["Kaikki sakot yht. (€)" 0 0 0 0 0]
                                      ["Kaikki yht. (€)" 0 0 0 0 0]

                                      {:otsikko "Ylläpitoluokka 3"}
                                      ["Muistutukset yht. (kpl)" "1 kpl" "0 kpl" "0 kpl" "0 kpl" "1 kpl"]
                                      ["Indeksit yht. (€)" 0 0 0 0 0]
                                      ["Kaikki sakot yht. (€)" 3000M 0 0 0 3000M]
                                      ["Kaikki yht. (€)" 3000M 0 0 0 3000M]

                                      {:otsikko "Ei ylläpitoluokkaa"}
                                      ["Muistutukset yht. (kpl)" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl"]
                                      ["Indeksit yht. (€)" 0 0 0 0 0]
                                      ["Kaikki sakot yht. (€)" 1500M 0 0 0 1500M]
                                      ["Kaikki yht. (€)" 1500M 0 0 0 1500M]

                                      {:otsikko "Yhteensä"}
                                      ["Muistutukset yht. (kpl)" "2 kpl" "0 kpl" "0 kpl" "0 kpl" "2 kpl"]
                                      ["Indeksit yht. (€)" 0 0 0 0 0]
                                      ["Kaikki sakot yht. (€)" 2500M 0 0 0 2500M]
                                      ["Kaikki yht. (€)" 2500M 0 0 0 2500M]))))




(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :sanktioraportti-yllapito
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (pvm/->pvm "1.1.2017")
                                              :loppupvm (pvm/->pvm "31.12.2017")
                                              :urakkatyyppi :paallystys}})
        nurkkasumma (last (last (last (last vastaus))))]
    (is (vector? vastaus))
    (is (=marginaalissa? nurkkasumma 5500.00M))
    (let [otsikko "KOKO MAA, Sanktioraportti ajalta 01.01.2017 - 31.12.2017"
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
                                      ["Muistutukset yht. (kpl)" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl"]
                                      ["Indeksit yht. (€)" 0 0 0 0 0 0 0 0 0 0]
                                      ["Kaikki sakot yht. (€)" 0 0 0 0 0 0 0 -2000M  0 -2000M]
                                      ["Kaikki yht. (€)" 0 0 0 0 0 0 0 -2000M 0 -2000M]

                                      {:otsikko "Ylläpitoluokka 2"}
                                      ["Muistutukset yht. (kpl)" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "1 kpl" "0 kpl" "1 kpl"]
                                      ["Indeksit yht. (€)" 0 0 0 0 0 0 0 0 0 0]
                                      ["Kaikki sakot yht. (€)" 0 0 0 0 0 0 0 0  0 0]
                                      ["Kaikki yht. (€)" 0 0 0 0 0 0 0 0 0 0]

                                      {:otsikko "Ylläpitoluokka 3"}
                                      ["Muistutukset yht. (kpl)" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "1 kpl" "0 kpl" "1 kpl"]
                                      ["Indeksit yht. (€)" 0 0 0 0 0 0 0 0 0 0]
                                      ["Kaikki sakot yht. (€)" 0 0 0 0 0 0 0 3000M  0 3000M]
                                      ["Kaikki yht. (€)" 0 0 0 0 0 0 0 3000M 0 3000M]

                                      {:otsikko "Ei ylläpitoluokkaa"}
                                      ["Muistutukset yht. (kpl)" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl"]
                                      ["Indeksit yht. (€)" 0 0 0 0 0 0 0 0 0 0]
                                      ["Kaikki sakot yht. (€)" 0 3000M 0 0 0 0 0 1500M  0 4500M]
                                      ["Kaikki yht. (€)" 0 3000M 0 0 0 0 0 1500M  0 4500M]

                                      {:otsikko "Yhteensä"}
                                      ["Muistutukset yht. (kpl)" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "0 kpl" "2 kpl" "0 kpl" "2 kpl"]
                                      ["Indeksit yht. (€)" 0 0 0 0 0 0 0 0 0 0]
                                      ["Kaikki sakot yht. (€)" 0 3000M 0 0 0 0 0 2500M  0 5500M]
                                      ["Kaikki yht. (€)" 0 3000M 0 0 0 0 0 2500M 0 5500M]))))
