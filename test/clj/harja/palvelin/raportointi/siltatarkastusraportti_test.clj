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
                    {:nimi "Siltatarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:korosta-rivit #{20}
                      :otsikko "Siltatarkastusraportti, Oulun alueurakka 2005-2012, Oulujoen silta (O-00001), 2007"
                      :sheet-nimi "Siltatarkastusraportti"
                      :tyhja "Sillalle ei ole tehty tarkastusta valittuna vuonna."
                      :viimeinen-rivi-yhteenveto? false}
                     [{:leveys 2
                       :otsikko "#"}
                      {:leveys 15
                       :otsikko "Kohde"}
                      {:leveys 2
                       :otsikko "Tulos"}
                      {:leveys 10
                       :otsikko "Lisätieto"}
                      {:leveys 5
                       :otsikko "Liitteet"
                       :tyyppi :liite}]
                     [{:otsikko "Aluerakenne"}
                      [1
                       "Maatukien siisteys ja kunto"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [2
                       "Välitukien siisteys ja kunto"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [3
                       "Laakeritasojen siisteys ja kunto"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      {:otsikko "Päällysrakenne"}
                      [4
                       "Kansilaatta"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [5
                       "Päällysteen kunto"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [6
                       "Reunapalkin siisteys ja kunto"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [7
                       "Reunapalkin liikuntasauma"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [8
                       "Reunapalkin ja päälllysteen välisen sauman siisteys ja kunto"
                       "B"
                       nil
                       [:liitteet
                        []]]
                      [9
                       "Sillanpäiden saumat"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [10
                       "Sillan ja penkereen raja"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      {:otsikko "Varusteet ja laitteet"}
                      [11
                       "Kaiteiden ja suojaverkkojen vauriot"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [12
                       "Liikuntasaumakaitteiden siisteys ja kunto"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [13
                       "Laakerit"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [14
                       "Syöksytorvet"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [15
                       "Tippuputket"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [16
                       "Kosketussuojat ja niiden kiinnitykset"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [17
                       "Valaistuslaitteet"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [18
                       "Johdot ja kaapelit"
                       "D"
                       nil
                       [:liitteet
                        []]]
                      [19
                       "Liikennemerkit"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      {:otsikko "Siltapaikan rakenteet"}
                      [20
                       "Kuivatuslaitteiden siisteys ja kunto"
                       "C"
                       nil
                       [:liitteet
                        []]]
                      [21
                       "Etuluiskien siisteys ja kunto"
                       "B"
                       nil
                       [:liitteet
                        []]]
                      [22
                       "Keilojen siisteys ja kunto"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [23
                       "Tieluiskien siisteys ja kunto"
                       "A"
                       nil
                       [:liitteet
                        []]]
                      [24
                       "Portaiden siisteys ja kunto"
                       "A"
                       nil
                       [:liitteet
                        []]]]]
                    [:yhteenveto
                     [["Tarkastaja"
                       "Sirkka Sillankoestaja"]
                      ["Tarkastettu"
                       "25.02.2007"]]]]))))

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:vuosi 2007
                                              :silta-id :kaikki}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Siltatarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:korosta-rivit #{0
                                       1}
                      :otsikko "Siltatarkastusraportti, Oulun alueurakka 2005-2012 vuodelta 2007"
                      :sheet-nimi "Siltatarkastusraportti"
                      :tyhja "Sillalle ei ole tehty tarkastusta valittuna vuonna."
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 5
                       :otsikko "Siltanumero"}
                      {:leveys 10
                       :otsikko "Silta"}
                      {:leveys 5
                       :otsikko "Tarkastettu"}
                      {:leveys 5
                       :otsikko "Tarkastaja"}
                      {:leveys 5
                       :otsikko "A"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 5
                       :otsikko "B"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 5
                       :otsikko "C"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 5
                       :otsikko "D"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 5
                       :otsikko "Liitteet"
                       :tyyppi :liite}]
                     [[902
                       "Pyhäjoen silta"
                       "05.05.2007"
                       "Mari Mittatarkka"
                       [:arvo-ja-osuus
                        {:arvo 20
                         :osuus 83}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 8}]
                       [:arvo-ja-osuus
                        {:arvo 1
                         :osuus 4}]
                       [:arvo-ja-osuus
                        {:arvo 1
                         :osuus 4}]
                       [:liitteet
                        []]]
                      [1537
                       "Oulujoen silta"
                       "25.02.2007"
                       "Sirkka Sillankoestaja"
                       [:arvo-ja-osuus
                        {:arvo 20
                         :osuus 83}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 8}]
                       [:arvo-ja-osuus
                        {:arvo 1
                         :osuus 4}]
                       [:arvo-ja-osuus
                        {:arvo 1
                         :osuus 4}]
                       [:liitteet
                        []]]
                      [6666
                       "Joutsensilta"
                       "Tarkastamatta"
                       "-"
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
                        {:arvo 0
                         :osuus 0}]
                       [:liitteet
                        []]]
                      [7777
                       "Kajaanintien silta"
                       "Tarkastamatta"
                       "-"
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
                        {:arvo 0
                         :osuus 0}]
                       [:liitteet
                        []]]
                      [325235
                       "Kempeleen testisilta"
                       "Tarkastamatta"
                       "-"
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
                        {:arvo 0
                         :osuus 0}]
                       [:liitteet
                        []]]
                      ["Yhteensä"
                       ""
                       ""
                       ""
                       [:arvo-ja-osuus
                        {:arvo 40
                         :osuus 83}]
                       [:arvo-ja-osuus
                        {:arvo 4
                         :osuus 8}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 4}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 4}]
                       [:liitteet
                        nil]]]]
                    nil]))))

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:vuosi 2007}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Siltatarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:korosta-rivit #{}
                      :otsikko "Siltatarkastusraportti, Pohjois-Pohjanmaa 2007"
                      :sheet-nimi "Siltatarkastusraportti"
                      :tyhja "Ei raportoitavia siltatarkastuksia."
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 10
                       :otsikko "Urakka"}
                      {:leveys 5
                       :otsikko "A"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 5
                       :otsikko "B"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 5
                       :otsikko "C"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 5
                       :otsikko "D"
                       :tyyppi :arvo-ja-osuus}]
                     [["Kempeleen valaistusurakka"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Muhoksen paikkausurakka"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Oulun alueurakka 2005-2012"
                       [:arvo-ja-osuus
                        {:arvo 40
                         :osuus 83}]
                       [:arvo-ja-osuus
                        {:arvo 4
                         :osuus 8}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 4}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 4}]]
                      ["Pudasjärven alueurakka 2007-2012"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Yhteensä"
                       [:arvo-ja-osuus
                        {:arvo 40
                         :osuus 83}]
                       [:arvo-ja-osuus
                        {:arvo 4
                         :osuus 8}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 4}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 4}]]]]
                    nil]))))

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "koko maa"
                                 :parametrit {:vuosi 2007}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Siltatarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:korosta-rivit #{}
                      :otsikko "Siltatarkastusraportti, KOKO MAA 2007"
                      :sheet-nimi "Siltatarkastusraportti"
                      :tyhja "Ei raportoitavia siltatarkastuksia."
                      :viimeinen-rivi-yhteenveto? true}
                     [{:leveys 10
                       :otsikko "Hallintayksikkö"}
                      {:leveys 5
                       :otsikko "A"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 5
                       :otsikko "B"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 5
                       :otsikko "C"
                       :tyyppi :arvo-ja-osuus}
                      {:leveys 5
                       :otsikko "D"
                       :tyyppi :arvo-ja-osuus}]
                     [["Uusimaa"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Varsinais-Suomi"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Kaakkois-Suomi"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Pirkanmaa"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Pohjois-Savo"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Keski-Suomi"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Etelä-Pohjanmaa"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Pohjois-Pohjanmaa ja Kainuu"
                       [:arvo-ja-osuus
                        {:arvo 40
                         :osuus 83}]
                       [:arvo-ja-osuus
                        {:arvo 4
                         :osuus 8}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 4}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 4}]]
                      ["Lappi"
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
                        {:arvo 0
                         :osuus 0}]]
                      ["Yhteensä"
                       [:arvo-ja-osuus
                        {:arvo 40
                         :osuus 83}]
                       [:arvo-ja-osuus
                        {:arvo 4
                         :osuus 8}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 4}]
                       [:arvo-ja-osuus
                        {:arvo 2
                         :osuus 4}]]]]
                    nil]))))