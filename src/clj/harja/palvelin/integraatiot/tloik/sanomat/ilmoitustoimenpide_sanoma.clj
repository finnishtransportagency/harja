(ns harja.palvelin.integraatiot.tloik.sanomat.ilmoitustoimenpide-sanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]])
  (:import (java.text SimpleDateFormat)
           (java.util UUID)))

(def +xsd-polku+ "xsd/tloik/")

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn formatoi-paivamaara [date]
  (when date (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.S") date)))

(defn muodosta-henkilo [data]
  (when data
    [:henkilo
     [:etunimi (:etunimi data)]
     [:sukunimi (:sukunimi data)]
     [:matkapuhelin (:matkapuhelin data)]
     [:sahkoposti (:sahkoposti data)]]))

(defn muodosta-organisaatio [data]
  (when data
    [:organisaatio
     [:nimi (:organisaatio data)]
     [:ytunnus (:ytunnus data)]]))

(defn muodosta-viesti [data]
  [:harja:toimenpide
   {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
   [:viestiId (str (UUID/randomUUID))]
   [:ilmoitusId (:ilmoitusid data)]
   [:tyyppi (:kuittaustyyppi data)]
   [:aika (formatoi-paivamaara (:kuitattu data))]
   [:vapaateksti (:vapaateksti data)]
   [:kasittelija
    (muodosta-henkilo (:kasittelija data))
    (muodosta-organisaatio (:kasittelija data))]
   [:ilmoittaja
    (muodosta-henkilo (:kuittaaja data))
    (muodosta-organisaatio (:kuittaaja data))]])

(defn muodosta [data]
  (let [sisalto (muodosta-viesti data)
        xml (tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "harja-tloik.xsd" xml)
      xml
      (do
        (log/error "Ilmoitustoimenpidettä ei voida lähettää. XML ei ole validia.")
        nil))))
