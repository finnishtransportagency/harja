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
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db ds
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
  (component/stop jarjestelma)
  (lopeta-harja-tarkkailija!))

(use-fixtures :each (compose-fixtures
                     urakkatieto-fixture
                     jarjestelma-fixture))

#_(deftest raportin-suoritus-urakka
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
                                              :tehtava-id  nil}})
        nurkkasumma (last (last (last vastaus)))]
    (is (vector? vastaus))
    (is (= nurkkasumma 2546.0M))))

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
                                              :tehtava-id  nil}})
        nurkkasumma (last (last (last vastaus)))]
    (is (vector? vastaus))))

#_(deftest raportin-suoritus-urakka-tehtava
  (let [vaylanhoito-ei-yksiloity-tpk-id (hae-vaylanhoito-ei-yksiloity-tpk-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kanavien-muutos-ja-lisatyot
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-saimaan-kanavaurakan-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2017 1 1))
                                              :loppupvm (c/to-date (t/local-date 2017 12 31))
                                              :urakka-id  (hae-saimaan-kanavaurakan-id)
                                              :kohde-id  nil
                                              :tehtava-id  vaylanhoito-ei-yksiloity-tpk-id}})
        nurkkasumma (last (last (last vastaus)))]
    (is (vector? vastaus))
    (is (= nurkkasumma 2546.0M))))

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
