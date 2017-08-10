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

(defn arvo-vastauksen-nnesta-elementista [vastaus n]
  (second (first (second (second (last (nth (nth (last vastaus) n) 3)))))))

(deftest raportin-suoritus-urakalle-toimii
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

          kok-hint (arvo-vastauksen-nnesta-elementista vastaus 0)
          yks-hint (arvo-vastauksen-nnesta-elementista vastaus 1)
          sanktiot (arvo-vastauksen-nnesta-elementista vastaus 2)
          _ (log/debug " sanktiot " sanktiot)
          talvisuolasakot (arvo-vastauksen-nnesta-elementista vastaus 3)
          _ (log/debug " talvisuolasakot " talvisuolasakot)
          muutos-ja-lisatyot (arvo-vastauksen-nnesta-elementista vastaus 4)
          akilliset (arvo-vastauksen-nnesta-elementista vastaus 5)
          vahinkojen-korjaukset (arvo-vastauksen-nnesta-elementista vastaus 6)
          bonukset (arvo-vastauksen-nnesta-elementista vastaus 7)
          erilliskustannukset (arvo-vastauksen-nnesta-elementista vastaus 8)

          indeksitarkistukset-koh-hint (arvo-vastauksen-nnesta-elementista vastaus 9)
          indeksitarkistukset-yks-hint (arvo-vastauksen-nnesta-elementista vastaus 10)
          indeksitarkistukset-sanktiot (arvo-vastauksen-nnesta-elementista vastaus 11)
          indeksitarkistukset-talvisuolasakot (arvo-vastauksen-nnesta-elementista vastaus 12)
          indeksitarkistukset-muutos-ja-lisatyot (arvo-vastauksen-nnesta-elementista vastaus 13)
          indeksitarkistukset-akilliset (arvo-vastauksen-nnesta-elementista vastaus 14)
          indeksitarkistukset-vahinkojen-korjaukset (arvo-vastauksen-nnesta-elementista vastaus 15)
          indeksitarkistukset-bonukset (arvo-vastauksen-nnesta-elementista vastaus 16)
          indeksitarkistukset-erilliskustannukset (arvo-vastauksen-nnesta-elementista vastaus 17)
          indeksitarkistukset-muut-kuin-kokhint (arvo-vastauksen-nnesta-elementista vastaus 18)
          indeksitarkistukset-kaikki (arvo-vastauksen-nnesta-elementista vastaus 19)
          kaikki-paitsi-kokhint-yhteensa (arvo-vastauksen-nnesta-elementista vastaus 20)

          nurkkasumma (:arvo (second (second (last (last (last (last vastaus)))))))
          _ (log/debug " nurkkasumma  " nurkkasumma)
          ]
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
      (is (= indeksitarkistukset-kaikki "1000,20"))
      (is (= kaikki-paitsi-kokhint-yhteensa "-6759,80"))


      (is (= nurkkasumma "155 250,20")))))