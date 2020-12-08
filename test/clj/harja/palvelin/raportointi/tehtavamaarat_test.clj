(ns harja.palvelin.raportointi.tehtavamaarat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

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

(deftest tehtavamaara-raportti-perusjutut
  (testing "Määrien haku raportille"
    (let [[urakka-raportti-tagi _
           [urakka-taulukko-tagi _ _ urakka-haun-rivit] :as urakan-kamat]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi       :tehtavamaarat
                           :konteksti  "urakka"
                           :urakka-id  (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :parametrit {:alkupvm  (c/to-date (t/local-date 2020 10 1))
                                        :loppupvm (c/to-date (t/local-date 2021 10 1))}})
          [_ _ [_ _ _ tyhja-aikavali-rivit] :as ei-tietoja-kamat]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi       :tehtavamaarat
                           :konteksti  "urakka"
                           :urakka-id  (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :parametrit {:alkupvm  (c/to-date (t/local-date 2014 10 1))
                                        :loppupvm (c/to-date (t/local-date 2015 10 1))}})
          [koko-raportti _ [koko-taulukko _ _ koko-maa-rivit] :as koko-maa-kamat]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi       :tehtavamaarat
                           :konteksti  "koko maa"
                           :parametrit {:alkupvm  (c/to-date (t/local-date 2020 10 1))
                                        :loppupvm (c/to-date (t/local-date 2021 10 1))}})
          [ely-raportti _ [ely-taulukko _ _ ely-rivit] :as ely-kamat]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi               :tehtavamaarat
                           :konteksti          "hallintayksikko"
                           :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                           :parametrit         {:alkupvm  (c/to-date (t/local-date 2020 10 1))
                                                :loppupvm (c/to-date (t/local-date 2021 10 1))}})]
      (testing "Urakka"
        (is (not (empty? urakka-haun-rivit)) "Palautuu raportille asioita urakalla")
        (is (empty? tyhja-aikavali-rivit) "Väärä aikaväli ei palauta rivejä urakalla")
        (is (contains? (first urakka-haun-rivit) :rivi) "Oikeat haut palauttuvat kivoja juttuja"))
      (is (not (empty? koko-maa-rivit)) "Palautuu raportille asioita koko maalla")
      (is (not (empty? ely-rivit)) "Palautuu raportille asioita hankintayksiköllä")
      (is (every? keyword? (conj []
                                 urakka-raportti-tagi
                                 urakka-taulukko-tagi
                                 koko-raportti
                                 koko-taulukko
                                 ely-taulukko
                                 ely-raportti)) "Palautuneet asiat näyttävät raportilta"))))

(deftest tehtavamaara-raportti-ikavat-jutut
  (testing "Määrien haku raportilla ei toimi"
    (let [[_ _ [_ _ _ rivit]]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                            :suorita-raportti
                            +kayttaja-jvh+
                            {:nimi       :tehtavamaarat
                             :konteksti  "urakka"
                             :parametrit {:alkupvm  (c/to-date (t/local-date 2016 10 1))
                                          :loppupvm (c/to-date (t/local-date 2017 10 1))}})
          [_ _ [_ _ _ rivit2]]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi       :tehtavamaarat
                           :konteksti  "urakka"
                           :urakka-id  82347592
                           :parametrit {:alkupvm  (c/to-date (t/local-date 2016 10 1))
                                        :loppupvm (c/to-date (t/local-date 2017 10 1))}})]
      (is (empty? rivit) "Haetaan urakkaa ilman urakka-id")
      (is (thrown? IllegalArgumentException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                            :suorita-raportti
                                                            +kayttaja-jvh+
                                                            {:nimi       :tehtavamaarat
                                                             :konteksti  "urakka"
                                                             :urakka-id  8234759283495
                                                             :parametrit {:loppupvm (c/to-date (t/local-date 2017 10 1))}})) "Parametreja puuttuu")
      (is (empty? rivit2) "Ei olemassa oleva id"))))