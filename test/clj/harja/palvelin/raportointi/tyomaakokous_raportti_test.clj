(ns harja.palvelin.raportointi.tyomaakokous-raportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
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

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(deftest tyomaakokousraportin-suoritus-mhu-urakalle-toimii-hoitokausi-2022-2023
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        parametrit  {:laskutusyhteenveto true, :sanktioraportti true, :tiestotarkastusraportti false, :loppupvm #inst "2022-01-31T21:59:59.000-00:00", :laatupoikkeamaraportti false, :ilmoitusraportti false, :alkupvm #inst "2021-12-31T22:00:00.000-00:00", :muutos-ja-lisatyot true, :urakkatyyppi :teiden-hoito}

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :tyomaakokous
                                 :konteksti "urakka"
                                 :urakka-id urakka-id
                                 :parametrit parametrit})
        raportin-nimi (-> vastaus second :nimi)
        raportit (nth vastaus 2)
        laskutusyhteenveto (take 15 raportit)
        perusluku (nth laskutusyhteenveto 2)
        indeksikerroin (nth laskutusyhteenveto 3)
        muutos-ja-lisatoiden-raportin-otsikko (-> (nth raportit 16) second :otsikko)
        sanktioraportin-otsikko (second (nth raportit 19))]
    
    (is (= raportin-nimi "Oulun MHU 2019-2024, Työmaakokousraportti tammikuussa 2022"))
    (is (= perusluku [:teksti "Indeksilaskennan perusluku: 110,8"]) "Peruslukuteksti")
    (is (= indeksikerroin [:teksti "Hoitokauden 2021-22 indeksikerroin: 1,261"]) "Laskutusyhteenvedon indeksikerroin")
    (is (= (-> laskutusyhteenveto first second) "Laskutusyhteenveto"))
    (is (= "Oulun MHU 2019-2024, Muutos- ja lisätöiden raportti, kaikki työtyypit tammikuussa 2022, Toimenpide: kaikki" muutos-ja-lisatoiden-raportin-otsikko))
    (is (= "Sanktiot, bonukset ja arvonvähennykset 01.01.2022 - 31.01.2022" sanktioraportin-otsikko))))


(deftest tyomaakokousraportin-suoritus-vanhalle-hoitourakalle-toimii
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        parametrit  {:laskutusyhteenveto true, :sanktioraportti true, :tiestotarkastusraportti false, :loppupvm #inst "2014-10-31T21:59:59.000-00:00", :laatupoikkeamaraportti false, :ilmoitusraportti false, :alkupvm #inst "2014-09-30T21:00:00.000-00:00", :muutos-ja-lisatyot true, :urakkatyyppi :hoito}

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :tyomaakokous
                                 :konteksti "urakka"
                                 :urakka-id urakka-id
                                 :parametrit parametrit})
        raportin-nimi (-> vastaus second :nimi)
        raportit (nth vastaus 2)
        laskutusyhteenveto (take 14 raportit)
        laskutusyhteenveto-taulukot (last (nth laskutusyhteenveto 6))
        muutos-ja-lisatoiden-raportin-otsikko (-> (nth raportit 12) second :otsikko)
        sanktioraportin-otsikko (-> (nth raportit 15) second :otsikko)]
    (is (= raportin-nimi "Oulun alueurakka 2014-2019, Työmaakokousraportti lokakuussa 2014"))
    (is (= (-> laskutusyhteenveto first second) "Laskutusyhteenveto"))
    (is (= [["Talvihoito (#68)" [:varillinen-teksti {:arvo 0.0M, :fmt :raha, :tyyli nil}] [:varillinen-teksti {:arvo 3500.0M, :fmt :raha, :tyyli nil}] [:varillinen-teksti {:arvo 3500.0M, :fmt :raha, :tyyli nil}]] ["Soratien hoito (#84)" [:varillinen-teksti {:arvo 0.0M, :fmt :raha, :tyyli nil}] [:varillinen-teksti {:arvo 10000.0M, :fmt :raha, :tyyli nil}] [:varillinen-teksti {:arvo 10000.0M, :fmt :raha, :tyyli nil}]] ["Toimenpiteet yhteensä" [:varillinen-teksti {:arvo 0.0M, :fmt :raha, :tyyli nil}] [:varillinen-teksti {:arvo 13500.0M, :fmt :raha, :tyyli nil}] [:varillinen-teksti {:arvo 13500.0M, :fmt :raha, :tyyli nil}]]]
          laskutusyhteenveto-taulukot))
    (is (= "Oulun alueurakka 2014-2019, Muutos- ja lisätöiden raportti, kaikki työtyypit lokakuussa 2014, Toimenpide: kaikki"
           muutos-ja-lisatoiden-raportin-otsikko))
    (is (= "Oulun alueurakka 2014-2019, Sanktioiden yhteenveto lokakuussa 2014") sanktioraportin-otsikko)))
