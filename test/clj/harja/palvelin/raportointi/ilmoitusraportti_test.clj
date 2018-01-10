(ns harja.palvelin.raportointi.ilmoitusraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.testiapurit :as apurit]
            [harja.pvm :as pvm]))

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
    (let [otsikko "Oulun alueurakka 2014-2019, Ilmoitusraportti ajalta 01.10.2014 - 01.10.2015"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Alue"}
                                          {:otsikko "TPP (Toimenpide\u00ADpyyntö)"}
                                          {:otsikko "TUR (Tiedoksi)"}
                                          {:otsikko "URK (Kysely)"})
      (apurit/tarkista-taulukko-rivit taulukko
                                      {:otsikko "12 Pohjois-Pohjanmaa"}
                                      ["Oulun alueurakka 2014-2019" 10 7 3]))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :ilmoitusraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [otsikko "Pohjois-Pohjanmaa, hoito, Ilmoitusraportti ajalta 01.10.2014 - 01.10.2015"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Alue"}
                                          {:otsikko "TPP (Toimenpide\u00ADpyyntö)"}
                                          {:otsikko "TUR (Tiedoksi)"}
                                          {:otsikko "URK (Kysely)"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                             (fn [[alue tpp tur urk :as rivi]]
                                               (and (= (count rivi) 4)
                                                    (string? alue)
                                                    (integer? tpp)
                                                    (integer? tur)
                                                    (integer? urk)))))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :ilmoitusraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [otsikko "KOKO MAA, hoito, Ilmoitusraportti ajalta 01.10.2014 - 01.10.2015"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Alue"}
                                          {:otsikko "TPP (Toimenpide\u00ADpyyntö)"}
                                          {:otsikko "TUR (Tiedoksi)"}
                                          {:otsikko "URK (Kysely)"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                             (fn [rivi]
                                               (let [[alue tpp tur urk :as r]
                                                     (if (map? rivi)
                                                       (:rivi rivi)
                                                       rivi)]
                                                 (and (= (count r) 4)
                                                      (string? alue)
                                                      (integer? tpp)
                                                      (integer? tur)
                                                      (integer? urk))))))))

(deftest ilmoitusraportti-pop-ely
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :ilmoitusraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (pvm/->pvm "1.10.2016")
                                              :loppupvm (pvm/->pvm "30.09.2017")
                                              :urakkatyyppi :kaikki}})
        pylvasgraafin-viimeinen-elementti (last (last (last vastaus)))]
    (is (vector? vastaus))
    (is (= pylvasgraafin-viimeinen-elementti ["2017/09" []]))
    (let [otsikko "Pohjois-Pohjanmaa, kaikki urakkatyypit, Ilmoitusraportti ajalta 01.10.2016 - 30.09.2017"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Alue"}
                                          {:otsikko "TPP (Toimenpide\u00ADpyyntö)"}
                                          {:otsikko "TUR (Tiedoksi)"}
                                          {:otsikko "URK (Kysely)"})
      (apurit/tarkista-taulukko-rivit taulukko
                                      (fn [[alue tpp tur urk & _]]
                                        (and (= alue "Pohjois-Pohjanmaa")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 5)
                                             (= (apurit/raporttisolun-arvo urk) 0)))))))

(deftest ilmoitusraportti-koko-maa
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :ilmoitusraportti
                                 :konteksti "koko maa"
                                 :hallintayksikko-id nil
                                 :parametrit {:alkupvm (pvm/->pvm "1.10.2016")
                                              :loppupvm (pvm/->pvm "30.09.2017")
                                              :urakkatyyppi :kaikki}})
        pylvasgraafin-viimeinen-elementti (last (last (last vastaus)))]
    (is (vector? vastaus))
    (is (= pylvasgraafin-viimeinen-elementti ["2017/09" []]))
    (let [otsikko "KOKO MAA, kaikki urakkatyypit, Ilmoitusraportti ajalta 01.10.2016 - 30.09.2017"
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
                                             (= (apurit/raporttisolun-arvo tur) 5)
                                             (= (apurit/raporttisolun-arvo urk) 0)))
                                      (fn [[alue tpp tur urk & _]]
                                        (and (= alue "KOKO MAA")
                                             (= (apurit/raporttisolun-arvo tpp) 0)
                                             (= (apurit/raporttisolun-arvo tur) 7)
                                             (= (apurit/raporttisolun-arvo urk) 0)))))))

(deftest ilmoitusraportti-koko-maa-urakoittain
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :ilmoitusraportti
                                 :konteksti "koko maa"
                                 :hallintayksikko-id nil
                                 :parametrit {:alkupvm (pvm/->pvm "1.10.2016")
                                              :loppupvm (pvm/->pvm "30.09.2017")
                                              :urakkatyyppi :kaikki
                                              :urakoittain? true}})
        pylvasgraafin-viimeinen-elementti (last (last (last vastaus)))]
    (is (vector? vastaus))
    (is (= pylvasgraafin-viimeinen-elementti ["2017/09" []]))
    (let [tyyppilajit-otsikko "KOKO MAA, kaikki urakkatyypit, Ilmoitusraportti ajalta 01.10.2016 - 30.09.2017"
          toimenpiteet-otsikko "Ilmoitukset aiheutuneiden toimenpiteiden mukaan"
          tyyppilajit-taulukko (apurit/taulukko-otsikolla vastaus tyyppilajit-otsikko)
          toimenpiteet-taulukko (apurit/taulukko-otsikolla vastaus toimenpiteet-otsikko)]
      (apurit/tarkista-taulukko-sarakkeet tyyppilajit-taulukko
                                          {:otsikko "Alue"}
                                          {:otsikko "TPP (Toimenpide\u00ADpyyntö)"}
                                          {:otsikko "TUR (Tiedoksi)"}
                                          {:otsikko "URK (Kysely)"})

      (apurit/tarkista-taulukko-rivit
        tyyppilajit-taulukko
        (fn [{otsikko :otsikko}]
          (= otsikko "01 Uusimaa"))
        (fn [[alue tpp tur urk & _]]
          (and (= alue "Espoon alueurakka 2014-2019")
               (= (apurit/raporttisolun-arvo tpp) 0)
               (= (apurit/raporttisolun-arvo tur) 1)
               (= (apurit/raporttisolun-arvo urk) 0)))
        (fn [[alue tpp tur urk & _]]
          (and (= alue "Vantaan alueurakka 2009-2019")
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
        (fn [[alue tpp tur urk & _]]
          (and (= alue "Kajaanin alueurakka 2014-2019")
               (= (apurit/raporttisolun-arvo tpp) 0)
               (= (apurit/raporttisolun-arvo tur) 1)
               (= (apurit/raporttisolun-arvo urk) 0)))
        (fn [[alue tpp tur urk & _]]
          (and (= alue "Muhoksen päällystysurakka")
               (= (apurit/raporttisolun-arvo tpp) 0)
               (= (apurit/raporttisolun-arvo tur) 1)
               (= (apurit/raporttisolun-arvo urk) 0)))
        (fn [[alue tpp tur urk & _]]
          (and (= alue "Oulun alueurakka 2014-2019")
               (= (apurit/raporttisolun-arvo tpp) 0)
               (= (apurit/raporttisolun-arvo tur) 3)
               (= (apurit/raporttisolun-arvo urk) 0)))
        (fn [{[alue tpp tur urk & _] :rivi}]
          (and (= alue "Pohjois-Pohjanmaa yhteensä")
               (= (apurit/raporttisolun-arvo tpp) 0)
               (= (apurit/raporttisolun-arvo tur) 5)
               (= (apurit/raporttisolun-arvo urk) 0)))

        (fn [[alue tpp tur urk & _]]
          (and (= alue "KOKO MAA")
               (= (apurit/raporttisolun-arvo tpp) 0)
               (= (apurit/raporttisolun-arvo tur) 7)
               (= (apurit/raporttisolun-arvo urk) 0))))

      (apurit/tarkista-taulukko-rivit
        toimenpiteet-taulukko
        (fn [[aiheutti-toimenpiteita ei-aiheuttanut yhteensa]]
          (and
            (= aiheutti-toimenpiteita 0)
            (= ei-aiheuttanut 7)
            (= yhteensa 7)))))))


(deftest ilmoitusraportti-koko-maa-pylvaat
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :ilmoitusraportti
                                 :konteksti "koko maa"
                                 :hallintayksikko-id nil
                                 :parametrit {:alkupvm (pvm/->pvm "1.10.2016")
                                              :loppupvm (pvm/->pvm "30.09.2017")
                                              :urakkatyyppi :kaikki
                                              :urakoittain? true}})
        pylvasgraafin-viimeinen-elementti (last (last (last vastaus)))]
    (is (vector? vastaus))
    (is (= pylvasgraafin-viimeinen-elementti ["2017/09" []]))
    (let [otsikko "Ilmoitukset kuukausittain 01.10.2016-30.09.2017"
          pylvaat (apurit/pylvaat-otsikolla vastaus otsikko)]
      (apurit/tarkista-pylvaat-otsikko pylvaat "Ilmoitukset kuukausittain 01.10.2016-30.09.2017")
      (apurit/tarkista-pylvaat-legend pylvaat ["TPP" "TUR" "URK"])
      (apurit/tarkista-pylvaat-data
        pylvaat
        [["2016/10" []] ["2016/11" []] ["2016/12" []] ["2017/01" [nil 5 nil]] ["2017/02" []]
         ["2017/03" []] ["2017/04" []] ["2017/05" []] ["2017/06" [nil 1 nil]] ["2017/07" []] ["2017/08" [nil 1 nil]] ["2017/09" []]]))))