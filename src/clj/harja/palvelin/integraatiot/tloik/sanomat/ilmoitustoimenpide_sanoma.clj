(ns harja.palvelin.integraatiot.tloik.sanomat.ilmoitustoimenpide-sanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]])
  (:import (java.text SimpleDateFormat)
           (java.util TimeZone)))

(def +xsd-polku+ "xsd/tloik/")

(defn formatoi-paivamaara [date]
  (when date
    (let [dateformat (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss")]
      ;; T-LOIK:n lähetetään ajat GMT+0 aikavyöhykkeellä
      (.setTimeZone dateformat (TimeZone/getTimeZone "GMT"))
      (.format dateformat date))))

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

(defn muodosta-viesti [{:keys [ilmoitusid kuittaustyyppi kuitattu vakiofraasi vapaateksti
                               kasittelija kuittaaja]} viesti-id]
  [:harja:toimenpide
   {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
   [:viestiId viesti-id]
   [:ilmoitusId ilmoitusid]
   [:tyyppi kuittaustyyppi]
   [:aika (formatoi-paivamaara kuitattu)]
   [:vapaateksti
    (str (when vakiofraasi
           (str vakiofraasi " "))
         vapaateksti)]
   [:kasittelija
    (muodosta-henkilo kasittelija)
    (muodosta-organisaatio kasittelija)]
   [:ilmoittaja
    (muodosta-henkilo kuittaaja)
    (muodosta-organisaatio kuittaaja)]])

(defn muodosta [data viesti-id]
  (let [sisalto (muodosta-viesti data viesti-id)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml)
      xml
      (do
        (log/error "Ilmoitustoimenpidettä ei voida lähettää. XML ei ole validia.")
        nil))))
