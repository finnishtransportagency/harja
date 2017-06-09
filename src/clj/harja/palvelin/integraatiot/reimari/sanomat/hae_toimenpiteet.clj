(ns harja.palvelin.integraatiot.reimari.sanomat.hae-toimenpiteet
  "Harja-Reimari integraation HaeToimenpiteet operaation XML-sanomien
  luku ja kirjoitus.

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
    (.toDate (xml/parsi-xsd-datetime-ms-aikaleimalla text))))

(def toimenpide-attribuutit {:id #(Integer/parseInt %)
                             :tyolaji identity
                             :tyoluokka identity
                             :tyyppi identity
                             :tila identity
                             :lisatyo #(Boolean/valueOf %)
                             :suoritettu aikaleima
                             :lisatieto identity
                             :luotu aikaleima
                             :muokattu aikaleima})

(def alus-avainmuunnos
  {:harja.domain.vesivaylat.alus/tunnus :harja.domain.vesivaylat.alus/r-tunnus
   :harja.domain.vesivaylat.alus/nimi :harja.domain.vesivaylat.alus/r-nimi})
(defn- lue-alus [a]
  (rename-keys (xml/lue-attribuutit a #(keyword "harja.domain.vesivaylat.alus" (name %))
                                    {:tunnus identity
                                     :nimi identity})
               alus-avainmuunnos))

(def sopimus-avainmuunnos
  {:harja.domain.vesivaylat.sopimus/nro :harja.domain.vesivaylat.sopimus/r-nro
   :harja.domain.vesivaylat.sopimus/tyyppi :harja.domain.vesivaylat.sopimus/r-tyyppi
   :harja.domain.vesivaylat.sopimus/nimi :harja.domain.vesivaylat.sopimus/r-nimi})

(defn- lue-sopimus [s]
  (rename-keys (xml/lue-attribuutit s #(keyword "harja.domain.vesivaylat.sopimus" (name %))
                                    {:nro #(Integer/parseInt %)
                                     :tyyppi identity
                                     :nimi identity})
               sopimus-avainmuunnos))

(def turvalaite-avainmuunnos
  {:harja.domain.vesivaylat.turvalaite/nro :harja.domain.vesivaylat.turvalaite/r-nro
   :harja.domain.vesivaylat.turvalaite/nimi :harja.domain.vesivaylat.turvalaite/r-nimi
   :harja.domain.vesivaylat.turvalaite/ryhma :harja.domain.vesivaylat.turvalaite/r-ryhma})
(defn- lue-turvalaite [tl]
  (rename-keys (xml/lue-attribuutit tl #(keyword "harja.domain.vesivaylat.turvalaite" (name %))
                                    {:nro identity
                                     :nimi identity
                                     :ryhma #(Integer/parseInt %)})
               turvalaite-avainmuunnos))

(defn- lue-komponentti [k]
  (xml/lue-attribuutit k #(keyword "harja.domain.vesivaylat.komponentti" (name %))
                       {:tila identity
                        :nimi identity
                        :id #(Integer/parseInt %)}))

(def vayla-avainmuunnos
  {:harja.domain.vesivaylat.vayla/nro :harja.domain.vesivaylat.vayla/r-nro
   :harja.domain.vesivaylat.vayla/nimi :harja.domain.vesivaylat.vayla/r-nimi})

(defn- lue-vayla [v]
  (rename-keys (xml/lue-attribuutit v #(keyword "harja.domain.vesivaylat.vayla" (name %))
                                     {:nro identity
                                      :nimi identity})
               vayla-avainmuunnos))

(defn- lue-urakoitsija [v]
  (xml/lue-attribuutit v #(keyword "harja.domain.vesivaylat.urakoitsija" (name %))
                       {:id #(Integer/parseInt %)
                        :nimi identity}))

(defn- lue-toimenpide [toimenpide]
  (merge
   (xml/lue-attribuutit toimenpide #(keyword "harja.domain.vesivaylat.toimenpide"
                                             (name (case %
                                                     :tyyppi :reimari-toimenpidetyyppi
                                                     :lisatyo :lisatyo?
                                                     :tyolaji :reimari-tyolaji
                                                     :tyoluokka :reimari-tyoluokka
                                                     %)))
                        toimenpide-attribuutit)
    (when-let [a (z/xml1-> toimenpide :alus)]
      {::toimenpide/alus (lue-alus a)})
    (when-let [s (z/xml1-> toimenpide :sopimus)]
      {::toimenpide/reimari-sopimus (lue-sopimus s)})
    (when-let [tl (z/xml1-> toimenpide :turvalaite)]
      {::toimenpide/turvalaite (lue-turvalaite tl)})
    (when-let [v (z/xml1-> toimenpide :vayla)]
      {::toimenpide/vayla  (lue-vayla v)})
    (when-let [v (z/xml1-> toimenpide :urakoitsija)]
      {::toimenpide/urakoitsija (lue-urakoitsija v)})
    {::toimenpide/komponentit (vec (z/xml-> toimenpide :komponentit :komponentti lue-komponentti))}))

(defn hae-toimenpiteet-vastaus [vastaus-xml]
  (if-let [ht (z/xml1-> vastaus-xml :S:Body :HaeToimenpiteet)]
    (vec (z/xml->
          ht
          :HaeToimenpiteetResponse
          :toimenpide
          lue-toimenpide))
    (log/error "Reimarin toimenpidehaun vastaus ei sisällä :HaeToimenpiteet -elementtiä")))

(defn lue-hae-toimenpiteet-vastaus [xml]
  (hae-toimenpiteet-vastaus (xml/lue xml "UTF-8")))
