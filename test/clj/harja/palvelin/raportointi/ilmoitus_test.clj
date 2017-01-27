(ns harja.palvelin.raportointi.ilmoitus-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.domain.ilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-lyhenne-ja-nimi +ilmoitustilat+]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi :refer :all]
            [harja.palvelin.raportointi.raportit.ilmoitus :as ilmoitusraportti]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.testiapurit :as apurit]))

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

(deftest hae-ilmoitukset-raportille-test
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        [alkupvm loppupvm] (pvm/paivamaaran-hoitokausi (pvm/->pvm "1.11.2016"))
        ilmoitukset
        (ilmoitusraportti/hae-ilmoitukset-raportille
          db +kayttaja-jvh+ {:hallintayksikko-id  nil :urakka-id nil
                             :urakoitsija nil :urakkatyyppi :hoito
                             :alkupvm alkupvm :loppupvm loppupvm})]
    (is (not (empty? ilmoitukset)))
    (is (= (count ilmoitukset) 5))))

(deftest ilmoitusraportti-pop-ely
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :ilmoitusraportti
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (pvm/->pvm "1.10.2016")
                                                      :loppupvm     (pvm/->pvm "30.09.2017")
                                                      :urakkatyyppi "hoito"}})
        pylvasgraafin-viimeinen-elementti (last (last (last vastaus)))]
    (is (vector? vastaus))
    (is (= pylvasgraafin-viimeinen-elementti ["2017/09" []]))
    (let [otsikko "Pohjois-Pohjanmaa, Ilmoitusraportti ajalta 01.10.2016 - 30.09.2017"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Alue"}
                                          {:otsikko "TPP (Toimenpide\u00ADpyyntö)"}
                                          {:otsikko "TUR (Tiedoksi)"}
                                          {:otsikko "URK (Kysely)"})
      (apurit/tarkista-taulukko-rivit taulukko
                                      (fn [[alue tpp tur urk & _ ]]
                                        (and (= alue "Pohjois-Pohjanmaa")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 3)
                                             (= (apurit/raporttisolun-arvo urk) 0)))))))

(deftest ilmoitusraportti-koko-maa
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :ilmoitusraportti
                                 :konteksti          "koko maa"
                                 :hallintayksikko-id nil
                                 :parametrit         {:alkupvm      (pvm/->pvm "1.10.2016")
                                                      :loppupvm     (pvm/->pvm "30.09.2017")
                                                      :urakkatyyppi "hoito"}})
        pylvasgraafin-viimeinen-elementti (last (last (last vastaus)))]
    (is (vector? vastaus))
    (is (= pylvasgraafin-viimeinen-elementti ["2017/09" []]))
    (let [otsikko "KOKO MAA, Ilmoitusraportti ajalta 01.10.2016 - 30.09.2017"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Alue"}
                                          {:otsikko "TPP (Toimenpide\u00ADpyyntö)"}
                                          {:otsikko "TUR (Tiedoksi)"}
                                          {:otsikko "URK (Kysely)"})
      (apurit/tarkista-taulukko-rivit taulukko
                                      (fn [{[alue tpp tur urk & _] :rivi}]
                                        (and (= alue "Uusimaa yhteensä")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 2)
                                             (= (apurit/raporttisolun-arvo urk) 0)))
                                      (fn [{[alue tpp tur urk & _] :rivi}]
                                        (and (= alue "Pohjois-Pohjanmaa yhteensä")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 3)
                                             (= (apurit/raporttisolun-arvo urk) 0)))
                                      (fn [[alue tpp tur urk & _ ]]
                                        (and (= alue "KOKO MAA")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 5)
                                             (= (apurit/raporttisolun-arvo urk) 0)))))))

(deftest ilmoitusraportti-koko-maa-urakoittain
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :ilmoitusraportti
                                 :konteksti          "koko maa"
                                 :hallintayksikko-id nil
                                 :parametrit         {:alkupvm      (pvm/->pvm "1.10.2016")
                                                      :loppupvm     (pvm/->pvm "30.09.2017")
                                                      :urakkatyyppi "hoito"
                                                      :urakoittain?       true}})
        pylvasgraafin-viimeinen-elementti (last (last (last vastaus)))]
    (is (vector? vastaus))
    (is (= pylvasgraafin-viimeinen-elementti ["2017/09" []]))
    (let [otsikko "KOKO MAA, Ilmoitusraportti ajalta 01.10.2016 - 30.09.2017"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Alue"}
                                          {:otsikko "TPP (Toimenpide\u00ADpyyntö)"}
                                          {:otsikko "TUR (Tiedoksi)"}
                                          {:otsikko "URK (Kysely)"})

      (apurit/tarkista-taulukko-rivit taulukko
                                      (fn [{otsikko :otsikko}]
                                        (= otsikko "01 Uusimaa"))
                                      (fn [[alue tpp tur urk & _ ]]
                                        (and (= alue "Vantaan alueurakka 2009-2019")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 1)
                                             (= (apurit/raporttisolun-arvo urk) 0)))
                                      (fn [[alue tpp tur urk & _ ]]
                                        (and (= alue "Espoon alueurakka 2014-2019")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 1)
                                             (= (apurit/raporttisolun-arvo urk) 0)))
                                      (fn [{[alue tpp tur urk & _] :rivi}]
                                        (and (= alue "Uusimaa yhteensä")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 2)
                                             (= (apurit/raporttisolun-arvo urk) 0)))

                                      (fn [{otsikko :otsikko}]
                                        (= otsikko "12 Pohjois-Pohjanmaa"))
                                      (fn [[alue tpp tur urk & _ ]]
                                        (and (= alue "Oulun alueurakka 2014-2019")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 2)
                                             (= (apurit/raporttisolun-arvo urk) 0)))
                                      (fn [[alue tpp tur urk & _ ]]
                                        (and (= alue "Kajaanin alueurakka 2014-2019")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 1)
                                             (= (apurit/raporttisolun-arvo urk) 0)))
                                      (fn [{[alue tpp tur urk & _] :rivi}]
                                        (and (= alue "Pohjois-Pohjanmaa yhteensä")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 3)
                                             (= (apurit/raporttisolun-arvo urk) 0)))

                                      (fn [[alue tpp tur urk & _ ]]
                                        (and (= alue "KOKO MAA")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 5)
                                             (= (apurit/raporttisolun-arvo urk) 0)))))))