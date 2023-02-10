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
        sanktiosumma (last (last (last (last (last (nth vastaus 4))))))]
    (is (vector? vastaus))
    (is (=marginaalissa? sanktiosumma 26488.52))
    (let [otsikko "Sanktiot"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (is (= "Oulun alueurakka 2014-2019" (:nimi (second vastaus))))
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
        sanktiosumma (last (last (last (last (last (nth vastaus 4))))))]
    (is (vector? vastaus))
    (is (=marginaalissa? sanktiosumma 71977.05))
    (let [otsikko "Sanktiot"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (is (= "Pohjois-Pohjanmaa" (:nimi (second vastaus))))
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
        sanktiosumma (last (last (last (last (last (nth vastaus 4))))))
        bonussumma (:arvo (second (last (:rivi (nth (nth (nth vastaus 5) 3) 2)))))]
    (is (vector? vastaus))
    (is (=marginaalissa? sanktiosumma 8099.80))
    (is (=marginaalissa? bonussumma 2000M))
    (let [otsikko "Sanktiot"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (is (= "Pohjois-Pohjanmaa" (:nimi (second vastaus))))
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
        sanktiotaulukko (nth vastaus 4)
        bonustaulukko (nth vastaus 5)
        arvonvahennystaulukko (nth vastaus 6)
        sanktiosumma (last (last (last (last (last sanktiotaulukko)))))
        bonussumma (last (:rivi (last (nth bonustaulukko 3))))
        arvonvahennyssumma (:arvo (second (last (:rivi (last (nth arvonvahennystaulukko 3))))))]
    (is (vector? vastaus))
    (is (=marginaalissa? sanktiosumma 15786.15))
    (is (=marginaalissa? bonussumma 4000M))
    (is (=marginaalissa? arvonvahennyssumma 1000M))
    (let [otsikko "Sanktiot"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (is (= "Koko maa" (:nimi (second vastaus))))
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

(defn suorita-sanktioraportti
  [konteksti [alkuvuosi alkukk alkupv] [loppuvuosi loppukk loppupv]]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :suorita-raportti
    +kayttaja-jvh+
    (merge 
      {:nimi       :sanktioraportti
       :konteksti  konteksti
       :parametrit {:alkupvm  (c/to-date (t/local-date alkuvuosi alkukk alkupv))
                    :loppupvm (c/to-date (t/local-date loppuvuosi loppukk loppupv))}}
      (cond
        (= konteksti "urakka")
        {:urakka-id  (hae-oulun-alueurakan-2014-2019-id)}

        (= konteksti "hallintayksikko")
        {:hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)}))))

(defn tarkista-rivi-summalle
  [summa]
  (fn [rivi]
    (if (and (vector? rivi)
          (= (first rivi) "Ryhmä C, sakot yht."))
      (= (second rivi) summa)
      true)))

(deftest raportin-suoritus-urakan-jalkeen-tulleilla-sanktioilla-toimii-urakalle
  (let [urakalla-sanktiot-tulee-mukaan-viimeisella-hoitokaudella
        (suorita-sanktioraportti "urakka" [2018 10 1] [2019 9 30])
        taulukko (apurit/taulukko-otsikolla
                   urakalla-sanktiot-tulee-mukaan-viimeisella-hoitokaudella
                   "Sanktiot")
        tarkistus-fn (tarkista-rivi-summalle 777M)]
    (is (= "Oulun alueurakka 2014-2019" (:nimi (second urakalla-sanktiot-tulee-mukaan-viimeisella-hoitokaudella))))
    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      tarkistus-fn)))

(deftest raportin-suoritus-urakan-jalkeen-tulleilla-sanktioilla-laskee-sanktiot-vain-jos-viimeinen-kuukausi-on-mukana
  (let [urakalla-sanktiot-ei-tule-mukaan-jollain-toisella-kaudella
        (suorita-sanktioraportti "urakka" [2018 9 1] [2019 8 1])
        taulukko (apurit/taulukko-otsikolla
                   urakalla-sanktiot-ei-tule-mukaan-jollain-toisella-kaudella
                   "Sanktiot")
        tarkistus-fn (tarkista-rivi-summalle 0)]
    (is (= "Oulun alueurakka 2014-2019" (:nimi (second urakalla-sanktiot-ei-tule-mukaan-jollain-toisella-kaudella))))
    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      tarkistus-fn)))

(defn tarkista-ely-rivit
  [tarkistus-fn]
  (fn [rivi]
    (if (and (vector? rivi)
          (= (first rivi) "Ryhmä C, sakot yht."))
      (tarkistus-fn rivi)
      true)))

(defn sanktio-loytyy-elyriveissa
  [rivi]
  (= 777M (some
            (fn [arvo] 
              (when-not (zero? arvo)
                arvo))
            (filter number? rivi))))

(defn ei-sanktiota-elyriveissa
  [rivi]
  (every? zero?
    (filter number? rivi)))

(deftest raportin-suoritus-urakan-jalkeen-tulleilla-sanktioilla-toimii-elylle
  (let [elylla-sanktiot-tulee-mukaan-jos-jossain-urakassa-viimeinen-hoitokausi
        (suorita-sanktioraportti "hallintayksikko" [2018 10 1] [2019 9 30])
        taulukko (apurit/taulukko-otsikolla
                   elylla-sanktiot-tulee-mukaan-jos-jossain-urakassa-viimeinen-hoitokausi
                   "Sanktiot")
        tarkista-fn (tarkista-ely-rivit sanktio-loytyy-elyriveissa)]
    (is (= "Pohjois-Pohjanmaa" (:nimi (second elylla-sanktiot-tulee-mukaan-jos-jossain-urakassa-viimeinen-hoitokausi))))
    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      tarkista-fn)))

(deftest raportin-suoritus-urakan-jalkeen-tulleilla-sanktioilla-toimii
  (let [elylla-sanktiot-ei-tule-mukaan-jos-edellista-casea-seuraava-hoitokausi
        (suorita-sanktioraportti "hallintayksikko" [2019 10 1] [2020 9 30])
        taulukko (apurit/taulukko-otsikolla
                   elylla-sanktiot-ei-tule-mukaan-jos-edellista-casea-seuraava-hoitokausi
                   "Sanktiot")
        tarkista-fn (tarkista-ely-rivit ei-sanktiota-elyriveissa)]
    (is (= "Pohjois-Pohjanmaa" (:nimi (second elylla-sanktiot-ei-tule-mukaan-jos-edellista-casea-seuraava-hoitokausi))))
    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      tarkista-fn)))
