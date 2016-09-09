(ns harja.palvelin.raportointi.ilmoitusraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
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
                                      {:otsikko "Pohjois-Pohjanmaa ja Kainuu"}
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
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (let [otsikko "Pohjois-Pohjanmaa ja Kainuu, Ilmoitusraportti ajalta 01.10.2014 - 01.10.2015"
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
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (let [otsikko "KOKO MAA, Ilmoitusraportti ajalta 01.10.2014 - 01.10.2015"
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