(ns harja.palvelin.raportointi.yksikkohintaiset-tyot-tehtavittain-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.urakat :as urk-q]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-tehtavittain :as raportti]
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
                                {:nimi       :yks-hint-tehtavien-summat
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2005 10 10))
                                              :loppupvm (c/to-date (t/local-date 2010 10 10))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi        "Yksikköhintaiset työt tehtävittäin"
                     :orientaatio :landscape}
                    [:taulukko
                     {:oikealle-tasattavat-kentat #{2
                                                    5
                                                    6}
                      :otsikko                    "Oulun alueurakka 2005-2012, Yksikköhintaiset työt tehtävittäin ajalta 10.10.2005 - 10.10.2010"
                      :sheet-nimi                 "Yksikköhintaiset työt tehtävittäin"
                      :tyhja                      nil
                      :viimeinen-rivi-yhteenveto? true}
                     '({:leveys  25
                        :otsikko "Tehtävä"}
                        {:leveys  5
                         :otsikko "Yks."}
                        {:fmt     :raha
                         :leveys  10
                         :otsikko "Yksikkö­hinta €"}
                        {:fmt     :numero
                         :leveys  10
                         :otsikko "Suunniteltu määrä hoitokaudella"}
                        {:fmt     :numero
                         :leveys  10
                         :otsikko "Toteutunut määrä"}
                        {:leveys  15
                         :otsikko "Suunnitellut kustannukset hoitokaudella €"
                         :fmt :raha}
                        {:leveys  15
                         :otsikko "Toteutuneet kustannukset €"
                         :fmt :raha})
                     '(("Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen"
                         [:info
                          ""]
                         [:info
                          ""]
                         [:info
                          "Ei suunnitelmaa"]
                         667M
                         [:info
                          ""]
                         [:info
                          ""])
                        ("Pensaiden täydennysistutus"
                          [:info
                           ""]
                          [:info
                           ""]
                          [:info
                           "Ei suunnitelmaa"]
                          668M
                          [:info
                           ""]
                          [:info
                           ""])
                        ["Yhteensä"
                         nil
                         nil
                         nil
                         nil
                         0
                         0])]
                    [:teksti
                     "Suunnittelutiedot näytetään vain haettaessa urakan tiedot hoitokaudelta tai sen osalta."]]))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :yks-hint-tehtavien-summat
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2005 10 10))
                                                      :loppupvm     (c/to-date (t/local-date 2010 10 10))
                                                      :urakkatyyppi "hoito"}})]
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
                                              :urakkatyyppi "hoito"}})]
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