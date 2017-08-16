(ns harja.palvelin.raportointi.laskutusyhteenveto_raportti_test
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

(defn- arvo-raportin-nnesta-elementista [vastaus n]
  (second (first (second (second (last (nth (nth (last vastaus) n) 3)))))))

(deftest raportin-suoritus-urakalle-toimii-hoitokausi-2014-2015
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :laskutusyhteenveto
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:urakkatyyppi :hoito
                                              :alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 9 30))}})]
    (is (vector? vastaus))
    (let [odotettu-otsikko "Oulun alueurakka 2014-2019, 01.10.2014-30.09.2015"
          saatu-otsikko (second (nth vastaus 2))

          kok-hint (arvo-raportin-nnesta-elementista vastaus 0)
          yks-hint (arvo-raportin-nnesta-elementista vastaus 1)
          sanktiot (arvo-raportin-nnesta-elementista vastaus 2)
          talvisuolasakot (arvo-raportin-nnesta-elementista vastaus 3)
          muutos-ja-lisatyot (arvo-raportin-nnesta-elementista vastaus 4)
          akilliset (arvo-raportin-nnesta-elementista vastaus 5)
          vahinkojen-korjaukset (arvo-raportin-nnesta-elementista vastaus 6)
          bonukset (arvo-raportin-nnesta-elementista vastaus 7)
          erilliskustannukset (arvo-raportin-nnesta-elementista vastaus 8)

          indeksitarkistukset-koh-hint (arvo-raportin-nnesta-elementista vastaus 9)
          indeksitarkistukset-yks-hint (arvo-raportin-nnesta-elementista vastaus 10)
          indeksitarkistukset-sanktiot (arvo-raportin-nnesta-elementista vastaus 11)
          indeksitarkistukset-talvisuolasakot (arvo-raportin-nnesta-elementista vastaus 12)
          indeksitarkistukset-muutos-ja-lisatyot (arvo-raportin-nnesta-elementista vastaus 13)
          indeksitarkistukset-akilliset (arvo-raportin-nnesta-elementista vastaus 14)
          indeksitarkistukset-vahinkojen-korjaukset (arvo-raportin-nnesta-elementista vastaus 15)
          indeksitarkistukset-bonukset (arvo-raportin-nnesta-elementista vastaus 16)
          indeksitarkistukset-erilliskustannukset (arvo-raportin-nnesta-elementista vastaus 17)
          indeksitarkistukset-muut-kuin-kokhint (arvo-raportin-nnesta-elementista vastaus 18)
          indeksitarkistukset-kaikki (arvo-raportin-nnesta-elementista vastaus 19)
          kaikki-paitsi-kokhint-yhteensa (arvo-raportin-nnesta-elementista vastaus 20)
          kaikki-yhteensa (arvo-raportin-nnesta-elementista vastaus 21)

          nurkkasumma (:arvo (second (second (last (last (last (last vastaus)))))))]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")
      (is (= kok-hint "162 010,00"))
      (is (= yks-hint "6 000,00"))
      (is (= sanktiot "-4 000,00"))
      (is (= talvisuolasakot"-29 760,00"))
      (is (= muutos-ja-lisatyot "12 000,00"))
      (is (= akilliset "3 000,00"))
      (is (= vahinkojen-korjaukset "1 000,00"))
      (is (= bonukset "1 000,00"))
      (is (= erilliskustannukset "3 000,00"))

      (is (= indeksitarkistukset-koh-hint "957,07"))
      (is (= indeksitarkistukset-yks-hint "56,51"))
      (is (= indeksitarkistukset-sanktiot "-49,90"))
      (is (= indeksitarkistukset-talvisuolasakot "-104,52"))
      (is (= indeksitarkistukset-muutos-ja-lisatyot "63,22"))
      (is (= indeksitarkistukset-akilliset "31,61"))
      (is (= indeksitarkistukset-vahinkojen-korjaukset "17,24"))
      (is (= indeksitarkistukset-bonukset "4,07"))
      (is (= indeksitarkistukset-erilliskustannukset "24,90"))
      (is (= indeksitarkistukset-muut-kuin-kokhint "43,13"))
      (is (= indeksitarkistukset-kaikki "1 000,20"))
      (is (= kaikki-paitsi-kokhint-yhteensa "-6 759,80"))


      (is (= kaikki-yhteensa nurkkasumma "155 250,20")))))

(deftest raportin-suoritus-urakalle-toimii-hoitokausi-2016-2017
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :laskutusyhteenveto
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:urakkatyyppi :hoito
                                              :alkupvm (c/to-date (t/local-date 2016 10 1))
                                              :loppupvm (c/to-date (t/local-date 2017 9 30))}})]
    (is (vector? vastaus))
    (let [odotettu-otsikko "Oulun alueurakka 2014-2019, 01.10.2016-30.09.2017"
          saatu-otsikko (second (nth vastaus 2))
          varoitus-indeksiarvojen-puuttumisesta (nth vastaus 5)
          yks-hint (arvo-raportin-nnesta-elementista vastaus 0)
          sanktiot (arvo-raportin-nnesta-elementista vastaus 1)

          indeksitarkistukset-yks-hint (arvo-raportin-nnesta-elementista vastaus 2)
          indeksitarkistukset-sanktiot (arvo-raportin-nnesta-elementista vastaus 3)
          indeksitarkistukset-muut-kuin-kokhint (arvo-raportin-nnesta-elementista vastaus 4)
          indeksitarkistukset-kaikki (arvo-raportin-nnesta-elementista vastaus 5)
          kaikki-paitsi-kokhint-yhteensa (arvo-raportin-nnesta-elementista vastaus 6)
          kaikki-yhteensa (arvo-raportin-nnesta-elementista vastaus 7)

          nurkkasumma (:arvo (second (second (last (last (last (last vastaus)))))))]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")
      (is (= varoitus-indeksiarvojen-puuttumisesta
             [:varoitusteksti "Laskutusyhteenvedon laskennassa tarvittavia indeksiarvoja puuttuu. "]))

      (is (= yks-hint "7 882,50"))
      (is (= sanktiot "-1 900,67"))
      (is (= indeksitarkistukset-yks-hint "0,00"))
      (is (= indeksitarkistukset-sanktiot "0,00"))
      (is (= indeksitarkistukset-muut-kuin-kokhint "0,00"))
      (is (= indeksitarkistukset-kaikki "0,00"))
      (is (= kaikki-paitsi-kokhint-yhteensa "5 981,83"))
      (is (= kaikki-yhteensa nurkkasumma "5 981,83")))))

(deftest raportin-suoritus-pop-elylle-toimii-hoitokausi-2014-2015
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :laskutusyhteenveto
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:urakkatyyppi :hoito
                                              :alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 9 30))}})]
    (is (vector? vastaus))
    (let [odotettu-otsikko "Pohjois-Pohjanmaa, 01.10.2014-30.09.2015"
          saatu-otsikko (second (nth vastaus 2))

          vastaus (butlast (butlast vastaus))
          kok-hint (arvo-raportin-nnesta-elementista vastaus 0)
          yks-hint (arvo-raportin-nnesta-elementista vastaus 1)
          sanktiot (arvo-raportin-nnesta-elementista vastaus 2)
          talvisuolasakot (arvo-raportin-nnesta-elementista vastaus 3)
          muutos-ja-lisatyot (arvo-raportin-nnesta-elementista vastaus 4)
          akilliset (arvo-raportin-nnesta-elementista vastaus 5)
          vahinkojen-korjaukset (arvo-raportin-nnesta-elementista vastaus 6)
          bonukset (arvo-raportin-nnesta-elementista vastaus 7)
          erilliskustannukset (arvo-raportin-nnesta-elementista vastaus 8)

          indeksitarkistukset-koh-hint (arvo-raportin-nnesta-elementista vastaus 9)
          indeksitarkistukset-yks-hint (arvo-raportin-nnesta-elementista vastaus 10)
          indeksitarkistukset-sanktiot (arvo-raportin-nnesta-elementista vastaus 11)
          indeksitarkistukset-talvisuolasakot (arvo-raportin-nnesta-elementista vastaus 12)
          indeksitarkistukset-muutos-ja-lisatyot (arvo-raportin-nnesta-elementista vastaus 13)
          indeksitarkistukset-akilliset (arvo-raportin-nnesta-elementista vastaus 14)
          indeksitarkistukset-vahinkojen-korjaukset (arvo-raportin-nnesta-elementista vastaus 15)
          indeksitarkistukset-bonukset (arvo-raportin-nnesta-elementista vastaus 16)
          indeksitarkistukset-erilliskustannukset (arvo-raportin-nnesta-elementista vastaus 17)
          indeksitarkistukset-muut-kuin-kokhint (arvo-raportin-nnesta-elementista vastaus 18)
          indeksitarkistukset-kaikki (arvo-raportin-nnesta-elementista vastaus 19)
          kaikki-paitsi-kokhint-yhteensa (arvo-raportin-nnesta-elementista vastaus 20)
          kaikki-yhteensa (arvo-raportin-nnesta-elementista vastaus 21)

          nurkkasumma (:arvo (second (second (last (last (last (last vastaus)))))))]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")
      (is (= kok-hint "324 020,00"))
      (is (= yks-hint "12 000,00"))
      (is (= sanktiot "-8 000,00"))
      (is (= talvisuolasakot"-59 520,00"))
      (is (= muutos-ja-lisatyot "24 000,00"))
      (is (= akilliset "6 000,00"))
      (is (= vahinkojen-korjaukset "2 000,00"))
      (is (= bonukset "2 000,00"))
      (is (= erilliskustannukset "6 000,00"))

      (is (= indeksitarkistukset-koh-hint "1 914,14"))
      (is (= indeksitarkistukset-yks-hint "113,03"))
      (is (= indeksitarkistukset-sanktiot "-99,81"))
      (is (= indeksitarkistukset-talvisuolasakot "-209,04"))
      (is (= indeksitarkistukset-muutos-ja-lisatyot "126,44"))
      (is (= indeksitarkistukset-akilliset "63,22"))
      (is (= indeksitarkistukset-vahinkojen-korjaukset "34,48"))
      (is (= indeksitarkistukset-bonukset "8,14"))
      (is (= indeksitarkistukset-erilliskustannukset "49,81"))
      (is (= indeksitarkistukset-muut-kuin-kokhint "86,26"))
      (is (= indeksitarkistukset-kaikki "2 000,40"))
      (is (= kaikki-paitsi-kokhint-yhteensa "-13 519,60"))

      (is (= kaikki-yhteensa nurkkasumma "310 500,40")))))

(deftest raportin-suoritus-pop-elylle-toimii-hoitokausi-2016-2017
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :laskutusyhteenveto
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:urakkatyyppi :hoito
                                              :alkupvm (c/to-date (t/local-date 2016 10 1))
                                              :loppupvm (c/to-date (t/local-date 2017 9 30))}})]
    (is (vector? vastaus))
    (let [odotettu-otsikko "Pohjois-Pohjanmaa, 01.10.2016-30.09.2017"

          saatu-otsikko (second (nth vastaus 2))

          yks-hint (arvo-raportin-nnesta-elementista vastaus 0)
          sanktiot (arvo-raportin-nnesta-elementista vastaus 1)

          indeksitarkistukset-yks-hint (arvo-raportin-nnesta-elementista vastaus 2)
          indeksitarkistukset-sanktiot (arvo-raportin-nnesta-elementista vastaus 3)
          kaikki-paitsi-kokhint-yhteensa (arvo-raportin-nnesta-elementista vastaus 4)
          kaikki-yhteensa (arvo-raportin-nnesta-elementista vastaus 5)

          nurkkasumma (:arvo (second (second (last (last (last (last vastaus)))))))]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")
      (is (= yks-hint "7 882,50"))
      (is (= sanktiot "-1 900,67"))
      (is (= indeksitarkistukset-yks-hint "0,00"))
      (is (= indeksitarkistukset-sanktiot "0,00"))
      (is (= indeksitarkistukset-muut-kuin-kokhint "0,00"))
      (is (= kaikki-paitsi-kokhint-yhteensa "5 981,83"))
      (is (= kaikki-yhteensa nurkkasumma "5 981,83")))))