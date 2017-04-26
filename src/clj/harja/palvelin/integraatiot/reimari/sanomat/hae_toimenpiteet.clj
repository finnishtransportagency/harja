(ns harja.palvelin.integraatiot.reimari.sanomat.hae-toimenpiteet
  "Harja-Reimari integraation HaeToimenpiteet operaation XML-sanomien
  luku ja kirjoitus.

  ks. resources/xsd/reimari/harja.xsd"
  (:require [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [harja.domain.vesivaylat.toimenpide :as toimenpide]
            [harja.domain.vesivaylat.alus :as alus]
            [harja.domain.vesivaylat.sopimus :as sopimus]

            [clojure.string :as str]
            [harja.pvm :as pvm]))


(defn- aikaleima [text]
  (when-not (str/blank? text)
    (xml/parsi-xsd-datetime-aikaleimalla text)))

(def toimenpide-attribuutit {:id identity
                             :tyolaji identity
                             :tyoluokka identity
                             :tyyppi identity
                             :tila identity
                             :lisatyo identity
                             :suoritettu aikaleima
                             :lisatieto identity
                             :luotu aikaleima
                             :muokattu aikaleima})

(println (lue-hae-toimenpiteet-vastaus (lue-xml (slurp "resources/xsd/reimari/vastaus.xml"))))

(defn- lue-alus [a]
  {::alus/id "IMPLEMENT" ::alus/nimi "ME"})

(defn- lue-sopimus [s]
  {::sopimus/id "foo"})

(defn- lue-turvalaite [tl]
  {:foobar 123})


(defn- lue-toimenpide [toimenpide]
  (merge
   (reduce (fn [m [avain lue-fn]]
             (assoc m (keyword "harja.domain.vesivaylat.toimenpide" (name avain))
                    (z/xml1-> toimenpide (z/attr avain) lue-fn)))
           {}
           toimenpide-attribuutit)
   (when-let [a (z/xml1-> toimenpide :alus)]
     {::toimenpide/alus (lue-alus a)})
   (when-let [s (z/xml1-> toimenpide :sopimus)]
     {::toimenpide/sopimus (lue-sopimus s)})
   (when-let [tl (z/xml1-> toimenpide :turvalaite)]
     {::toimenpide/turvalaite (lue-turvalaite tl)})))

(defn lue-hae-toimenpiteet-vastaus [vastaus]
  (z/xml-> vastaus
           :HaeToimenpiteetResponse
           :toimenpide
           lue-toimenpide))

(defn lue-xml [xml]
  (xml/lue xml "UTF-8"))
