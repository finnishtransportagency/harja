(ns harja.palvelin.integraatiot.reimari.sanomat.hae-turvalaitekomponentit-test
  (:require  [clojure.test :as t :refer [deftest is]]
             [harja.testi :as testi]
             [harja.palvelin.integraatiot.reimari.sanomat.hae-turvalaitekomponentit :as hae-turvalaitekomponentit]
             [harja.domain.vesivaylat.turvalaitekomponentti :as turvalaitekomponentti]
             [harja.domain.vesivaylat.turvalaite :as turvalaite]
             [harja.domain.vesivaylat.vayla :as vayla]
             [harja.domain.vesivaylat.sopimus :as sopimus]
             [harja.domain.vesivaylat.alus :as alus]
             [harja.pvm :as pvm]
             [clojure.spec.alpha :as s]))

(def turvalaitekomponentti
  {:harja.domain.vesivaylat.turvalaitekomponentti/sarjanumero
   "234234423",
   :harja.domain.vesivaylat.turvalaitekomponentti/loppupvm
   #inst "2017-07-20T00:00:00.000-00:00",
   :harja.domain.vesivaylat.turvalaitekomponentti/turvalaitenro "234",
   :harja.domain.vesivaylat.turvalaitekomponentti/valiaikainen false,
   :harja.domain.vesivaylat.turvalaitekomponentti/luoja "Aatos",
   :harja.domain.vesivaylat.turvalaitekomponentti/komponentti-id "4242",
   :harja.domain.vesivaylat.turvalaitekomponentti/id "9595",
   :harja.domain.vesivaylat.turvalaitekomponentti/alkupvm
   #inst "2011-05-01T01:00:00.000-00:00",
   :harja.domain.vesivaylat.turvalaitekomponentti/muokkaaja "Vilho",
   :harja.domain.vesivaylat.turvalaitekomponentti/muokattu
   #inst "2016-07-20T00:00:00.000-00:00",
   :harja.domain.vesivaylat.turvalaitekomponentti/luontiaika
   #inst "2010-05-01T01:00:00.000-00:00",
   :harja.domain.vesivaylat.turvalaitekomponentti/lisatiedot
   "asennettu kivasti"})

(deftest esimerkki-xml-parsinta
  (let [luettu-turvalaitekomponentti
        (-> "resources/xsd/reimari/turvalaitekomponentit-vastaus.xml"
            slurp
            hae-turvalaitekomponentit/lue-hae-turvalaitekomponentit-vastaus
            first)]
    ;; (clojure.pprint/pprint luettu-turvalaitekomponentti)
    (is (some? luettu-turvalaitekomponentti))
    (println (s/explain-str ::turvalaitekomponentti/turvalaitekomponentti luettu-turvalaitekomponentti))
    (is (nil? (s/explain-data ::turvalaitekomponentti/turvalaitekomponentti luettu-turvalaitekomponentti)))
    (testi/tarkista-map-arvot turvalaitekomponentti luettu-turvalaitekomponentti)))
