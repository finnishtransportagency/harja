(ns harja.palvelin.integraatiot.reimari.sanomat.hae-turvalaiteryhmat-test
  (:require [clojure.test :as t :refer [deftest is]]
            [harja.testi :as testi]
            [harja.palvelin.integraatiot.reimari.sanomat.hae-turvalaiteryhmat :as hae-turvalaiteryhmat]
            [harja.domain.vesivaylat.turvalaiteryhma :as turvalaiteryhma]
            [harja.domain.vesivaylat.turvalaite :as turvalaite]
            [harja.domain.vesivaylat.vayla :as vayla]
            [harja.domain.vesivaylat.sopimus :as sopimus]
            [harja.domain.vesivaylat.alus :as alus]
            [harja.pvm :as pvm]
            [clojure.spec.alpha :as s]))

(def turvalaiteryhma-tietue
  {:turvalaiteryma/tunnus "1234",
   :turvalaiteryhma/nimi "Merireimari"
   :turvalaiteryhma/kuvaus "1234: Merireimari"
   :turvalaiteryhma/turvalaitteet
   [{:harja.domain.vesivaylat.turvalaite/nro "5678",
     :harja.domain.vesivaylat.turvalaite/nimi "Michelsöharun",
     :harja.domain.vesivaylat.turvalaite/ryhma 1234}
    {:harja.domain.vesivaylat.turvalaite/nro "5679",
     :harja.domain.vesivaylat.turvalaite/nimi "Huldviksgrund",
     :harja.domain.vesivaylat.turvalaite/ryhma 1234}
    {:harja.domain.vesivaylat.turvalaite/nro "5670",
     :harja.domain.vesivaylat.turvalaite/nimi "Klobben ylempi",
     :harja.domain.vesivaylat.turvalaite/ryhma 1234}
    {:harja.domain.vesivaylat.turvalaite/nro "5671",
     :harja.domain.vesivaylat.turvalaite/nimi "Svartholm ylempi",
     :harja.domain.vesivaylat.turvalaite/ryhma 1234}]})

(deftest esimerkki-xml-parsinta
  (let [luettu-turvalaiteryhma
        (-> "resources/xsd/reimari/haeturvalaiteryhmat-vastaus.xml"
            slurp
            hae-turvalaiteryhmat/lue-hae-turvalaiteryhmat-vastaus
            first)]
    (clojure.pprint/pprint luettu-turvalaiteryhma)
    (is (some? luettu-turvalaiteryhma))
    (testi/tarkista-map-arvot turvalaiteryhma-tietue luettu-turvalaiteryhma)))
