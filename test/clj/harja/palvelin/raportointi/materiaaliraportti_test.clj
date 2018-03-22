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
                                {:nimi :materiaaliraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})
        taulukko (apurit/taulukko-otsikolla vastaus "Oulun alueurakka 2014-2019, Materiaaliraportti ajalta 01.10.2014 - 01.10.2015")
        rivit (last taulukko)
        formiaatti-vinkki (last vastaus)]
    (is (= (list ["Oulun alueurakka 2014-2019" 2000M 200M 1800M 2000M 0]
                 (list "Yhteensä" 2000M 200M 1800M 2000M 0))
           rivit))
    (is (= [:teksti "Formiaatteja ei lasketa talvisuolan kokonaiskäyttöön."] formiaatti-vinkki))
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Materiaaliraportti")

    (apurit/tarkista-taulukko-kaikki-rivit-ja-yhteenveto
      taulukko
      (fn [[urakka materiaali]]
        (and (string? urakka)
             (number? materiaali)))
      (fn [[yhteenveto yhteensa]]
        (and (string? yhteenveto)
             (number? yhteensa))))

    (apurit/tarkista-taulukko-yhteensa
      taulukko
      1)))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :materiaaliraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "Pohjois-Pohjanmaa, Materiaaliraportti ajalta 01.10.2014 - 01.10.2015")
        rivit (sort-by ffirst (last taulukko))
        formiaatti-vinkki (last vastaus)]
    (is (= (list ["Kajaanin alueurakka 2014-2019" 2000M 0 2000M 0]
                 ["Oulun alueurakka 2014-2019" 2000M 200M 1800M 2000M]
                 (list "Yhteensä" 4000M 200M 3800M 2000M))
           rivit))
    (is (= [:teksti "Formiaatteja ei lasketa talvisuolan kokonaiskäyttöön."] formiaatti-vinkki))
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Materiaaliraportti")
    (apurit/tarkista-taulukko-kaikki-rivit-ja-yhteenveto
      taulukko
      (fn [[urakka materiaali]]
        (and (string? urakka)
             (number? materiaali)))
      (fn [[yht summa]]
        (and (string? yht)
             (number? summa))))
    (apurit/tarkista-taulukko-yhteensa taulukko 1)))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :materiaaliraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "KOKO MAA, Materiaaliraportti ajalta 01.10.2014 - 01.10.2015")
        rivit (last taulukko)
        formiaatti-vinkki (last vastaus)]
    (is (= (list ["Uusimaa" 4000M 0 4000M 0]
             ["Pohjois-Pohjanmaa" 4000M 200M 3800M 2000M]
             (list "Yhteensä" 8000M 200M 7800M 2000M))
           rivit))
    (is (= [:teksti "Formiaatteja ei lasketa talvisuolan kokonaiskäyttöön."] formiaatti-vinkki))
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Materiaaliraportti")
    (apurit/tarkista-taulukko-kaikki-rivit-ja-yhteenveto
      taulukko
      (fn [[urakka materiaali]]
        (and (string? urakka)
             (number? materiaali)))
      (fn [[yhteenveto yhteensa]]
        (and (string? yhteenveto)
             (number? yhteensa))))
    (apurit/tarkista-taulukko-yhteensa
      taulukko
      1)))