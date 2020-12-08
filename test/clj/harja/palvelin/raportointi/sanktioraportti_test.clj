(ns harja.palvelin.raportointi.sanktioraportti-test
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

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :sanktioraportti
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2011 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))}})
        nurkkasumma (last (last (last (last vastaus))))]
    (is (vector? vastaus))
    (is (=marginaalissa? nurkkasumma 26488.52))
    (let [otsikko "Oulun alueurakka 2014-2019, Sanktioiden yhteenveto ajalta 01.10.2011 - 01.10.2016"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko ""}
                                          {:otsikko "Oulun alueurakka 2014-2019"}))))


(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :sanktioraportti
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2011 10 1))
                                                      :loppupvm     (c/to-date (t/local-date 2016 10 1))
                                                      :urakkatyyppi :hoito}})
        nurkkasumma (last (last (last (last vastaus))))]
    (is (vector? vastaus))
    (is (=marginaalissa? nurkkasumma 71977.05))
    (let [otsikko "Pohjois-Pohjanmaa, Sanktioiden yhteenveto ajalta 01.10.2011 - 01.10.2016"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko ""}
                                          {:otsikko "Kajaanin alueurakka 2014-2019"}
                                          {:otsikko "Oulun alueurakka 2005-2012"}
                                          {:otsikko "Oulun alueurakka 2014-2019"}
                                          {:otsikko "Pudasjärven alueurakka 2007-2012"}
                                          {:otsikko "Yh\u00ADteen\u00ADsä"}))))

(deftest raportin-suoritus-hallintayksikolle-toimii-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :sanktioraportti
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2015 1 1))
                                                      :loppupvm     (c/to-date (t/local-date 2015 12 31))
                                                      :urakkatyyppi :hoito}})
        nurkkasumma (last (last (last (last vastaus))))]
    (is (vector? vastaus))
    (is (=marginaalissa? nurkkasumma 8099.80))
    (let [otsikko "Pohjois-Pohjanmaa, Sanktioiden yhteenveto ajalta 01.01.2015 - 31.12.2015"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko ""}
                                          {:otsikko "Kajaanin alueurakka 2014-2019"}
                                          {:otsikko "Oulun alueurakka 2014-2019"}
                                          {:otsikko "Yh\u00ADteen\u00ADsä"}))))


(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :sanktioraportti
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2015 1 1))
                                              :loppupvm     (c/to-date (t/local-date 2015 12 31))
                                              :urakkatyyppi :hoito}})
        nurkkasumma (last (last (last (last vastaus))))]
    (is (vector? vastaus))
    (is (=marginaalissa? nurkkasumma 15786.15))
    (let [otsikko "KOKO MAA, Sanktioiden yhteenveto ajalta 01.01.2015 - 31.12.2015"
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
                                          {:otsikko "Yh\u00ADteen\u00ADsä"}))))
