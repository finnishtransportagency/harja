(ns harja.palvelin.raportointi.siltatarkastusraportti-test
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

(deftest raportin-suoritus-sillalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:vuosi 2007
                                              :silta-id 1}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Siltatarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko                    "Siltatarkastusraportti, Oulun alueurakka 2005-2012, Oulujoen silta (O-00001), 2007"
                      :sheet-nimi                 "Siltatarkastusraportti"
                      :tyhja                      "Sillalle ei ole tehty tarkastusta valittuna vuonna."
                      :viimeinen-rivi-yhteenveto? false}
                     [{:leveys  2
                       :otsikko "#"}
                      {:leveys  15
                       :otsikko "Kohde"}
                      {:leveys  2
                       :otsikko "Tulos"}
                      {:leveys  10
                       :otsikko "Lisätieto"}
                      {:leveys  5
                       :otsikko "Liitteet"
                       :tyyppi  :liite}]
                     [{:korosta? false
                       :otsikko  "Aluerakenne"
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [1
                                  "Maatukien siisteys ja kunto"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [2
                                  "Välitukien siisteys ja kunto"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [3
                                  "Laakeritasojen siisteys ja kunto"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :otsikko  "Päällysrakenne"
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [4
                                  "Kansilaatta"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [5
                                  "Päällysteen kunto"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [6
                                  "Reunapalkin siisteys ja kunto"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [7
                                  "Reunapalkin liikuntasauma"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [8
                                  "Reunapalkin ja päälllysteen välisen sauman siisteys ja kunto"
                                  "B"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [9
                                  "Sillanpäiden saumat"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [10
                                  "Sillan ja penkereen raja"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :otsikko  "Varusteet ja laitteet"
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [11
                                  "Kaiteiden ja suojaverkkojen vauriot"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [12
                                  "Liikuntasaumakaitteiden siisteys ja kunto"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [13
                                  "Laakerit"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [14
                                  "Syöksytorvet"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [15
                                  "Tippuputket"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [16
                                  "Kosketussuojat ja niiden kiinnitykset"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [17
                                  "Valaistuslaitteet"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? true
                       :rivi     [18
                                  "Johdot ja kaapelit"
                                  "D"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   true}
                      {:korosta? false
                       :rivi     [19
                                  "Liikennemerkit"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :otsikko  "Siltapaikan rakenteet"
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [20
                                  "Kuivatuslaitteiden siisteys ja kunto"
                                  "C"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [21
                                  "Etuluiskien siisteys ja kunto"
                                  "B"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [22
                                  "Keilojen siisteys ja kunto"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [23
                                  "Tieluiskien siisteys ja kunto"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     [24
                                  "Portaiden siisteys ja kunto"
                                  "A"
                                  nil
                                  [:liitteet
                                   []]]
                       :virhe?   false}]]
                    [:yhteenveto
                     [["Tarkastaja"
                       "Sirkka Sillankoestaja"]
                      ["Tarkastettu"
                       "25.02.2007"]]]]))))

(deftest raportin-suoritus-urakan-kaikille-silloille-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:vuosi 2007
                                              :silta-id :kaikki}})]
    (is (vector? vastaus))
    (is (= 4 (count vastaus)))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:vuosi 2007}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Siltatarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko                    "Siltatarkastusraportti, Pohjois-Pohjanmaa ja Kainuu 2007"
                      :sheet-nimi                 "Siltatarkastusraportti"
                      :tyhja                      "Ei raportoitavia siltatarkastuksia."
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys  10
                       :otsikko "Urakka"}
                      {:leveys  5
                       :otsikko "A"
                       :tyyppi  :arvo-ja-osuus}
                      {:leveys  5
                       :otsikko "B"
                       :tyyppi  :arvo-ja-osuus}
                      {:leveys  5
                       :otsikko "C"
                       :tyyppi  :arvo-ja-osuus}
                      {:leveys  5
                       :otsikko "D"
                       :tyyppi  :arvo-ja-osuus}]
                     [{:korosta? false
                       :rivi     ["Kempeleen valaistusurakka"
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]]
                       :virhe?   false}
                      {:korosta? false
                       :rivi     ["Muhoksen paikkausurakka"
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]]
                       :virhe?   false}
                      {:korosta? true
                       :rivi     ["Oulun alueurakka 2005-2012"
                                  [:arvo-ja-osuus
                                   {:arvo  40
                                    :osuus 83}]
                                  [:arvo-ja-osuus
                                   {:arvo  4
                                    :osuus 8}]
                                  [:arvo-ja-osuus
                                   {:arvo  2
                                    :osuus 4}]
                                  [:arvo-ja-osuus
                                   {:arvo  2
                                    :osuus 4}]]
                       :virhe?   true}
                      {:korosta? false
                       :rivi     ["Pudasjärven alueurakka 2007-2012"
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]
                                  [:arvo-ja-osuus
                                   {:arvo  0
                                    :osuus 0}]]
                       :virhe?   false}
                      ["Yhteensä"
                       [:arvo-ja-osuus
                        {:arvo  40
                         :osuus 83}]
                       [:arvo-ja-osuus
                        {:arvo  4
                         :osuus 8}]
                       [:arvo-ja-osuus
                        {:arvo  2
                         :osuus 4}]
                       [:arvo-ja-osuus
                        {:arvo  2
                         :osuus 4}]]]]
                    nil]))))

