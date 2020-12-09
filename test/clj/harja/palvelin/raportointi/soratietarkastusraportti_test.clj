(ns harja.palvelin.raportointi.soratietarkastusraportti-test
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
                                {:nimi :soratietarkastusraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Soratietarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:korosta-rivit #{0
                                       1
                                       3}
                      :otsikko "Oulun alueurakka 2014-2019, Soratietarkastusraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Soratietarkastusraportti"
                      :tyhja nil
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 8
                       :otsikko "Päi­vä­mää­rä"}
                      {:leveys 5
                       :otsikko "Tie"}
                      {:leveys 6
                       :otsikko "Aosa"}
                      {:leveys 6
                       :otsikko "Aet"}
                      {:leveys 6
                       :otsikko "Losa"}
                      {:leveys 6
                       :otsikko "Let"}
                      {:leveys 5
                       :otsikko "Hoi­to­luok­ka"}
                      {:leveys 8
                       :otsikko "1"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 8
                       :otsikko "2"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 8
                       :otsikko "3"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 8
                       :otsikko "4"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 8
                       :otsikko "5"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 8
                       :otsikko "Yht"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 8
                       :otsikko "1+2"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 10
                       :otsikko "Laa­tu­poik­ke­a­ma"}]
                     [["08.01.2015"
                       5
                       364
                       8011
                       nil
                       nil
                       "II"
                       [:arvo-ja-osuus
                        {:arvo 1
                         :osuus 33}]
                       [:arvo-ja-osuus
                        {:arvo 0
                         :osuus 0}]
                       [:arvo-ja-osuus
                        {:arvo 0
                         :osuus 0}]
                       [:arvo-ja-osuus
                        {:arvo 0
                         :osuus 0}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 67}]
                       [:arvo-ja-osuus
                        {:arvo 3
                         :osuus 100}]
                       [:arvo-ja-osuus
                        {:arvo 1
                         :osuus 33}]
                       "Kyllä (1)"]
                       ["06.01.2015"
                        4
                        364
                        8012
                        nil
                        nil
                        "III"
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 25}]
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 25}]
                        [:arvo-ja-osuus
                         {:arvo 0
                          :osuus 0}]
                        [:arvo-ja-osuus
                         {:arvo 2
                          :osuus 50}]
                        [:arvo-ja-osuus
                         {:arvo 0
                          :osuus 0}]
                        [:arvo-ja-osuus
                         {:arvo 4
                          :osuus 100}]
                        [:arvo-ja-osuus
                         {:arvo 2
                          :osuus 50}]
                        "Kyllä (1, 2)"]
                       ["06.01.2015"
                        4
                        364
                        8012
                        nil
                        nil
                        "I"
                        [:arvo-ja-osuus
                         {:arvo 0
                          :osuus 0}]
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 33}]
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 33}]
                        [:arvo-ja-osuus
                         {:arvo 0
                          :osuus 0}]
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 33}]
                        [:arvo-ja-osuus
                         {:arvo 3
                          :osuus 100}]
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 33}]
                        nil]
                       ["05.01.2015"
                        5
                        364
                        8011
                        nil
                        nil
                        "II"
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 33}]
                        [:arvo-ja-osuus
                         {:arvo 0
                          :osuus 0}]
                        [:arvo-ja-osuus
                         {:arvo 0
                          :osuus 0}]
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 33}]
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 33}]
                        [:arvo-ja-osuus
                         {:arvo 3
                          :osuus 100}]
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 33}]
                        "Kyllä (1)"]
                       ["05.01.2015"
                        5
                        364
                        8012
                        nil
                        nil
                        "II"
                        [:arvo-ja-osuus
                         {:arvo 0
                          :osuus 0}]
                        [:arvo-ja-osuus
                         {:arvo 0
                          :osuus 0}]
                        [:arvo-ja-osuus
                         {:arvo 2
                          :osuus 40}]
                        [:arvo-ja-osuus
                         {:arvo 1
                          :osuus 20}]
                        [:arvo-ja-osuus
                         {:arvo 2
                          :osuus 40}]
                        [:arvo-ja-osuus
                         {:arvo 5
                          :osuus 100}]
                        [:arvo-ja-osuus
                         {:arvo 0
                          :osuus 0}]
                        nil]
                       ["Yhteensä"
                        nil
                        nil
                        nil
                        nil
                        nil
                        nil
                        [:arvo-ja-osuus
                         {:arvo 3
                          :osuus 17}]
                        [:arvo-ja-osuus
                         {:arvo 2
                          :osuus 11}]
                        [:arvo-ja-osuus
                         {:arvo 3
                          :osuus 17}]
                        [:arvo-ja-osuus
                         {:arvo 4
                          :osuus 22}]
                        [:arvo-ja-osuus
                         {:arvo 6
                          :osuus 33}]
                        [:arvo-ja-osuus
                         {:arvo 18
                          :osuus 100}]
                        [:arvo-ja-osuus
                         {:arvo 5
                          :osuus 28}]
                        nil]]]
                    [:yhteenveto
                     [[1
                       "Vähintään yksi mittaustulos arvoltaan 1"]
                      [2
                       "Vähintään yksi mittaustulos arvoltaan 2 yhtenäisellä 20m tie­osuudella hoito­luokassa II tai III."]]]]))))


(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :soratietarkastusraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 1 1))
                                              :loppupvm (c/to-date (t/local-date 2015 1 31))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [otsikko "Pohjois-Pohjanmaa, Soratietarkastusraportti tammikuussa 2015"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Päi\u00ADvä\u00ADmää\u00ADrä"}
                                          {:otsikko "Tie"}
                                          {:otsikko "Aosa"}
                                          {:otsikko "Aet"}
                                          {:otsikko "Losa"}
                                          {:otsikko "Let"}
                                          {:otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}
                                          {:otsikko "1"}
                                          {:otsikko "2"}
                                          {:otsikko "3"}
                                          {:otsikko "4"}
                                          {:otsikko "5"}
                                          {:otsikko "Yht"}
                                          {:otsikko "1+2"}
                                          {:otsikko "Laa\u00ADtu\u00ADpoik\u00ADke\u00ADa\u00ADma"}))))


(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :soratietarkastusraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 1 1))
                                              :loppupvm (c/to-date (t/local-date 2015 1 31))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [otsikko "KOKO MAA, Soratietarkastusraportti tammikuussa 2015"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Päi\u00ADvä\u00ADmää\u00ADrä"}
                                          {:otsikko "Tie"}
                                          {:otsikko "Aosa"}
                                          {:otsikko "Aet"}
                                          {:otsikko "Losa"}
                                          {:otsikko "Let"}
                                          {:otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}
                                          {:otsikko "1"}
                                          {:otsikko "2"}
                                          {:otsikko "3"}
                                          {:otsikko "4"}
                                          {:otsikko "5"}
                                          {:otsikko "Yht"}
                                          {:otsikko "1+2"}
                                          {:otsikko "Laa\u00ADtu\u00ADpoik\u00ADke\u00ADa\u00ADma"}))))
