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

(deftest raportin-suoritus-urakalle-toimii
  (let [hoitokausi (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :suorita-raportti
                                   +kayttaja-jvh+
                                   {:nimi :vesivaylien-laskutusyhteenveto
                                    :konteksti "urakka"
                                    :urakka-id (hae-helsingin-vesivaylaurakan-id)
                                    :parametrit {:alkupvm (c/to-date (t/local-date 2017 8 1))
                                                 :loppupvm (c/to-date (t/local-date 2018 7 31))}})
        elokuu (kutsu-palvelua (:http-palvelin jarjestelma)
                               :suorita-raportti
                               +kayttaja-jvh+
                               {:nimi :vesivaylien-laskutusyhteenveto
                                :konteksti "urakka"
                                :urakka-id (hae-helsingin-vesivaylaurakan-id)
                                :parametrit {:alkupvm (c/to-date (t/local-date 2017 8 1))
                                             :loppupvm (c/to-date (t/local-date 2017 8 31))}})
        syyskuu (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :vesivaylien-laskutusyhteenveto
                                 :konteksti "urakka"
                                 :urakka-id (hae-helsingin-vesivaylaurakan-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2017 9 1))
                                              :loppupvm (c/to-date (t/local-date 2017 9 30))}})
        lokakuu (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :vesivaylien-laskutusyhteenveto
                                 :konteksti "urakka"
                                 :urakka-id (hae-helsingin-vesivaylaurakan-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2017 10 1))
                                              :loppupvm (c/to-date (t/local-date 2017 10 31))}})
        marraskuu (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :suorita-raportti
                                  +kayttaja-jvh+
                                  {:nimi :vesivaylien-laskutusyhteenveto
                                   :konteksti "urakka"
                                   :urakka-id (hae-helsingin-vesivaylaurakan-id)
                                   :parametrit {:alkupvm (c/to-date (t/local-date 2017 11 1))
                                                :loppupvm (c/to-date (t/local-date 2017 11 30))}})

        molemmat-vaylatyypit-otsikko "Molemmat väylätyypit, tai ei väylätyyppiä"
        kauppamerenkulku-otsikko "Kauppamerenkulku"
        muu-merenkulku-otsikko "Muu vesiliikenne"
        yhteenveto-otsikko "Yhteenveto"]

    (testing "Vastaukset tulivat oikein"
      (is (vector? hoitokausi))
      (is (= (first hoitokausi) :raportti))

      (is (vector? elokuu))
      (is (= (first elokuu) :raportti))

      (is (vector? syyskuu))
      (is (= (first syyskuu) :raportti))

      (is (vector? lokakuu))
      (is (= (first lokakuu) :raportti))

      (is (vector? marraskuu))
      (is (= (first marraskuu) :raportti)))

    (testing "Kauppamerenkulku, muu vesiliikenne, ja yhteensä -taulukot löytyy"
      (is (true? (apurit/taulukko-otsikolla? hoitokausi kauppamerenkulku-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? hoitokausi muu-merenkulku-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? hoitokausi yhteenveto-otsikko)))

      (is (true? (apurit/taulukko-otsikolla? elokuu kauppamerenkulku-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? elokuu muu-merenkulku-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? elokuu yhteenveto-otsikko)))

      (is (true? (apurit/taulukko-otsikolla? syyskuu kauppamerenkulku-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? syyskuu muu-merenkulku-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? syyskuu yhteenveto-otsikko)))

      (is (true? (apurit/taulukko-otsikolla? lokakuu kauppamerenkulku-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? lokakuu muu-merenkulku-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? lokakuu yhteenveto-otsikko)))

      (is (true? (apurit/taulukko-otsikolla? marraskuu kauppamerenkulku-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? marraskuu muu-merenkulku-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? marraskuu yhteenveto-otsikko))))

    (testing "'Molemmat väylätyypit' taulukko näytetään vain, jos siellä on muita summia kuin 0"
      (is (true? (apurit/taulukko-otsikolla? hoitokausi molemmat-vaylatyypit-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? elokuu molemmat-vaylatyypit-otsikko)))
      (is (false? (apurit/taulukko-otsikolla? syyskuu molemmat-vaylatyypit-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? lokakuu molemmat-vaylatyypit-otsikko)))
      (is (true? (apurit/taulukko-otsikolla? marraskuu molemmat-vaylatyypit-otsikko))))

    (testing "Elokuun tarkemmat arvot"
      (let [taulukko (apurit/taulukko-otsikolla elokuu kauppamerenkulku-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Kokonaishintaiset toimenpiteet"
                (and (=marginaalissa? (apurit/raporttisolun-arvo suunnitellut) 100.30M)
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 100.30M)
                     (=marginaalissa? (apurit/raporttisolun-arvo jaljella) 0M))

                "Hietasaaren poijujen korjaus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yksittäiset toimenpiteet ilman tilausta"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 100.30M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla elokuu muu-merenkulku-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Kokonaishintaiset toimenpiteet"
                (and (=marginaalissa? (apurit/raporttisolun-arvo suunnitellut) 0M)
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (=marginaalissa? (apurit/raporttisolun-arvo jaljella) 0M))

                "Oulaisten meriväylän kunnostus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla elokuu molemmat-vaylatyypit-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Harjassa luotujen tilaus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 500.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 500.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla elokuu yhteenveto-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Kustan\u00ADnus\u00ADlaji"}
          {:otsikko "Toteutunut"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[kustannuslaji toteutunut :as rivi]]
            (and
              (= (count rivi) 2)
              (case (apurit/raporttisolun-arvo kustannuslaji)
                "Toimenpiteet"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 600.30M)

                "Sanktiot"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)

                "Erilliskustannukset"
                true

                "Kaikki yhteensä"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 600.30M)

                false))))))

    (testing "Syyskuun tarkemmat arvot"
      (let [taulukko (apurit/taulukko-otsikolla syyskuu kauppamerenkulku-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
               "Kokonaishintaiset toimenpiteet"
               (and (=marginaalissa? (apurit/raporttisolun-arvo suunnitellut) 101.8M)
                    (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 101.8M)
                    (=marginaalissa? (apurit/raporttisolun-arvo jaljella) 0M))

               "Hietasaaren poijujen korjaus"
               (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                    (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                    (empty? (apurit/raporttisolun-arvo jaljella)))

               "Yksittäiset toimenpiteet ilman tilausta"
               (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                    (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                    (empty? (apurit/raporttisolun-arvo jaljella)))

               "Yhteensä"
               (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                    (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 101.8M)
                    (empty? (apurit/raporttisolun-arvo jaljella)))

               false)))))

      (let [taulukko (apurit/taulukko-otsikolla syyskuu muu-merenkulku-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
               "Kokonaishintaiset toimenpiteet"
               (and (=marginaalissa? (apurit/raporttisolun-arvo suunnitellut) 0M)
                    (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                    (=marginaalissa? (apurit/raporttisolun-arvo jaljella) 0M))

               "Oulaisten meriväylän kunnostus"
               (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                    (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                    (empty? (apurit/raporttisolun-arvo jaljella)))

               "Yhteensä"
               (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                    (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                    (empty? (apurit/raporttisolun-arvo jaljella)))

               false)))))

      (let [taulukko (apurit/taulukko-otsikolla syyskuu yhteenveto-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Kustan\u00ADnus\u00ADlaji"}
          {:otsikko "Toteutunut"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[kustannuslaji toteutunut :as rivi]]
            (and
              (= (count rivi) 2)
              (case (apurit/raporttisolun-arvo kustannuslaji)
                "Toimenpiteet"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 101.8M)

                "Sanktiot"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)

                "Erilliskustannukset"
                true

                "Kaikki yhteensä"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 101.8M)

                false))))))

    (testing "Lokakuun tarkemmat arvot"
      (let [taulukko (apurit/taulukko-otsikolla lokakuu kauppamerenkulku-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Kokonaishintaiset toimenpiteet"
                (and (=marginaalissa? (apurit/raporttisolun-arvo suunnitellut) 100.90M)
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 100.90M)
                     (=marginaalissa? (apurit/raporttisolun-arvo jaljella) 0M))

                "Hietasaaren poijujen korjaus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yksittäiset toimenpiteet ilman tilausta"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 100.90M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla lokakuu muu-merenkulku-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Kokonaishintaiset toimenpiteet"
                (and (=marginaalissa? (apurit/raporttisolun-arvo suunnitellut) 0M)
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (=marginaalissa? (apurit/raporttisolun-arvo jaljella) 0M))

                "Oulaisten meriväylän kunnostus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla lokakuu molemmat-vaylatyypit-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Harjassa luotujen tilaus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 600.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 600.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla lokakuu yhteenveto-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Kustan\u00ADnus\u00ADlaji"}
          {:otsikko "Toteutunut"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[kustannuslaji toteutunut :as rivi]]
            (and
              (= (count rivi) 2)
              (case (apurit/raporttisolun-arvo kustannuslaji)
                "Toimenpiteet"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 700.90M)

                "Sanktiot"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)

                "Erilliskustannukset"
                true

                "Kaikki yhteensä"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 700.90M)

                false))))))

    (testing "Marraskuun tarkemmat arvot"
      (let [taulukko (apurit/taulukko-otsikolla marraskuu kauppamerenkulku-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Kokonaishintaiset toimenpiteet"
                (and (=marginaalissa? (apurit/raporttisolun-arvo suunnitellut) 102.70)
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 102.70)
                     (=marginaalissa? (apurit/raporttisolun-arvo jaljella) 0M))

                "Hietasaaren poijujen korjaus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yksittäiset toimenpiteet ilman tilausta"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 102.70)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla marraskuu muu-merenkulku-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Kokonaishintaiset toimenpiteet"
                (and (=marginaalissa? (apurit/raporttisolun-arvo suunnitellut) 0M)
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (=marginaalissa? (apurit/raporttisolun-arvo jaljella) 0M))

                "Oulaisten meriväylän kunnostus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla marraskuu molemmat-vaylatyypit-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Harjassa luotujen tilaus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 60000.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 60000.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla marraskuu yhteenveto-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Kustan\u00ADnus\u00ADlaji"}
          {:otsikko "Toteutunut"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[kustannuslaji toteutunut :as rivi]]
            (and
              (= (count rivi) 2)
              (case (apurit/raporttisolun-arvo kustannuslaji)
                "Toimenpiteet"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 60102.70M)

                "Sanktiot"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)

                "Erilliskustannukset"
                true

                "Kaikki yhteensä"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 60102.70M)

                false))))))

    (testing "Koko hoitokauden tarkemmat arvot"
      (let [taulukko (apurit/taulukko-otsikolla hoitokausi kauppamerenkulku-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Kokonaishintaiset toimenpiteet"
                (and (=marginaalissa? (apurit/raporttisolun-arvo suunnitellut) 1230.0M)
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 1230.0M)
                     (=marginaalissa? (apurit/raporttisolun-arvo jaljella) 0M))

                "Hietasaaren poijujen korjaus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 826.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yksittäiset toimenpiteet ilman tilausta"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 2056.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla hoitokausi muu-merenkulku-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Kokonaishintaiset toimenpiteet"
                (and (=marginaalissa? (apurit/raporttisolun-arvo suunnitellut) 0M)
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)
                     (=marginaalissa? (apurit/raporttisolun-arvo jaljella) 0M))

                "Oulaisten meriväylän kunnostus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 30.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 30.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla hoitokausi molemmat-vaylatyypit-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Hinnoit\u00ADtelu"}
          {:otsikko "Suunni\u00ADtellut"}
          {:otsikko "Toteutunut"}
          {:otsikko "Jäljellä"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[hinnoittelu suunnitellut toteutunut jaljella :as rivi]]
            (and
              (= (count rivi) 4)
              (case (apurit/raporttisolun-arvo hinnoittelu)
                "Harjassa luotujen tilaus"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 61100.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                "Yhteensä"
                (and (empty? (apurit/raporttisolun-arvo suunnitellut))
                     (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 61100.0M)
                     (empty? (apurit/raporttisolun-arvo jaljella)))

                false)))))

      (let [taulukko (apurit/taulukko-otsikolla hoitokausi yhteenveto-otsikko)]
        (apurit/tarkista-taulukko-sarakkeet
          taulukko
          {:otsikko "Kustan\u00ADnus\u00ADlaji"}
          {:otsikko "Toteutunut"})

        (apurit/tarkista-taulukko-kaikki-rivit
          taulukko
          (fn [[kustannuslaji toteutunut :as rivi]]
            (and
              (= (count rivi) 2)
              (case (apurit/raporttisolun-arvo kustannuslaji)
                "Toimenpiteet"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 63186.0M)

                "Sanktiot"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 0M)

                "Erilliskustannukset"
                true

                "Kaikki yhteensä"
                (=marginaalissa? (apurit/raporttisolun-arvo toteutunut) 63186.0M)

                false))))))))

(deftest raportin-suoritus-urakalle-toimii-2
  (let [_ (println "Testing...")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :vesivaylien-laskutusyhteenveto
                                 :konteksti "urakka"
                                 :urakka-id (hae-helsingin-vesivaylaurakan-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2017 8 1))
                                              :loppupvm (c/to-date (t/local-date 2018 7 31))}})
        _ (println "VASTAUS")
        _ (clojure.pprint/pprint vastaus)
        odotettu-otsikko "Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL, Laskutusyhteenveto ajalta 01.08.2016 - 31.07.2017"
        saatu-otsikko (:nimi (second vastaus))
        taulukko-kauppamerenkulku (get vastaus 2)
        taulukko-kauppamerenkulku-yhteensa (yhteensa-rivi taulukko-kauppamerenkulku)
        taulukko-muu-vesiliikenne (get vastaus 3)
        taulukko-muu-vesiliikenne-yhteensa (yhteensa-rivi taulukko-muu-vesiliikenne)
        taulukko-yhteenveto (get vastaus 5)
        taulukko-yhteenveto-yhteensa (yhteensa-rivi taulukko-yhteenveto)]


    (is (vector? vastaus))
    (is (= (first vastaus) :raportti))

    (is (= ["Yhteensä" "" 2056.00M ""] taulukko-kauppamerenkulku-yhteensa))
    (is (= ["Yhteensä" "" 30M ""] taulukko-muu-vesiliikenne-yhteensa))
    (is (= ["Kaikki yhteensä" 63186.00M] taulukko-yhteenveto-yhteensa))))

(deftest raportin-suoritus-urakalle-toimii-pyhaselka
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :vesivaylien-laskutusyhteenveto
                                 :konteksti "urakka"
                                 :urakka-id (hae-pyhaselan-vesivaylaurakan-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2016 8 1))
                                              :loppupvm (c/to-date (t/local-date 2017 7 31))}})
        odotettu-otsikko "hae-pyhaselan-vesivaylaurakan-id, Laskutusyhteenveto ajalta 01.08.2016 - 31.07.2017"
        saatu-otsikko (:nimi (second vastaus))
        taulukko-kauppamerenkulku (nth vastaus 2)
        taulukko-kauppamerenkulku-yhteensa (yhteensa-rivi taulukko-kauppamerenkulku)
        taulukko-muu-vesiliikenne (nth vastaus 3)
        taulukko-muu-vesiliikenne-yhteensa (yhteensa-rivi taulukko-muu-vesiliikenne)
        taulukko-yhteenveto (nth vastaus 5)
        taulukko-yhteenveto-yhteensa (yhteensa-rivi taulukko-yhteenveto)]


    (is (vector? vastaus))
    (is (= (first vastaus) :raportti))

    (is (= ["Yhteensä" "" 30.0M ""] taulukko-kauppamerenkulku-yhteensa))
    (is (= ["Yhteensä" "" 0M ""] taulukko-muu-vesiliikenne-yhteensa))
    (is (= ["Kaikki yhteensä" 10030.0M] taulukko-yhteenveto-yhteensa))))


(deftest raportin-suoritus-hallintayksikolle-toimii-hoitokausi-2016-2017
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :vesivaylien-laskutusyhteenveto
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-sisavesivaylien-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2016 8 1))
                                              :loppupvm (c/to-date (t/local-date 2017 7 30))}})
        odotettu-otsikko "Sisävesiväylät, Laskutusyhteenveto ajalta 01.08.2016 - 31.07.2017"
        saatu-otsikko (:nimi (second vastaus))
        taulukko-kauppamerenkulku (nth vastaus 2)
        taulukko-kauppamerenkulku-yhteensa (yhteensa-rivi taulukko-kauppamerenkulku)
        taulukko-muu-vesiliikenne (nth vastaus 3)
        taulukko-muu-vesiliikenne-yhteensa (yhteensa-rivi taulukko-muu-vesiliikenne)
        taulukko-yhteenveto (nth vastaus 5)
        taulukko-yhteenveto-yhteensa (yhteensa-rivi taulukko-yhteenveto)]

    (is (vector? vastaus))
    (is (= (first vastaus) :raportti))

    (is (= ["Yhteensä" "" 60.0M ""] taulukko-kauppamerenkulku-yhteensa))
    (is (= ["Yhteensä" "" 0M ""] taulukko-muu-vesiliikenne-yhteensa))
    (is (= ["Kaikki yhteensä" 20060.0M] taulukko-yhteenveto-yhteensa))))

