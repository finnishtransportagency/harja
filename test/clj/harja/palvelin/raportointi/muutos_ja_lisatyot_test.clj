(ns harja.palvelin.raportointi.muutos-ja-lisatyot-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.testiapurit :as apurit]
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


(deftest raportin-suoritus-hallintayksikolle-hoitokausi
  (let [vastaus-kaikki-tyotyypit (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :suorita-raportti
                                   +kayttaja-jvh+
                                   {:nimi :muutos-ja-lisatyot
                                    :konteksti "elinvoimakeskus"
                                    :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
                                    :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                                 :loppupvm (c/to-date (t/local-date 2015 9 30))
                                                 :urakkatyyppi :hoito
                                                 :toimenpide-id nil
                                                 :muutostyotyyppi nil}})
        vastaus-kaikki-lisatyot (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :suorita-raportti
                                  +kayttaja-jvh+
                                  {:nimi :muutos-ja-lisatyot
                                   :konteksti "elinvoimakeskus"
                                   :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
                                   :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                                :loppupvm (c/to-date (t/local-date 2015 9 30))
                                                :urakkatyyppi :hoito
                                                :toimenpide-id nil
                                                :muutostyotyyppi :lisatyo}})]
    (is (vector? vastaus-kaikki-tyotyypit))
    (let [otsikko-kaikki-tyotyypit "Pohjois-Suomi, Muutos- ja lisätöiden raportti, kaikki työtyypit ajalta 01.10.2014 - 30.09.2015, Toimenpide: kaikki"
          taulukko-kaikki-tyotyypit (apurit/taulukko-otsikolla vastaus-kaikki-tyotyypit otsikko-kaikki-tyotyypit)
          nurkkasumman-teksti-kaikki-tyotyypit (last (last vastaus-kaikki-tyotyypit))
          otsikko-kaikki-lisatyot "Pohjois-Suomi, Lisätöiden raportti ajalta 01.10.2014 - 30.09.2015, Toimenpide: kaikki"
          taulukko-kaikki-lisatyot (apurit/taulukko-otsikolla vastaus-kaikki-lisatyot otsikko-kaikki-lisatyot)
          nurkkasumman-teksti-kaikki-lisatyot (last (last vastaus-kaikki-lisatyot))]
      (is (= "Summat ja indeksit yhteensä 16 112,07 €" nurkkasumman-teksti-kaikki-tyotyypit) "nurkkasumman teksti")
      (is (= "Summat ja indeksit yhteensä 5 017,24 €" nurkkasumman-teksti-kaikki-lisatyot) "nurkkasumman teksti")
      (apurit/tarkista-taulukko-sarakkeet taulukko-kaikki-tyotyypit
        {:otsikko "Tyyppi"}
        {:otsikko "Summa €"}
        {:otsikko "Ind.korotus €"})
      (apurit/tarkista-taulukko-sarakkeet taulukko-kaikki-lisatyot
        {:otsikko "Tyyppi"}
        {:otsikko "Summa €"}
        {:otsikko "Ind.korotus €"}))))


(deftest raportin-suoritus-kokomaa-hoitokausi
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :muutos-ja-lisatyot
                   :konteksti "koko maa"
                   :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                :loppupvm (c/to-date (t/local-date 2015 9 30))
                                :urakkatyyppi :hoito
                                :urakoittain? true}})
        nurkkasumman-teksti (last (last vastaus))]
    (is (vector? vastaus))
    (is (= "Summat ja indeksit yhteensä 47 458,39 €" nurkkasumman-teksti) "nurkkasumman teksti")
    (let [otsikko "KOKO MAA, Muutos- ja lisätöiden raportti ajalta 01.10.2014 - 30.09.2015, Kaikki toimenpiteet"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
        {:otsikko "Urakka"}
        {:otsikko "Pvm"}
        {:otsikko "Tyyppi"}
        {:otsikko "Toimenpide"}
        {:otsikko "Tehtävä"}
        {:otsikko "Lisätieto"}
        {:otsikko "Määrä"}
        {:otsikko "Summa €"}
        {:otsikko "Ind.korotus €"}))))

(deftest raportin-suoritus-kokomaa-hoitokausi
  (let [vastaus-kaikki-tyotyypit (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :suorita-raportti
                                   +kayttaja-jvh+
                                   {:nimi :muutos-ja-lisatyot
                                    :konteksti "koko maa"
                                    :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                                 :loppupvm (c/to-date (t/local-date 2015 9 30))
                                                 :urakkatyyppi :hoito
                                                 :urakoittain? false
                                                 :toimenpide-id nil
                                                 :muutostyotyyppi nil}})
        vastaus-kaikki-vahinkojen-korjaukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :suorita-raportti
                                               +kayttaja-jvh+
                                               {:nimi :muutos-ja-lisatyot
                                                :konteksti "koko maa"
                                                :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                                             :loppupvm (c/to-date (t/local-date 2015 9 30))
                                                             :urakkatyyppi :hoito
                                                             :urakoittain? false
                                                             :toimenpide-id nil
                                                             :muutostyotyyppi :vahinkojen-korjaukset}})
        nurkkasumman-teksti-kaikki-tyotyypit (last (last vastaus-kaikki-tyotyypit))
        nurkkasumman-teksti-kaikki-vahinkojen-korjaukset (last (last vastaus-kaikki-vahinkojen-korjaukset))]
    (is (vector? vastaus-kaikki-tyotyypit))
    (is (= "Summat ja indeksit yhteensä 47 458,39 €" nurkkasumman-teksti-kaikki-tyotyypit) "nurkkasumman teksti")
    (is (= "Summat ja indeksit yhteensä 2 926,17 €" nurkkasumman-teksti-kaikki-vahinkojen-korjaukset) "nurkkasumman teksti")
    (let [otsikko-kaikki-tyotyypit "KOKO MAA, Muutos- ja lisätöiden raportti, kaikki työtyypit ajalta 01.10.2014 - 30.09.2015, Toimenpide: kaikki"
          taulukko-kaikki-tyotyypit (apurit/taulukko-otsikolla vastaus-kaikki-tyotyypit otsikko-kaikki-tyotyypit)
          otsikko-kaikki-vahinkojen-korjaukset "KOKO MAA, Vahinkojen korjausten raportti ajalta 01.10.2014 - 30.09.2015, Toimenpide: kaikki"
          taulukko-kaikki-vahinkojen-korjaukset (apurit/taulukko-otsikolla vastaus-kaikki-vahinkojen-korjaukset otsikko-kaikki-vahinkojen-korjaukset)]
      (apurit/tarkista-taulukko-sarakkeet taulukko-kaikki-tyotyypit
        {:otsikko "Tyyppi"}
        {:otsikko "Summa €"}
        {:otsikko "Ind.korotus €"})
      (apurit/tarkista-taulukko-sarakkeet taulukko-kaikki-tyotyypit
        {:otsikko "Tyyppi"}
        {:otsikko "Summa €"}
        {:otsikko "Ind.korotus €"}))))
