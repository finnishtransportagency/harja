(ns harja.palvelin.integraatiot.integraatiopisteet.jms
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-epaonnistunut-lahetys [lokittaja tapahtuma-id virheviesti]
  (log/error virheviesti)
  (lokittaja :epaonnistunut virheviesti nil tapahtuma-id nil)
  (virheet/heita-sisainen-kasittelyvirhe-poikkeus
    {:koodi :sonja-lahetys-epaonnistui :viesti virheviesti}))

(defn kasittele-poikkeus-lahetyksessa [lokittaja tapahtuma-id poikkeus virheviesti]
  (log/error poikkeus virheviesti)
  (lokittaja :epaonnistunut virheviesti (format "Poikkeus: %s" (.getMessage poikkeus)) tapahtuma-id nil)
  (virheet/heita-sisainen-kasittelyvirhe-poikkeus
    {:koodi :sonja-lahetys-epaonnistui :viesti (format "Poikkeus: %s" poikkeus)}))

(defn laheta-jonoon
  ([lokittaja sonja jono viesti] (laheta-jonoon lokittaja sonja jono viesti nil))
  ([lokittaja sonja jono viesti viesti-id]
   (log/debug (format "Lähetetään JMS jonoon: %s viesti: %s." jono viesti))
   (let [tapahtuma-id (lokittaja :alkanut nil nil)
         virheviesti (format "Lähetys JMS jonoon: %s epäonnistui." jono)]
     (try
       (if-let [jms-viesti-id (sonja/laheta sonja jono viesti)]
         (lokittaja :jms-viesti tapahtuma-id (or viesti-id jms-viesti-id) "ulos" viesti)
         (kasittele-epaonnistunut-lahetys lokittaja tapahtuma-id virheviesti))
       (catch Exception poikkeus
         (kasittele-poikkeus-lahetyksessa lokittaja tapahtuma-id poikkeus virheviesti))))))

(defn jonolahettaja [lokittaja sonja jono]
  (fn [viesti viesti-id]
    (laheta-jonoon lokittaja sonja jono viesti viesti-id)))

(defn kuittausjonokuuntelija [lokittaja sonja jono viestiparseri viesti->id onnistunut? kasittelija]
  (log/debug "Käynnistetään JMS viestikuuntelija kuuntelemaan jonoa: " jono)
  (try
    (sonja/kuuntele sonja jono
                    (fn [viesti]
                      (log/debug (format "Vastaanotettiin jonosta: %s viesti: %s" jono viesti))
                      (let [viestin-sisalto (.getText viesti)
                            data (viestiparseri viestin-sisalto)
                            viesti-id (viesti->id data)
                            onnistunut (onnistunut? data)]
                        (if viesti-id
                          (lokittaja :saapunut-jms-kuittaus viesti-id viestin-sisalto onnistunut)
                          (log/error "Kuittauksesta ei voitu hakea viesti-id:tä."))
                        (kasittelija data viesti-id onnistunut))))
    (catch Exception e
      (log/error e "Jono: %s kuittauskuuntelijassa tapahtui poikkeus."))))
