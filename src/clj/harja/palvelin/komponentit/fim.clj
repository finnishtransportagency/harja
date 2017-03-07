(ns harja.palvelin.komponentit.fim
  "Komponentti FIM-käyttäjätietojen hakemiseen."
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet])
  (:import (java.io ByteArrayInputStream)))

(defprotocol FIMHaku
  (hae-urakan-kayttajat
    [this sampoid]
    "Hakee urakkaan liitetyt käyttäjät."))

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
   :Company :organisaatio
   :Disabled [:poistettu #(= "true" %)]})

(defn- roolien-kuvaukset-ja-nimet [roolit urakan-sampo-id]
  (and
    roolit
    (let [roolit (into []
                       (comp
                         (filter #(str/starts-with? % urakan-sampo-id))
                         (map #(subs % (inc (count urakan-sampo-id))))
                         (map #(oikeudet/roolit %))
                         (remove nil?))
                       (str/split roolit #","))]
      {:roolit (mapv :kuvaus roolit)
       :roolinimet (mapv :nimi roolit)})))

(defn- kuvaa-roolit [henkilot urakan-sampo-id]
  (map
    #(merge %
            (roolien-kuvaukset-ja-nimet (:roolit %) urakan-sampo-id))
    (remove #(true? (:poistettu %)) henkilot)))

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
                              [avain (muunnos (z/xml1-> p elementti z/text))])
                            [avain (z/xml1-> p elementti z/text)])))
                   +fim-elementit+))))

(defn lue-xml [bytet]
  (xml-zip (parse (ByteArrayInputStream. (.getBytes bytet)))))

(defn- urakan-kayttajat-parametrit [urakan-sampo-id]
  {:filter (str "SopimusID=" urakan-sampo-id)
   :ignorecache "false"
   :fetch "AccountName,FirstName,LastName,DisplayName,Email,MobilePhone,Company,Disabled"})


(defn suodata-kayttajaroolit
  "Suodattaa käyttäjät, jotka kuuluvat ainakin yhteen annetuista rooleista (setti)."
  [kayttajat pidettavat-roolit]
  (filter
    (fn [kayttaja]
      (let [kayttajan-roolit (into #{} (map
                                         str/lower-case
                                         (:roolit kayttaja)))
            pidettavat-roolit (into #{} (map
                                          str/lower-case
                                          pidettavat-roolit))]
        (some? (some pidettavat-roolit kayttajan-roolit))))
    kayttajat))

(defrecord FIM [url]
  component/Lifecycle
  (start [this]
    this)

  (stop [this]
    this)

  FIMHaku
  (hae-urakan-kayttajat
    [{:keys [url db integraatioloki]} urakan-sampo-id]
    (assert urakan-sampo-id "Urakan sampo-id puutuu!")
    (when-not (empty? url)
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "fim" "hae-urakan-kayttajat"
        #(-> (integraatiotapahtuma/laheta
               % :http {:metodi :GET
                        :url url
                        :parametrit (urakan-kayttajat-parametrit urakan-sampo-id)})
             :body
             lue-xml
             lue-fim-vastaus
             (kuvaa-roolit urakan-sampo-id))))))

(defrecord FakeFIM [tiedosto]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  FIMHaku
  (hae-urakan-kayttajat [_ sampoid]
    (kuvaa-roolit (get (read-string (slurp tiedosto)) sampoid) sampoid)))

(defn hae-urakan-kayttajat-jotka-roolissa [this sampo-id roolit-set]
  (let [urakan-kayttajat (hae-urakan-kayttajat this sampo-id)
        kayttajat-roolissa (suodata-kayttajaroolit urakan-kayttajat roolit-set)]
    kayttajat-roolissa))
