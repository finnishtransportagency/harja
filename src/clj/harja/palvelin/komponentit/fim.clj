(ns harja.palvelin.komponentit.fim
  "Komponentti FIM käyttäjätietojen hakemiseen."
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]))

(def +fim-elementit+
  "Mäppäys FIM elementeistä suomenkielisiin avaimiin"
  {:ObjectID :tunniste
   :AccountName :kayttajatunnus
   :FirstName :etunimi
   :LastName :sukunimi
   :Email :email
   :MobilePhone :puhelin
   :Company :organisaatio})

(defn lue-fim-vastaus
  "Lukee FIM REST vastaus annetusta XML zipperistä. Palauttaa sekvenssin käyttäjä mäppejä."
  [xml]
  (z/xml-> xml
           :person
           (fn [p]
             (into {}
                   (map (fn [[elementti avain]]
                          [avain (z/xml1-> p elementti z/text)]))
                   +fim-elementit+))))

(defn lue-xml [string]
  (xml-zip (parse (java.io.ByteArrayInputStream. (.getBytes string)))))
