(ns harja.palvelin.raportointi.toimenpidekilometrit-test
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
                                              :hoitoluokat  #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Toimenpidekilometrit"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko    "Oulun alueurakka 2014-2019, Toimenpidekilometrit ajalta 01.10.2014 - 01.10.2015"
                      :rivi-ennen '({:sarakkeita 1
                                    :teksti     "Alue"}
                                    {:sarakkeita 8
                                     :teksti     "Oulun alueurakka 2014-2019"})
                      :sheet-nimi "Toimenpidekilometrit"
                      :tyhja      "Ei raportoitavia tehtäviä."}
                     [{:otsikko "Hoi­to­luok­ka"}
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
                       :tasaa   :keskita}]
                     []]]))))


(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :toimenpidekilometrit
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2014 10 1))
                                                      :loppupvm     (c/to-date (t/local-date 2015 10 1))
                                                      :hoitoluokat  #{1 2 3 4 5 6 8 7}
                                                      :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Toimenpidekilometrit"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko    "Pohjois-Pohjanmaa ja Kainuu, Toimenpidekilometrit ajalta 01.10.2014 - 01.10.2015"
                      :rivi-ennen '({:sarakkeita 1
                                    :teksti     "Alue"}
                                    {:sarakkeita 8
                                     :teksti     "Kajaanin alueurakka 2014-2019"}
                                    {:sarakkeita 8
                                     :teksti     "Oulun alueurakka 2014-2019"})
                      :sheet-nimi "Toimenpidekilometrit"
                      :tyhja      "Ei raportoitavia tehtäviä."}
                     [{:otsikko "Hoi­to­luok­ka"}
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
                       :tasaa   :keskita}]
                     []]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :toimenpidekilometrit
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2014 1 1))
                                              :loppupvm     (c/to-date (t/local-date 2015 12 31))
                                              :hoitoluokat  #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Toimenpidekilometrit"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko    "KOKO MAA, Toimenpidekilometrit ajalta 01.01.2014 - 31.12.2015"
                      :rivi-ennen '({:sarakkeita 1
                                    :teksti     "Alue"}
                                    {:sarakkeita 8
                                     :teksti     "Uusimaa"}
                                    {:sarakkeita 8
                                     :teksti     "Varsinais-Suomi"}
                                    {:sarakkeita 8
                                     :teksti     "Kaakkois-Suomi"}
                                    {:sarakkeita 8
                                     :teksti     "Pirkanmaa"}
                                    {:sarakkeita 8
                                     :teksti     "Pohjois-Savo"}
                                    {:sarakkeita 8
                                     :teksti     "Keski-Suomi"}
                                    {:sarakkeita 8
                                     :teksti     "Etelä-Pohjanmaa"}
                                    {:sarakkeita 8
                                     :teksti     "Pohjois-Pohjanmaa ja Kainuu"}
                                    {:sarakkeita 8
                                     :teksti     "Lappi"})
                      :sheet-nimi "Toimenpidekilometrit"
                      :tyhja      "Ei raportoitavia tehtäviä."}
                     [{:otsikko "Hoi­to­luok­ka"}
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
                       :tasaa   :keskita}]
                     []]]))))
