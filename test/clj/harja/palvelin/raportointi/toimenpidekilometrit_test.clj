(ns harja.palvelin.raportointi.toimenpidekilometrit-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.raportointi.testiapurit :as apurit]
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
                                {:nimi       :toimenpidekilometrit
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm     (c/to-date (t/local-date 2015 10 1))
                                              :hoitoluokat  #{1 2 3 4 5 6 7 9 10}
                                              :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "Oulun alueurakka 2014-2019, Toimenpidekilometrit ajalta 01.10.2014 - 01.10.2015")]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Toimenpidekilometrit")
    (apurit/tarkista-taulukko-sarakkeet
      taulukko
      {:otsikko "Hoi­to­luok­ka"}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita})
    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      (fn [r]
        (let [rivi (if (map? r) (:rivi r) r)
              hoitoluokka (first rivi)
              toimenpidekilometrit (rest rivi)]
          (and
            (and (string? hoitoluokka)
                 (not-empty hoitoluokka))
            (every?
             (fn [solu]
               (or (and (number? solu)
                        (<= 0 solu))
                   (nil? solu)))
             toimenpidekilometrit)))))))

(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :toimenpidekilometrit
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2014 10 1))
                                                      :loppupvm     (c/to-date (t/local-date 2015 10 1))
                                                      :hoitoluokat  #{1 2 3 4 5 6 7 9 10}
                                                      :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "Pohjois-Pohjanmaa, Toimenpidekilometrit ajalta 01.10.2014 - 01.10.2015")]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Toimenpidekilometrit")
    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      (fn [r]
        (let [rivi (if (map? r) (:rivi r) r)
              hoitoluokka (first rivi)
              toimenpidekilometrit (rest rivi)]
          (and
            (and (string? hoitoluokka)
                 (not-empty hoitoluokka))
            (every?
              (fn [solu]
                (or (and (number? solu)
                         (<= 0 solu))
                    (nil? solu)))
              toimenpidekilometrit)))))
    (apurit/tarkista-taulukko-sarakkeet
      taulukko
      {:otsikko "Hoi­to­luok­ka"}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita}
      {:otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita})))

(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :toimenpidekilometrit
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2014 1 1))
                                              :loppupvm     (c/to-date (t/local-date 2015 12 31))
                                              :hoitoluokat  #{1 2 3 4 5 6 7 9 10}
                                              :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "KOKO MAA, Toimenpidekilometrit ajalta 01.01.2014 - 31.12.2015")]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Toimenpidekilometrit")
    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      (fn [r]
        (let [rivi (if (map? r) (:rivi r) r)
              hoitoluokka (first rivi)
              toimenpidekilometrit (rest rivi)]
          (and
            (and (string? hoitoluokka)
                 (not-empty hoitoluokka))
            (every?
              (fn [solu]
                (or (and (number? solu)
                         (<= 0 solu))
                    (nil? solu)))
              toimenpidekilometrit)))))
    (apurit/tarkista-taulukko-sarakkeet
      taulukko
      {:otsikko "Hoi­to­luok­ka"}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "IsE"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Is"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "I"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "Ib"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "TIb"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "II"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "III"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K1"
       :tasaa   :keskita}
      {:fmt     :numero
       :otsikko "K2"
       :tasaa   :keskita})))
