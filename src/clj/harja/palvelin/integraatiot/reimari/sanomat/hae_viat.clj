(ns harja.palvelin.integraatiot.reimari.sanomat.hae-viat
  "Harja-Reimari integraation HaeViat-operaation XML-sanomien
  luku.

  ks. resources/xsd/reimari/harja.xsd"
  (:require [harja.tyokalut.xml :as xml]

            [clojure.data.zip.xml :as z]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.vikailmoitus :as vv-vikailmoitus]
            [harja.palvelin.integraatiot.reimari.apurit :refer [paivamaara aikaleima]]
            [harja.domain.vesivaylat.alus :as alus]
            [harja.domain.vesivaylat.turvalaite :as turvalaite]
            [harja.domain.vesivaylat.vayla :as vayla]
            [clojure.string :as str]
            [clojure.set :refer [rename-keys]]
            [harja.pvm :as pvm]))

(def attribuutit {:id #(Integer/parseInt %)
                  :turvalaitenro identity ;; #(Integer/parseInt %)
                  :lisatiedot identity
                  :ilmoittaja identity
                  :ilmoittajan-yhteystieto identity
                  :epakunnossa #(Boolean/valueOf %)
                  :tyyppikoodi identity
                  :tilakoodi identity
                  :havaittu aikaleima
                  :kirjattu aikaleima
                  :korjattu aikaleima
                  :muokattu aikaleima
                  :luontiaika aikaleima
                  :luoja identity
                  :muokkaaja identity})

(defn- lue-vika [vika]
  (xml/lue-attribuutit vika
                       #(keyword "harja.domain.vesivaylat.vikailmoitus"
                                 (name %))
                       attribuutit))

(defn hae-viat-vastaus [vastaus-xml]
  (if-let [ht (z/xml1-> vastaus-xml :S:Body :HaeViat)]
    (vec (z/xml->
          ht
          :HaeViatResponse
          :vika
          lue-vika))
    (log/warn "Reimarin vikahaun vastaus ei sisällä :HaeViat -elementtiä")))

(defn lue-hae-viat-vastaus [xml]
  (hae-viat-vastaus (xml/lue xml "UTF-8")))
