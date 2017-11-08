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
  {:harja.domain.vesivaylat.turvalaiteryhma/tunnus 1234,
   :harja.domain.vesivaylat.turvalaiteryhma/nimi "Merireimari"
   :harja.domain.vesivaylat.turvalaiteryhma/kuvaus "1234: Merireimari"
   :harja.domain.vesivaylat.turvalaiteryhma/turvalaitteet
   [5678 5679 5670 5671]})

(def toinen-turvalaiteryhma-tietue
  {:harja.domain.vesivaylat.turvalaiteryhma/tunnus 1235,
   :harja.domain.vesivaylat.turvalaiteryhma/nimi "Järvireimari"
   :harja.domain.vesivaylat.turvalaiteryhma/kuvaus "1235: Järvireimari"
   :harja.domain.vesivaylat.turvalaiteryhma/turvalaitteet
   [4678 4679 4670 4671]})

(deftest esimerkki-xml-parsinta
  (let [luettu-turvalaiteryhma
        (-> "resources/xsd/reimari/haeturvalaiteryhmat-vastaus.xml"
            slurp
            hae-turvalaiteryhmat/lue-hae-turvalaiteryhmat-vastaus
            first)]
    (is (some? luettu-turvalaiteryhma))
    (testi/tarkista-map-arvot turvalaiteryhma-tietue luettu-turvalaiteryhma)))

(deftest  esimerkki-xml-parsinta-toinen-tietue
  (let [luettu-turvalaiteryhma
        (-> "resources/xsd/reimari/haeturvalaiteryhmat-vastaus.xml"
            slurp
            hae-turvalaiteryhmat/lue-hae-turvalaiteryhmat-vastaus
            second)]
    (is (some? luettu-turvalaiteryhma))
    (testi/tarkista-map-arvot toinen-turvalaiteryhma-tietue luettu-turvalaiteryhma)))
