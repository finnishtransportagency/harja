(ns harja.palvelin.raportointi.suolasakkoraportti-test
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
            [harja.palvelin.raportointi.testiapurit :as apurit]
            [harja.testi :as testi]))

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

(defn tarkista-sarakkeet [taulukko]
  (apurit/tarkista-taulukko-sarakkeet
   taulukko
   {:otsikko "Urakka"}
   {:fmt     :numero
    :otsikko "Keski­lämpö­tila"}
   {:fmt     :numero
    :otsikko "Pitkän aikavälin keski­lämpö­tila"}
   {:fmt     :numero
    :otsikko "Talvi­suolan max-määrä (t)"}
   {:fmt     :numero
    :otsikko "Bonus­raja (t)"}
   {:fmt     :numero
    :otsikko "Sakko­raja (t)"}
   {:fmt     :numero
    :otsikko "Kerroin"}
   {:fmt     :numero
    :otsikko "Kohtuul­lis­tarkis­tettu sakko­raja (t)"}
   {:fmt     :numero
    :otsikko "Käytetty suola­määrä (t)"}
   {:fmt     :numero
    :otsikko "Suola­erotus (t)"}
   {:fmt     :raha
    :otsikko "Sakko/\u00ADbonus € / t"}
   {:fmt     :raha
    :otsikko "Sakko € / t"}
   {:fmt     :raha
    :otsikko "Sakko/\u00ADbonus €"}
   {:fmt     :raha
    :otsikko "Indeksi €"}
   {:fmt     :raha
    :otsikko "Indeksi­korotettu sakko €"}))

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :suolasakko
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 9 30))}})]
    (is (vector? vastaus))

    (let [elementit (apurit/tarkista-raportti vastaus "Suolasakkoraportti")
          taulukko (apurit/taulukko-otsikolla
                    vastaus
                    "Oulun alueurakka 2014-2019, Suolasakkoraportti ajalta 01.10.2014 - 30.09.2015")]

      (tarkista-sarakkeet taulukko)

      (apurit/tarkista-taulukko-rivit
       taulukko
       (fn [[urakka-nimi lampotila keskilampo sallittu bonusraja sakkoraja kerroin
             kohtuullistarkistettu-sakkoraja kaytetty-talvisuolaa erotus
             sakkobonus-per-t vainsakko-per-t sakkobonus indeksi
             indeksikorotettuna & _ ]]
         (and (= urakka-nimi "Oulun alueurakka 2014-2019")
              (testi/=marginaalissa? lampotila -6.2)
              (testi/=marginaalissa? keskilampo -9.3)
              (testi/=marginaalissa? sallittu 800)
              (testi/=marginaalissa? bonusraja 760)
              (testi/=marginaalissa? sakkoraja 840)
              (testi/=marginaalissa? (:arvo (second kerroin)) 1.2)
              (testi/=marginaalissa? kohtuullistarkistettu-sakkoraja 1008)
              (testi/=marginaalissa? kaytetty-talvisuolaa 2000)
              (testi/=marginaalissa? erotus 992)
              (testi/=marginaalissa? sakkobonus-per-t 30)
              (nil? vainsakko-per-t)
              (testi/=marginaalissa? sakkobonus -29760)
              (testi/=marginaalissa? indeksi -104.52)
              (testi/=marginaalissa? indeksikorotettuna -29864.52)))
       (fn [[yht & _]]
         (= "Yhteensä" yht)))

      (apurit/tarkista-taulukko-yhteensa taulukko 3)
      (apurit/tarkista-taulukko-yhteensa taulukko 12))))

(deftest raportin-suoritus-vantaa-suolabonus
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :suolasakko
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-vantaan-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 9 30))}})]
    (is (vector? vastaus))

    (let [elementit (apurit/tarkista-raportti vastaus "Suolasakkoraportti")
          taulukko (apurit/taulukko-otsikolla
                     vastaus
                     "Vantaan alueurakka 2009-2019, Suolasakkoraportti ajalta 01.10.2015 - 30.09.2016")]
      (tarkista-sarakkeet taulukko)

      (apurit/tarkista-taulukko-rivit
        taulukko
        (fn [[urakka-nimi lampotila keskilampo sallittu bonusraja sakkoraja kerroin
              kohtuullistarkistettu-sakkoraja kaytetty-talvisuolaa erotus
              sakkobonus-per-t vainsakko-per-t sakkobonus indeksi
              indeksikorotettuna & _ ]]
          (and (= urakka-nimi "Vantaan alueurakka 2009-2019")
               (testi/=marginaalissa? lampotila -3.5)
               (testi/=marginaalissa? keskilampo -5.6)
               (testi/=marginaalissa? sallittu 1100)
               (testi/=marginaalissa? bonusraja 1045)
               (testi/=marginaalissa? sakkoraja 1155)
               (testi/=marginaalissa? (apurit/raporttisolun-arvo kerroin) 1.1)
               (testi/=marginaalissa? kohtuullistarkistettu-sakkoraja 1270.50)
               (testi/=marginaalissa? kaytetty-talvisuolaa 842.6)
               (testi/=marginaalissa? erotus 202.4)
               (testi/=marginaalissa? sakkobonus-per-t 30)
               (nil? vainsakko-per-t)
               (testi/=marginaalissa? sakkobonus 6072)
               (testi/=marginaalissa? indeksi 850.555)
               (testi/=marginaalissa? indeksikorotettuna 6922.5558)))
        (fn [[yht & _]]
          (= "Yhteensä" yht)))

      (apurit/tarkista-taulukko-yhteensa taulukko 3)
      (apurit/tarkista-taulukko-yhteensa taulukko 12)
      (apurit/tarkista-taulukko-yhteensa taulukko 14))))

(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :suolasakko
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm     (c/to-date (t/local-date 2015 9 30))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [elementit (apurit/tarkista-raportti vastaus "Suolasakkoraportti")
          taulukko (apurit/taulukko-otsikolla
                    vastaus
                    "Pohjois-Pohjanmaa, Suolasakkoraportti ajalta 01.10.2014 - 30.09.2015")]

      (tarkista-sarakkeet taulukko)
      (apurit/tarkista-taulukko-rivit
       taulukko
       (fn [[kajaani & _ ]]
         (= kajaani "Kajaanin alueurakka 2014-2019"))
       (fn [[urakka-nimi lampotila keskilampo sallittu & _ ]]
         (and (= urakka-nimi "Oulun alueurakka 2014-2019")
              (testi/=marginaalissa? lampotila -6.2)
              (testi/=marginaalissa? keskilampo -9.3)
              (testi/=marginaalissa? sallittu 800)))
       (fn [[yht & _]]
         (= "Yhteensä" yht)))

      (apurit/tarkista-taulukko-yhteensa taulukko 3)
      (apurit/tarkista-taulukko-yhteensa taulukko 12))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :suolasakko
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm     (c/to-date (t/local-date 2015 9 30))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [elementit (apurit/tarkista-raportti vastaus "Suolasakkoraportti")
          taulukko (apurit/taulukko-otsikolla
                    vastaus
                    "KOKO MAA, Suolasakkoraportti ajalta 01.10.2014 - 30.09.2015")]


      (tarkista-sarakkeet taulukko)

      (apurit/tarkista-taulukko-kaikki-rivit
       taulukko
       (fn [rivi]
         (or (and (map? rivi)
                  (:lihavoi? rivi))
             (and (vector? rivi)
                  (string? (first rivi))))))

      (apurit/tarkista-taulukko-yhteensa taulukko 3)
      (apurit/tarkista-taulukko-yhteensa taulukko 12))))
