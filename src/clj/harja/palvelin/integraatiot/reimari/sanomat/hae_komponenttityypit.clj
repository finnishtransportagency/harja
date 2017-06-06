(ns harja.palvelin.integraatiot.reimari.sanomat.hae-komponenttityypit
  "Harja-Reimari integraation HaeKomponenttiTyypit-operaation XML-sanomien
  luku.

  ks. resources/xsd/reimari/harja.xsd"
  (:require [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.toimenpide :as toimenpide]
            [harja.domain.vesivaylat.alus :as alus]
            [harja.domain.vesivaylat.sopimus :as sopimus]
            [harja.domain.vesivaylat.turvalaite :as turvalaite]
            [harja.domain.vesivaylat.vayla :as vayla]
            [clojure.string :as str]
            [clojure.set :refer [rename-keys]]
            [harja.pvm :as pvm]))

(defn- aikaleima [text]
  (when-not (str/blank? text)
    (.toDate (xml/parsi-xsd-datetime-aikaleimalla text))))

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
                                    :alkupvm aikaleima
                                    :loppupvm aikaleima})

(defn- lue-komponenttityyppi [komponenttityyppi]
  (xml/lue-attribuutit komponenttityyppi #(keyword "harja.domain.vesivaylat.komponenttityyppi"
                                                   (name %))
                       komponenttityyppi-attribuutit))

(defn hae-komponenttityypit-vastaus [vastaus-xml]
  (vec (z/xml-> vastaus-xml
                :HaeKomponenttiTyypitResponse
                :komponenttityyppi
                lue-komponenttityyppi)))

(defn lue-hae-komponenttityypit-vastaus [xml]
  (hae-komponenttityypit-vastaus (xml/lue xml "UTF-8")))
