(ns harja.palvelin.raportointi.kanavien-muutos-ja-lisatyot-test
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
            [harja.palvelin.raportointi.raportit :refer :all]
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

(deftest raportin-suoritus-urakka-hoitokausi
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kanavien-muutos-ja-lisatyot
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-saimaan-kanavaurakan-id)
                                 :kohde-id  nil
                                 :tehtava-id  nil
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2018 1 1))
                                              :loppupvm (c/to-date (t/local-date 2018 12 31))}})]

    (is (vector? vastaus))
    ;(let [otsikko "Saimaan kanava, Kaikki kohteet"
    ;      taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
    ;  (apurit/tarkista-taulukko-sarakkeet taulukko
    ;                                      {:otsikko "Pvm"}
    ;                                      {:otsikko "Tyyppi"}
    ;                                      {:otsikko "Toimenpide"}
    ;                                      {:otsikko "Tehtävä"}
    ;                                      {:otsikko "Lisätieto"}
    ;                                      {:otsikko "Määrä"}
    ;                                      {:otsikko "Summa €"}
    ;                                      {:otsikko "Ind.korotus €"}))
    ))
