(ns harja.palvelin.raportointi.indeksitarkastus-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi :refer :all]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
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

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :indeksitarkistus
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 1 1))
                                              :loppupvm (c/to-date (t/local-date 2015 12 31))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Indeksitarkistus")

    ;; Kaikki yhteensä
    (let [otsikko "Kaikki yhteensä"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Kuukausi"}
                                          {:otsikko "Kokonaishintaiset työt"}
                                          {:otsikko "Yksikköhintaiset työt"}
                                          {:otsikko "Äkillinen hoitotyö"}
                                          {:otsikko "Sanktiot"}
                                          {:otsikko "Suolabonukset ja -sanktiot"}
                                          {:otsikko "Yhteensä (€)"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                             (fn [[kuukausi kok-hint yks-hint akilliset-hoitotyot sanktiot
                                                   suolabonus yhteensa :as rivi]]
                                               (and (= (count rivi) 7)
                                                    (string? kuukausi)
                                                    (number? kok-hint)
                                                    (number? yks-hint)
                                                    (number? akilliset-hoitotyot)
                                                    (number? sanktiot)
                                                    (number? suolabonus)
                                                    (number? yhteensa)))))

    ;; Talvihoito
    (let [otsikko "Talvihoito"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Kuukausi"}
                                          {:otsikko "Kokonaishintaiset työt"}
                                          {:otsikko "Yksikköhintaiset työt"}
                                          {:otsikko "Äkillinen hoitotyö"}
                                          {:otsikko "Sanktiot"}
                                          {:otsikko "Suolabonukset ja -sanktiot"}
                                          {:otsikko "Yhteensä (€)"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                             (fn [[kuukausi kok-hint yks-hint akilliset-hoitotyot sanktiot
                                                   suolabonus yhteensa :as rivi]]
                                               (and (= (count rivi) 7)
                                                    (string? kuukausi)
                                                    (number? kok-hint)
                                                    (number? yks-hint)
                                                    (number? akilliset-hoitotyot)
                                                    (number? sanktiot)
                                                    (number? suolabonus)
                                                    (number? yhteensa)))))

    ;; Liikenneympäristön hoito
    (let [otsikko "Liikenneympäristön hoito"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Kuukausi"}
                                          {:otsikko "Kokonaishintaiset työt"}
                                          {:otsikko "Yksikköhintaiset työt"}
                                          {:otsikko "Äkillinen hoitotyö"}
                                          {:otsikko "Sanktiot"}
                                          {:otsikko "Yhteensä (€)"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                             (fn [[kuukausi kok-hint yks-hint akilliset-hoitotyot sanktiot
                                                   yhteensa :as rivi]]
                                               (and (= (count rivi) 6)
                                                    (string? kuukausi)
                                                    (number? kok-hint)
                                                    (number? yks-hint)
                                                    (number? akilliset-hoitotyot)
                                                    (number? sanktiot)
                                                    (number? yhteensa)))))

    ;; Soratien hoito
    (let [otsikko "Soratien hoito"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Kuukausi"}
                                          {:otsikko "Kokonaishintaiset työt"}
                                          {:otsikko "Yksikköhintaiset työt"}
                                          {:otsikko "Äkillinen hoitotyö"}
                                          {:otsikko "Sanktiot"}
                                          {:otsikko "Yhteensä (€)"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                             (fn [[kuukausi kok-hint yks-hint akilliset-hoitotyot sanktiot
                                                   yhteensa :as rivi]]
                                               (and (= (count rivi) 6)
                                                    (string? kuukausi)
                                                    (number? kok-hint)
                                                    (number? yks-hint)
                                                    (number? akilliset-hoitotyot)
                                                    (number? sanktiot)
                                                    (number? yhteensa)))))))