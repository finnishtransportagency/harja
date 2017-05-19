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
            [harja.pvm :as pvm]
            [clojure.set :as set]))


(defn- aikaleima [text]
  (when-not (str/blank? text)
    (.toDate (xml/parsi-xsd-datetime-aikaleimalla text))))

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

(defn- lue-alus [a]
  (xml/lue-attribuutit a #(keyword "harja.domain.vesivaylat.alus" (name %))
                       {:tunnus identity
                        :nimi identity}))

(defn- lue-sopimus [s]
  (xml/lue-attribuutit s #(keyword "harja.domain.vesivaylat.sopimus" (name %))
                       {:nro #(Integer/parseInt %)
                        :tyyppi identity
                        :nimi identity}))

(defn- lue-turvalaite [tl]
  (xml/lue-attribuutit tl #(keyword "harja.domain.vesivaylat.turvalaite" (name %))
                       {:nro identity
                        :nimi identity
                        :ryhma #(Integer/parseInt %)}))

(defn- lue-vayla [v]
  (xml/lue-attribuutit v #(keyword "harja.domain.vesivaylat.vayla" (name %))
                       {:nro identity
                        :nimi identity}))

(defn- lue-toimenpide [toimenpide]
  (merge
    (xml/lue-attribuutit toimenpide #(keyword "harja.domain.vesivaylat.toimenpide" (name %))
                         toimenpide-attribuutit)
    (when-let [a (z/xml1-> toimenpide :alus)]
      {::toimenpide/alus (lue-alus a)})
    (when-let [s (z/xml1-> toimenpide :sopimus)]
      {::toimenpide/sopimus (lue-sopimus s)})
    (when-let [tl (z/xml1-> toimenpide :turvalaite)]
      {::toimenpide/turvalaite (lue-turvalaite tl)})
    (when-let [v (z/xml1-> toimenpide :vayla)]
      {::toimenpide/vayla (lue-vayla v)})))

(defn hae-toimenpiteet-vastaus [vastaus-xml]
  (let [muunnetut (z/xml-> vastaus-xml
                           :HaeToimenpiteetResponse
                           :toimenpide
                           lue-toimenpide)]
    (mapv #(set/rename-keys % {::toimenpide/toimenpide ::toimenpide/reimari-toimenpidetyyppi
                               ::toimenpide/tyolaji ::toimenpide/reimari-tyolaji
                               ::toimenpide/tyoluokka ::toimenpide/reimari-tyoluokka})
          muunnetut)))

(defn lue-hae-toimenpiteet-vastaus [xml]
  (hae-toimenpiteet-vastaus (xml/lue xml "UTF-8")))
