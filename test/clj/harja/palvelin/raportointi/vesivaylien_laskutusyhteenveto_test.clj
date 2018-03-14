(ns harja.palvelin.raportointi.vesivaylien-laskutusyhteenveto-test
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
            [harja.palvelin.raportointi.testiapurit :as apurit]))

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


(defn- yhteensa-rivi [kustannuslaji]
  (let [rivi (last (last kustannuslaji))]
    rivi))


(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :vesivaylien-laskutusyhteenveto
                                 :konteksti "urakka"
                                 :urakka-id (hae-helsingin-vesivaylaurakan-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2016 8 1))
                                              :loppupvm (c/to-date (t/local-date 2017 7 31))}})
        odotettu-otsikko "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL, Laskutusyhteenveto ajalta 01.08.2016 - 31.07.2017"
        saatu-otsikko (:nimi (second vastaus))
        taulukko-kauppamerenkulku (nth vastaus 2)
        taulukko-kauppamerenkulku-yhteensa (yhteensa-rivi taulukko-kauppamerenkulku)
        taulukko-muu-vesiliikenne (nth vastaus 3)
        taulukko-muu-vesiliikenne-yhteensa (yhteensa-rivi taulukko-muu-vesiliikenne)
        taulukko-yhteenveto (nth vastaus 4)
        taulukko-yhteenveto-yhteensa (yhteensa-rivi taulukko-yhteenveto)]


    (is (vector? vastaus))
    (is (= (first vastaus) :raportti))

    (is (= ["Yhteensä" "" 30.0M ""] taulukko-kauppamerenkulku-yhteensa))
    (is (= ["Yhteensä" "" 0M ""] taulukko-muu-vesiliikenne-yhteensa))
    (is (= ["Kaikki yhteensä" 30.0M] taulukko-yhteenveto-yhteensa))))



(deftest raportin-suoritus-hallintayksikolle-toimii-hoitokausi-2016-2017
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :kanavien-laskutusyhteenveto
                                 :konteksti "urakka"
                                 :hallintayksikko-id (hae-sisavesivaylien-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2017 8 1))
                                              :loppupvm (c/to-date (t/local-date 2018 7 30))}})
        odotettu-otsikko "Sisävesiväylät, Laskutusyhteenveto ajalta 01.08.2016 - 31.07.2017"
        saatu-otsikko (:nimi (second vastaus))
        taulukko-kauppamerenkulku (nth vastaus 2)
        taulukko-kauppamerenkulku-yhteensa (yhteensa-rivi taulukko-kauppamerenkulku)
        taulukko-muu-vesiliikenne (nth vastaus 3)
        taulukko-muu-vesiliikenne-yhteensa (yhteensa-rivi taulukko-muu-vesiliikenne)
        taulukko-yhteenveto (nth vastaus 4)
        taulukko-yhteenveto-yhteensa (yhteensa-rivi taulukko-yhteenveto)]


    (is (vector? vastaus))
    (is (= (first vastaus) :raportti))

    ;; FIXME: assertit kohdilleen eli oltava tuplamäärä yhteen urakkaan verrattuna
    (is (= ["Yhteensä" "" 30.0M ""] taulukko-kauppamerenkulku-yhteensa))
    (is (= ["Yhteensä" "" 0M ""] taulukko-muu-vesiliikenne-yhteensa))
    (is (= ["Kaikki yhteensä" 30.0M] taulukko-yhteenveto-yhteensa))))

