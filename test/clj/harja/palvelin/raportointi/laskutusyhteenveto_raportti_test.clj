(ns harja.palvelin.raportointi.laskutusyhteenveto-raportti-test
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

          oulun-au-talvihoito-kok-hint-maksueranumero (first (first (nth (nth (last vastaus) 0) 3)))

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

          nurkkasumma (:arvo (second (second (last (last (last (last vastaus)))))))

          numero (ffirst (q "SELECT numero
                             FROM maksuera
                             WHERE nimi = 'Oulu Talvihoito TP ME 2014-2019' AND
                                   tyyppi = 'kokonaishintainen';"))]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")

      (is (= (str "Talvihoito (#" numero ")") oulun-au-talvihoito-kok-hint-maksueranumero))

      (is (=marginaalissa? kok-hint 162010.00M))
      (is (=marginaalissa? yks-hint 6000.00M))
      (is (=marginaalissa? sanktiot -4000.00M))
      (is (=marginaalissa? talvisuolasakot -29760.00M))
      (is (=marginaalissa? muutos-ja-lisatyot 12000.00M))
      (is (=marginaalissa? akilliset 3000.00M))
      (is (=marginaalissa? vahinkojen-korjaukset 1000.00M))
      (is (=marginaalissa? bonukset 1000.00M))
      (is (=marginaalissa? erilliskustannukset 3000.00M))

      (is (=marginaalissa? indeksitarkistukset-koh-hint 957.07M))
      (is (=marginaalissa? indeksitarkistukset-yks-hint 56.51M))
      (is (=marginaalissa? indeksitarkistukset-sanktiot -49.90M))
      (is (=marginaalissa? indeksitarkistukset-talvisuolasakot -104.52M))
      (is (=marginaalissa? indeksitarkistukset-muutos-ja-lisatyot 63.22M))
      (is (=marginaalissa? indeksitarkistukset-akilliset 31.61M))
      (is (=marginaalissa? indeksitarkistukset-vahinkojen-korjaukset 17.24M))
      (is (=marginaalissa? indeksitarkistukset-bonukset 5.906M))
      (is (=marginaalissa? indeksitarkistukset-erilliskustannukset 24.90M))
      (is (=marginaalissa? indeksitarkistukset-muut-kuin-kokhint 44.96M))
      (is (=marginaalissa? indeksitarkistukset-kaikki 1002.03))
      (is (=marginaalissa? kaikki-paitsi-kokhint-yhteensa -6757.96M))

      (is (=marginaalissa? kaikki-yhteensa nurkkasumma 155250.20M)))))

(deftest raportin-suoritus-urakalle-toimii-hoitokausi-2016-2017
  (let [_ (u (str "DELETE FROM indeksi WHERE nimi = 'MAKU 2005' AND kuukausi in (10,11,12) AND vuosi = 2016"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
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

      (is (=marginaalissa? yks-hint 7882.50M))
      (is (=marginaalissa? sanktiot -1900.67M))
      (is (=marginaalissa? indeksitarkistukset-yks-hint 0.00M))
      (is (=marginaalissa? indeksitarkistukset-sanktiot -191.15M))
      (is (=marginaalissa? indeksitarkistukset-muut-kuin-kokhint -167.62))
      (is (=marginaalissa? indeksitarkistukset-kaikki -167.62M))
      (is (=marginaalissa? kaikki-paitsi-kokhint-yhteensa 5790.67M))
      (is (=marginaalissa? kaikki-yhteensa nurkkasumma 5981.83M)))))

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
      (is (=marginaalissa? indeksitarkistukset-bonukset 0.0M))
      (is (=marginaalissa? indeksitarkistukset-erilliskustannukset 49.81M))
      (is (=marginaalissa? indeksitarkistukset-muut-kuin-kokhint 154.02M))
      (is (=marginaalissa? indeksitarkistukset-kaikki 247.12M))
      (is (=marginaalissa? kaikki-paitsi-kokhint-yhteensa -15441.88M))

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

          yks-hint-indeksitarkistukset (arvo-raportin-nnesta-elementista vastaus 2)
          sanktioiden-indeksitarkistukset (arvo-raportin-nnesta-elementista vastaus 3)
          muiden-kuin-kokhint-indeksitarkistukset (arvo-raportin-nnesta-elementista vastaus 4)
          kaikki-indeksitarkistukset (arvo-raportin-nnesta-elementista vastaus 5)
          kaikki-paitsi-kokhint-yhteensa (arvo-raportin-nnesta-elementista vastaus 6)
          kaikki-yhteensa (arvo-raportin-nnesta-elementista vastaus 7)
          nurkkasumma (:arvo (second (second (last (last (last (last vastaus)))))))]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")

      (is (=marginaalissa? yks-hint 7882.50M))
      (is (=marginaalissa? sanktiot -1900.67M))
      (is (=marginaalissa? yks-hint-indeksitarkistukset 2310.39M))
      (is (=marginaalissa? sanktioiden-indeksitarkistukset -191.159M))
      (is (=marginaalissa? muiden-kuin-kokhint-indeksitarkistukset 2119.23M))
      (is (=marginaalissa? kaikki-indeksitarkistukset 2119.23M))
      (is (=marginaalissa? kaikki-paitsi-kokhint-yhteensa 8101.06M))

      (is (=marginaalissa? kaikki-yhteensa nurkkasumma 8101.06M)))))