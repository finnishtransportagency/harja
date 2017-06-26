(ns harja.palvelin.integraatiot.reimari.sanomat.hae-komponenttityypit
  "Harja-Reimari integraation HaeKomponenttiTyypit-operaation XML-sanomien
  luku.

  ks. resources/xsd/reimari/harja.xsd"
  (:require [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.komponenttityyppi :as komponenttityyppi]
            [harja.palvelin.integraatiot.reimari.apurit :refer [paivamaara aikaleima]]
            [harja.domain.vesivaylat.alus :as alus]
            [harja.domain.vesivaylat.sopimus :as sopimus]
            [harja.domain.vesivaylat.turvalaite :as turvalaite]
            [harja.domain.vesivaylat.vayla :as vayla]
            [clojure.set :refer [rename-keys]]
            [harja.pvm :as pvm]))


(def komponenttityyppi-attribuutit {:id identity
                                    :nimi identity
                                    :lisatiedot identity
                                    :luokan-id identity
                                    :luokan-nimi identity
                                    :luokan-lisatiedot identity
                                    :luokan-paivitysaika aikaleima
                                    :luokan-luontiaika aikaleima
                                    :merk-cod identity
                                    :luontiaika aikaleima
                                    :muokattu aikaleima
                                    :alkupvm paivamaara
                                    :loppupvm paivamaara})

(defn- lue-komponenttityyppi [komponenttityyppi]
  (xml/lue-attribuutit komponenttityyppi #(keyword "harja.domain.vesivaylat.komponenttityyppi"
                                                   (name %))
                       komponenttityyppi-attribuutit))

(defn hae-komponenttityypit-vastaus [vastaus-xml]
  (if-let [ht (z/xml1-> vastaus-xml :S:Body :HaeKomponenttiTyypit)]
    (vec (z/xml->
          ht
          :HaeKomponenttiTyypitResponse
          :komponenttityyppi
          lue-komponenttityyppi))
    (log/error "Reimarin komponenttityyppihaun vastaus ei sisällä :HaeKomponenttiTyypit -elementtiä")))

(defn lue-hae-komponenttityypit-vastaus [xml]
  (hae-komponenttityypit-vastaus (xml/lue xml "UTF-8")))
