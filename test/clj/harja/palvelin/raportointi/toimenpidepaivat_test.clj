(ns harja.palvelin.raportointi.toimenpidepaivat-test
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
                                {:nimi :toimenpidepaivat
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2005 10 1))
                                              :loppupvm (c/to-date (t/local-date 2006 10 1))
                                              :hoitoluokat #{1 2 3 4 5 6 7 9 10}
                                              :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "Toimenpidepäivät aikavälillä 01.10.2005 - 01.10.2006 (365 päivää)")]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Monenako päivänä toimenpidettä on tehty aikavälillä")

    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      (fn [rivi]
        (let [rivi (if (map? rivi) (:rivi rivi) rivi)
              tehtava (first rivi)
              lukumaarat (rest rivi)]
          (and (string? tehtava)
               (not-empty tehtava)

               (every?
                 (fn [solu]
                   (or (nil? solu)
                       (<= 0 solu)))
                 lukumaarat)))))

    (apurit/tarkista-taulukko-sarakkeet
      taulukko
      {:otsikko "Teh­tä­vä"}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea})))

(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :toimenpidepaivat
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2005 10 1))
                                              :loppupvm (c/to-date (t/local-date 2006 10 1))
                                              :hoitoluokat #{1 2 3 4 5 6 7 9 10}
                                              :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "Toimenpidepäivät aikavälillä 01.10.2005 - 01.10.2006 (365 päivää)")]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Monenako päivänä toimenpidettä on tehty aikavälillä")

    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      (fn [rivi]
        (let [rivi (if (map? rivi) (:rivi rivi) rivi)
              tehtava (first rivi)
              lukumaarat (rest rivi)]
          (and (string? tehtava)
               (not-empty tehtava)

               (every?
                 (fn [solu]
                   (or (nil? solu)
                       (<= 0 solu)))
                 lukumaarat)))))

    (apurit/tarkista-taulukko-sarakkeet
      taulukko
      {:otsikko "Teh­tä­vä"}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea})))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :toimenpidepaivat
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2005 1 1))
                                              :loppupvm (c/to-date (t/local-date 2006 12 31))
                                              :hoitoluokat #{1 2 3 4 5 6 7 9 10}
                                              :urakkatyyppi :hoito}})
        taulukko (apurit/taulukko-otsikolla vastaus "Toimenpidepäivät aikavälillä 01.01.2005 - 31.12.2006 (729 päivää)")]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Monenako päivänä toimenpidettä on tehty aikavälillä")

    (apurit/tarkista-taulukko-kaikki-rivit
      taulukko
      (fn [rivi]
        (let [rivi (if (map? rivi) (:rivi rivi) rivi)
              tehtava (first rivi)
              lukumaarat (rest rivi)]
          (and (string? tehtava)
               (not-empty tehtava)

               (every?
                 (fn [solu]
                   (or (nil? solu)
                       (<= 0 solu)))
                 lukumaarat)))))

    (apurit/tarkista-taulukko-sarakkeet
      taulukko
      {:otsikko "Teh­tä­vä"}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "IsE"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Is"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "I"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "Ib"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "TIb"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "II"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "III"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K1"
       :tasaa   :oikea}
      {:fmt     :kokonaisluku
       :otsikko "K2"
       :tasaa   :oikea})))
