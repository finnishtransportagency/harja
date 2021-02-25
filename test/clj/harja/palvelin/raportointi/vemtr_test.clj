(ns harja.palvelin.raportointi.vemtr-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.raportointi.raportit.vemtr :as vemtr]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

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

(def ekat [{:nimi "Erillinen1" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
           {:nimi "Erillinen2" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
           {:nimi "Erillinen3" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
           {:nimi "Erillinen4" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
           {:nimi "Sama1" :hallintayksikko 1 :suunniteltu 4 :toteuma 2}
           {:nimi "Sama1" :hallintayksikko 1 :suunniteltu 4 :toteuma 2}
           {:nimi "Erillinen5" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
           {:nimi "Erillinen6" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
           {:nimi "Sama1" :hallintayksikko 2 :suunniteltu 1 :toteuma 1}
           {:nimi "Sama2" :hallintayksikko 1 :suunniteltu 6 :toteuma 3}
           {:nimi "Sama2" :hallintayksikko 1 :suunniteltu 6 :toteuma 3}])

(def tokat [{:nimi "Erillinen9" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
            {:nimi "Erillinen8" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
            {:nimi "Erillinen7" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
            {:nimi "Sama3" :hallintayksikko 1 :suunniteltu 4 :toteuma 2}
            {:nimi "Sama4" :hallintayksikko 1 :suunniteltu 4 :toteuma 2}
            {:nimi "Sama1" :hallintayksikko 2 :suunniteltu 1 :toteuma 1}
            {:nimi "Sama3" :hallintayksikko 1 :suunniteltu 6 :toteuma 3}
            {:nimi "Sama4" :hallintayksikko 1 :suunniteltu 6 :toteuma 3}])

(deftest vemtr-harvelit
  (testing "VEMTR prosessointihärvelit"
    (is (= 14 (count (vemtr/kombota-samat-tehtavat ekat tokat))))))

;; Kombota-samat-tehtavat on muutettu niin, että myös toimenpide vaikuttaa yhdistämiseen
;; Koska on mahdollista, että saman niminen tehtävä on kahden eri toimenpiteen alla
(def e2 [{:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide2" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 2 :suunniteltu 4 :toteuma 2}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 2 :suunniteltu 4 :toteuma 2}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 2 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 2 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 2 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 2 :suunniteltu 6 :toteuma 3}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 2 :suunniteltu 6 :toteuma 3}])

(def t2 [{:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 1 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 1 :suunniteltu 4 :toteuma 2}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 1 :suunniteltu 4 :toteuma 2}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 2 :suunniteltu 1 :toteuma 1}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 2 :suunniteltu 6 :toteuma 3}
         {:nimi "Nimi1" :toimenpide "Toimenpide1" :hallintayksikko 2 :suunniteltu 6 :toteuma 3}])

(deftest vemtr-harvelit-ver2
  (testing "VEMTR prosessointihärvelit"
    (is (= 3 (count (vemtr/yhdistele-toimenpiteet-ja-tehtavat e2 t2))))))


(deftest vemtr-raportti-perusjutut
  (testing "Valtakunnallisten määrien haku raportille"
    (let [[koko-raportti _ [koko-taulukko _ _ koko-maa-rivit] :as koko-maa-kamat]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi :tehtavamaarat
                           :konteksti "koko maa"
                           :parametrit {:alkupvm (c/to-date (t/local-date 2020 10 1))
                                        :loppupvm (c/to-date (t/local-date 2021 10 1))}})
          [ely-raportti _ [ely-taulukko _ _ ely-rivit] :as ely-kamat]
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :suorita-raportti
                          +kayttaja-jvh+
                          {:nimi :tehtavamaarat
                           :konteksti "hallintayksikko"
                           :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                           :parametrit {:alkupvm (c/to-date (t/local-date 2020 10 1))
                                        :loppupvm (c/to-date (t/local-date 2021 10 1))}})]

      (is (not (empty? koko-maa-rivit)) "Palautuu raportille asioita koko maalla")
      (is (not (empty? ely-rivit)) "Palautuu raportille asioita hankintayksiköllä"))))