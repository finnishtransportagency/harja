(ns harja.palvelin.ajastetut-tehtavat.paivystajatarkistukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.ajastetut-tehtavat.paivystajatarkistukset :as paivystajatarkistukset]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [clj-time.core :as t])
  (:use org.httpkit.fake))

(deftest urakoiden-paivystajien-haku-toimii
  (let [testitietokanta (tietokanta/luo-tietokanta testitietokanta)
        urakoiden-paivystykset (paivystajatarkistukset/hae-urakoiden-paivystykset
                                 testitietokanta
                                 (t/local-date 2016 10 1))]
    ;; Oulun alueurakka 2014-2019 löytyy 3 päivystystä
    (is (= (count (:paivystykset (first (filter
                                        #(= (:nimi %) "Oulun alueurakka 2014-2019")
                                        urakoiden-paivystykset))))
           3))

    ;; Kaikki testidatan käynnissä olleet urakat löytyi
    (is (= (count urakoiden-paivystykset) 16))))
