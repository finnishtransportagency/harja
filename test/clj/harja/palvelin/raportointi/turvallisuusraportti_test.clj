(ns harja.palvelin.raportointi.turvallisuusraportti-test
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
            [clojure.core.match :refer [match]]
            [clojure.string :as str]
            [harja.palvelin.raportointi.testiapurit :as apurit]))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
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
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn raportti-testien-vastaus
  [{:keys [konteksti urakka-id hallintayksikko-id alkupvm loppupvm
           urakkatyyppi tarkistettavat-sarakkett]}]
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :turvallisuus
                                 :konteksti konteksti
                                 :urakka-id urakka-id
                                 :hallintayksikko-id hallintayksikko-id
                                 :parametrit {:alkupvm (c/to-date (apply t/local-date alkupvm))
                                              :loppupvm (c/to-date (apply t/local-date loppupvm))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi urakkatyyppi}})]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Turvallisuusraportti")
    vastaus))

(deftest raportin-suoritus-urakalle-toimii
  (let [konteksti "urakka"
        urakka-id (hae-oulun-alueurakan-2014-2019-id)
        alkupvm [2014 10 1]
        loppupvm [2015 10 1]
        urakkatyyppi :hoito

        vastaus (raportti-testien-vastaus {:konteksti konteksti :urakka-id urakka-id :alkupvm alkupvm
                                           :loppupvm loppupvm :urakkatyyppi urakkatyyppi})

        otsikko (str "Oulun alueurakka 2014-2019, "
                     "Turvallisuusraportti ajalta 01.10.2014 - 01.10.2015")
        taulukko (apurit/elementti vastaus [:taulukko {:otsikko otsikko} _ _])]
    (apurit/tarkista-taulukko-otsikko taulukko otsikko)
    (apurit/tarkista-taulukko-sarakkeet taulukko
                                        {:otsikko "Tyyppi"}
                                        {:otsikko "Määrä"})
    (apurit/tarkista-taulukko-yhteensa taulukko 1)))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [konteksti "hallintayksikko"
        hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
        alkupvm [2014 10 1]
        loppupvm [2015 10 1]
        urakkatyyppi :hoito

        vastaus (raportti-testien-vastaus {:konteksti konteksti :hallintayksikko-id hallintayksikko-id :alkupvm alkupvm
                                           :loppupvm loppupvm :urakkatyyppi urakkatyyppi})

        otsikko (str "Pohjois-Pohjanmaa, "
                     "Turvallisuusraportti ajalta 01.10.2014 - 01.10.2015")
        taulukko (apurit/elementti vastaus [:taulukko {:otsikko otsikko} _ _])]
    (apurit/tarkista-taulukko-otsikko taulukko otsikko)
    (apurit/tarkista-taulukko-sarakkeet taulukko
                                        {:otsikko "Tyyppi"}
                                        {:otsikko "Määrä"})
    (apurit/tarkista-taulukko-yhteensa taulukko 1)))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [konteksti "koko maa"
        alkupvm [2014 1 1]
        loppupvm [2015 12 31]
        urakkatyyppi :hoito

        vastaus (raportti-testien-vastaus {:konteksti konteksti :alkupvm alkupvm :loppupvm loppupvm
                                           :urakkatyyppi urakkatyyppi})

        raportin-otsikko "KOKO MAA, Turvallisuusraportti ajalta 01.01.2014 - 31.12.2015"
        taulukko (apurit/taulukko-otsikolla vastaus otsikko)
        hallintayksikot (into #{} (map first (apurit/taulukon-rivit taulukko)))]
    (= hallintayksikot #{"Uusimaa" "Varsinais-Suomi" "Kaakkois-Suomi"
                         "Pirkanmaa" "Pohjois-Savo" "Keski-Suomi"
                         "Etelä-Pohjanmaa" "Pohjois-Pohjanmaa"
                         "Lappi" "Koko maa"})
    (apurit/tarkista-taulukko-sarakkeet taulukko
                                        {:otsikko "Hallintayksikkö"}
                                        {:otsikko "Työtapaturmat"}
                                        {:otsikko "Vaaratilanteet"}
                                        {:otsikko "Turvallisuushavainnot"}
                                        {:otsikko "Muut"})
    (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                           (fn [[alue tyo vaara havainnot muut :as rivi]]
                                             (and (= (count rivi) 5)
                                                  (string? alue)
                                                  (number? tyo)
                                                  (number? vaara)
                                                  (number? havainnot)
                                                  (number? muut))))
    (let [vakavuus (apurit/taulukko-otsikolla vastaus "Turvallisuuspoikkeamat vakavuusasteittain")]
      (apurit/tarkista-taulukko-sarakkeet vakavuus
                                          {:otsikko "Hallintayksikkö"}
                                          {:otsikko "Lievät"}
                                          {:otsikko "Vakavat"})
      (apurit/tarkista-taulukko-kaikki-rivit vakavuus
                                             (fn [[hal lievat vakavat :as rivi]]
                                               (and (= (count rivi) 3)
                                                    (string? hal)
                                                    (number? lievat)
                                                    (number? vakavat)))))))

(deftest raportin-suoritus-vesivayla-urakalle-toimii
  (let [konteksti "urakka"
        urakka-id (hae-helsingin-vesivaylaurakan-id)
        alkupvm [2017 1 1]
        loppupvm [2017 12 31]
        urakkatyyppi :vesivayla

        vastaus (raportti-testien-vastaus {:konteksti konteksti :urakka-id urakka-id :alkupvm alkupvm
                                           :loppupvm loppupvm :urakkatyyppi urakkatyyppi})

        otsikko (str "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL, "
                     "Turvallisuusraportti ajalta 01.01.2017 - 31.12.2017")
        taulukko (apurit/elementti vastaus [:taulukko {:otsikko otsikko} _ _])]
    (apurit/tarkista-taulukko-otsikko taulukko otsikko)
    (apurit/tarkista-taulukko-sarakkeet taulukko
                                        {:otsikko "Tyyppi"}
                                        {:otsikko "Määrä"})
    (apurit/tarkista-taulukko-yhteensa taulukko 1)))

(deftest raportin-suoritus-vesivayla-hallintayksikolle-toimii
  (let [konteksti "hallintayksikko"
        hallintayksikko-id (hae-merivayla-hallintayksikon-id)
        alkupvm [2017 1 1]
        loppupvm [2017 12 31]
        urakkatyyppi :vesivayla

        vastaus (raportti-testien-vastaus {:konteksti konteksti :hallintayksikko-id hallintayksikko-id :alkupvm alkupvm
                                           :loppupvm loppupvm :urakkatyyppi urakkatyyppi})

        otsikko (str "Meriväylät, "
                     "Turvallisuusraportti ajalta 01.01.2017 - 31.12.2017")
        taulukko (apurit/elementti vastaus [:taulukko {:otsikko otsikko} _ _])]
    (apurit/tarkista-taulukko-otsikko taulukko otsikko)
    (apurit/tarkista-taulukko-sarakkeet taulukko
                                        {:otsikko "Tyyppi"}
                                        {:otsikko "Määrä"})
    (apurit/tarkista-taulukko-yhteensa taulukko 1)))

(deftest raportin-suoritus-vesivayla-koko-maalle-toimii
  (let [konteksti "koko maa"
        alkupvm [2017 1 1]
        loppupvm [2017 12 31]
        urakkatyyppi :vesivayla

        vastaus (raportti-testien-vastaus {:konteksti konteksti :alkupvm alkupvm :loppupvm loppupvm
                                           :urakkatyyppi urakkatyyppi})

        raportin-otsikko "KOKO MAA, Turvallisuusraportti ajalta 01.01.2017 - 31.12.2017"
        taulukko (apurit/taulukko-otsikolla vastaus otsikko)
        hallintayksikot (into #{} (map first (apurit/taulukon-rivit taulukko)))]
    (= hallintayksikot #{"Sisävesiväylät" "Meriväylät" "Kanavat ja avattavat sillat" "Koko maa"})
    (apurit/tarkista-taulukko-sarakkeet taulukko
                                        {:otsikko "Hallintayksikkö"}
                                        {:otsikko "Työtapaturmat"}
                                        {:otsikko "Vaaratilanteet"}
                                        {:otsikko "Turvallisuushavainnot"}
                                        {:otsikko "Muut"})
    (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                           (fn [[alue tyo vaara havainnot muut :as rivi]]
                                             (and (= (count rivi) 5)
                                                  (string? alue)
                                                  (number? tyo)
                                                  (number? vaara)
                                                  (number? havainnot)
                                                  (number? muut))))
    (let [vakavuus (apurit/taulukko-otsikolla vastaus "Turvallisuuspoikkeamat vakavuusasteittain")]
      (apurit/tarkista-taulukko-sarakkeet vakavuus
                                          {:otsikko "Hallintayksikkö"}
                                          {:otsikko "Lievät"}
                                          {:otsikko "Vakavat"})
      (apurit/tarkista-taulukko-kaikki-rivit vakavuus
                                             (fn [[hal lievat vakavat :as rivi]]
                                               (and (= (count rivi) 3)
                                                    (string? hal)
                                                    (number? lievat)
                                                    (number? vakavat)))))))
