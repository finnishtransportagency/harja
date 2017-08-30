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
             [:varoitusteksti "Seuraavissa urakoissa indeksilaskentaa ei voitu täysin suorittaa, koska tarpeellisia indeksiarvoja puuttuu: Oulun alueurakka 2014-2019"]))

      (is (= yks-hint "7 882,50"))
      (is (= sanktiot "-1 900,67"))
      (is (= indeksitarkistukset-yks-hint "0,00"))
      (is (= indeksitarkistukset-sanktiot "0,00"))
      (is (= indeksitarkistukset-muut-kuin-kokhint "0,00"))
      (is (= indeksitarkistukset-kaikki "0,00"))
      (is (= kaikki-paitsi-kokhint-yhteensa "5 981,83"))
      (is (= kaikki-yhteensa nurkkasumma "5 981,83")))))

(deftest raportin-suoritus-pop-elylle-toimii-hoitokausi-2014-2015-kun-092015-indeksiarvo-puuttuu
  (let [_ (u (str "DELETE FROM indeksi WHERE nimi = 'MAKU 2005' AND kuukausi = 9 AND vuosi = 2015"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
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
          varoitus-indeksiarvojen-puuttumisesta (nth vastaus 3)

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
      (is (= varoitus-indeksiarvojen-puuttumisesta
             [:varoitusteksti "Seuraavissa urakoissa indeksilaskentaa ei voitu täysin suorittaa, koska tarpeellisia indeksiarvoja puuttuu: Oulun alueurakka 2014-2019, Kajaanin alueurakka 2014-2019"]) )
      (is (=marginaalissa? kok-hint 324020.0M))
      (is (=marginaalissa? yks-hint  12000.0M))
      (is (=marginaalissa? sanktiot  -8000.0M))
      (is (=marginaalissa? talvisuolasakot -59520.0M))
      (is (=marginaalissa? muutos-ja-lisatyot  24000.0M))
      (is (=marginaalissa? akilliset  6000.0M))
      (is (=marginaalissa? vahinkojen-korjaukset  2000.0M))
      (is (=marginaalissa? bonukset  2000.0M))
      (is (=marginaalissa? erilliskustannukset  6000.0M))

      (is (=marginaalissa? indeksitarkistukset-koh-hint 0.00M))
      (is (=marginaalissa? indeksitarkistukset-yks-hint 113.03M))
      (is (=marginaalissa? indeksitarkistukset-sanktiot -99.81M))
      (is (=marginaalissa? indeksitarkistukset-talvisuolasakot -209.04M))
      (is (=marginaalissa? indeksitarkistukset-muutos-ja-lisatyot 126.44M))
      (is (=marginaalissa? indeksitarkistukset-akilliset 63.22M))
      (is (=marginaalissa? indeksitarkistukset-vahinkojen-korjaukset 34.48M))
      (is (=marginaalissa? indeksitarkistukset-bonukset 8.14M))
      (is (=marginaalissa? indeksitarkistukset-erilliskustannukset 49.81M))
      (is (=marginaalissa? indeksitarkistukset-muut-kuin-kokhint 86.26M))
      (is (=marginaalissa? indeksitarkistukset-kaikki 247.13M))
      (is (=marginaalissa? kaikki-paitsi-kokhint-yhteensa -15433.74M))

      (is (=marginaalissa? kaikki-yhteensa nurkkasumma 308586.26M)))))

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
          vastaus (butlast (butlast vastaus))

          yks-hint (arvo-raportin-nnesta-elementista vastaus 0)
          sanktiot (arvo-raportin-nnesta-elementista vastaus 1)

          kaikki-paitsi-kokhint-yhteensa (arvo-raportin-nnesta-elementista vastaus 2)
          kaikki-yhteensa (arvo-raportin-nnesta-elementista vastaus 3)
          nurkkasumma (:arvo (second (second (last (last (last (last vastaus)))))))]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")
      (is (= yks-hint "7 882,50"))
      (is (= sanktiot "-1 900,67"))
      (is (= kaikki-paitsi-kokhint-yhteensa "5 981,83"))
      (is (= kaikki-yhteensa nurkkasumma "5 981,83")))))
