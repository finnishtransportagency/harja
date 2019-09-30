(ns harja.palvelin.raportointi.yksikkohintaiset-tyot-tehtavittain-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
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
                                {:nimi       :yks-hint-tehtavien-summat
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2005 10 10))
                                              :loppupvm (c/to-date (t/local-date 2010 10 10))}})
        taulukko (apurit/taulukko-otsikolla vastaus "Oulun alueurakka 2005-2012, Yksikköhintaiset työt tehtävittäin ajalta 10.10.2005 - 10.10.2010")
        tyhja-raporttisolu? (fn [solu]
                              )]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Yksikköhintaiset työt tehtävittäin")
    (apurit/tarkista-taulukko-rivit
      taulukko
      (fn [[tehtava yksikko hinta suunniteltu-maara toteutunut-maara suunnitellut-kustannukset
            toteutuneet-kustannukset]]
        (and (string? (apurit/raporttisolun-arvo tehtava))
             (apurit/tyhja-raporttisolu? yksikko)
             (apurit/tyhja-raporttisolu? hinta)
             (and (apurit/raporttisolu? suunniteltu-maara) (string? (apurit/raporttisolun-arvo suunniteltu-maara)))
             (number? toteutunut-maara)
             (apurit/tyhja-raporttisolu? suunnitellut-kustannukset)
             (apurit/tyhja-raporttisolu? toteutuneet-kustannukset)))
      (fn [[tehtava yksikko hinta suunniteltu-maara toteutunut-maara suunnitellut-kustannukset
            toteutuneet-kustannukset]]
        (and (string? (apurit/raporttisolun-arvo tehtava))
             (apurit/tyhja-raporttisolu? yksikko)
             (apurit/tyhja-raporttisolu? hinta)
             (and (apurit/raporttisolu? suunniteltu-maara) (string? (apurit/raporttisolun-arvo suunniteltu-maara)))
             (number? toteutunut-maara)
             (apurit/tyhja-raporttisolu? suunnitellut-kustannukset)
             (apurit/tyhja-raporttisolu? toteutuneet-kustannukset)))
      (fn [rivi]
        (let [yhteenveto-otsikko (first rivi)
              suunnitellut-yhteensa (nth rivi 5)
              toteutuneet-yhteensa (last rivi)
              loput (take 4 (rest rivi))]
          (and (string? yhteenveto-otsikko)
               (number? suunnitellut-yhteensa)
               (number? toteutuneet-yhteensa)
               (every? nil? loput)))))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :yks-hint-tehtavien-summat
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2005 10 10))
                                                      :loppupvm     (c/to-date (t/local-date 2010 10 10))
                                                      :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Yksikköhintaiset työt tehtävittäin"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{}
                      :otsikko                    "Pohjois-Pohjanmaa, Yksikköhintaiset työt tehtävittäin ajalta 10.10.2005 - 10.10.2010"
                      :sheet-nimi                 "Yksikköhintaiset työt tehtävittäin"
                      :tyhja                      nil
                      :viimeinen-rivi-yhteenveto? true}
                     '({:leveys  25
                       :otsikko "Tehtävä"}
                       {:leveys  5
                        :otsikko "Yks."}
                       {:fmt     :numero
                        :leveys  10
                        :otsikko "Toteutunut määrä"})
                     '(("Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen"
                         "m2"
                         667M)
                        ("Pensaiden täydennysistutus"
                          "m2"
                          668M)
                        ("Yhteensä"
                          nil
                          1335M))]
                    nil]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :yks-hint-tehtavien-summat
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2005 10 10))
                                              :loppupvm     (c/to-date (t/local-date 2010 10 10))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Yksikköhintaiset työt tehtävittäin"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{}
                      :otsikko                    "KOKO MAA, Yksikköhintaiset työt tehtävittäin ajalta 10.10.2005 - 10.10.2010"
                      :sheet-nimi                 "Yksikköhintaiset työt tehtävittäin"
                      :tyhja                      nil
                      :viimeinen-rivi-yhteenveto? true}
                     '({:leveys  25
                       :otsikko "Tehtävä"}
                       {:leveys  5
                        :otsikko "Yks."}
                       {:fmt     :numero
                        :leveys  10
                        :otsikko "Toteutunut määrä"})
                     '(("Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen"
                         "m2"
                         667M)
                        ("Pensaiden täydennysistutus"
                          "m2"
                          668M)
                        ("Yhteensä"
                          nil
                          1335M))]
                    nil]))))