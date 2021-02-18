(ns harja.palvelin.raportointi.toimenpideajat-test
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

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :toimenpideajat
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "Oulun alueurakka 2014-2019")]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Oulun alueurakka 2014-2019, Toimenpiteiden ajoittuminen ajalta 01.10.2014 - 01.10.2015")

    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      (fn [r]
        (let [rivi (if (map? r) (:rivi r) r)
              tehtava (first rivi)
              kappalemaarat (rest rivi)]
          (and
            (and (string? tehtava)
                 (not-empty tehtava))
            (every?
              (fn [solu]
                (or (and (number? solu)
                         (<= 0 solu))
                    (nil? solu)))
              kappalemaarat)))))

    (log/debug (pr-str (nth taulukko 2)))

    (apurit/tarkista-taulukko-sarakkeet
      taulukko
      {:otsikko "Teh­tä­vä"}
      {:otsikko "< 6", :tasaa :oikea, :reunus :vasen}
      {:otsikko "6 - 10", :tasaa :oikea, :reunus :ei}
      {:otsikko "10 - 14", :tasaa :oikea, :reunus :ei}
      {:otsikko "14 - 18", :tasaa :oikea, :reunus :ei}
      {:otsikko "18 - 22", :tasaa :oikea, :reunus :ei}
      {:otsikko "22 - 02", :tasaa :oikea, :reunus :oikea}
      {:otsikko "< 6", :tasaa :oikea, :reunus :vasen}
      {:otsikko "6 - 10", :tasaa :oikea, :reunus :ei}
      {:otsikko "10 - 14", :tasaa :oikea, :reunus :ei}
      {:otsikko "14 - 18", :tasaa :oikea, :reunus :ei}
      {:otsikko "18 - 22", :tasaa :oikea, :reunus :ei}
      {:otsikko "22 - 02", :tasaa :oikea, :reunus :oikea}
      {:otsikko "< 6", :tasaa :oikea, :reunus :vasen}
      {:otsikko "6 - 10", :tasaa :oikea, :reunus :ei}
      {:otsikko "10 - 14", :tasaa :oikea, :reunus :ei}
      {:otsikko "14 - 18", :tasaa :oikea, :reunus :ei}
      {:otsikko "18 - 22", :tasaa :oikea, :reunus :ei}
      {:otsikko "22 - 02", :tasaa :oikea, :reunus :oikea}
      {:otsikko "< 6", :tasaa :oikea, :reunus :vasen}
      {:otsikko "6 - 10", :tasaa :oikea, :reunus :ei}
      {:otsikko "10 - 14", :tasaa :oikea, :reunus :ei}
      {:otsikko "14 - 18", :tasaa :oikea, :reunus :ei}
      {:otsikko "18 - 22", :tasaa :oikea, :reunus :ei}
      {:otsikko "22 - 02", :tasaa :oikea, :reunus :oikea}
      {:otsikko "< 6", :tasaa :oikea, :reunus :vasen}
      {:otsikko "6 - 10", :tasaa :oikea, :reunus :ei}
      {:otsikko "10 - 14", :tasaa :oikea, :reunus :ei}
      {:otsikko "14 - 18", :tasaa :oikea, :reunus :ei}
      {:otsikko "18 - 22", :tasaa :oikea, :reunus :ei}
      {:otsikko "22 - 02", :tasaa :oikea, :reunus :oikea}
      {:otsikko "< 6", :tasaa :oikea, :reunus :vasen}
      {:otsikko "6 - 10", :tasaa :oikea, :reunus :ei}
      {:otsikko "10 - 14", :tasaa :oikea, :reunus :ei}
      {:otsikko "14 - 18", :tasaa :oikea, :reunus :ei}
      {:otsikko "18 - 22", :tasaa :oikea, :reunus :ei}
      {:otsikko "22 - 02", :tasaa :oikea, :reunus :oikea}
      {:otsikko "< 6", :tasaa :oikea, :reunus :vasen}
      {:otsikko "6 - 10", :tasaa :oikea, :reunus :ei}
      {:otsikko "10 - 14", :tasaa :oikea, :reunus :ei}
      {:otsikko "14 - 18", :tasaa :oikea, :reunus :ei}
      {:otsikko "18 - 22", :tasaa :oikea, :reunus :ei}
      {:otsikko "22 - 02", :tasaa :oikea, :reunus :oikea}
      {:otsikko "< 6", :tasaa :oikea, :reunus :vasen}
      {:otsikko "6 - 10", :tasaa :oikea, :reunus :ei}
      {:otsikko "10 - 14", :tasaa :oikea, :reunus :ei}
      {:otsikko "14 - 18", :tasaa :oikea, :reunus :ei}
      {:otsikko "18 - 22", :tasaa :oikea, :reunus :ei}
      {:otsikko "22 - 02", :tasaa :oikea, :reunus :oikea}
      {:otsikko "Yht.", :tasaa :oikea, :leveys 5})))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :toimenpideajat
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "Pohjois-Pohjanmaa")]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Pohjois-Pohjanmaa, Toimenpiteiden ajoittuminen ajalta 01.10.2014 - 01.10.2015")

    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      (fn [r]
        (let [rivi (if (map? r) (:rivi r) r)
              tehtava (first rivi)
              kappalemaarat (rest rivi)]
          (and
            (and (string? tehtava)
                 (not-empty tehtava))
            (every?
              (fn [solu]
                (or (and (number? solu)
                         (<= 0 solu))
                    (nil? solu)))
              kappalemaarat)))))

    (apurit/tarkista-taulukko-sarakkeet
      taulukko
      {:otsikko "Teh­tä­vä"}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "Yht." :tasaa :oikea})))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :toimenpideajat
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 1 1))
                                              :loppupvm (c/to-date (t/local-date 2015 12 31))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "KOKO MAA")]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "KOKO MAA, Toimenpiteiden ajoittuminen ajalta 01.01.2014 - 31.12.2015")

    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      (fn [r]
        (let [rivi (if (map? r) (:rivi r) r)
              tehtava (first rivi)
              kappalemaarat (rest rivi)]
          (and
            (and (string? tehtava)
                 (not-empty tehtava))
            (every?
              (fn [solu]
                (or (and (number? solu)
                         (<= 0 solu))
                    (nil? solu)))
              kappalemaarat)))))

    (apurit/tarkista-taulukko-sarakkeet
      taulukko
      {:otsikko "Teh­tä­vä"}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "< 6" :reunus :vasen :tasaa :oikea}
      {:otsikko "6 - 10" :reunus :ei :tasaa :oikea}
      {:otsikko "10 - 14" :reunus :ei :tasaa :oikea}
      {:otsikko "14 - 18" :reunus :ei :tasaa :oikea}
      {:otsikko "18 - 22" :reunus :ei :tasaa :oikea}
      {:otsikko "22 - 02" :reunus :oikea :tasaa :oikea}
      {:otsikko "Yht." :tasaa :oikea})))
