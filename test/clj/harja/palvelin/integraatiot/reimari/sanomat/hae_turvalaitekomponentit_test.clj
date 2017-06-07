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
  {::turvalaitekomponentti/id "9595"
   ;; ...
   })

(deftest esimerkki-xml-parsinta
  (let [luettu-turvalaitekomponentti
        (-> "resources/xsd/reimari/turvalaitekomponentit-vastaus.xml"
            slurp
            hae-turvalaitekomponentit/lue-hae-turvalaitekomponentit-vastaus
            first)]
    (clojure.pprint/pprint luettu-turvalaitekomponentti)
    (is (some? luettu-turvalaitekomponentti))
    (println (s/explain-str ::turvalaitekomponentti/turvalaitekomponentti luettu-turvalaitekomponentti))
    (is (nil? (s/explain-data ::turvalaitekomponentti/turvalaitekomponentti luettu-turvalaitekomponentti)))
    (testi/tarkista-map-arvot turvalaitekomponentti luettu-turvalaitekomponentti)))
