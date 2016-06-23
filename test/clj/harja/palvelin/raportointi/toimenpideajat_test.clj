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
                                {:nimi :toimenpideajat
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Toimenpidekilometrit"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Oulun alueurakka 2014-2019, Toimenpidekilometrit ajalta 01.10.2014 - 01.10.2015"
                      :rivi-ennen [{:sarakkeita 1
                                    :teksti "Alue"}
                                    {:sarakkeita 8
                                     :teksti "Oulun alueurakka 2014-2019"}]
                      :sheet-nimi "Toimenpidekilometrit"
                      :tyhja "Ei raportoitavia tehtäviä."}
                     [{:otsikko "Hoi­to­luok­ka"}
                      {:otsikko "Is"
                       :tasaa :keskita}
                      {:otsikko "I"
                       :tasaa :keskita}
                      {:otsikko "Ib"
                       :tasaa :keskita}
                      {:otsikko "TIb"
                       :tasaa :keskita}
                      {:otsikko "II"
                       :tasaa :keskita}
                      {:otsikko "III"
                       :tasaa :keskita}
                      {:otsikko "K1"
                       :tasaa :keskita}
                      {:otsikko "K2"
                       :tasaa :keskita}]
                     []]]))))


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
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Toimenpiteiden ajoittuminen"
                     :orientaatio :landscape}
                    [[:taulukko
                      {:otsikko "Toimenpiteiden ajoittuminen"
                       :rivi-ennen [{:sarakkeita 1
                                     :teksti "Hoi­to­luok­ka"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "Is"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "I"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "Ib"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "TIb"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "II"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "III"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "K1"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "K2"}
                                     {:sarakkeita 1
                                      :teksti ""}]}
                      [{:leveys 12
                        :otsikko "Teh­tä­vä"}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 5
                        :otsikko "Yht."
                        :tasaa :oikea}]
                      []]]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :toimenpideajat
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 1 1))
                                              :loppupvm (c/to-date (t/local-date 2015 12 31))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Toimenpiteiden ajoittuminen"
                     :orientaatio :landscape}
                    [[:taulukko
                      {:otsikko "Toimenpiteiden ajoittuminen"
                       :rivi-ennen [{:sarakkeita 1
                                     :teksti "Hoi­to­luok­ka"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "Is"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "I"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "Ib"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "TIb"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "II"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "III"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "K1"}
                                     {:sarakkeita 6
                                      :tasaa :keskita
                                      :teksti "K2"}
                                     {:sarakkeita 1
                                      :teksti ""}]}
                      [{:leveys 12
                        :otsikko "Teh­tä­vä"}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "< 6"
                        :reunus :vasen
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "6 - 10"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "10 - 14"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "14 - 18"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "18 - 22"
                        :reunus :ei
                        :tasaa :keskita}
                       {:leveys 83/48
                        :otsikko "22 - 02"
                        :reunus :oikea
                        :tasaa :keskita}
                       {:leveys 5
                        :otsikko "Yht."
                        :tasaa :oikea}]
                      []]]]))))
