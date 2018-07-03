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

;; TODO: testiaineistossa ei ole muutos- ja lisätöihin soveltuvia tapauksia.
;; Muutos- ja lisätyönä tallennettu toteuma on linkattu toimenpidekoodiin, joka on relevantti vain kokonaishintaisille.

(deftest raportin-suoritus-urakka
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kanavien-muutos-ja-lisatyot
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-saimaan-kanavaurakan-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2017 1 1))
                                              :loppupvm (c/to-date (t/local-date 2017 12 31))
                                              :urakka-id  (hae-saimaan-kanavaurakan-id)
                                              :kohde-id  nil
                                              :tehtava-id  nil}})]
    (is (vector? vastaus))))

(deftest raportin-suorius-urakka-kohde
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kanavien-muutos-ja-lisatyot
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-saimaan-kanavaurakan-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2016 8 1))
                                              :loppupvm (c/to-date (t/local-date 2017 4 1))
                                              :urakka-id  (hae-saimaan-kanavaurakan-id)
                                              :kohde-id  (hae-kohde-palli)
                                              :tehtava-id  nil}})]
    (is (vector? vastaus))))

(deftest raportin-suoritus-urakka-tehtava
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kanavien-muutos-ja-lisatyot
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-saimaan-kanavaurakan-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2017 1 1))
                                              :loppupvm (c/to-date (t/local-date 2017 12 31))
                                              :urakka-id  (hae-saimaan-kanavaurakan-id)
                                              :kohde-id  nil
                                              :tehtava-id  3058}})]

    (is (vector? vastaus))))

(deftest raportin-suoritus-koko-maa
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kanavien-muutos-ja-lisatyot
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2017 1 1))
                                              :loppupvm (c/to-date (t/local-date 2017 12 31))
                                              :urakka-id  nil
                                              :kohde-id  nil
                                              :tehtava-id  nil}})]
    (is (vector? vastaus))))