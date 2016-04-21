(ns harja.palvelin.komponentit.fim
  "Komponentti FIM käyttäjätietojen hakemiseen."
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma])
  (:import (java.io ByteArrayInputStream)))

;; Kentät, joita voidaan hakea:
;; ObjectID EmployeeEndDate
;; AccountName FirstName MiddleName LastName
;; JobTitle Company OfficePhone OfficeLocation Toimiala Department Yksikko
;; MobilePhone Email City roomnumber Manager KayttonimiPaivystysnumero

(def +fim-elementit+
  "Mäppäys FIM elementeistä suomenkielisiin avaimiin ja mahdollisiin prosessointeihin"
  {:ObjectID :tunniste
   :AccountName :kayttajatunnus
   :FirstName :etunimi
   :LastName :sukunimi
   :Email :sahkoposti
   :MobilePhone [:puhelin #(str/replace % " " "")]
   :Role :roolit
   :Company :organisaatio})

(defn lue-fim-vastaus
  "Lukee FIM REST vastaus annetusta XML zipperistä. Palauttaa sekvenssin urakan käyttäjiä."
  [xml]
  (z/xml-> xml
           :member
           (fn [p]
             (into {}
                   (map (fn [[elementti avain]]
                          (if (vector? avain)
                            (let [[avain muunnos] avain]
                              [avain (z/xml1-> p elementti z/text muunnos)])
                            [avain (z/xml1-> p elementti z/text)])))
                   +fim-elementit+))))

(defn lue-xml [bytet]
  (xml-zip (parse (ByteArrayInputStream. bytet))))

(defn- urakan-kayttajat-parametrit [urakan-sampo-id]
  {:filter (str "SopimusID=" urakan-sampo-id)
   :ignorecache "false"
   :fetch "AccountName,FirstName,LastName,DisplayName,Email,MobilePhone,Company"})

(defn hae-urakan-kayttajat
  "Hakee urakkaan liitetyt käyttäjät."
  [{:keys [url db integraatioloki]} urakan-sampo-id]
  (when-not (empty? url)
    (integraatiotapahtuma/suorita-integraatio
     db integraatioloki "fim" "hae-urakan-kayttajat"
     #(-> (integraatiotapahtuma/laheta
           % :http {:metodi :GET
                    :url url
                    :parametrit (urakan-kayttajat-parametrit urakan-sampo-id)})
          :body
          lue-xml
          lue-fim-vastaus))))

(defrecord FIM [url]
  component/Lifecycle
  (start [this]
    this)

  (stop [this]
    this))
