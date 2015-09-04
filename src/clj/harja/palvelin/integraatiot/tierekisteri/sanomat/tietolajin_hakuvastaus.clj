(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakuvastaus
  (:require [clojure.xml :refer [parse]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]
            [clojure.data.zip.xml :as z]))

(def +xsd-polku+ "xsd/tierekisteri/schemas/")


(defn onnistunut-vastaus? [vastaus]
  (= "OK" (z/xml1-> vastaus :ns2:status z/text)))

(defn hae-tunniste [data]
  (z/xml1-> (z/xml1-> (z/xml1-> data  :ns2:tietolajit) :ns2:tietolaji) :tietolajitunniste z/text))

(defn hae-ominaisuudet [data]
  )

(defn lue [viesti]
  (let [data (xml/lue viesti)]
    ;; todo: lisää virheiden parsinta
    {:onnistunut (onnistunut-vastaus? data)
     :tunniste (hae-tunniste data)
     :ominaisuudet (hae-ominaisuudet data)}))