(ns harja.palvelin.integraatiot.reimari.sanomat.hae-turvalaitekomponentit
  "Harja-Reimari integraation HaeTurvalaiteKomponentit-operaation XML-sanomien
  luku.

  ks. resources/xsd/reimari/harja.xsd"
  (:require [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.turvalaitekomponentti :as turvalaitekomponentti]
            [harja.palvelin.integraatiot.reimari.apurit :refer [paivamaara aikaleima]]
            [harja.domain.vesivaylat.alus :as alus]
            [harja.domain.vesivaylat.turvalaite :as turvalaite]
            [harja.domain.vesivaylat.vayla :as vayla]
            [clojure.string :as str]
            [clojure.set :refer [rename-keys]]
            [harja.pvm :as pvm]))

(def attribuutit {:id identity
                  :lisatiedot identity
                  :turvalaitenro identity
                  :komponentti-id identity
                  :sarjanumero identity
                  :luoja identity
                  :valiaikainen #(Boolean/valueOf %)
                  :luontiaika aikaleima
                  :muokattu aikaleima
                  :muokkaaja identity
                  :alkupvm paivamaara
                  :loppupvm paivamaara})

(defn- lue-turvalaitekomponentti [turvalaitekomponentti]
  (xml/lue-attribuutit turvalaitekomponentti
                       #(keyword "harja.domain.vesivaylat.turvalaitekomponentti"
                                 (name %))
                       attribuutit))

(defn hae-turvalaitekomponentit-vastaus [vastaus-xml]
  (if-let [ht (z/xml1-> vastaus-xml :S:Body :HaeTurvalaiteKomponentit)]
    (vec (z/xml->
          ht
          :HaeTurvalaiteKomponentitResponse
          :turvalaitekomponentti
          lue-turvalaitekomponentti))
    (log/error "Reimarin turvalaitekomponenttihaun vastaus ei sisällä :HaeTurvalaiteKomponentit -elementtiä")))

(defn lue-hae-turvalaitekomponentit-vastaus [xml]
  (hae-turvalaitekomponentit-vastaus (xml/lue xml "UTF-8")))
