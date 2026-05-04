(ns harja.palvelin.palvelut.ilmoitukset-evk-test
  "Ilmoitusten elinvoimakeskus-suodatuksen testit"
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [harja.domain.tieliikenneilmoitukset :refer [+ilmoitustyypit+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.ilmoitukset :as ilmoitukset]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component])
  (:import (java.util Date)))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (urakkatieto-alustus!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-ilmoitukset (component/using
                                           (ilmoitukset/->Ilmoitukset)
                                           [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (urakkatieto-lopetus!))

(use-fixtures :each jarjestelma-fixture)

(def hae-ilmoitukset-parametrit
  {:hallintayksikko nil
   :urakka nil
   :hoitokausi nil
   :aikavali [(Date. 0 0 0) (Date.)]
   :tyypit +ilmoitustyypit+
   :tilat [:kuittaamaton :vastaanotettu :aloitettu :lopetettu]
   :aloituskuittauksen-ajankohta :kaikki
   :hakuehto ""
   :lajittelu-suunta :laskeva})

(defn hae [parametrit]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-ilmoitukset +kayttaja-jvh+ parametrit))

(deftest hae-ilmoituksia-eri-elinvoimakeskukset-eivat-sekoitu
  (let [psu-evk-id (hae-pohjois-suomen-evk-id)
        uud-evk-id (ffirst (q "SELECT id FROM organisaatio WHERE nimi = 'Uusimaa' AND tyyppi = 'elinvoimakeskus'"))
        psu-parametrit (merge hae-ilmoitukset-parametrit {:hallintayksikko psu-evk-id})
        uud-parametrit (merge hae-ilmoitukset-parametrit {:hallintayksikko uud-evk-id})
        psu-ilmoitukset (hae psu-parametrit)
        uud-ilmoitukset (hae uud-parametrit)
        psu-urakka-idt (set (map :urakka psu-ilmoitukset))
        uud-urakka-idt (set (map :urakka uud-ilmoitukset))
        psu-urakat-kannasta (set (map first (q (format "SELECT id FROM urakka WHERE elinvoimakeskus_id = %s" psu-evk-id))))
        uud-urakat-kannasta (set (map first (q (format "SELECT id FROM urakka WHERE elinvoimakeskus_id = %s" uud-evk-id))))]

    (testing "EVK-idt löytyvät"
      (is (some? psu-evk-id) "PSU EVK löytyy")
      (is (some? uud-evk-id) "UUD EVK löytyy")
      (is (not= psu-evk-id uud-evk-id) "EVK:t ovat eri organisaatioita"))

    (testing "PSU:n ilmoitukset kuuluvat vain PSU:n urakoihin"
      (is (every? #(contains? psu-urakat-kannasta %) psu-urakka-idt)
          "Kaikki PSU:n ilmoitusten urakat kuuluvat PSU:n elinvoimakeskukseen"))

    (testing "UUD:n ilmoitukset kuuluvat vain UUD:n urakoihin"
      (is (every? #(contains? uud-urakat-kannasta %) uud-urakka-idt)
          "Kaikki UUD:n ilmoitusten urakat kuuluvat UUD:n elinvoimakeskukseen"))

    (testing "Eri EVK:iden ilmoitukset eivät sekoitu keskenään"
      (is (empty? (set/intersection psu-urakka-idt uud-urakka-idt))
          "PSU:n ja UUD:n ilmoitusten urakat eivät saa olla samoja"))))
