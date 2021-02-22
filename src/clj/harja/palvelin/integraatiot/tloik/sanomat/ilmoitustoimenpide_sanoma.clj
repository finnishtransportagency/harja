(ns harja.palvelin.integraatiot.tloik.sanomat.ilmoitustoimenpide-sanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.tyokalut.merkkijono :as merkkijono])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/tloik/")

(defn muodosta-henkilo [data]
  (when data
    [:henkilo
     [:etunimi (merkkijono/leikkaa 32 (xml/escape-xml-varten (:etunimi data)))]
     [:sukunimi (merkkijono/leikkaa 32 (xml/escape-xml-varten (:sukunimi data)))]
     [:matkapuhelin (merkkijono/leikkaa 32 (:matkapuhelin data))]
     [:sahkoposti (merkkijono/leikkaa 64 (:sahkoposti data))]]))

(defn muodosta-organisaatio [data]
  (when data
    [:organisaatio
     [:nimi (xml/escape-xml-varten (:organisaatio data))]
     [:ytunnus (:ytunnus data)]]))

(defn muodosta-vapaateksti [vakiofraasi vapaateksti]
  (let [vapaateksti (merkkijono/leikkaa 1024 (str (when vakiofraasi (str vakiofraasi " ")) vapaateksti))]
    (xml/tee-c-data-elementti-tarvittaessa vapaateksti)))

(defn muodosta-viesti [{:keys [ilmoitusid kuittaustyyppi kuitattu vakiofraasi vapaateksti kasittelija kuittaaja]}
                       viesti-id]
  [:harja:toimenpide
   {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
   [:viestiId viesti-id]
   [:ilmoitusId ilmoitusid]
   [:tyyppi kuittaustyyppi]
   [:aika (xml/datetime->gmt-0-pvm kuitattu)]
   [:vapaateksti (muodosta-vapaateksti vakiofraasi vapaateksti)]
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
      (let [virheviesti "Ilmoitustoimenpidettä ei voida lähettää. XML ei ole validia."]
        (log/error virheviesti)
        (throw+ {:type virheet/+invalidi-xml+
                 :virheet [{:koodi :invalidi-ilmoitustoimenpide-xml
                            :viesti virheviesti}]})))))
