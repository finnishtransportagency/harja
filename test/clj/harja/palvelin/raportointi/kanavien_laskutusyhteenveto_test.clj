(ns harja.palvelin.raportointi.kanavien-laskutusyhteenveto-test
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

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(defn- yhteensa-rivi [kustannuslaji]
  (let [rivi (last (last kustannuslaji))]
    rivi))

(deftest raportin-suoritus-urakalle-toimii-hoitokausi-2017-2018
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :kanavien-laskutusyhteenveto
                                 :konteksti "urakka"
                                 :urakka-id (hae-saimaan-kanavaurakan-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2017 8 1))
                                              :loppupvm (c/to-date (t/local-date 2018 7 30))}})]
    (is (vector? vastaus))
    (let [odotettu-otsikko "Saimaan kanava, Laskutusyhteenveto ajalta 01.08.2017 - 30.07.2018"
          saatu-otsikko (:nimi (second vastaus))
          kok-hint (yhteensa-rivi (nth vastaus 2))
          muutos-ja-lisatyot (yhteensa-rivi (nth vastaus 3))
          sanktiot (yhteensa-rivi (nth vastaus 4))
          erilliskustannukset (yhteensa-rivi (nth vastaus 5))
          kaikki-yhteensa (yhteensa-rivi (nth vastaus 6))]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")

      (is (=marginaalissa? (second kok-hint) 15000M))
      (is (=marginaalissa? (nth kok-hint 2) 15000M))
      (is (=marginaalissa? (nth kok-hint 3) 0M))
      (is (=marginaalissa? (nth muutos-ja-lisatyot 2) 1545.00M))
      (is (=marginaalissa? (nth sanktiot 2) 5000M))
      (is (=marginaalissa? (nth erilliskustannukset 2) 10000M))
      (is (=marginaalissa? (nth kaikki-yhteensa 2) 31545M)))))
