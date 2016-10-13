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

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :muutos-ja-lisatyot
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 9 30))}})
        nurkkasumma (last (last (last (last vastaus))))]
    (is (vector? vastaus))
    (is (=marginaalissa? nurkkasumma 16000.00))
    (let [otsikko "Oulun alueurakka 2014-2019, Muutos- ja lisätöiden raportti ajalta 01.10.2014 - 30.09.2015, Kaikki toimenpiteet"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Pvm"}
                                          {:otsikko "Tyyppi"}
                                          {:otsikko "Sop. nro"}
                                          {:otsikko "Toimenpide"}
                                          {:otsikko "Tehtävä"}
                                          {:otsikko "Määrä"}
                                          {:otsikko "Summa €"}
                                          {:otsikko "Ind.korotus €"}))))


(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :muutos-ja-lisatyot
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2014 10 1))
                                                      :loppupvm     (c/to-date (t/local-date 2015 9 30))
                                                      :urakkatyyppi "hoito"}})
        nurkkasumma (last (last (last (last vastaus))))]
    (is (vector? vastaus))
    (is (=marginaalissa? nurkkasumma 32000.00))
    (let [otsikko "Pohjois-Pohjanmaa ja Kainuu, Muutos- ja lisätöiden raportti ajalta 01.10.2014 - 30.09.2015, Kaikki toimenpiteet"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Urakka"}
                                          {:otsikko "Pvm"}
                                          {:otsikko "Tyyppi"}
                                          {:otsikko "Sop. nro"}
                                          {:otsikko "Toimenpide"}
                                          {:otsikko "Tehtävä"}
                                          {:otsikko "Määrä"}
                                          {:otsikko "Summa €"}
                                          {:otsikko "Ind.korotus €"}))))


(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :muutos-ja-lisatyot
                                 :konteksti          "koko maa"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2014 10 1))
                                                      :loppupvm     (c/to-date (t/local-date 2015 9 30))
                                                      :urakkatyyppi "hoito"}})
        nurkkasumma (last (last (last (last vastaus))))]
    (is (vector? vastaus))
    (is (=marginaalissa? nurkkasumma 64000.00))
    (let [otsikko "KOKO MAA, Muutos- ja lisätöiden raportti ajalta 01.10.2014 - 30.09.2015, Kaikki toimenpiteet"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Urakka"}
                                          {:otsikko "Pvm"}
                                          {:otsikko "Tyyppi"}
                                          {:otsikko "Sop. nro"}
                                          {:otsikko "Toimenpide"}
                                          {:otsikko "Tehtävä"}
                                          {:otsikko "Määrä"}
                                          {:otsikko "Summa €"}
                                          {:otsikko "Ind.korotus €"}))))
