(ns harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus
  (:require [clojure.xml :refer [parse]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]
            [clojure.data.zip.xml :as z]))

(def +xsd-polku+ "xsd/tierekisteri/skeemat/")

(defn onnistunut-vastaus? [data]
  (= "OK" (z/xml1-> data :ns2:status z/text)))

(defn parsi-virheet [data]
  (z/xml-> data :ns2:virheet :ns2:virhe z/text))

(defn parsi-tunniste [data]
  (-> data (z/xml1-> :tietolajitunniste z/text)))

(defn parsi-voimassaolo [data]
  (when data
    {:alkupvm (xml/parsi-paivamaara (z/xml1-> data :alkupvm z/text))
     :loppupvm (xml/parsi-paivamaara (z/xml1-> data :loppupvm z/text))}))

(defn parsi-koodisto [data]
  {:koodiryhma (z/xml1-> data :koodiryhma z/text)
   :koodi (xml/parsi-kokonaisluku (z/xml1-> data :koodi z/text))
   :lyhenne (z/xml1-> data :lyhenne z/text)
   :selite (z/xml1-> data :selite z/text)
   :muutospvm (xml/parsi-paivamaara (z/xml1-> data :muutospvm z/text))
   :voimassaolo (parsi-voimassaolo (z/xml1-> data :voimassaolo))})

(defn parsi-ominaisuus [data]
  {:kenttatunniste (z/xml1-> data :kenttatunniste z/text)
   :jarjestysnumero (xml/parsi-kokonaisluku (z/xml1-> data :jarjestysnumero z/text))
   :pituus (xml/parsi-kokonaisluku (z/xml1-> data :pituus z/text))
   :pakollinen (xml/parsi-totuusarvo (z/xml1-> data :pakollinen z/text))
   :selite (z/xml1-> data :selite z/text)
   :tietotyyppi (xml/parsi-avain (z/xml1-> data :tietotyyppi z/text))
   :desimaalit (xml/parsi-kokonaisluku (z/xml1-> data :desimaalit z/text))
   :alaraja (xml/parsi-kokonaisluku (z/xml1-> data :alaraja z/text))
   :ylaraja (xml/parsi-kokonaisluku (z/xml1-> data :ylaraja z/text))
   :muutospvm (xml/parsi-paivamaara (z/xml1-> data :muutospvm z/text))
   :voimassaolo (parsi-voimassaolo (z/xml1-> data :voimassaolo))
   :koodisto (when-let [koodisto (z/xml-> data :koodisto :koodi parsi-koodisto)]
               (when (not (empty? koodisto)) koodisto))})

(defn parsi-ominaisuudet [data]
  (z/xml-> data :ominaisuudet :ominaisuus parsi-ominaisuus))

(defn parsi-tietolaji [data]
  (when-let [tietolaji (z/xml1-> data :ns2:tietolajit :ns2:tietolaji)]
    {:tunniste (parsi-tunniste tietolaji)
     :ominaisuudet (parsi-ominaisuudet tietolaji)}))

(defn parsi-tietueen-sijainti [data]
  (let [koordinaatit (z/xml1-> data :koordinaatit)
        linkki (z/xml1-> data :linkki)
        tie (z/xml1-> data :tie)]
    {:koordinaatit {:x (xml/parsi-desimaaliluku (z/xml1-> koordinaatit :x z/text))
                    :y (xml/parsi-desimaaliluku (z/xml1-> koordinaatit :y z/text))
                    :z (xml/parsi-desimaaliluku (z/xml1-> koordinaatit :z z/text))}
     :linkki {:id (xml/parsi-kokonaisluku (z/xml1-> linkki :id z/text))
              :marvo (xml/parsi-kokonaisluku (z/xml1-> linkki :marvo z/text))}
     :tie {:numero (xml/parsi-kokonaisluku (z/xml1-> tie :numero z/text))
           :aet (xml/parsi-kokonaisluku (z/xml1-> tie :aet z/text))
           :aosa (xml/parsi-kokonaisluku (z/xml1-> tie :aosa z/text))
           :let (xml/parsi-kokonaisluku (z/xml1-> tie :let z/text))
           :losa (xml/parsi-kokonaisluku (z/xml1-> tie :losa z/text))
           :ajr (xml/parsi-kokonaisluku (z/xml1-> tie :ajr z/text))
           :puoli (xml/parsi-kokonaisluku (z/xml1-> tie :puoli z/text))
           :alkupvm (xml/parsi-paivamaara (z/xml1-> tie :alkupvm z/text))}}))

(defn parsi-tietueen-tietolaji [data]
  {:tietolajitunniste (z/xml1-> data :tietolajitunniste z/text)
   :arvot (xml/raakateksti data :arvot)})

(defn parsi-tietueet [data]
  (let [tietueet (z/xml-> data :ns2:tietueet :ns2:tietue)]
    (doall
      (for [tietue tietueet]
        (when tietue
          {:tunniste (z/xml1-> tietue :tunniste z/text)
           :alkupvm (xml/parsi-paivamaara (z/xml1-> tietue :alkupvm z/text))
           :loppupvm (xml/parsi-paivamaara (z/xml1-> tietue :loppupvm z/text))
           :karttapvm (xml/parsi-paivamaara (z/xml1-> tietue :karttapvm z/text))
           :piiri (z/xml1-> tietue :piiri z/text)
           :kuntoluokka (z/xml1-> tietue :kuntoluokka z/text)
           :urakka (xml/parsi-kokonaisluku (z/xml1-> tietue :urakka z/text))
           :sijainti (parsi-tietueen-sijainti (z/xml1-> tietue :sijainti))
           :tietolaji (parsi-tietueen-tietolaji (z/xml1-> tietue :tietolaji))})))))

(defn parsi-tietueotsikon-sijainti [data]
  (let [tie (z/xml1-> data :tie)]
    {:tie {:numero (xml/parsi-kokonaisluku (z/xml1-> tie :numero z/text))
           :aet (xml/parsi-kokonaisluku (z/xml1-> tie :aet z/text))
           :aosa (xml/parsi-kokonaisluku (z/xml1-> tie :aosa z/text))
           :let (xml/parsi-kokonaisluku (z/xml1-> tie :let z/text))
           :losa (xml/parsi-kokonaisluku (z/xml1-> tie :losa z/text))
           :ajr (xml/parsi-kokonaisluku (z/xml1-> tie :ajr z/text))
           :puoli (xml/parsi-kokonaisluku (z/xml1-> tie :puoli z/text))
           :alkupvm (xml/parsi-paivamaara (z/xml1-> tie :alkupvm z/text))}}))

(defn parsi-tietueotsikot [data]
  (let [tietueet (z/xml-> data :ns2:tietueotsikot :ns2:tietueotsikko)]
    (doall
      (for [tietue tietueet]
        (when tietue
          {:tunniste (z/xml1-> tietue :tunniste z/text)
           :alkupvm (xml/parsi-paivamaara (z/xml1-> tietue :alkupvm z/text))
           :loppupvm (xml/parsi-paivamaara (z/xml1-> tietue :loppupvm z/text))
           :karttapvm (xml/parsi-paivamaara (z/xml1-> tietue :karttapvm z/text))
           :piiri (z/xml1-> tietue :piiri z/text)
           :kuntoluokka (z/xml1-> tietue :kuntoluokka z/text)
           :urakka (xml/parsi-kokonaisluku (z/xml1-> tietue :urakka z/text))
           :sijainti (parsi-tietueotsikon-sijainti (z/xml1-> tietue :sijainti))})))))

(defn lue [viesti]
  (let [data (xml/lue viesti)
        vastaus {:onnistunut (onnistunut-vastaus? data)}]
    (cond-> vastaus

            (z/xml1-> data :ns2:virheet)
            (assoc :virheet (parsi-virheet data))

            (z/xml1-> data :ns2:tietolajit)
            (assoc :tietolaji (parsi-tietolaji data))

            (z/xml1-> data :ns2:tietueotsikot)
            (assoc :tietueotsikot (vec (map #(assoc {} :tietueotsikko %) (parsi-tietueotsikot data))))

            true
            (assoc :tietueet (vec (map #(assoc {} :tietue %) (parsi-tietueet data)))))))
