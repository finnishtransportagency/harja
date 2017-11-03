(ns harja.palvelin.integraatiot.reimari.sanomat.hae-turvalaiteryhmat
  "Harja-Reimari-integraation HaeTurvalaiteryhmat-operaation XML-sanomien (request, response) luku.
  Rajapintamäärittely: resources/xsd/reimari/harja.xsd"

  (:require [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.turvalaiteryhma :as turvalaiteryhma]
            [harja.palvelin.integraatiot.reimari.apurit :refer [paivamaara aikaleima]]
            [clojure.string :as str]
            [clojure.set :refer [rename-keys]]
            [harja.pvm :as pvm]))

(def turvalaiteryhma-attribuutit {:tunnus identity
                                  :nimi identity
                                  :kuvaus identity
                                  :turvalaitteet identity})

(defn- lue-turvalaite [tl]
  (xml/lue-attribuutit tl #(keyword "harja.domain.vesivaylat.turvalaite" (name %))
                                    {:nro identity
                                     :nimi identity
                                     :ryhma #(Integer/parseInt %)}))

(defn- lue-turvalaiteryhma [turvalaiteryhma]
  (merge
  (xml/lue-attribuutit turvalaiteryhma
                       #(keyword "harja.domain.vesivaylat.turvalaiteryhma"
                                 (name %))
                       turvalaiteryhma-attribuutit)
  {::turvalaiteryhma/turvalaitteet (vec (z/xml-> turvalaiteryhma :turvalaitteet :turvalaite lue-turvalaite))}))

(defn hae-turvalaiteryhmat-vastaus [vastaus-xml]
  (if-let [ht (z/xml1-> vastaus-xml :S:Body :HaeTurvalaiteryhmat)]
    (vec (z/xml->
           ht
           :HaeTurvalaiteryhmatResponse
           :turvalaiteryhma
           lue-turvalaiteryhma
           ))
    (log/error "Reimarin turvalaiteryhmähaun vastaus ei sisällä :HaeTurvalaiteRyhmat -elementtiä")))

(defn lue-hae-turvalaiteryhmat-vastaus [xml]
  (hae-turvalaiteryhmat-vastaus (xml/lue xml "UTF-8")))
