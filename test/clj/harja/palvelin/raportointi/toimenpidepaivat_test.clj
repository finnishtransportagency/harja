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
                                {:nimi :toimenpidepaivat
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2005 10 1))
                                              :loppupvm (c/to-date (t/local-date 2006 10 1))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Monenako päivänä toimenpidettä on tehty aikavälillä"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Toimenpidepäivät aikavälillä 01.10.2005 - 01.10.2006 (365 päivää)"
                      :rivi-ennen [{:sarakkeita 1
                                    :teksti "Alueet"}
                                   {:sarakkeita 8
                                    :teksti "Oulun alueurakka 2005-2012"}]}
                     [{:otsikko "Teh­tä­vä"}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}]
                     [["Uuden radan rakentaminen, päällysrakenne"
                       0
                       2
                       0
                       0
                       0
                       0
                       0
                       0]]]]))))


(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :toimenpidepaivat
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2005 10 1))
                                              :loppupvm (c/to-date (t/local-date 2006 10 1))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Monenako päivänä toimenpidettä on tehty aikavälillä"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Toimenpidepäivät aikavälillä 01.10.2005 - 01.10.2006 (365 päivää)"
                      :rivi-ennen [{:sarakkeita 1
                                    :teksti "Alueet"}
                                   {:sarakkeita 8
                                    :teksti "Oulun alueurakka 2005-2012"}]}
                     [{:otsikko "Teh­tä­vä"}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}]
                     [["Uuden radan rakentaminen, päällysrakenne"
                       0
                       2
                       0
                       0
                       0
                       0
                       0
                       0]]]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :toimenpidepaivat
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2005 1 1))
                                              :loppupvm (c/to-date (t/local-date 2006 12 31))
                                              :hoitoluokat #{1 2 3 4 5 6 8 7}
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Monenako päivänä toimenpidettä on tehty aikavälillä"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Toimenpidepäivät aikavälillä 01.01.2005 - 31.12.2006 (729 päivää)"
                      :rivi-ennen [{:sarakkeita 1
                                    :teksti "Alueet"}
                                   {:sarakkeita 8
                                    :teksti "01 Uusimaa"}
                                   {:sarakkeita 8
                                    :teksti "02 Varsinais-Suomi"}
                                   {:sarakkeita 8
                                    :teksti "03 Kaakkois-Suomi"}
                                   {:sarakkeita 8
                                    :teksti "04 Pirkanmaa"}
                                   {:sarakkeita 8
                                    :teksti "08 Pohjois-Savo"}
                                   {:sarakkeita 8
                                    :teksti "09 Keski-Suomi"}
                                   {:sarakkeita 8
                                    :teksti "10 Etelä-Pohjanmaa"}
                                   {:sarakkeita 8
                                    :teksti "12 Pohjois-Pohjanmaa ja Kainuu"}
                                   {:sarakkeita 8
                                    :teksti "14 Lappi"}]}
                     [{:otsikko "Teh­tä­vä"}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}
                      {:otsikko "Is"
                       :tasaa :oikea}
                      {:otsikko "I"
                       :tasaa :oikea}
                      {:otsikko "Ib"
                       :tasaa :oikea}
                      {:otsikko "TIb"
                       :tasaa :oikea}
                      {:otsikko "II"
                       :tasaa :oikea}
                      {:otsikko "III"
                       :tasaa :oikea}
                      {:otsikko "K1"
                       :tasaa :oikea}
                      {:otsikko "K2"
                       :tasaa :oikea}]
                     [["Uuden radan rakentaminen, päällysrakenne"
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       2
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0
                       0]]]]))))
