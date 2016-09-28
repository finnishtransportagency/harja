(ns harja.palvelin.integraatiot.integraatiopisteet.jms
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.integraatiot :as q])
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
         (lokittaja :jms-viesti tapahtuma-id (or viesti-id jms-viesti-id) "ulos" viesti jono)
         (kasittele-epaonnistunut-lahetys lokittaja tapahtuma-id virheviesti))
       (catch Exception poikkeus
         (log/error poikkeus "Virhe JMS lähetyksessä jonoon: " jono)
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
                          (lokittaja :saapunut-jms-kuittaus viesti-id viestin-sisalto onnistunut jono)
                          (log/error "Kuittauksesta ei voitu hakea viesti-id:tä."))
                        (kasittelija data viesti-id onnistunut))))
    (catch Exception e
      (log/error e "Jono: %s kuittauskuuntelijassa tapahtui poikkeus."))))

(defn kuuntele-ja-kuittaa [lokittaja sonja jono-sisaan jono-ulos viestiparseri kuittausmuodostaja kasittelija]
  (log/debug "Käynnistetään JMS kuuntelija jonolle: " jono-sisaan ", kuittaukset lähetetään jonoon: " jono-ulos)
  (try
    (sonja/kuuntele
      sonja jono-sisaan
      (fn [viesti]
        (log/debug "Vastaanotettiin viesti jonosta " jono-sisaan ": " viesti)
        (let [viestin-sisalto (.getText viesti)
              ulkoinen-id (.getJMSCorrelationID viesti)
              tapahtuma-id (lokittaja :saapunut-jms-viesti ulkoinen-id viestin-sisalto jono-sisaan)
              [onnistui? data] (try
                                 [true (viestiparseri viestin-sisalto)]
                                 (catch Exception e
                                   [false e]))]
          (log/debug "Viesti parsinta onnistui? " onnistui?)
          (if onnistui?
            ;; Viestin parsinta onnistui, yritetään käsitellä se
            (try
              (let [vastaus (kasittelija data)
                    vastauksen-sisalto (kuittausmuodostaja vastaus)]
                (lokittaja :lahteva-jms-kuittaus vastauksen-sisalto tapahtuma-id true "" jono-ulos)
                (sonja/laheta sonja jono-ulos vastauksen-sisalto {:correlation-id ulkoinen-id}))
              (catch Exception e
                ;; Hallitsematon virhe viestin käsittelyssä, kirjataan epäonnistunut integraatio
                (lokittaja :epaonnistunut viestin-sisalto "" tapahtuma-id ulkoinen-id)))

            ;; Viestin parsinta epäonnistui, kirjataan suoraan epäonnistunut integraatio
            (lokittaja :epaonnistunut viestin-sisalto (str "Viestin lukeminen epäonnistui" (.getMessage data))
                       tapahtuma-id ulkoinen-id)))))))
