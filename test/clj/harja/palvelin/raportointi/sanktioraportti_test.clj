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

(deftest raportin-suoritus-urakan-jalkeen-tulleilla-sanktioilla-toimii
  (let [urakalla-sanktiot-tulee-mukaan-viimeisella-hoitokaudella
        (kutsu-palvelua (:http-palvelin jarjestelma)
          :suorita-raportti
          +kayttaja-jvh+
          {:nimi       :sanktioraportti
           :konteksti  "urakka"
           :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
           :parametrit {:alkupvm  (c/to-date (t/local-date 2018 10 1))
                        :loppupvm (c/to-date (t/local-date 2019 9 30))}})
        
        urakalla-sanktiot-ei-tule-mukaan-jollain-toisella-kaudella
        (kutsu-palvelua (:http-palvelin jarjestelma)
          :suorita-raportti
          +kayttaja-jvh+
          {:nimi       :sanktioraportti
           :konteksti  "urakka"
           :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
           :parametrit {:alkupvm  (c/to-date (t/local-date 2018 9 1))
                        :loppupvm (c/to-date (t/local-date 2019 8 1))}})
        
        elylla-sanktiot-tulee-mukaan-jos-jossain-urakassa-viimeinen-hoitokausi
        (kutsu-palvelua (:http-palvelin jarjestelma)
          :suorita-raportti
          +kayttaja-jvh+
          {:nimi               :sanktioraportti
           :konteksti          "hallintayksikko"
           :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
           :parametrit         {:alkupvm      (c/to-date (t/local-date 2018 10 1))
                                :loppupvm     (c/to-date (t/local-date 2019 9 30))
                                :urakkatyyppi :hoito}})
        
        elylla-sanktiot-ei-tule-mukaan-jos-edellista-casea-seuraava-hoitokausi
        (kutsu-palvelua (:http-palvelin jarjestelma)
          :suorita-raportti
          +kayttaja-jvh+
          {:nimi               :sanktioraportti
           :konteksti          "hallintayksikko"
           :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
           :parametrit         {:alkupvm      (c/to-date (t/local-date 2019 10 1))
                                :loppupvm     (c/to-date (t/local-date 2020 9 30))
                                :urakkatyyppi :hoito}})]
    (is (some? (some
                 #(when
                      (and
                        (vector? %)
                        (= (first %) "Ryhmä C, sakot yht."))
                    (= (second %) 777M))
                 (as-> urakalla-sanktiot-tulee-mukaan-viimeisella-hoitokaudella u
                   (nth u 2)
                   (nth u 3)))) "Urakan jälkeen tullut sanktio näkyy, kun viimeisen hoitokauden viimeinen kuukausi mukana")
    (is (nil? (some
                 #(when
                      (and
                        (vector? %)
                        (= (first %) "Ryhmä C, sakot yht."))
                    (not (= (second %) 0)))
                 (as-> urakalla-sanktiot-ei-tule-mukaan-jollain-toisella-kaudella u
                   (nth u 2)
                   (nth u 3)))) "Urakan jälkeen tullut sanktio ei näy, jos viimeinen kuukausi ei mukana")
    (is (some?
          (some
                #(when
                     (and
                       (vector? %)
                       (= (first %) "Ryhmä C, sakot yht."))
                   (= 777M (some
                             (fn [arvo] 
                               (when-not (zero? arvo)
                                 arvo))
                             (filter number? %))))
                (as-> elylla-sanktiot-tulee-mukaan-jos-jossain-urakassa-viimeinen-hoitokausi u
                  (nth u 2)
                  (nth u 3)))) "Elyllä haettassa jos haussa mukana jonkun urakan viimeinen kuukausi, sen jälkeen tulleet sanktiot lasketaan mukaan")
    (is (nil? (some
                 #(when
                      (and
                        (vector? %)
                        (= (first %) "Ryhmä C, sakot yht."))
                    (not (every? zero?
                           (filter number? %))))
                 (as-> elylla-sanktiot-ei-tule-mukaan-jos-edellista-casea-seuraava-hoitokausi u
                   (nth u 2)
                   (nth u 3)))) "Edellisen vastapari, ei viimeisiä kuukausia, ei sen jälkeen tulleita sanktioita vaikka jonkin perintäpvm tällä jaksolla")))
