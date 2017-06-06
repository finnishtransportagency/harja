(ns harja.palvelin.integraatiot.reimari.sanomat.hae-komponenttityypit-test
  (:require  [clojure.test :as t :refer [deftest is]]
             [harja.testi :as testi]
             [harja.palvelin.integraatiot.reimari.sanomat.hae-komponenttityypit :as hae-komponenttityypit]
             [harja.domain.vesivaylat.komponenttityyppi :as komponenttityyppi]
             [harja.domain.vesivaylat.turvalaite :as turvalaite]
             [harja.domain.vesivaylat.vayla :as vayla]
             [harja.domain.vesivaylat.sopimus :as sopimus]
             [harja.domain.vesivaylat.alus :as alus]
             [harja.pvm :as pvm]
             [clojure.spec.alpha :as s]))

(def komponenttityyppi
  {::komponenttityyppi/id "4242"
   ::komponenttityyppi/nimi "Punainen lamppu"})

(deftest esimerkki-xml-parsinta
  (let [luettu-komponenttityyppi
        (-> "resources/xsd/reimari/komponenttityypit-vastaus.xml"
            slurp
            hae-komponenttityypit/lue-hae-komponenttityypit-vastaus
            first)]
    (println (s/explain-str ::komponenttityyppi/komponenttityyppi luettu-komponenttityyppi))
    (is (nil? (s/explain-data ::komponenttityyppi/komponenttityyppi luettu-komponenttityyppi)))
    (testi/tarkista-map-arvot komponenttityyppi luettu-komponenttityyppi)))
