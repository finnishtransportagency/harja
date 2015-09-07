(ns harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus
  (:require [clojure.xml :refer [parse]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]
            [clojure.data.zip.xml :as z]))

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(defn onnistunut-vastaus? [data]
  (= "OK" (z/xml1-> data :ns2:status z/text)))

(defn parsi-virheet [data]
  (z/xml-> data :ns2:virheet :ns2:virhe z/text))

(defn parsi-tunniste [data]
  (-> data (z/xml1-> :tietolajitunniste z/text)))

(defn parsi-voimassaolo [data]
  {:alkupvm  (xml/parsi-paivamaara (z/xml1-> data :alkupvm z/text))
   :loppupvm (xml/parsi-paivamaara (z/xml1-> data :loppupvm z/text))})

(defn parsi-koodisto [data]
  {:koodiryhma (z/xml1-> data :koodiryhma z/text)
   :koodi      (xml/parsi-kokonaisluku (z/xml1-> data :koodi z/text))
   :lyhenne    (z/xml1-> data :lyhenne z/text)
   :selite     (z/xml1-> data :selite z/text)
   :muutospvm  (xml/parsi-paivamaara (z/xml1-> data :muutospvm z/text))})

(defn parsi-ominaisuus [data]
  {:kenttatunniste  (z/xml1-> data :kenttatunniste z/text)
   :jarjestysnumero (xml/parsi-kokonaisluku (z/xml1-> data :jarjestysnumero z/text))
   :pituus          (xml/parsi-kokonaisluku (z/xml1-> data :pituus z/text))
   :pakollinen      (xml/parsi-totuusarvo (z/xml1-> data :pakollinen z/text))
   :selite          (z/xml1-> data :selite z/text)
   :tietotyyppi     (xml/parsi-avain (z/xml1-> data :tietotyyppi z/text))
   :desimaalit      (xml/parsi-kokonaisluku (z/xml1-> data :desimaalit z/text))
   :alaraja         (xml/parsi-kokonaisluku (z/xml1-> data :alaraja z/text))
   :ylaraja         (xml/parsi-kokonaisluku (z/xml1-> data :ylaraja z/text))
   :voimassaolo     (parsi-voimassaolo (z/xml1-> data :voimassaolo))
   :koodisto        (when-let [koodisto (z/xml-> data :koodisto :koodi parsi-koodisto)]
                      (when (not (empty? koodisto)) koodisto))})

(defn parsi-ominaisuudet [data]
  (z/xml-> data :ominaisuudet :ominaisuus parsi-ominaisuus))

(defn parsi-tietolaji [data]
  (let [tietolaji (z/xml1-> data :ns2:tietolajit :ns2:tietolaji)]
    {:tunniste     (parsi-tunniste tietolaji)
     :ominaisuudet (parsi-ominaisuudet tietolaji)}))

(defn lue [viesti]
  (let [data (xml/lue viesti)
        vastaus {:onnistunut (onnistunut-vastaus? data)}]
    (-> vastaus
        (cond-> (z/xml1-> data :ns2:virheet) (assoc :virheet (parsi-virheet data)))
        (cond-> (z/xml1-> data :ns2:tietolajit) (assoc :tietolaji (parsi-tietolaji data))))))